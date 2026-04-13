package de.tbuchmann.bxtend.ast2dag.rules;

import ast.Model;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr;
import java.util.List;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;

/**
 * Transformation rule that synchronises the root {@code Model} elements of both metamodels.
 * 
 * <p>This is always the <em>first</em> rule to run in both the forward and backward
 * propagation passes because all other rules require the root {@code Model} objects to exist
 * before they can assign containment or cross-reference relationships.
 * 
 * <h2>Correspondence type</h2>
 * The root models are linked by a {@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.BasicElem}
 * correspondence (1-to-1): one AST {@code Model} ↔ one DAG {@code Model}.
 * 
 * <h2>Forward (AST → DAG)</h2>
 * For each AST {@code Model} found in the source resource, the rule ensures a DAG {@code Model}
 * exists in the target resource.  Idempotent: if a correspondence already exists (incremental run),
 * the target element is reused.
 * 
 * <h2>Backward (DAG → AST)</h2>
 * For each DAG {@code Model} found in the target resource, the rule ensures an AST {@code Model}
 * exists in the source resource.  Idempotent in the same way.
 */
@SuppressWarnings("all")
public class Model2Model extends Elem2Elem {
  /**
   * Constructs the rule and sets the rule identifier to {@code "root"}.
   * 
   * @param src  the source (AST) model resource
   * @param trgt the target (DAG) model resource
   * @param corr the correspondence model resource
   */
  public Model2Model(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "root";
  }

  /**
   * Forward pass: for every AST {@code Model} root, creates (or reuses) a corresponding
   * DAG {@code Model} root and adds it to the target resource.
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<Model> _function = (Model m) -> {
      final Corr corr = this.getOrCreateCorrModelElement(m, this.ruleID);
      final EObject target = this.getOrCreateTargetElem(corr, this.targetPackage.getModel());
      EList<EObject> _contents = this.targetModel.getContents();
      _contents.add(target);
    };
    IteratorExtensions.<Model>forEach(Iterators.<Model>filter(this.sourceModel.getAllContents(), Model.class), _function);
  }

  /**
   * Backward pass: for every DAG {@code Model} root, creates (or reuses) a corresponding
   * AST {@code Model} root and adds it to the source resource.
   */
  @Override
  public void targetToSource() {
    final Procedure1<dag.Model> _function = (dag.Model m) -> {
      final Corr corr = this.getOrCreateCorrModelElement(m, this.ruleID);
      final List<EObject> source = this.getOrCreateSourceElem(corr, this.sourcePackage.getModel());
      EList<EObject> _contents = this.sourceModel.getContents();
      Iterables.<EObject>addAll(_contents, source);
    };
    IteratorExtensions.<dag.Model>forEach(Iterators.<dag.Model>filter(this.targetModel.getAllContents(), dag.Model.class), _function);
  }
}
