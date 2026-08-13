package de.tbuchmann.bxtend.set2oset.rules

import org.eclipse.emf.ecore.resource.Resource
import osets.MyOrderedSet
import sets.MySet

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
class MySet2MyOrderedSet extends Elem2Elem {

	/**
	 * Constructs the rule and registers it against the given three EMF resources.
	 *
	 * @param src  the EMF resource containing the source ({@code MySet}) model
	 * @param trgt the EMF resource containing the target ({@code MyOrderedSet}) model
	 * @param corr the EMF resource containing the correspondence model
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "MySet2MyOrderedSet";
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
	override void sourceToTarget() {
		sourceModel.allContents.filter(typeof(MySet)).forEach[source |
			val corr = source.getOrCreateCorrModelElement(ruleID);
			val target = corr.getOrCreateTargetElem(targetPackage.myOrderedSet) as MyOrderedSet;
			target.setName(source.getName());
			targetModel.contents += target
			corrToName.put(corr, source.name)
		]
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
	override void targetToSource() {
		targetModel.allContents.filter(typeof(MyOrderedSet)).forEach[target |
			val corr = target.getOrCreateCorrModelElement(ruleID);
			val source = corr.getOrCreateSourceElem(sourcePackage.mySet) as MySet;
			source.setName(target.getName());
			sourceModel.contents += source
			corrToName.put(corr, source.name)
		]
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
	override void synch() {
		val setList = sourceModel.allContents.filter(typeof(MySet)).toList
		val unmatchedSets = targetModel.allContents.filter(typeof(MyOrderedSet)).filter[s | s.corrModelElem === null].toList

		setList.forEach [ source |
			val corr = source.getOrCreateCorrModelElement(ruleID)
			var target = corr.targetElement as MyOrderedSet
			if (target !== null) {
				unmatchedSets.remove(target)
				if (corrToName.get(corr) != source.name)
					target.name = source.name
				else
					source.name = target.name
			} else {
				target = unmatchedSets.findFirst[t | t.name == source.name]
				if (target !== null) {
					corr.targetElement = target
					elementsToCorr.put(target, corr)
					unmatchedSets.remove(target)
				} else {
					target = corr.getOrCreateTargetElem(targetPackage.myOrderedSet) as MyOrderedSet => [name = source.name]
					targetModel.contents += target
				}
			}
			corrToName.put(corr, source.name)
		]

		unmatchedSets.forEach [ target |
			val corr = target.getOrCreateCorrModelElement(ruleID)
			val source = corr.getOrCreateSourceElem(sourcePackage.mySet) as MySet => [name = target.name]
			sourceModel.contents += source
			corrToName.put(corr, source.name)
		]
	}
}