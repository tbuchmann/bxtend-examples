package de.tbuchmann.bxtend.ast2dag.rules;

import ast.Model;
import ast.Operator;
import ast.Variable;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import dag.ArithmeticOperator;
import dag.DagPackage;
import dag.Expression;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import org.eclipse.xtext.xbase.lib.XbaseGenerated;

/**
 * Transformation rule that synchronises {@code Operator} (interior) nodes between the AST and DAG.
 * 
 * <p>An {@code Operator} is a binary node with a left child, a right child, and an
 * {@code op : ArithmeticOperator} attribute (Add, Subtract, Multiply, Divide).  This rule is the
 * most complex one in the transformation because it must handle the central structural mismatch
 * between the two metamodels:
 * <ul>
 *   <li>In the <b>AST</b>, every {@code Operator} node <em>contains</em> its children (containment
 *       references), so the model is a strict binary tree.</li>
 *   <li>In the <b>DAG</b>, the {@code left} and {@code right} references are non-containment
 *       cross-references, so a single child node can be shared by multiple parent operators,
 *       turning the structure into a DAG.</li>
 * </ul>
 * 
 * <h2>Deduplication key (forward direction)</h2>
 * Two AST subtrees are considered <em>structurally equal</em> — and therefore map to the
 * <em>same</em> DAG operator node — if they have the same operator type and their respective
 * left and right subtrees are also structurally equal (recursively).  This deep structural
 * comparison is performed by the dispatched {@link #equalsToWithChilds(Expression, Expression)}
 * family of methods.
 * 
 * <h2>Forward pass (AST → DAG)</h2>
 * The pass runs in two phases:
 * <ol>
 *   <li><b>Node mapping phase</b> – for each {@code ast.Operator}, a corresponding
 *       {@code dag.Operator} is found or created using the structural equality check.  The
 *       {@link MultiElem} correspondence groups all AST operators that map to the same DAG node.
 *       If an AST operator's correspondence diverges (different op or different subtree structure),
 *       it is detached and re-added.</li>
 *   <li><b>Reference wiring phase</b> ({@link #setReferences(Model)}) – traverses the AST tree
 *       top-down and sets the non-containment {@code dag.Operator.left} and {@code dag.Operator.right}
 *       cross-references to the DAG elements already mapped by earlier rules.  This second phase
 *       is needed because the child DAG nodes may not have been registered in the correspondence
 *       map until after the node mapping phase completes.</li>
 * </ol>
 * 
 * <h2>Backward pass (DAG → AST)</h2>
 * The backward pass uses an <em>iterative pre-order traversal</em>:
 * <ol>
 *   <li>The single root {@code dag.Operator} (the one with empty {@code leftInverse} and
 *       {@code rightInverse}) is identified.  If there are zero roots, the pass is skipped;
 *       if there are more than one, an {@link AssertionError} is thrown because the DAG must
 *       have exactly one root.</li>
 *   <li>Two parallel worklists ({@code preOrder} and {@code preOrderSrc}) are maintained — one for
 *       DAG operators and one for their corresponding AST operator copies.</li>
 *   <li>At each step the current DAG operator's {@code op} attribute is copied to the AST side.
 *       Then the right and left DAG children (if they are themselves operators) are pushed onto
 *       the front of the worklists.  New AST operator copies are created in the correspondence
 *       model, identified by the predicate {@code e.leftInverse == currentSrc} or
 *       {@code e.rightInverse == currentSrc}, ensuring that duplicate sub-DAGs expand into
 *       distinct sub-trees in the AST.</li>
 * </ol>
 */
@SuppressWarnings("all")
public class Operator2Operator extends Elem2Elem {
  /**
   * Constructs the rule and sets the rule identifier to {@code "operator2operator"}.
   * 
   * @param src  the source (AST) model resource
   * @param trgt the target (DAG) model resource
   * @param corr the correspondence model resource
   */
  public Operator2Operator(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "operator2operator";
  }

