package de.tbuchmann.bxtend.ast2dag.rules;

import ast.Expression;
import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Ast2dagFactory;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.BasicElem;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.xbase.lib.CollectionLiterals;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;

/**
 * Top-level orchestrator for the bidirectional, incremental AST ↔ DAG transformation.
 * 
 * <p>This class is the single entry point used by the BXtend adapter.  It owns the three
 * EMF {@link Resource} instances (source AST model, target DAG model, and correspondence
 * model) and two ordered lists of {@link Elem2Elem} rules – one for each propagation
 * direction.  Calling {@link #sourceToTarget()} or {@link #targetToSource()} iterates the
 * respective rule list in order and then performs housekeeping deletions.
 * 
 * <h2>Rule ordering</h2>
 * <ul>
 *   <li><b>Forward (AST → DAG):</b> bottom-up order.
 *       {@link Model2Model} first creates the DAG root; then
 *       {@link Variable2Variable} and {@link Number2Number} map the leaves; finally
 *       {@link Operator2Operator} maps the interior nodes and wires their children.
 *       Leaves must be present before operators reference them.</li>
 *   <li><b>Backward (DAG → AST):</b> top-down order.
 *       {@link Model2Model} first creates the AST root; then
 *       {@link Operator2Operator} expands the DAG operators into tree operators
 *       (including duplicate sub-trees for shared DAG nodes); finally
 *       {@link Variable2Variable} and {@link Number2Number} populate the leaves.</li>
 * </ul>
 * 
 * <h2>Deletion handling</h2>
 * After each propagation pass, stale correspondence entries (those whose source or target
 * element has been deleted from the model) are detected and the orphaned counterparts are
 * removed from both the opposite model and the correspondence model.
 * 
 * <h2>Constructors</h2>
 * Two constructors are provided:
 * <ol>
 *   <li>URI-based – loads resources from the file system via a fresh {@link ResourceSet};
 *       suitable for stand-alone execution.</li>
 *   <li>Resource-based – accepts already-loaded EMF {@link Resource} objects; used by the
 *       BXtend adapter during Benchmarx tests.</li>
 * </ol>
 */
@SuppressWarnings("all")
public class Ast2dagTransformation {
  /**
   * EMF resource holding the source (ExpressionAST) model.
   */
  private Resource sourceModel;

  /**
   * EMF resource holding the target (ExpressionDAG) model.
   */
  private Resource targetModel;

  /**
   * EMF resource holding the correspondence model (corresp.ecore instances).
   */
  private Resource corrModel;

  /**
   * Ordered list of rules applied during forward propagation (AST → DAG).
   */
  private List<Elem2Elem> rulesFwd = new ArrayList<Elem2Elem>();

  /**
   * Ordered list of rules applied during backward propagation (DAG → AST).
   */
  private List<Elem2Elem> rulesBwd = new ArrayList<Elem2Elem>();

