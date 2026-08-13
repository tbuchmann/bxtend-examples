package de.tbuchmann.bxtend.set2oset.rules

import org.eclipse.emf.ecore.resource.Resource
import osets.Element
import osets.MyOrderedSet
import sets.MySet

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
class Element2Element extends Elem2Elem {

	/**
	 * Constructs the rule and registers it against the given three EMF resources.
	 *
	 * @param src  the EMF resource containing the source ({@code MySet}) model
	 * @param trgt the EMF resource containing the target ({@code MyOrderedSet}) model
	 * @param corr the EMF resource containing the correspondence model
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "Element2Element";
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
	override void sourceToTarget() {
		// Find the current tail of the linked list (the element with no successor).
		// New target elements will be appended after this tail.
		var Element tail = targetModel.allContents.filter(typeof(Element)).findFirst[next === null]
		for (var it = sourceModel.allContents.filter(typeof(sets.Element)); it.hasNext();) {
			val source = it.next();
			val corr = source.getOrCreateCorrModelElement(ruleID);
			var Element target = corr.targetElement as Element;
			if (target === null) {
				// New source element – create the target element and append it to the list.
				target = corr.getOrCreateTargetElem(targetPackage.element) as Element;
				target.previous = tail;
				tail = target;
			}
			// Synchronise the value attribute and wire the containment to MyOrderedSet.
			target.value = source.value;
			target.orderedSet = source.eContainer.corrModelElem.targetElement as MyOrderedSet;
			// Seed corrToName here too (not just in synch()): a correspondence created via
			// this plain forward path must not look "unsynced" (null) to a later synch()
			// call, which would otherwise treat the null as "source changed" and push the
			// stale source value back over a legitimate concurrent target-side rename.
			corrToName.put(corr, source.value)
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
	override void targetToSource() {
		targetModel.allContents.filter(typeof(Element)).forEach[target |
			val corr = target.getOrCreateCorrModelElement(ruleID);
			val source = corr.getOrCreateSourceElem(sourcePackage.element) as sets.Element;
			source.value = target.value;
			source.set = target.eContainer.corrModelElem.sourceElement as MySet;
			corrToName.put(corr, source.value)
		]
	}

	/**
	 * Reconciles concurrent edits to {@code sets.Element} ↔ {@code osets.Element} pairs.
	 *
	 * <p>{@code value} is both the content and the matching key here (unlike
	 * {@code Place2Place} there is no separate independent attribute), so it follows the same
	 * push-forward-on-change / pull-backward-otherwise logic as
	 * {@link MySet2MyOrderedSet#synch()}, using {@link #corrToName}.</p>
	 *
	 * <p>List-append semantics are preserved exactly as in {@link #sourceToTarget()}: only a
	 * genuinely new target element (created because neither an existing correspondence nor a
	 * same-valued unmatched target element was found) is appended after the current tail of
	 * the doubly-linked list. Re-linked or already-linked elements keep their existing
	 * position. A brand-new target-only element (no correspondence at all) is pulled backward
	 * without touching the list, exactly as {@link #targetToSource()} does.</p>
	 */
	override void synch() {
		val elemList = sourceModel.allContents.filter(typeof(sets.Element)).toList
		val unmatched = targetModel.allContents.filter(typeof(Element)).filter[e | e.corrModelElem === null].toList
		var Element tail = targetModel.allContents.filter(typeof(Element)).findFirst[next === null]

		// Plain for loops (not forEach closures) because Xtend compiles forEach[...] to a
		// native Java lambda, which requires captured locals like `tail` to be effectively
		// final; a for loop allows the reassignment below.
		for (source : elemList) {
			val corr = source.getOrCreateCorrModelElement(ruleID)
			var target = corr.targetElement as Element
			if (target !== null) {
				unmatched.remove(target)
				if (corrToName.get(corr) != source.value)
					target.value = source.value
				else
					source.value = target.value
			} else {
				target = unmatched.findFirst[t | t.value == source.value]
				if (target !== null) {
					corr.targetElement = target
					elementsToCorr.put(target, corr)
					unmatched.remove(target)
				} else {
					target = corr.getOrCreateTargetElem(targetPackage.element) as Element
					target.previous = tail
					tail = target
					target.value = source.value
				}
				target.orderedSet = source.eContainer.corrModelElem.targetElement as MyOrderedSet
			}
			corrToName.put(corr, source.value)
		}

		for (target : unmatched) {
			val corr = target.getOrCreateCorrModelElement(ruleID)
			val source = corr.getOrCreateSourceElem(sourcePackage.element) as sets.Element => [value = target.value]
			source.set = target.eContainer.corrModelElem.sourceElement as MySet
			corrToName.put(corr, source.value)
		}
	}
}