  /**
   * Forward pass: maps every {@code ast.Operator} to a (possibly shared) {@code dag.Operator},
   * then wires the DAG cross-references via {@link #setReferences}.
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<Operator> _function = (Operator op) -> {
      Corr _corrModelElem = this.getCorrModelElem(op);
      final MultiElem corr = ((MultiElem) _corrModelElem);
      if ((corr == null)) {
        this.addToTargetElem(op);
      } else {
        EObject _targetElement = corr.getTargetElement();
        final dag.Operator targetOp = ((dag.Operator) _targetElement);
        final Function1<EObject, Boolean> _function_1 = (EObject it) -> {
          return Boolean.valueOf(((it instanceof Operator) && Objects.equals(((Operator) it).getOp(), op.getOp())));
        };
        boolean _forall = IterableExtensions.<EObject>forall(corr.getSourceElements(), _function_1);
        if (_forall) {
          targetOp.setOp(this.getConformOperator(op.getOp()));
        }
        if ((((!this.conformsTo(op.getOp(), targetOp.getOp())) || (!this.equalsToWithChilds(op, ((Operator) corr.getSourceElements().get(0))))) || (!Objects.equals(targetOp, this.findTargetElem(op))))) {
          EList<EObject> _sourceElements = corr.getSourceElements();
          _sourceElements.remove(op);
          this.addToTargetElem(op);
        }
      }
    };
    IteratorExtensions.<Operator>forEach(Iterators.<Operator>filter(this.sourceModel.getAllContents(), Operator.class), _function);
    EObject _get = this.sourceModel.getContents().get(0);
    this.setReferences(((Model) _get));
  }

  /**
   * Backward pass: reconstructs the AST operator tree from the DAG using iterative
   * pre-order traversal, expanding shared DAG nodes into duplicate AST subtrees.
   */
  @Override
  public void targetToSource() {
    List<dag.Operator> preOrder = new ArrayList<dag.Operator>();
    for (Iterator<dag.Operator> it = Iterators.<dag.Operator>filter(this.targetModel.getAllContents(), dag.Operator.class); it.hasNext();) {
      {
        final dag.Operator op = it.next();
        if ((op.getLeftInverse().isEmpty() && op.getRightInverse().isEmpty())) {
          preOrder.add(op);
        }
      }
    }
    int _size = preOrder.size();
    boolean _equals = (_size == 0);
    if (_equals) {
      return;
    } else {
      int _size_1 = preOrder.size();
      boolean _greaterThan = (_size_1 > 1);
      if (_greaterThan) {
        throw new AssertionError("Dag has multiple root elements.");
      }
    }
    List<Operator> preOrderSrc = new ArrayList<Operator>();
    Corr _orCreateCorrModelElement = this.getOrCreateCorrModelElement(preOrder.get(0), this.ruleID);
    final MultiElem corrRoot = ((MultiElem) _orCreateCorrModelElement);
    final Predicate<EObject> _function = (EObject it) -> {
      return true;
    };
    EObject _orCreateSourceElem = this.getOrCreateSourceElem(corrRoot, this.sourcePackage.getOperator(), _function);
    final Operator srcRoot = ((Operator) _orCreateSourceElem);
    preOrderSrc.add(srcRoot);
    EObject _sourceElement = this.getCorrModelElem(preOrder.get(0).getModel()).getSourceElement();
    srcRoot.setModel(((Model) _sourceElement));
    while ((!preOrder.isEmpty())) {
      {
        final dag.Operator current = preOrder.remove(0);
        final Operator currentSrc = preOrderSrc.remove(0);
        ArithmeticOperator _op = current.getOp();
        if (_op != null) {
          switch (_op) {
            case ADD:
              currentSrc.setOp(ast.ArithmeticOperator.ADD);
              break;
            case DIVIDE:
              currentSrc.setOp(ast.ArithmeticOperator.DIVIDE);
              break;
            case MULTIPLY:
              currentSrc.setOp(ast.ArithmeticOperator.MULTIPLY);
              break;
            case SUBTRACT:
              currentSrc.setOp(ast.ArithmeticOperator.SUBTRACT);
              break;
            default:
              break;
          }
        }
        Expression _right = current.getRight();
        if ((_right instanceof dag.Operator)) {
          Expression _right_1 = current.getRight();
          preOrder.add(0, ((dag.Operator) _right_1));
          Corr _orCreateCorrModelElement_1 = this.getOrCreateCorrModelElement(preOrder.get(0), this.ruleID);
          final MultiElem corrRight = ((MultiElem) _orCreateCorrModelElement_1);
          final Predicate<EObject> _function_1 = (EObject e) -> {
            Operator _rightInverse = ((Operator) e).getRightInverse();
            return Objects.equals(_rightInverse, currentSrc);
          };
          EObject _orCreateSourceElem_1 = this.getOrCreateSourceElem(corrRight, 
            this.sourcePackage.getOperator(), _function_1);
          final Operator srcRight = ((Operator) _orCreateSourceElem_1);
          preOrderSrc.add(0, srcRight);
          currentSrc.setRight(srcRight);
        }
        Expression _left = current.getLeft();
        if ((_left instanceof dag.Operator)) {
          Expression _left_1 = current.getLeft();
          preOrder.add(0, ((dag.Operator) _left_1));
          Corr _orCreateCorrModelElement_2 = this.getOrCreateCorrModelElement(preOrder.get(0), this.ruleID);
          final MultiElem corrLeft = ((MultiElem) _orCreateCorrModelElement_2);
          final Predicate<EObject> _function_2 = (EObject e) -> {
            Operator _leftInverse = ((Operator) e).getLeftInverse();
            return Objects.equals(_leftInverse, currentSrc);
          };
          EObject _orCreateSourceElem_2 = this.getOrCreateSourceElem(corrLeft, 
            this.sourcePackage.getOperator(), _function_2);
          final Operator srcLeft = ((Operator) _orCreateSourceElem_2);
          preOrderSrc.add(0, srcLeft);
          currentSrc.setLeft(srcLeft);
        }
      }
    }
  }

