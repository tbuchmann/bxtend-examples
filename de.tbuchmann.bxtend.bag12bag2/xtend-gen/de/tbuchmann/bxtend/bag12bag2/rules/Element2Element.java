package de.tbuchmann.bxtend.bag12bag2.rules;

import bags1.Bags1Package;
import bags1.Element;
import bags1.MyBag;
import bags2.Bags2Package;
import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Corr;
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.MultiElem;
import java.util.Objects;
import java.util.function.Consumer;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IterableExtensions;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.ObjectExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;

/**
 * BXtend rule that maps individual {@code Element} objects between the two bag models,
 * implementing the core many-to-one compression / decompression semantics.
 * 
 * <h2>Transformation Semantics</h2>
 * <p>Bag1 stores each occurrence of a value as a separate {@code bags1.Element} object.
 * Bag2 uses a single {@code bags2.Element} per distinct value and records the number of
 * occurrences in its {@code multiplicity} attribute. This rule maintains
 * <em>many-to-one</em> correspondences using {@link MultiElem}:</p>
 * <pre>
 *   bags1.Element (value="Beer") ─┐
 *   bags1.Element (value="Beer") ─┤ MultiElem ──→ bags2.Element(value="Beer", multiplicity=N)
 *   ...                           ┘
 * </pre>
 * 
 * <h2>Forward Propagation ({@code sourceToTarget})</h2>
 * <p>Iterates over all Bag1 {@code Element} objects. For each element:</p>
 * <ol>
 *   <li>If it has no existing correspondence, it is grouped into the appropriate
 *       Bag2 {@code Element} via {@link #addToTargetElem}: the rule first searches
 *       the already-created Bag2 elements for one with a matching value
 *       ({@link #findTargetElem}), and either reuses it or creates a new one.</li>
 *   <li>If a correspondence already exists and the element's value still matches
 *       all elements in the group, the Bag2 target value is kept in sync.</li>
 *   <li>If the value has changed so that it no longer fits the existing group,
 *       the element is removed from the old correspondence and re-grouped by
 *       calling {@link #addToTargetElem} again.</li>
 * </ol>
 * <p>After all elements have been visited, the {@code multiplicity} attribute of each
 * Bag2 {@code Element} is set to the number of Bag1 elements in its correspondence
 * ({@code sourceElements.size}).</p>
 * 
 * <h2>Backward Propagation ({@code targetToSource})</h2>
 * <p>Iterates over all Bag2 {@code Element} objects. For each element:</p>
 * <ol>
 *   <li>Retrieves or creates the {@link MultiElem} correspondence.</li>
 *   <li>Adjusts the number of Bag1 {@code Element} objects in the correspondence to
 *       match {@code e.multiplicity}: missing elements are added, surplus elements are
 *       deleted via {@link EcoreUtil#delete}.</li>
 *   <li>Synchronises the {@code value} and {@code bag} references on every Bag1
 *       element so that they reflect the current Bag2 state.</li>
 * </ol>
 * 
 * <h2>Helper Methods</h2>
 * <ul>
 *   <li>{@link #addToTargetElem(Element)} – groups a Bag1 {@code Element} into
 *       an existing or newly created Bag2 {@code Element} with the same value, and
 *       links the two through a {@link MultiElem} correspondence.</li>
 *   <li>{@link #findTargetElem(Element)} – searches the Bag2 container of the
 *       source element's corresponding bag for a Bag2 {@code Element} whose
 *       {@code value} matches the given Bag1 element.</li>
 * </ul>
 * 
 * @see Bag2Bag
 * @see Elem2Elem
 * @see Bag12bag2Transformation
 */
@SuppressWarnings("all")
public class Element2Element extends Elem2Elem {
  public Element2Element(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "Element2Element";
  }