  /**
   * URI-based constructor: loads the three models from the file system.
   * 
   * <p>A single shared {@link ResourceSet} is used so that cross-resource references
   * (e.g. from the correspondence model to the AST/DAG models) resolve correctly.
   * If the correspondence resource is empty (first run), a fresh {@code Transformation}
   * root element is added so that rules can immediately start appending correspondences.
   * 
   * @param source        URI of the source (AST) model
   * @param target        URI of the target (DAG) model
   * @param correspondence URI of the correspondence model
   */
  public Ast2dagTransformation(final URI source, final URI target, final URI correspondence) {
    final ResourceSet set = new ResourceSetImpl();
    this.sourceModel = set.getResource(source, true);
    this.targetModel = set.getResource(target, true);
    this.corrModel = set.getResource(correspondence, true);
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(Ast2dagFactory.eINSTANCE.createTransformation());
    }
    this.addRulesFwd();
    this.addRulesBwd();
  }

  /**
   * Resource-based constructor: accepts already-loaded EMF resources.
   * 
   * <p>Used by the BXtend adapter during Benchmarx test execution where the framework
   * manages the resource lifecycle.  Initialises the correspondence model root element
   * if needed and registers all rules.
   * 
   * @param source        already-loaded source (AST) EMF resource
   * @param target        already-loaded target (DAG) EMF resource
   * @param correspondence already-loaded correspondence EMF resource
   */
  public Ast2dagTransformation(final Resource source, final Resource target, final Resource correspondence) {
    this.sourceModel = source;
    this.targetModel = target;
    this.corrModel = correspondence;
    int _size = this.corrModel.getContents().size();
    boolean _equals = (_size == 0);
    if (_equals) {
      this.corrModel.getContents().add(Ast2dagFactory.eINSTANCE.createTransformation());
    }
    this.addRulesFwd();
    this.addRulesBwd();
  }

  /**
   * Populates {@link #rulesFwd} with the four forward rules in bottom-up order:
   * Model → Variables → Numbers → Operators.
   * Leaves are created before operator rules try to reference them.
   */
  private void addRulesFwd() {
    Model2Model _model2Model = new Model2Model(this.sourceModel, this.targetModel, this.corrModel);
    this.rulesFwd.add(_model2Model);
    Variable2Variable _variable2Variable = new Variable2Variable(this.sourceModel, this.targetModel, this.corrModel);
    this.rulesFwd.add(_variable2Variable);
    Number2Number _number2Number = new Number2Number(this.sourceModel, this.targetModel, this.corrModel);
    this.rulesFwd.add(_number2Number);
    Operator2Operator _operator2Operator = new Operator2Operator(this.sourceModel, this.targetModel, this.corrModel);
    this.rulesFwd.add(_operator2Operator);
  }

  /**
   * Populates {@link #rulesBwd} with the four backward rules in top-down order:
   * Model → Operators → Variables → Numbers.
   * Operators must be reconstructed before leaves are assigned to their children.
   */
  private void addRulesBwd() {
    Model2Model _model2Model = new Model2Model(this.sourceModel, this.targetModel, this.corrModel);
    this.rulesBwd.add(_model2Model);
    Operator2Operator _operator2Operator = new Operator2Operator(this.sourceModel, this.targetModel, this.corrModel);
    this.rulesBwd.add(_operator2Operator);
    Variable2Variable _variable2Variable = new Variable2Variable(this.sourceModel, this.targetModel, this.corrModel);
    this.rulesBwd.add(_variable2Variable);
    Number2Number _number2Number = new Number2Number(this.sourceModel, this.targetModel, this.corrModel);
    this.rulesBwd.add(_number2Number);
  }

  /**
   * Runs all forward rules (AST → DAG) in order and then removes any target elements
   * that are no longer backed by a source element.
   * 
   * <p>The method is a no-op when the source model is empty (nothing to propagate).
   */
  public void sourceToTarget() {
    int _size = this.sourceModel.getContents().size();
    boolean _notEquals = (_size != 0);
    if (_notEquals) {
      for (final Elem2Elem e : this.rulesFwd) {
        e.sourceToTarget();
      }
    }
    this.deleteUnreferencedTargetElements();
  }

  /**
   * Runs all backward rules (DAG → AST) in order and then removes any source elements
   * that are no longer backed by a target element.
   * 
   * <p>The method is a no-op when the target model is empty (nothing to propagate).
   */
  public void targetToSource() {
    int _size = this.targetModel.getContents().size();
    boolean _notEquals = (_size != 0);
    if (_notEquals) {
      for (final Elem2Elem e : this.rulesBwd) {
        e.targetToSource();
      }
    }
    this.deleteUnreferencedSourceElements();
  }

  /**
   * Placeholder consistency check.
   * 
   * @return always {@code true} – full consistency checking is not yet implemented
   */
  public boolean checkCorrespondences() {
    return true;
  }

  /**
   * Scans the correspondence model for entries whose source side is absent.
   * <ul>
   *   <li>A {@link BasicElem} entry is stale when its single {@code sourceElement} is {@code null}.</li>
   *   <li>A {@link MultiElem} entry is stale when its {@code sourceElements} list is empty.</li>
   * </ul>
   * 
   * @return a lazy iterator of stale {@link Corr} entries (source deleted, target still alive)
   */
  public Iterator<Corr> detectSourceDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      return Boolean.valueOf((((c instanceof BasicElem) && (c.getSourceElement() == null)) || ((c instanceof MultiElem) && ((MultiElem) c).getSourceElements().isEmpty())));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Scans the correspondence model for entries whose target side has been deleted,
   * i.e. where {@code targetElement} is {@code null}.
   * 
   * @return a lazy iterator of stale {@link Corr} entries (target deleted, source still alive)
   */
  public Iterator<Corr> detectTargetDeletions() {
    final Function1<Corr, Boolean> _function = (Corr c) -> {
      EObject _targetElement = c.getTargetElement();
      return Boolean.valueOf((_targetElement == null));
    };
    return IteratorExtensions.<Corr>filter(Iterators.<Corr>filter(this.corrModel.getAllContents(), Corr.class), _function);
  }

  /**
   * Deletes target (DAG) elements that have become dangling after a forward
   * propagation pass removed their corresponding AST source elements.
   * 
   * <p>Both the orphaned target element and the now-useless correspondence entry are
   * queued for deletion, then removed together via {@link EcoreUtil#delete}.
   */
  public void deleteUnreferencedTargetElements() {
    final List<EObject> deletionList = CollectionLiterals.<EObject>newArrayList();
    final Procedure1<Corr> _function = (Corr c) -> {
      EObject _targetElement = c.getTargetElement();
      deletionList.add(_targetElement);
      deletionList.add(c);
    };
    IteratorExtensions.<Corr>forEach(this.detectSourceDeletions(), _function);
    final Consumer<EObject> _function_1 = (EObject e) -> {
      EcoreUtil.delete(e, true);
    };
    deletionList.forEach(_function_1);
  }

  /**
   * Deletes source (AST) elements that have become dangling after a backward
   * propagation pass removed their corresponding DAG target elements.
   * 
   * <p>For {@link MultiElem} correspondences all source elements are collected;
   * for {@link BasicElem} correspondences only the single source element is collected.
   * Additionally, any {@link Expression} node that has lost all structural links
   * (no parent operator, no model reference) is detected and removed to prevent leaks.
   */
  public void deleteUnreferencedSourceElements() {
    final List<EObject> deletionList = CollectionLiterals.<EObject>newArrayList();
    final Procedure1<Corr> _function = (Corr c) -> {
      if ((c instanceof MultiElem)) {
        final Consumer<EObject> _function_1 = (EObject e) -> {
          deletionList.add(e);
        };
        ((MultiElem)c).getSourceElements().forEach(_function_1);
      } else {
        EObject _sourceElement = c.getSourceElement();
        deletionList.add(_sourceElement);
      }
      deletionList.add(c);
    };
    IteratorExtensions.<Corr>forEach(this.detectTargetDeletions(), _function);
    final Function1<Expression, Boolean> _function_1 = (Expression e) -> {
      return Boolean.valueOf((((e.getLeftInverse() == null) && (e.getRightInverse() == null)) && (e.getModel() == null)));
    };
    final Procedure1<Expression> _function_2 = (Expression it) -> {
      deletionList.add(it);
    };
    IteratorExtensions.<Expression>forEach(IteratorExtensions.<Expression>filter(Iterators.<Expression>filter(this.sourceModel.getAllContents(), Expression.class), _function_1), _function_2);
    final Consumer<EObject> _function_3 = (EObject e) -> {
      EcoreUtil.delete(e, true);
    };
    deletionList.forEach(_function_3);
  }
}