  /**
   * Returns {@code true} if the AST {@code operator} type is the DAG counterpart of
   * {@code operator2}.  Used to detect divergences between the AST and DAG attribute values.
   */
  private boolean conformsTo(final ast.ArithmeticOperator operator, final ArithmeticOperator operator2) {
    ArithmeticOperator _conformOperator = this.getConformOperator(operator);
    return Objects.equals(operator2, _conformOperator);
  }

  /**
   * Returns {@code true} if two AST {@link ArithmeticOperator} values are the same.
   * Useful for comparing two AST operators without converting to the DAG enum.
   */
  private boolean conformsTo(final ast.ArithmeticOperator operator, final ast.ArithmeticOperator operator2) {
    boolean _switchResult = false;
    if (operator != null) {
      switch (operator) {
        case ADD:
          _switchResult = Objects.equals(operator2, ast.ArithmeticOperator.ADD);
          break;
        case DIVIDE:
          _switchResult = Objects.equals(operator2, ast.ArithmeticOperator.DIVIDE);
          break;
        case MULTIPLY:
          _switchResult = Objects.equals(operator2, ast.ArithmeticOperator.MULTIPLY);
          break;
        case SUBTRACT:
          _switchResult = Objects.equals(operator2, ast.ArithmeticOperator.SUBTRACT);
          break;
        default:
          break;
      }
    }
    return _switchResult;
  }

  /**
   * Base case for the dispatched structural equality check.
   * Returns {@code false} when the two expressions have different concrete types.
   */
  private boolean _equalsToWithChilds(final ast.Expression e, final ast.Expression e2) {
    return false;
  }

  /**
   * Recursively checks whether two AST {@link Operator} subtrees are structurally equal:
   * same operator type, same left subtree, and same right subtree.
   * 
   * @param operator  the first operator
   * @param operator2 the second operator
   * @return {@code true} if both subtrees are structurally identical
   */
  private boolean _equalsToWithChilds(final Operator operator, final Operator operator2) {
    return ((this.conformsTo(operator.getOp(), operator2.getOp()) && this.equalsToWithChilds(operator.getLeft(), operator2.getLeft())) && this.equalsToWithChilds(operator.getRight(), operator2.getRight()));
  }

  /**
   * Structural equality check for two {@link Variable} leaves.
   * Equal iff their {@code name} attributes are equal.
   */
  private boolean _equalsToWithChilds(final Variable a, final Variable b) {
    String _name = a.getName();
    String _name_1 = b.getName();
    return Objects.equals(_name, _name_1);
  }

  /**
   * Structural equality check for two {@link Number} leaves.
   * Equal iff their {@code value} attributes are equal.
   */
  private boolean _equalsToWithChilds(final ast.Number a, final ast.Number b) {
    int _value = a.getValue();
    int _value_1 = b.getValue();
    return (_value == _value_1);
  }

  /**
   * Converts an AST {@link ArithmeticOperator} enum literal to its DAG counterpart.
   * 
   * @param operator the AST arithmetic operator
   * @return the equivalent {@link dag.ArithmeticOperator} literal
   */
  private ArithmeticOperator getConformOperator(final ast.ArithmeticOperator operator) {
    ArithmeticOperator _switchResult = null;
    if (operator != null) {
      switch (operator) {
        case ADD:
          _switchResult = ArithmeticOperator.ADD;
          break;
        case DIVIDE:
          _switchResult = ArithmeticOperator.DIVIDE;
          break;
        case MULTIPLY:
          _switchResult = ArithmeticOperator.MULTIPLY;
          break;
        case SUBTRACT:
          _switchResult = ArithmeticOperator.SUBTRACT;
          break;
        default:
          break;
      }
    }
    return _switchResult;
  }

