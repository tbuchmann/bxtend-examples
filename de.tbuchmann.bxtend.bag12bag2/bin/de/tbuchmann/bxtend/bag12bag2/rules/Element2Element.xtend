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
			(c.targetElement as bags2.Element).multiplicity = c.sourceElements.size
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
				corr.sourceElements += createSourceElement(Bags1Package.eINSTANCE.element)
			}
			while(corr.sourceElements.size > e.multiplicity) {
				EcoreUtil.delete(corr.sourceElements.get(0), true)
			}
			corr.sourceElements.forEach[ 
				val el = it as Element
				el.value = e.value
				el.bag = e.bag.getCorrModelElem.sourceElement as bags1.MyBag
			]
			
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