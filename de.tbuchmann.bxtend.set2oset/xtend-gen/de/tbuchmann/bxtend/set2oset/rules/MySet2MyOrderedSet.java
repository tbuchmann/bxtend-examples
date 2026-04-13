package de.tbuchmann.bxtend.set2oset.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Corr;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import osets.MyOrderedSet;
import sets.MySet;

/**
 * BXtend rule that synchronises the top-level container objects of the two models:
 * {@code sets.MySet} (source) ↔ {@code osets.MyOrderedSet} (target).
 * 
 * <p>This rule is responsible for the root-level alignment between the two metamodels.
 * It propagates the container's {@code name} attribute in both directions and ensures that
 * each {@code MySet} / {@code MyOrderedSet} pair is registered in the correspondence model
 * before the element-level rule {@link Element2Element} processes the contained
 * {@code sets.Element} / {@code osets.Element} objects. The rule must therefore be added to
 * the rule list in {@link Set2osetTransformation} <em>before</em> {@link Element2Element}.</p>
 * 
 * <h2>Forward propagation ({@link #sourceToTarget()})</h2>
 * <p>For every {@code MySet} in the source model the rule looks up or creates the
 * corresponding {@code MyOrderedSet} in the target model, then copies the {@code name}
 * attribute and adds the target root to the target resource's contents list.  The
 * {@code getOrCreate…} helpers ensure idempotency: if the correspondence and the target
 * object already exist from a previous synchronisation step they are reused rather than
 * duplicated.</p>
 * 
 * <h2>Backward propagation ({@link #targetToSource()})</h2>
 * <p>Symmetric to the forward direction: for every {@code MyOrderedSet} in the target model
 * the rule looks up or creates the corresponding {@code MySet} in the source model, then
 * copies the {@code name} attribute back and adds the source root to the source resource's
 * contents list.</p>
 */
@SuppressWarnings("all")
public class MySet2MyOrderedSet extends Elem2Elem {
  /**
   * Constructs the rule and registers it against the given three EMF resources.
   * 
   * @param src  the EMF resource containing the source ({@code MySet}) model
   * @param trgt the EMF resource containing the target ({@code MyOrderedSet}) model
   * @param corr the EMF resource containing the correspondence model
   */
  public MySet2MyOrderedSet(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "MySet2MyOrderedSet";
  }

  /**
   * Forward propagation: source → target.
   * 
   * <p>Iterates over all {@code MySet} instances in the source resource.  For each one:</p>
   * <ol>
   *   <li>Retrieves or creates a {@link de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Corr}
   *       linking this {@code MySet} to its target counterpart.</li>
   *   <li>Retrieves or creates the corresponding {@code MyOrderedSet} target object.</li>
   *   <li>Copies the {@code name} attribute from source to target.</li>
   *   <li>Adds the target root to the target resource's contents list (harmless if already
   *       present, because EMF collections deduplicate containment assignments).</li>
   * </ol>
   */
  @Override
  public void sourceToTarget() {
    final Procedure1<MySet> _function = (MySet source) -> {
      final Corr corr = this.getOrCreateCorrModelElement(source, this.ruleID);
      EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getMyOrderedSet());
      final MyOrderedSet target = ((MyOrderedSet) _orCreateTargetElem);
      target.setName(source.getName());
      EList<EObject> _contents = this.targetModel.getContents();
      _contents.add(target);
    };
    IteratorExtensions.<MySet>forEach(Iterators.<MySet>filter(this.sourceModel.getAllContents(), MySet.class), _function);
  }

  /**
   * Backward propagation: target → source.
   * 
   * <p>Iterates over all {@code MyOrderedSet} instances in the target resource.  For each one:</p>
   * <ol>
   *   <li>Retrieves or creates a {@link de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Corr}
   *       linking this {@code MyOrderedSet} to its source counterpart.</li>
   *   <li>Retrieves or creates the corresponding {@code MySet} source object.</li>
   *   <li>Copies the {@code name} attribute from target back to source.</li>
   *   <li>Adds the source root to the source resource's contents list.</li>
   * </ol>
   */
  @Override
  public void targetToSource() {
    final Procedure1<MyOrderedSet> _function = (MyOrderedSet target) -> {
      final Corr corr = this.getOrCreateCorrModelElement(target, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getMySet());
      final MySet source = ((MySet) _orCreateSourceElem);
      source.setName(target.getName());
      EList<EObject> _contents = this.sourceModel.getContents();
      _contents.add(source);
    };
    IteratorExtensions.<MyOrderedSet>forEach(Iterators.<MyOrderedSet>filter(this.targetModel.getAllContents(), MyOrderedSet.class), _function);
  }
}
