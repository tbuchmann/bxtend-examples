package de.tbuchmann.bxtend.ast2dag.rules;

import ast.Model;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import dag.DagPackage;
import dag.Operator;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem;
import java.util.Objects;
import java.util.function.Predicate;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;

/**
 * Transformation rule that synchronises {@code Number} leaf nodes between the AST and DAG.
 * 
 * <p>A {@code Number} is an operand leaf whose sole data attribute is its integer {@code value}.
 * Like {@link Variable2Variable}, this rule handles the DAG's <em>deduplication</em> semantics:
 * multiple {@code ast.Number} nodes with the same integer value may share a single
 * {@code dag.Number} node, so the correspondence is always a {@link MultiElem} (many-to-1).
 * 
 * <h2>Deduplication key</h2>
 * Two number nodes are considered <em>structurally equal</em> if their {@code value} attributes
 * are equal.  The private helper {@link #findTargetElem} searches the DAG model's flat expression
 * list for an existing {@code dag.Number} with the matching value.
 * 
 * <h2>Forward pass (AST → DAG)</h2>
 * <ol>
 *   <li>If the AST number has no correspondence yet, {@link #addToTargetElem} either finds an
 *       existing DAG number with the same value or creates a new one, then adds the AST number
 *       to the {@link MultiElem}.sourceElements list.</li>
 *   <li>If a correspondence exists and all source elements agree on the current value (unchanged),
 *       the rule checks whether another DAG number with the matching value already exists; if so,
 *       the AST number migrates to that correspondence.  Otherwise it updates the DAG value in-place.</li>
 *   <li>If the DAG number's value diverges from the AST number, the AST number is detached from
 *       its current correspondence and re-linked via {@link #addToTargetElem}.</li>
 * </ol>
 * 
 * <h2>Backward pass (DAG → AST)</h2>
 * For each {@code dag.Number}:
 * <ul>
 *   <li>Numbers with no parent operators map to a single AST number owned directly by the
 *       {@code ast.Model}.</li>
 *   <li>For each parent operator that references this number as a <em>left</em> child, one AST
 *       number copy is created (or reused) per corresponding AST operator copy, identified by
 *       the predicate {@code e.leftInverse == leftParent}.</li>
 *   <li>Symmetrically for parent operators that reference this number as a <em>right</em> child.</li>
 * </ul>
 */
@SuppressWarnings("all")
public class Number2Number extends Elem2Elem {
  /**
   * Constructs the rule and sets the rule identifier to {@code "number2number"}.
   * 
   * @param src  the source (AST) model resource
   * @param trgt the target (DAG) model resource
   * @param corr the correspondence model resource
   */
  public Number2Number(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "number2number";
  }

