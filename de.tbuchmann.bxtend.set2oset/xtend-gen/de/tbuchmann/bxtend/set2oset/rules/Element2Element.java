package de.tbuchmann.bxtend.set2oset.rules;

import com.google.common.collect.Iterators;
import de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Corr;
import java.util.Iterator;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.xbase.lib.Functions.Function1;
import org.eclipse.xtext.xbase.lib.IteratorExtensions;
import org.eclipse.xtext.xbase.lib.Procedures.Procedure1;
import osets.Element;
import osets.MyOrderedSet;
import sets.MySet;

/**
 * BXtend rule that synchronises individual elements of the two models:
 * {@code sets.Element} (source) ↔ {@code osets.Element} (target).
 * 
 * <p>The key asymmetry between the source and the target metamodel is that
 * {@code osets.Element} participates in a <em>doubly-linked list</em> via its
 * {@code next} / {@code previous} cross-references, whereas {@code sets.Element}
 * has no ordering information at all.  This rule bridges that gap:</p>
 * <ul>
 *   <li>During <strong>forward propagation</strong> newly created target elements are
 *       appended to the tail of the existing linked list so that previously established
 *       ordering is preserved.</li>
 *   <li>During <strong>backward propagation</strong> the ordering attributes are ignored
 *       (they have no counterpart in the source); only the {@code value} attribute is
 *       propagated back.</li>
 * </ul>
 * 
 * <h2>Rule ordering dependency</h2>
 * <p>This rule relies on the container correspondence established by
 * {@link MySet2MyOrderedSet}: when setting {@code target.orderedSet} /
 * {@code source.set} it navigates to the container's correspondence via
 * {@code eContainer.corrModelElem.targetElement} and
 * {@code eContainer.corrModelElem.sourceElement} respectively.
 * {@link MySet2MyOrderedSet} must therefore be applied <em>before</em> this rule in
 * {@link Set2osetTransformation}.</p>
 * 
 * <h2>Forward propagation detail – linked-list maintenance</h2>
 * <p>BXtend's generated template appends every new {@code osets.Element} to the tail of the
 * doubly-linked list that currently exists in the target model.  Before the loop the current
 * tail is located by searching for the {@code osets.Element} whose {@code next} reference is
 * {@code null}.  Each new element sets its {@code previous} pointer to that tail and becomes
 * the new tail.  Elements that already exist (found via their correspondence) are <em>not</em>
 * re-linked, preserving any reordering the user may have performed on the target side.</p>
 * 
 * <h2>Backward propagation detail</h2>
 * <p>Because the source metamodel ({@code Sets.ecore}) has no ordering concept, the
 * backward direction is straightforward: for each {@code osets.Element} in the target the
 * rule looks up or creates a corresponding {@code sets.Element}, copies the {@code value}
 * attribute, and wires the element to its parent {@code MySet} via the {@code set}
 * containment reference.  The doubly-linked-list references ({@code next}/{@code previous})
 * on the target side are not read, modified, or propagated.</p>
 */
@SuppressWarnings("all")
public class Element2Element extends Elem2Elem {
  /**
   * Constructs the rule and registers it against the given three EMF resources.
   * 
   * @param src  the EMF resource containing the source ({@code MySet}) model
   * @param trgt the EMF resource containing the target ({@code MyOrderedSet}) model
   * @param corr the EMF resource containing the correspondence model
   */
  public Element2Element(final Resource src, final Resource trgt, final Resource corr) {
    super(src, trgt, corr);
    this.ruleID = "Element2Element";
  }

  /**
   * Forward propagation: source → target.
   * 
   * <p>Algorithm:</p>
   * <ol>
   *   <li>Locate the current tail of the target linked list (the {@code osets.Element} whose
   *       {@code next} is {@code null}), or {@code null} if the list is empty.  New elements
   *       will be appended after this tail.</li>
   *   <li>For each {@code sets.Element} in the source model:
   *     <ol type="a">
   *       <li>Retrieve or create the {@link de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Corr}
   *           for this source element.</li>
   *       <li>Retrieve the existing target element from the correspondence (may be
   *           {@code null} for a newly added source element).</li>
   *       <li>If no target element exists yet, create one, set its {@code previous}
   *           pointer to the current tail, and advance the tail pointer to the new
   *           element.  This appends the new element at the end of the linked list
   *           without disturbing existing ordering.</li>
   *       <li>Copy the {@code value} attribute from source to target.</li>
   *       <li>Wire the target element to the corresponding {@code MyOrderedSet} via the
   *           {@code orderedSet} containment reference (looked up through the container's
   *           correspondence entry).</li>
   *     </ol>
   *   </li>
   * </ol>
   * 
   * <p><b>Note on linked-list integrity:</b> Only <em>new</em> elements (those without a
   * pre-existing target correspondence) receive a {@code previous} assignment here.
   * Existing elements keep their current position in the list.  The complementary
   * re-linking after deletions is handled by
   * {@link Set2osetTransformation#deleteUnreferencedTargetElements()}.</p>
   */
  @Override
  public void sourceToTarget() {
    final Function1<Element, Boolean> _function = (Element it) -> {
      Element _next = it.getNext();
      return Boolean.valueOf((_next == null));
    };
    Element tail = IteratorExtensions.<Element>findFirst(Iterators.<Element>filter(this.targetModel.getAllContents(), Element.class), _function);
    for (Iterator<sets.Element> it = Iterators.<sets.Element>filter(this.sourceModel.getAllContents(), sets.Element.class); it.hasNext();) {
      {
        final sets.Element source = it.next();
        final Corr corr = this.getOrCreateCorrModelElement(source, this.ruleID);
        EObject _targetElement = corr.getTargetElement();
        Element target = ((Element) _targetElement);
        if ((target == null)) {
          EObject _orCreateTargetElem = this.getOrCreateTargetElem(corr, this.targetPackage.getElement());
          target = ((Element) _orCreateTargetElem);
          target.setPrevious(tail);
          tail = target;
        }
        target.setValue(source.getValue());
        EObject _targetElement_1 = this.getCorrModelElem(source.eContainer()).getTargetElement();
        target.setOrderedSet(((MyOrderedSet) _targetElement_1));
      }
    }
  }

  /**
   * Backward propagation: target → source.
   * 
   * <p>For each {@code osets.Element} in the target model:</p>
   * <ol>
   *   <li>Retrieve or create a {@link de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Corr}
   *       for this target element.</li>
   *   <li>Retrieve or create the corresponding {@code sets.Element} source object.</li>
   *   <li>Copy the {@code value} attribute from target to source.</li>
   *   <li>Wire the source element to the corresponding {@code MySet} via the {@code set}
   *       containment reference (looked up through the container's correspondence entry).</li>
   * </ol>
   * 
   * <p>Ordering information ({@code next} / {@code previous}) is intentionally not
   * propagated back, since the source metamodel ({@code Sets.ecore}) has no order concept.</p>
   */
  @Override
  public void targetToSource() {
    final Procedure1<Element> _function = (Element target) -> {
      final Corr corr = this.getOrCreateCorrModelElement(target, this.ruleID);
      EObject _orCreateSourceElem = this.getOrCreateSourceElem(corr, this.sourcePackage.getElement());
      final sets.Element source = ((sets.Element) _orCreateSourceElem);
      source.setValue(target.getValue());
      EObject _sourceElement = this.getCorrModelElem(target.eContainer()).getSourceElement();
      source.setSet(((MySet) _sourceElement));
    };
    IteratorExtensions.<Element>forEach(Iterators.<Element>filter(this.targetModel.getAllContents(), Element.class), _function);
  }
}
