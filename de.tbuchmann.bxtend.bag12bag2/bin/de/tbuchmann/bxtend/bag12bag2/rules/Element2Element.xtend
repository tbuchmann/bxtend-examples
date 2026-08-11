package de.tbuchmann.bxtend.bag12bag2.rules

import bags1.Bags1Package
import bags1.Element
import bags2.Bags2Package
import bags2.MyBag
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.util.EcoreUtil
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.MultiElem

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
class Element2Element extends Elem2Elem {
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "Element2Element"
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
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(Element)).forEach[e | 
			val corr = e.getCorrModelElem  as MultiElem
			if(corr === null) {
				e.addToTargetElem
			} else {
				val t = corr.targetElement as bags2.Element
				if(corr.sourceElements.forall[it instanceof Element && (it as Element).value == e.value]) {
					t.value = e.value
				}
				if(t.value != e.value) {
					corr.sourceElements -= e
					e.addToTargetElem
				}
			}
		]
		// Recompute multiplicities: each Bag2 Element's multiplicity must equal the
		// number of Bag1 Elements that are grouped under its MultiElem correspondence.
		corrModel.allContents.filter[it instanceof MultiElem && (it as MultiElem).desc == ruleID].forEach[
			val c = it as MultiElem
			val t = c.targetElement as bags2.Element
			t.multiplicity = c.sourceElements.size
			corrToName.put(c, t.value)
			corrToMultiplicity.put(c, t.multiplicity)
		]
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
	override targetToSource() {
		targetModel.allContents.filter(typeof(bags2.Element)).forEach[ e |
			val corr = e.getOrCreateCorrModelElement(ruleID) as MultiElem
			while(corr.sourceElements.size < e.multiplicity) {
				val newEl = createSourceElement(Bags1Package.eINSTANCE.element)
				corr.sourceElements += newEl
				elementsToCorr.put(newEl, corr)
			}
			while(corr.sourceElements.size > e.multiplicity) {
				EcoreUtil.delete(corr.sourceElements.get(0), true)
			}
			corr.sourceElements.forEach[
				val el = it as Element
				el.value = e.value
				el.bag = e.bag.getCorrModelElem.sourceElement as bags1.MyBag
			]
			corrToName.put(corr, e.value)
			corrToMultiplicity.put(corr, e.multiplicity)
		]
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
	 *       (source wins) if both changed since the last synchronisation.</li>
	 *   <li><b>Absorb target-only insertions:</b> any Bag2 {@code Element} that still has
	 *       no correspondence at all is pulled backward into a freshly created Bag1 group,
	 *       mirroring {@link #targetToSource()}.</li>
	 * </ol>
	 */
	override void synch() {
		sourceModel.allContents.filter(typeof(Element)).toList.forEach[ e |
			val corr = e.getCorrModelElem as MultiElem
			if (corr === null) {
				e.addToTargetElem
			} else if (corr.targetElement !== null) {
				val lastValue = corrToName.get(corr)
				if (lastValue !== null && e.value != lastValue) {
					// this element's value changed since the last synchronisation; it no
					// longer belongs to its current group. The group's value itself (as
					// opposed to this one element) is resolved in the pass below.
					corr.sourceElements -= e
					e.addToTargetElem
				}
			}
			// else: corr.targetElement === null - this element's group was concurrently
			// deleted on the target side. Deliberately do NOT resurrect it here (that would
			// let a concurrent source-side addition to the same group revive a group the
			// target explicitly deleted, accumulating stale and new elements together).
			// Leave it attached to the now-dead corr so the orchestrator's
			// deleteUnreferencedSourceElements() sweeps it up afterwards, same as it would
			// for a plain (non-concurrent) target-side deletion.
		]

		corrModel.allContents.filter(typeof(MultiElem)).filter[desc == ruleID].filter[targetElement !== null].filter[!sourceElements.empty].toList.forEach[ corr |
			val t = corr.targetElement as bags2.Element
			val groupValue = (corr.sourceElements.head as Element).value
			val lastValue = corrToName.get(corr)
			if (lastValue === null || groupValue != lastValue)
				t.value = groupValue
			else if (t.value != lastValue)
				corr.sourceElements.forEach[(it as Element).value = t.value]
			corrToName.put(corr, t.value)

			val lastMultiplicity = corrToMultiplicity.get(corr)
			val sourceChanged = lastMultiplicity === null || lastMultiplicity != corr.sourceElements.size
			val targetChanged = lastMultiplicity === null || lastMultiplicity != t.multiplicity
			if (sourceChanged) {
				t.multiplicity = corr.sourceElements.size
			} else if (targetChanged) {
				while (corr.sourceElements.size < t.multiplicity) {
					val newEl = createSourceElement(Bags1Package.eINSTANCE.element) as Element => [
						value = t.value
						bag = t.bag.getCorrModelElem.sourceElement as bags1.MyBag
					]
					corr.sourceElements += newEl
					elementsToCorr.put(newEl, corr)
				}
				while (corr.sourceElements.size > t.multiplicity) {
					EcoreUtil.delete(corr.sourceElements.get(0), true)
				}
			}
			corrToMultiplicity.put(corr, t.multiplicity)
		]

		targetModel.allContents.filter(typeof(bags2.Element)).filter[e | e.corrModelElem === null].toList.forEach[ e |
			val corr = e.getOrCreateCorrModelElement(ruleID) as MultiElem
			while (corr.sourceElements.size < e.multiplicity) {
				val newEl = createSourceElement(Bags1Package.eINSTANCE.element)
				corr.sourceElements += newEl
				elementsToCorr.put(newEl, corr)
			}
			corr.sourceElements.forEach[
				val el = it as Element
				el.value = e.value
				el.bag = e.bag.getCorrModelElem.sourceElement as bags1.MyBag
			]
			corrToName.put(corr, e.value)
			corrToMultiplicity.put(corr, e.multiplicity)
		]
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
	def private addToTargetElem(Element e) {
		var newTarget = e.findTargetElem
		if(newTarget === null) {
			newTarget = createTargetElement(Bags2Package.eINSTANCE.element) as bags2.Element
		}
		val newCorr = newTarget.getOrCreateCorrModelElement(ruleID) as MultiElem
		newCorr.sourceElements += e
		newTarget.value = e.value
		newTarget.bag = e.bag.getCorrModelElem.targetElement as MyBag
		elementsToCorr.put(e, newCorr)
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
	def private findTargetElem(Element e) {
		(e.bag.getCorrModelElem.targetElement as MyBag).elements.findFirst[it.value == e.value]
	}
}