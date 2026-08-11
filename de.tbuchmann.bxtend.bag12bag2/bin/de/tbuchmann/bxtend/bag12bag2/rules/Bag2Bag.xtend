package de.tbuchmann.bxtend.bag12bag2.rules

import bags1.Bags1Package
import bags1.MyBag
import bags2.Bags2Package
import org.eclipse.emf.ecore.resource.Resource

/**
 * BXtend rule that establishes a one-to-one correspondence between the
 * root container objects of the two bag models.
 *
 * <h2>Transformation Semantics</h2>
 * <p>Both Bag1 and Bag2 use a single {@code MyBag} object as the top-level container
 * that owns all individual element objects. This rule keeps the two root containers
 * in correspondence:</p>
 * <pre>
 *   Bag1 MyBag  ←──── BasicElem ────→  Bag2 MyBag
 * </pre>
 * <p>The correspondence is stored as a {@link de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.BasicElem}
 * because it is a strict 1-to-1 relationship: exactly one Bag1 root maps to exactly
 * one Bag2 root. The {@code desc} attribute of the {@code BasicElem} is set to
 * {@value #RULE_ID} so that other rules can identify correspondences created by this
 * rule when iterating over the correspondence model.</p>
 *
 * <h2>Forward Propagation ({@code sourceToTarget})</h2>
 * <p>Iterates over every {@code MyBag} instance in the Bag1 resource (in practice there
 * is always exactly one), retrieves or creates a {@code BasicElem} correspondence, and
 * retrieves or creates the corresponding Bag2 {@code MyBag}. The Bag2 root is then
 * added to the target resource if it is not already contained there.</p>
 *
 * <h2>Backward Propagation ({@code targetToSource})</h2>
 * <p>Mirrors the forward direction: iterates over every {@code bags2.MyBag} in the
 * target resource, retrieves or creates a {@code BasicElem} correspondence, and
 * retrieves or creates the corresponding Bag1 {@code MyBag}. The Bag1 root is added to
 * the source resource if it is not already contained there.</p>
 *
 * <h2>Note on Element Content</h2>
 * <p>This rule only manages the root {@code MyBag} containers themselves. The
 * individual {@code Element} objects that live inside each bag are handled by the
 * {@link Element2Element} rule, which runs immediately after this one in the rule
 * pipeline defined by {@link Bag12bag2Transformation#addRules()}.</p>
 *
 * @see Element2Element
 * @see Elem2Elem
 * @see Bag12bag2Transformation
 */
class Bag2Bag extends Elem2Elem {
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "Bag2Bag"
	}
	
	/**
	 * Forward propagation: Bag1 → Bag2.
	 *
	 * <p>For each {@code MyBag} in the Bag1 source resource:
	 * <ol>
	 *   <li>Retrieves or creates a {@link de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.BasicElem}
	 *       correspondence via {@link #getOrCreateCorrModelElement}.</li>
	 *   <li>Retrieves or creates the linked Bag2 {@code MyBag} via
	 *       {@link #getOrCreateTargetElem}.</li>
	 *   <li>Ensures the Bag2 {@code MyBag} is contained in the target resource.</li>
	 * </ol>
	 * </p>
	 */
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(MyBag)).forEach[ b |
				val corr = b.getOrCreateCorrModelElement(ruleID)
				val targetElement = corr.getOrCreateTargetElem(Bags2Package.eINSTANCE.myBag) as bags2.MyBag
				if(!targetModel.contents.contains(targetElement))
					targetModel.contents.add(targetElement)
		]
	}
	
	/**
	 * Backward propagation: Bag2 → Bag1.
	 *
	 * <p>For each {@code bags2.MyBag} in the Bag2 target resource:
	 * <ol>
	 *   <li>Retrieves or creates a {@link de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.BasicElem}
	 *       correspondence via {@link #getOrCreateCorrModelElement}.</li>
	 *   <li>Retrieves or creates the first linked Bag1 {@code MyBag} via
	 *       {@link #getOrCreateSourceElem} (index 0 of the returned list).</li>
	 *   <li>Ensures the Bag1 {@code MyBag} is contained in the source resource.</li>
	 * </ol>
	 * </p>
	 */
	override targetToSource() {
		targetModel.allContents.filter(typeof(bags2.MyBag)).forEach[b |
				val corr = b.getOrCreateCorrModelElement(ruleID)
				val targetElement = corr.getOrCreateSourceElem(Bags1Package.eINSTANCE.myBag).get(0) as MyBag
				if(!sourceModel.contents.contains(targetElement))
					sourceModel.contents.add(targetElement)
		]
	}

	/**
	 * Reconciles the root {@code MyBag} pair.
	 *
	 * <p>{@code MyBag} carries no attributes — it is purely a container — so there is
	 * nothing to push or pull beyond re-establishing the correspondence itself, which
	 * {@link #sourceToTarget()} already does idempotently.</p>
	 */
	override void synch() {
		sourceToTarget()
	}
}