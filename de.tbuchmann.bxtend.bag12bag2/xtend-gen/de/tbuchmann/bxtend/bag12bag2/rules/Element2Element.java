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
    final Procedure1<Element> _function = (Element e) -> {
      Corr _corrModelElem = this.getCorrModelElem(e);
      final MultiElem corr = ((MultiElem) _corrModelElem);
      if ((corr == null)) {
        this.addToTargetElem(e);
      } else {
        EObject _targetElement = corr.getTargetElement();
        final bags2.Element t = ((bags2.Element) _targetElement);
        final Function1<EObject, Boolean> _function_1 = (EObject it) -> {
          return Boolean.valueOf(((it instanceof Element) && Objects.equals(((Element) it).getValue(), e.getValue())));
        };
        boolean _forall = IterableExtensions.<EObject>forall(corr.getSourceElements(), _function_1);
        if (_forall) {
          t.setValue(e.getValue());
        }
        String _value = t.getValue();
        String _value_1 = e.getValue();
        boolean _notEquals = (!Objects.equals(_value, _value_1));
        if (_notEquals) {
          EList<EObject> _sourceElements = corr.getSourceElements();
          _sourceElements.remove(e);
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
      ((bags2.Element) _targetElement).setMultiplicity(c.getSourceElements().size());
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
        EList<EObject> _sourceElements = corr.getSourceElements();
        EObject _createSourceElement = this.createSourceElement(Bags1Package.eINSTANCE.getElement());
        _sourceElements.add(_createSourceElement);
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
    };
    IteratorExtensions.<bags2.Element>forEach(Iterators.<bags2.Element>filter(this.targetModel.getAllContents(), bags2.Element.class), _function);
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