  /**
   * Forward propagation: Bag1 → Bag2.
   * 
   * <p>Each Bag1 {@code Element} is inspected in turn:
   * <ul>
   *   <li>No correspondence yet → delegate to {@link #addToTargetElem}.</li>
   *   <li>Correspondence exists and all source elements still share the same value
   *       → keep the Bag2 element's value up to date.</li>
   *   <li>Correspondence exists but the value has diverged → remove from the old
   *       group and re-add via {@link #addToTargetElem}.</li>
   * </ul>
   * After processing all elements the {@code multiplicity} of every Bag2 element
   * is updated to reflect the actual size of the source-element group.</p>
   */
  @Override
  public void sourceToTarget() {
    // forall[it.value == e.value] over corr.sourceElements is, for any e that is
    // currently a member of that list, exactly equivalent to "this group has only
    // one distinct value" - independent of which member e happens to be. Scanning
    // it fresh for every element costs O(group size) per element, i.e. O(n^2) for
    // a single fully-grouped batch of n elements. Instead compute it once per
    // group and reuse it for every member of that group, invalidating the cached
    // answer only when membership actually changes (a regroup below removes an
    // element from its old group).
    final java.util.Map<MultiElem, Boolean> homogeneousCache = new java.util.HashMap<>();
    final Procedure1<Element> _function = (Element e) -> {
      Corr _corrModelElem = this.getCorrModelElem(e);
      final MultiElem corr = ((MultiElem) _corrModelElem);
      if ((corr == null)) {
        this.addToTargetElem(e);
      } else {
        EObject _targetElement = corr.getTargetElement();
        final bags2.Element t = ((bags2.Element) _targetElement);
        boolean homogeneous = homogeneousCache.computeIfAbsent(corr, (MultiElem c) -> {
          java.util.Set<String> distinctValues = new java.util.HashSet<>();
          for (EObject it : c.getSourceElements()) {
            distinctValues.add(((Element) it).getValue());
          }
          return distinctValues.size() == 1;
        });
        if (homogeneous) {
          t.setValue(e.getValue());
        }
        String _value = t.getValue();
        String _value_1 = e.getValue();
        boolean _notEquals = (!Objects.equals(_value, _value_1));
        if (_notEquals) {
          EList<EObject> _sourceElements = corr.getSourceElements();
          _sourceElements.remove(e);
          homogeneousCache.remove(corr);
          this.addToTargetElem(e);
        }
      }
    };
    IteratorExtensions.<Element>forEach(Iterators.<Element>filter(this.sourceModel.getAllContents(), Element.class), _function);
    final Function1<EObject, Boolean> _function_1 = (EObject it) -> {
      return Boolean.valueOf(((it instanceof MultiElem) && Objects.equals(((MultiElem) it).getDesc(), this.ruleID)));
    };
    final Procedure1<EObject> _function_2 = (EObject it) -> {
      final MultiElem c = ((MultiElem) it);
      EObject _targetElement = c.getTargetElement();
      final bags2.Element t = ((bags2.Element) _targetElement);
      t.setMultiplicity(c.getSourceElements().size());
      Elem2Elem.corrToName.put(c, t.getValue());
      Elem2Elem.corrToMultiplicity.put(c, Integer.valueOf(t.getMultiplicity()));
    };
    IteratorExtensions.<EObject>forEach(IteratorExtensions.<EObject>filter(this.corrModel.getAllContents(), _function_1), _function_2);
  }

  /**
   * Backward propagation: Bag2 → Bag1.
   * 
   * <p>For each Bag2 {@code Element} {@code e}:
   * <ol>
   *   <li>Retrieves or creates the {@link MultiElem} correspondence.</li>
   *   <li>Grows the list of Bag1 source elements until it has exactly
   *       {@code e.multiplicity} entries (adding new {@code bags1.Element} objects
   *       as needed).</li>
   *   <li>Shrinks the list by deleting surplus Bag1 elements (using
   *       {@link EcoreUtil#delete}) until the size equals {@code e.multiplicity}.</li>
   *   <li>Sets the {@code value} and {@code bag} cross-reference on every
   *       surviving Bag1 element to match the Bag2 element and its owning bag.</li>
   * </ol>
   * </p>
   */
  @Override
  public void targetToSource() {
    final Procedure1<bags2.Element> _function = (bags2.Element e) -> {
      Corr _orCreateCorrModelElement = this.getOrCreateCorrModelElement(e, this.ruleID);
      final MultiElem corr = ((MultiElem) _orCreateCorrModelElement);
      while ((corr.getSourceElements().size() < e.getMultiplicity())) {
        {
          final EObject newEl = this.createSourceElement(Bags1Package.eINSTANCE.getElement());
          EList<EObject> _sourceElements = corr.getSourceElements();
          _sourceElements.add(newEl);
          Elem2Elem.elementsToCorr.put(newEl, corr);
        }
      }
      while ((corr.getSourceElements().size() > e.getMultiplicity())) {
        EcoreUtil.delete(corr.getSourceElements().get(0), true);
      }
      final Consumer<EObject> _function_1 = (EObject it) -> {
        final Element el = ((Element) it);
        el.setValue(e.getValue());
        EObject _sourceElement = this.getCorrModelElem(e.getBag()).getSourceElement();
        el.setBag(((MyBag) _sourceElement));
      };
      corr.getSourceElements().forEach(_function_1);
      Elem2Elem.corrToName.put(corr, e.getValue());
      Elem2Elem.corrToMultiplicity.put(corr, Integer.valueOf(e.getMultiplicity()));
    };
    IteratorExtensions.<bags2.Element>forEach(Iterators.<bags2.Element>filter(this.targetModel.getAllContents(), bags2.Element.class), _function);
  }

