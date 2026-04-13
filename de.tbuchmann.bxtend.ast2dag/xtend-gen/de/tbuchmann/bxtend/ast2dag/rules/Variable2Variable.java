package de.tbuchmann.bxtend.ast2dag.rules;

import ast.Model;
import ast.Variable;
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
 * Transformation rule that synchronises {@code Variable} leaf nodes between the AST and DAG.
 * 
 * <p>A {@code Variable} is an operand leaf whose sole data attribute is its string {@code name}.
 * Because identical variable names in the AST are deduplicated into a single DAG node, the
 * correspondence for a {@code dag.Variable} is a {@link MultiElem} that may reference several
 * {@code ast.Variable} instances (one for each occurrence in the expression tree).
 * 
 * <h2>Deduplication key</h2>
 * Two variables are considered <em>equal</em> and thus share a DAG node if and only if their
 * {@code name} attributes are equal.  The helper {@link #findTargetElem} searches the DAG
 * model's flat expression list for a {@code dag.Variable} with the matching name.
 * 
 * <h2>Forward pass (AST → DAG)</h2>
 * <ol>
 *   <li>If the AST variable has no correspondence yet, {@link #addToTargetElem} either finds
 *       an existing DAG variable with the same name or creates a new one, then links the AST
 *       variable into the {@link MultiElem}.sourceElements list.</li>
 *   <li>If a correspondence exists and <em>all</em> source elements in that correspondence
 *       agree on the current name (i.e. the name has not been changed), the rule checks
 *       whether a different DAG variable with the matching name already exists; if so, the
 *       AST variable migrates to that correspondence. Otherwise it updates the DAG name
 *       in-place.</li>
 *   <li>If the name of the DAG variable no longer matches the AST variable, the AST variable
 *       is removed from the current correspondence and re-added via {@link #addToTargetElem}
 *       to find or create the correct target node.</li>
 * </ol>
 * 
 * <h2>Backward pass (DAG → AST)</h2>
 * For each {@code dag.Variable}:
 * <ul>
 *   <li>Variables with no parent operators (i.e. not referenced from any {@code Operator.left}
 *       or {@code Operator.right}) are mapped to a single AST variable owned directly by the
 *       {@code ast.Model}.</li>
 *   <li>For each parent operator that references this variable as a <em>left</em> child, one
 *       AST variable is created (or reused) per corresponding AST operator copy, identified by
 *       the predicate {@code e.leftInverse == leftParent}.</li>
 *   <li>Symmetrically for parent operators that reference this variable as a <em>right</em> child.</li>
 * </ul>
 */
@SuppressWarnings("all")
public class Variable2Variable extends Elem2Elem {
  /**
   * Constructs the rule and sets the rule identifier to {@code "variable2variable"}.
   * 
   * @param src  the source (AST) model resource
   * @param trgt the target (DAG) model resource
   * @param corr the correspondence model resource
   */
  public Variable2Variable(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "variable2variable";
  }

  /**
   * Forward pass: iterates all {@code ast.Variable} nodes and ensures each is
   * represented in the DAG by a (possibly shared) {@code dag.Variable} node with the
   * same {@code name}.
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<Variable> _function = (Variable v) -> {
      Corr _corrModelElem = this.getCorrModelElem(v);
      final MultiElem corr = ((MultiElem) _corrModelElem);
      if ((corr == null)) {
        this.addToTargetElem(v);
      } else {
        EObject _targetElement = corr.getTargetElement();
        final dag.Variable t = ((dag.Variable) _targetElement);
        final Function1<EObject, Boolean> _function_1 = (EObject it) -> {
          return Boolean.valueOf(((it instanceof Variable) && Objects.equals(((Variable) it).getName(), v.getName())));
        };
        boolean _forall = IterableExtensions.<EObject>forall(corr.getSourceElements(), _function_1);
        if (_forall) {
          final dag.Variable newTarget = this.findTargetElem(v);
          if ((newTarget != null)) {
            Corr _corrModelElem_1 = this.getCorrModelElem(newTarget);
            EList<EObject> _sourceElements = ((MultiElem) _corrModelElem_1).getSourceElements();
            _sourceElements.add(v);
          } else {
            t.setName(v.getName());
          }
        }
        String _name = t.getName();
        String _name_1 = v.getName();
        boolean _notEquals = (!Objects.equals(_name, _name_1));
        if (_notEquals) {
          EList<EObject> _sourceElements_1 = corr.getSourceElements();
          _sourceElements_1.remove(v);
          this.addToTargetElem(v);
        }
      }
    };
    IteratorExtensions.<Variable>forEach(Iterators.<Variable>filter(this.sourceModel.getAllContents(), Variable.class), _function);
  }

  /**
   * Backward pass: iterates all {@code dag.Variable} nodes and reconstructs the
   * corresponding AST variable copies, one per parent operator reference.
   */
  @Override
  public void targetToSource() {
    final Procedure1<dag.Variable> _function = (dag.Variable v) -> {
      if ((v.getLeftInverse().isEmpty() && v.getRightInverse().isEmpty())) {
        Corr _orCreateCorrModelElement = this.getOrCreateCorrModelElement(v, this.ruleID);
        final MultiElem corr = ((MultiElem) _orCreateCorrModelElement);
        final Predicate<EObject> _function_1 = (EObject it) -> {
          return true;
        };
        EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getVariable(), _function_1);
        final Variable src = ((Variable) _orCreateSourceElem);
        src.setName(v.getName());
        EObject _sourceElement = this.getCorrModelElem(v.getModel()).getSourceElement();
        src.setModel(((Model) _sourceElement));
      }
      EList<Operator> _leftInverse = v.getLeftInverse();
      for (final Operator left : _leftInverse) {
        {
          Corr _orCreateCorrModelElement_1 = this.getOrCreateCorrModelElement(left, this.ruleID);
          final MultiElem parentCorr = ((MultiElem) _orCreateCorrModelElement_1);
          EList<EObject> _sourceElements = parentCorr.getSourceElements();
          for (final EObject leftParent : _sourceElements) {
            {
              Corr _orCreateCorrModelElement_2 = this.getOrCreateCorrModelElement(v, this.ruleID);
              final MultiElem corr_1 = ((MultiElem) _orCreateCorrModelElement_2);
              final Predicate<EObject> _function_2 = (EObject e) -> {
                ast.Operator _leftInverse_1 = ((Variable) e).getLeftInverse();
                return Objects.equals(_leftInverse_1, leftParent);
              };
              EObject _orCreateSourceElem_1 = this.getOrCreateSourceElem(corr_1, this.sourcePackage.getVariable(), _function_2);
              final Variable src_1 = ((Variable) _orCreateSourceElem_1);
              src_1.setName(v.getName());
              src_1.setLeftInverse(((ast.Operator) leftParent));
            }
          }
        }
      }
      EList<Operator> _rightInverse = v.getRightInverse();
      for (final Operator right : _rightInverse) {
        {
          Corr _orCreateCorrModelElement_1 = this.getOrCreateCorrModelElement(right, this.ruleID);
          final MultiElem parentCorr = ((MultiElem) _orCreateCorrModelElement_1);
          EList<EObject> _sourceElements = parentCorr.getSourceElements();
          for (final EObject rightParent : _sourceElements) {
            {
              Corr _orCreateCorrModelElement_2 = this.getOrCreateCorrModelElement(v, this.ruleID);
              final MultiElem corr_1 = ((MultiElem) _orCreateCorrModelElement_2);
              final Predicate<EObject> _function_2 = (EObject e) -> {
                ast.Operator _rightInverse_1 = ((Variable) e).getRightInverse();
                return Objects.equals(_rightInverse_1, rightParent);
              };
              EObject _orCreateSourceElem_1 = this.getOrCreateSourceElem(corr_1, this.sourcePackage.getVariable(), _function_2);
              final Variable src_1 = ((Variable) _orCreateSourceElem_1);
              src_1.setName(v.getName());
              src_1.setRightInverse(((ast.Operator) rightParent));
            }
          }
        }
      }
    };
    IteratorExtensions.<dag.Variable>forEach(Iterators.<dag.Variable>filter(this.targetModel.getAllContents(), dag.Variable.class), _function);
  }

  /**
   * Finds or creates a {@code dag.Variable} for the given AST variable and registers the
   * link in the correspondence model.
   * 
   * <p>If a {@code dag.Variable} with the same name already exists in the DAG model, the
   * AST variable is added to that node's {@link MultiElem}.sourceElements (sharing the
   * existing DAG node).  Otherwise, a new {@code dag.Variable} is created and placed in
   * the DAG model's {@code exprs} list.
   * 
   * @param e the AST variable to map into the DAG
   */
  private Corr addToTargetElem(final Variable e) {
    Corr _xblockexpression = null;
    {
      dag.Variable newTarget = this.findTargetElem(e);
      if ((newTarget == null)) {
        EObject _createTargetElement = this.createTargetElement(DagPackage.eINSTANCE.getVariable());
        newTarget = ((dag.Variable) _createTargetElement);
      }
      Corr _orCreateCorrModelElement = this.getOrCreateCorrModelElement(newTarget, this.ruleID);
      final MultiElem newCorr = ((MultiElem) _orCreateCorrModelElement);
      EList<EObject> _sourceElements = newCorr.getSourceElements();
      _sourceElements.add(e);
      newTarget.setName(e.getName());
      EObject _targetElement = this.getCorrModelElem(e.getModel()).getTargetElement();
      newTarget.setModel(((dag.Model) _targetElement));
      _xblockexpression = this.put(Elem2Elem.elementsToCorr, newCorr);
    }
    return _xblockexpression;
  }

  /**
   * Searches the DAG model's flat expression list for an existing {@code dag.Variable}
   * whose {@code name} matches that of the given AST variable.
   * 
   * @param e the AST variable whose name is used as the search key
   * @return the matching {@code dag.Variable}, or {@code null} if none exists
   */
  private dag.Variable findTargetElem(final Variable e) {
    EObject _targetElement = this.getCorrModelElem(e.getModel()).getTargetElement();
    final Function1<dag.Variable, Boolean> _function = (dag.Variable it) -> {
      String _name = it.getName();
      String _name_1 = e.getName();
      return Boolean.valueOf(Objects.equals(_name, _name_1));
    };
    return IterableExtensions.<dag.Variable>findFirst(Iterables.<dag.Variable>filter(((dag.Model) _targetElement).getExprs(), dag.Variable.class), _function);
  }
}