  /**
   * Finds or creates a {@code dag.Operator} for the given AST operator and registers the
   * correspondence link.
   * 
   * <p>An existing DAG operator is reused when it is structurally equal to {@code o} and its
   * correspondence entry is non-empty.  Otherwise a new {@code dag.Operator} is created and
   * added to the DAG model's {@code exprs} list.
   * 
   * @param o the AST operator to map into the DAG
   */
  private Corr addToTargetElem(final Operator o) {
    Corr _xblockexpression = null;
    {
      dag.Operator newTarget = this.findTargetElem(o);
      if ((newTarget == null)) {
        EObject _createTargetElement = this.createTargetElement(
          DagPackage.eINSTANCE.getOperator());
        newTarget = ((dag.Operator) _createTargetElement);
      }
      Corr _orCreateCorrModelElement = this.getOrCreateCorrModelElement(newTarget, this.ruleID);
      final MultiElem newCorr = ((MultiElem) _orCreateCorrModelElement);
      EList<EObject> _sourceElements = newCorr.getSourceElements();
      _sourceElements.add(o);
      newTarget.setOp(this.getConformOperator(o.getOp()));
      EObject _get = this.targetModel.getContents().get(0);
      newTarget.setModel(((dag.Model) _get));
      _xblockexpression = this.put(Elem2Elem.elementsToCorr, newCorr);
    }
    return _xblockexpression;
  }

  /**
   * Searches the DAG model's flat expression list for a {@code dag.Operator} whose first
   * registered source element is structurally equal to {@code o}.
   * 
   * <p>A DAG operator is considered a match only when its correspondence entry already has
   * at least one source element (i.e. it was produced by a previous forward pass and its
   * subtree identity is therefore known).
   * 
   * @param o the AST operator used as the structural search key
   * @return the matching {@code dag.Operator}, or {@code null} if none exists
   */
  private dag.Operator findTargetElem(final Operator o) {
    EObject _targetElement = this.getCorrModelElem(o.getModel()).getTargetElement();
    final Function1<dag.Operator, Boolean> _function = (dag.Operator op) -> {
      return Boolean.valueOf(((!((MultiElem) this.getCorrModelElem(op)).getSourceElements().isEmpty()) && 
        this.equalsToWithChilds(((Operator) ((MultiElem) this.getCorrModelElem(op)).getSourceElements().get(0)), o)));
    };
    return IterableExtensions.<dag.Operator>findFirst(Iterables.<dag.Operator>filter(((dag.Model) _targetElement).getExprs(), 
      dag.Operator.class), _function);
  }

  /**
   * Phase-2 reference wiring entry point for the AST {@link Model} root.
   * Delegates to the root {@code expr} if one exists.
   * 
   * @param model the AST model root
   */
  private void _setReferences(final Model model) {
    ast.Expression _expr = model.getExpr();
    if (_expr!=null) {
      this.setReferences(_expr);
    }
  }

  /**
   * Phase-2 reference wiring for an {@link Operator} node.
   * Sets the non-containment {@code dag.Operator.left} and {@code dag.Operator.right}
   * cross-references to the DAG elements already registered in the correspondence map,
   * then recurses into the left and right children.
   * 
   * @param o the AST operator whose DAG counterpart needs its children wired
   */
  private void _setReferences(final Operator o) {
    EObject _targetElement = this.getCorrModelElem(o).getTargetElement();
    final dag.Operator target = ((dag.Operator) _targetElement);
    EObject _targetElement_1 = this.getCorrModelElem(o.getLeft()).getTargetElement();
    target.setLeft(((Expression) _targetElement_1));
    EObject _targetElement_2 = this.getCorrModelElem(o.getRight()).getTargetElement();
    target.setRight(((Expression) _targetElement_2));
    this.setReferences(o.getLeft());
    this.setReferences(o.getRight());
  }

  /**
   * Phase-2 reference wiring base case for non-operator {@link Expression} nodes (leaves).
   * Leaves have no children to wire, so this is a no-op.
   * 
   * @param e a leaf expression node
   */
  private void _setReferences(final ast.Expression e) {
  }

  @XbaseGenerated
  private boolean equalsToWithChilds(final ast.Expression a, final ast.Expression b) {
    if (a instanceof ast.Number
         && b instanceof ast.Number) {
      return _equalsToWithChilds((ast.Number)a, (ast.Number)b);
    } else if (a instanceof Variable
         && b instanceof Variable) {
      return _equalsToWithChilds((Variable)a, (Variable)b);
    } else if (a instanceof Operator
         && b instanceof Operator) {
      return _equalsToWithChilds((Operator)a, (Operator)b);
    } else if (a != null
         && b != null) {
      return _equalsToWithChilds(a, b);
    } else {
      throw new IllegalArgumentException("Unhandled parameter types: " +
        Arrays.<Object>asList(a, b).toString());
    }
  }

  @XbaseGenerated
  private void setReferences(final EObject o) {
    if (o instanceof Operator) {
      _setReferences((Operator)o);
      return;
    } else if (o instanceof ast.Expression) {
      _setReferences((ast.Expression)o);
      return;
    } else if (o instanceof Model) {
      _setReferences((Model)o);
      return;
    } else {
      throw new IllegalArgumentException("Unhandled parameter types: " +
        Arrays.<Object>asList(o).toString());
    }
  }
}