  /**
   * Reconciles concurrent edits to Bag1 {@code Element} groups and their Bag2
   * counterparts. Three passes:
   * 
   * <ol>
   *   <li><b>Regroup:</b> every Bag1 element without a correspondence yet, or whose
   *       value has drifted from the last-known group value ({@link #corrToName}), is
   *       (re-)grouped via {@link #addToTargetElem} — the same mechanism
   *       {@link #sourceToTarget()} already uses for regrouping. An element whose
   *       correspondence's target group was concurrently deleted is deliberately left
   *       attached to that now-dead correspondence rather than resurrected: the target's
   *       deletion wins, and the orchestrator's {@code deleteUnreferencedSourceElements()}
   *       sweeps up the orphaned element(s) afterwards. A concurrent source-side
   *       <em>addition</em> to that same (just-deleted) group is unaffected by this — it
   *       has no correspondence of its own yet, so {@link #addToTargetElem} regroups it
   *       independently via {@link #findTargetElem}, which (since the old target is gone)
   *       creates a fresh target/correspondence rather than reusing the dead one. The net
   *       effect is that the pre-existing element(s) are deleted while the new addition
   *       survives as its own single-element group, not merged with the deleted one.</li>
   *   <li><b>Reconcile surviving groups:</b> for every {@link MultiElem} correspondence
   *       that still has both a target element and source elements, the group's
   *       {@code value} and multiplicity are
   *       resolved independently against last-known snapshots ({@link #corrToName} /
   *       {@link #corrToMultiplicity}): each is pushed forward if it changed on the
   *       source, pulled backward if it only changed on the target, and left as-is
   *       (source wins) if both changed since the last synchronisation. <b>Exception:</b>
   *       if the group lost members since the last sync (a deletion) while its value was
   *       independently renamed on the target, that is treated as a single conflict over
   *       the whole group rather than two unrelated attribute changes — the rename is
   *       rejected and the reduced count is kept (source wins both axes), instead of
   *       pulling the rename and pushing the new count independently, which would produce
   *       a hybrid state neither side asked for. A group that instead <em>grew</em> (e.g.
   *       by absorbing an element that regrouped into it in the pass above) is not this
   *       case and keeps the normal independent per-axis resolution.</li>
   *   <li><b>Absorb target-only insertions:</b> any Bag2 {@code Element} that still has
   *       no correspondence at all is pulled backward into a freshly created Bag1 group,
   *       mirroring {@link #targetToSource()}.</li>
   * </ol>
   */
  @Override
  public void synch() {
    final Consumer<Element> _function = (Element e) -> {
      Corr _corrModelElem = this.getCorrModelElem(e);
      final MultiElem corr = ((MultiElem) _corrModelElem);
      if ((corr == null)) {
        this.addToTargetElem(e);
      } else {
        EObject _targetElement = corr.getTargetElement();
        boolean _tripleNotEquals = (_targetElement != null);
        if (_tripleNotEquals) {
          final String lastValue = Elem2Elem.corrToName.get(corr);
          if (((lastValue != null) && (!Objects.equals(e.getValue(), lastValue)))) {
            EList<EObject> _sourceElements = corr.getSourceElements();
            _sourceElements.remove(e);
            this.addToTargetElem(e);
          }
        }
      }
    };
    IteratorExtensions.<Element>toList(Iterators.<Element>filter(this.sourceModel.getAllContents(), Element.class)).forEach(_function);
    final Function1<MultiElem, Boolean> _function_1 = (MultiElem it) -> {
      String _desc = it.getDesc();
      return Boolean.valueOf(Objects.equals(_desc, this.ruleID));
    };
    final Function1<MultiElem, Boolean> _function_2 = (MultiElem it) -> {
      EObject _targetElement = it.getTargetElement();
      return Boolean.valueOf((_targetElement != null));
    };
    final Function1<MultiElem, Boolean> _function_3 = (MultiElem it) -> {
      boolean _isEmpty = it.getSourceElements().isEmpty();
      return Boolean.valueOf((!_isEmpty));
    };
    final Consumer<MultiElem> _function_4 = (MultiElem corr) -> {
      EObject _targetElement = corr.getTargetElement();
      final bags2.Element t = ((bags2.Element) _targetElement);
      EObject _head = IterableExtensions.<EObject>head(corr.getSourceElements());
      final String groupValue = ((Element) _head).getValue();
      final String lastValue = Elem2Elem.corrToName.get(corr);
      final Integer lastMultiplicity = Elem2Elem.corrToMultiplicity.get(corr);
      final boolean shrunk = ((lastMultiplicity != null) && (corr.getSourceElements().size() < (lastMultiplicity).intValue()));
      if (((lastValue == null) || (!Objects.equals(groupValue, lastValue)))) {
        t.setValue(groupValue);
      } else {
        if (((!Objects.equals(t.getValue(), lastValue)) && (!shrunk))) {
          final Consumer<EObject> _function_5 = (EObject it) -> {
            ((Element) it).setValue(t.getValue());
          };
          corr.getSourceElements().forEach(_function_5);
        } else {
          if (((!Objects.equals(t.getValue(), lastValue)) && shrunk)) {
            t.setValue(groupValue);
          }
        }
      }
      Elem2Elem.corrToName.put(corr, t.getValue());
      final boolean sourceChanged = ((lastMultiplicity == null) || ((lastMultiplicity).intValue() != corr.getSourceElements().size()));
      final boolean targetChanged = ((lastMultiplicity == null) || ((lastMultiplicity).intValue() != t.getMultiplicity()));
      if (sourceChanged) {
        t.setMultiplicity(corr.getSourceElements().size());
      } else {
        if (targetChanged) {
          while ((corr.getSourceElements().size() < t.getMultiplicity())) {
            {
              EObject _createSourceElement = this.createSourceElement(Bags1Package.eINSTANCE.getElement());
              final Procedure1<Element> _function_6 = (Element it) -> {
                it.setValue(t.getValue());
                EObject _sourceElement = this.getCorrModelElem(t.getBag()).getSourceElement();
                it.setBag(((MyBag) _sourceElement));
              };
              final Element newEl = ObjectExtensions.<Element>operator_doubleArrow(((Element) _createSourceElement), _function_6);
              EList<EObject> _sourceElements = corr.getSourceElements();
              _sourceElements.add(newEl);
              Elem2Elem.elementsToCorr.put(newEl, corr);
            }
          }
          while ((corr.getSourceElements().size() > t.getMultiplicity())) {
            EcoreUtil.delete(corr.getSourceElements().get(0), true);
          }
        }
      }
      Elem2Elem.corrToMultiplicity.put(corr, Integer.valueOf(t.getMultiplicity()));
    };
    IteratorExtensions.<MultiElem>toList(IteratorExtensions.<MultiElem>filter(IteratorExtensions.<MultiElem>filter(IteratorExtensions.<MultiElem>filter(Iterators.<MultiElem>filter(this.corrModel.getAllContents(), MultiElem.class), _function_1), _function_2), _function_3)).forEach(_function_4);
    final Function1<bags2.Element, Boolean> _function_5 = (bags2.Element e) -> {
      Corr _corrModelElem = this.getCorrModelElem(e);
      return Boolean.valueOf((_corrModelElem == null));
    };
    final Consumer<bags2.Element> _function_6 = (bags2.Element e) -> {
      Corr _orCreateCorrModelElement = this.getOrCreateCorrModelElement(e, this.ruleID);
      final MultiElem corr = ((MultiElem) _orCreateCorrModelElement);
      while ((corr.getSourceElements().size() < e.getMultiplicity())) {
        {
          final EObject newEl = this.createSourceElement(Bags1Package.eINSTANCE.getElement());
          EList<EObject> _sourceElements = corr.getSourceElements();
          _sourceElements.add(newEl);
          Elem2Elem.elementsToCorr.put(newEl, corr);
        }
      }
      final Consumer<EObject> _function_7 = (EObject it) -> {
        final Element el = ((Element) it);
        el.setValue(e.getValue());
        EObject _sourceElement = this.getCorrModelElem(e.getBag()).getSourceElement();
        el.setBag(((MyBag) _sourceElement));
      };
      corr.getSourceElements().forEach(_function_7);
      Elem2Elem.corrToName.put(corr, e.getValue());
      Elem2Elem.corrToMultiplicity.put(corr, Integer.valueOf(e.getMultiplicity()));
    };
    IteratorExtensions.<bags2.Element>toList(IteratorExtensions.<bags2.Element>filter(Iterators.<bags2.Element>filter(this.targetModel.getAllContents(), bags2.Element.class), _function_5)).forEach(_function_6);
  }