  /**
   * Forward pass: iterates all {@code ast.Number} nodes and ensures each is represented
   * in the DAG by a (possibly shared) {@code dag.Number} node with the same {@code value}.
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<ast.Number> _function = (ast.Number n) -> {
      Corr _corrModelElem = this.getCorrModelElem(n);
      final MultiElem corr = ((MultiElem) _corrModelElem);
      if ((corr == null)) {
        this.addToTargetElem(n);
      } else {
        EObject _targetElement = corr.getTargetElement();
        final dag.Number t = ((dag.Number) _targetElement);
        final Function1<EObject, Boolean> _function_1 = (EObject it) -> {
          return Boolean.valueOf(((it instanceof Number) && (((ast.Number) it).getValue() == n.getValue())));
        };
        boolean _forall = IterableExtensions.<EObject>forall(corr.getSourceElements(), _function_1);
        if (_forall) {
          final dag.Number newTarget = this.findTargetElem(n);
          if ((newTarget != null)) {
            Corr _corrModelElem_1 = this.getCorrModelElem(newTarget);
            EList<EObject> _sourceElements = ((MultiElem) _corrModelElem_1).getSourceElements();
            _sourceElements.add(n);
          } else {
            t.setValue(n.getValue());
          }
        }
        int _value = t.getValue();
        int _value_1 = n.getValue();
        boolean _notEquals = (_value != _value_1);
        if (_notEquals) {
          EList<EObject> _sourceElements_1 = corr.getSourceElements();
          _sourceElements_1.remove(n);
          this.addToTargetElem(n);
        }
      }
    };
    IteratorExtensions.<ast.Number>forEach(Iterators.<ast.Number>filter(this.sourceModel.getAllContents(), ast.Number.class), _function);
  }

  /**
   * Backward pass: iterates all {@code dag.Number} nodes and reconstructs the
   * corresponding AST number copies, one per parent operator reference.
   */
  @Override
  public void targetToSource() {
    final Procedure1<dag.Number> _function = (dag.Number n) -> {
      if ((n.getLeftInverse().isEmpty() && n.getRightInverse().isEmpty())) {
        Corr _orCreateCorrModelElement = this.getOrCreateCorrModelElement(n, this.ruleID);
        final MultiElem corr = ((MultiElem) _orCreateCorrModelElement);
        final Predicate<EObject> _function_1 = (EObject it) -> {
          return true;
        };
        EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getNumber(), _function_1);
        final ast.Number src = ((ast.Number) _orCreateSourceElem);
        src.setValue(n.getValue());
        EObject _sourceElement = this.getCorrModelElem(n.getModel()).getSourceElement();
        src.setModel(((Model) _sourceElement));
      }
      EList<Operator> _leftInverse = n.getLeftInverse();
      for (final Operator left : _leftInverse) {
        {
          Corr _orCreateCorrModelElement_1 = this.getOrCreateCorrModelElement(left, this.ruleID);
          final MultiElem parentCorr = ((MultiElem) _orCreateCorrModelElement_1);
          EList<EObject> _sourceElements = parentCorr.getSourceElements();
          for (final EObject leftParent : _sourceElements) {
            {
              Corr _orCreateCorrModelElement_2 = this.getOrCreateCorrModelElement(n, this.ruleID);
              final MultiElem corr_1 = ((MultiElem) _orCreateCorrModelElement_2);
              final Predicate<EObject> _function_2 = (EObject e) -> {
                ast.Operator _leftInverse_1 = ((ast.Number) e).getLeftInverse();
                return Objects.equals(_leftInverse_1, leftParent);
              };
              EObject _orCreateSourceElem_1 = this.getOrCreateSourceElem(corr_1, 
                this.sourcePackage.getNumber(), _function_2);
              final ast.Number src_1 = ((ast.Number) _orCreateSourceElem_1);
              src_1.setValue(n.getValue());
              src_1.setLeftInverse(((ast.Operator) leftParent));
            }
          }
        }
      }
      EList<Operator> _rightInverse = n.getRightInverse();
      for (final Operator right : _rightInverse) {
        {
          Corr _orCreateCorrModelElement_1 = this.getOrCreateCorrModelElement(right, this.ruleID);
          final MultiElem parentCorr = ((MultiElem) _orCreateCorrModelElement_1);
          EList<EObject> _sourceElements = parentCorr.getSourceElements();
          for (final EObject rightParent : _sourceElements) {
            {
              Corr _orCreateCorrModelElement_2 = this.getOrCreateCorrModelElement(n, this.ruleID);
              final MultiElem corr_1 = ((MultiElem) _orCreateCorrModelElement_2);
              final Predicate<EObject> _function_2 = (EObject e) -> {
                ast.Operator _rightInverse_1 = ((ast.Number) e).getRightInverse();
                return Objects.equals(_rightInverse_1, rightParent);
              };
              EObject _orCreateSourceElem_1 = this.getOrCreateSourceElem(corr_1, 
                this.sourcePackage.getNumber(), _function_2);
              final ast.Number src_1 = ((ast.Number) _orCreateSourceElem_1);
              src_1.setValue(n.getValue());
              src_1.setRightInverse(((ast.Operator) rightParent));
            }
          }
        }
      }
    };
    IteratorExtensions.<dag.Number>forEach(Iterators.<dag.Number>filter(this.targetModel.getAllContents(), dag.Number.class), _function);
  }

  /**
   * Finds or creates a {@code dag.Number} for the given AST number and registers the
   * link in the correspondence model.
   * 
   * <p>If a {@code dag.Number} with the same value already exists in the DAG model's
   * {@code exprs} list, the AST number is added to that node's correspondence (sharing
   * the existing DAG node).  Otherwise a new {@code dag.Number} is created and placed in
   * the DAG model.
   * 
   * @param e the AST number to map into the DAG
   */
  private Corr addToTargetElem(final ast.Number e) {
    Corr _xblockexpression = null;
    {
      dag.Number newTarget = this.findTargetElem(e);
      if ((newTarget == null)) {
        EObject _createTargetElement = this.createTargetElement(DagPackage.eINSTANCE.getNumber());
        newTarget = ((dag.Number) _createTargetElement);
      }
      Corr _orCreateCorrModelElement = this.getOrCreateCorrModelElement(newTarget, this.ruleID);
      final MultiElem newCorr = ((MultiElem) _orCreateCorrModelElement);
      EList<EObject> _sourceElements = newCorr.getSourceElements();
      _sourceElements.add(e);
      newTarget.setValue(e.getValue());
      EObject _targetElement = this.getCorrModelElem(e.getModel()).getTargetElement();
      newTarget.setModel(((dag.Model) _targetElement));
      _xblockexpression = this.put(Elem2Elem.elementsToCorr, newCorr);
    }
    return _xblockexpression;
  }

  /**
   * Searches the DAG model's flat expression list for an existing {@code dag.Number}
   * whose {@code value} matches that of the given AST number.
   * 
   * @param e the AST number whose value is used as the search key
   * @return the matching {@code dag.Number}, or {@code null} if none exists
   */
  private dag.Number findTargetElem(final ast.Number e) {
    EObject _targetElement = this.getCorrModelElem(e.getModel()).getTargetElement();
    final Function1<dag.Number, Boolean> _function = (dag.Number it) -> {
      int _value = it.getValue();
      int _value_1 = e.getValue();
      return Boolean.valueOf((_value == _value_1));
    };
    return IterableExtensions.<dag.Number>findFirst(Iterables.<dag.Number>filter(((dag.Model) _targetElement).getExprs(), dag.Number.class), _function);
  }
}
