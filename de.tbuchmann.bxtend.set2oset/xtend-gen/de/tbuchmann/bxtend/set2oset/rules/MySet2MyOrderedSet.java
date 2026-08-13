package de.tbuchmann.bxtend.set2oset.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Corr;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
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
      Elem2Elem.corrToName.put(corr, source.getName());
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
      Elem2Elem.corrToName.put(corr, source.getName());
    };
    IteratorExtensions.<MyOrderedSet>forEach(Iterators.<MyOrderedSet>filter(this.targetModel.getAllContents(), MyOrderedSet.class), _function);
  }

  /**
   * Reconciles the root {@code MySet} ↔ {@code MyOrderedSet} pair. Both models are
   * single-root, so there is normally at most one unmatched element per side.
   * 
   * <ol>
   *   <li>If already linked, push the name forward when it changed on the source since
   *       the last synchronisation ({@link #corrToName}), otherwise pull it backward.</li>
   *   <li>If unlinked, re-link to an unmatched same-named container, or create a new one.</li>
   *   <li>Any container still unmatched afterwards is used to create the missing
   *       counterpart (target-side insertion).</li>
   * </ol>
   */
  @Override
  public void synch() {
    final List<MySet> setList = IteratorExtensions.<MySet>toList(Iterators.<MySet>filter(this.sourceModel.getAllContents(), MySet.class));
    final Function1<MyOrderedSet, Boolean> _function = (MyOrderedSet s) -> {
      Corr _corrModelElem = this.getCorrModelElem(s);
      return Boolean.valueOf((_corrModelElem == null));
    };
    final List<MyOrderedSet> unmatchedSets = IteratorExtensions.<MyOrderedSet>toList(IteratorExtensions.<MyOrderedSet>filter(Iterators.<MyOrderedSet>filter(this.targetModel.getAllContents(), MyOrderedSet.class), _function));
    final Consumer<MySet> _function_1 = (MySet source) -> {
      final Corr corr = this.getOrCreateCorrModelElement(source, this.ruleID);
      EObject _targetElement = corr.getTargetElement();
      MyOrderedSet target = ((MyOrderedSet) _targetElement);
      if ((target != null)) {
        unmatchedSets.remove(target);
        String _get = Elem2Elem.corrToName.get(corr);
        String _name = source.getName();
        boolean _notEquals = (!Objects.equals(_get, _name));
        if (_notEquals) {
          target.setName(source.getName());
        } else {
          source.setName(target.getName());
        }
      } else {
        final Function1<MyOrderedSet, Boolean> _function_2 = (MyOrderedSet t) -> {
          String _name_1 = t.getName();
          String _name_2 = source.getName();
          return Boolean.valueOf(Objects.equals(_name_1, _name_2));
        };
        target = IterableExtensions.<MyOrderedSet>findFirst(unmatchedSets, _function_2);
        if ((target != null)) {
          corr.setTargetElement(target);
          Elem2Elem.elementsToCorr.put(target, corr);
          unmatchedSets.remove(target);
        } else {
          EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getMyOrderedSet());
          final Procedure1<MyOrderedSet> _function_3 = (MyOrderedSet it) -> {
            it.setName(source.getName());
          };
          MyOrderedSet _doubleArrow = ObjectExtensions.<MyOrderedSet>operator_doubleArrow(((MyOrderedSet) _orCreateTargetElem), _function_3);
          target = _doubleArrow;
          EList<EObject> _contents = this.targetModel.getContents();
          _contents.add(target);
        }
      }
      Elem2Elem.corrToName.put(corr, source.getName());
    };
    setList.forEach(_function_1);
    final Consumer<MyOrderedSet> _function_2 = (MyOrderedSet target) -> {
      final Corr corr = this.getOrCreateCorrModelElement(target, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getMySet());
      final Procedure1<MySet> _function_3 = (MySet it) -> {
        it.setName(target.getName());
      };
      final MySet source = ObjectExtensions.<MySet>operator_doubleArrow(((MySet) _orCreateSourceElem), _function_3);
      EList<EObject> _contents = this.sourceModel.getContents();
      _contents.add(source);
      Elem2Elem.corrToName.put(corr, source.getName());
    };
    unmatchedSets.forEach(_function_2);
  }
}