  /**
   * Groups the given Bag1 {@code Element} into the appropriate Bag2 {@code Element}.
   * 
   * <p>The algorithm:
   * <ol>
   *   <li>Search for an existing Bag2 {@code Element} with the same {@code value}
   *       inside the corresponding Bag2 {@code MyBag} (via {@link #findTargetElem}).</li>
   *   <li>If none exists, create a fresh {@code bags2.Element}.</li>
   *   <li>Retrieve or create the {@link MultiElem} correspondence for the Bag2 element.</li>
   *   <li>Add the Bag1 element to {@code MultiElem.sourceElements}.</li>
   *   <li>Set the {@code value} and {@code bag} attributes on the Bag2 element
   *       to match the Bag1 element and its owning bag's target correspondence.</li>
   *   <li>Register the Bag1 element in the shared {@link #elementsToCorr} cache.</li>
   * </ol>
   * </p>
   * 
   * @param e the Bag1 {@code Element} to be grouped into a Bag2 element
   */
  private Corr addToTargetElem(final Element e) {
    Corr _xblockexpression = null;
    {
      bags2.Element newTarget = this.findTargetElem(e);
      if ((newTarget == null)) {
        EObject _createTargetElement = this.createTargetElement(Bags2Package.eINSTANCE.getElement());
        newTarget = ((bags2.Element) _createTargetElement);
      }
      Corr _orCreateCorrModelElement = this.getOrCreateCorrModelElement(newTarget, this.ruleID);
      final MultiElem newCorr = ((MultiElem) _orCreateCorrModelElement);
      EList<EObject> _sourceElements = newCorr.getSourceElements();
      _sourceElements.add(e);
      newTarget.setValue(e.getValue());
      EObject _targetElement = this.getCorrModelElem(e.getBag()).getTargetElement();
      newTarget.setBag(((bags2.MyBag) _targetElement));
      _xblockexpression = Elem2Elem.elementsToCorr.put(e, newCorr);
    }
    return _xblockexpression;
  }

  /**
   * Searches the Bag2 container of the given Bag1 {@code Element} for a
   * Bag2 {@code Element} with a matching {@code value}.
   * 
   * <p>The search is performed inside the Bag2 {@code MyBag} that corresponds to
   * the owning Bag1 {@code MyBag} of {@code e}. If such a Bag2 element already
   * exists it can be reused as the target for the group; otherwise the caller
   * ({@link #addToTargetElem}) creates a new one.</p>
   * 
   * @param e the Bag1 source element whose corresponding Bag2 element is sought
   * @return the first matching Bag2 {@code Element}, or {@code null} if none exists
   */
  private bags2.Element findTargetElem(final Element e) {
    EObject _targetElement = this.getCorrModelElem(e.getBag()).getTargetElement();
    final Function1<bags2.Element, Boolean> _function = (bags2.Element it) -> {
      String _value = it.getValue();
      String _value_1 = e.getValue();
      return Boolean.valueOf(Objects.equals(_value, _value_1));
    };
    return IterableExtensions.<bags2.Element>findFirst(((bags2.MyBag) _targetElement).getElements(), _function);
  }
}
