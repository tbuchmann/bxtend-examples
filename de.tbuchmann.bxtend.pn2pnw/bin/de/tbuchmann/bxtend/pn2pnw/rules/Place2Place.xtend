package de.tbuchmann.bxtend.pn2pnw.rules

import org.eclipse.emf.ecore.resource.Resource
import pn.Place
import pnw.Net

/**
 * Bidirectional transformation rule that synchronises {@code Place} elements
 * between the unweighted Petri net ({@code pn}) and the weighted Petri net
 * ({@code pnw}).
 *
 * <p><b>Correspondence:</b></p>
 * <pre>
 *   pn.Place  ←→  pnw.Place
 *   Synchronised attributes: {@code name}, {@code noOfTokens}
 * </pre>
 *
 * <p>Place containment is maintained via the owning {@code Net}: every place
 * is added to the {@code elements} list of the net that corresponds to its
 * own source/target net (looked up through the shared correspondence map).
 * Therefore this rule must be executed <em>after</em> {@link Net2Net}.</p>
 *
 * <p>Arc connectivity (edges to/from transitions) is handled separately by
 * {@link Transition2Transition}.</p>
 *
 * <p>The rule uses the rule identifier {@code "place"} to tag every
 * {@link de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Corr Corr} it
 * creates.</p>
 */
class Place2Place extends Elem2Elem {

	/**
	 * Constructs the rule and sets the rule identifier to {@code "place"}.
	 *
	 * @param src   the source-model resource (unweighted Petri net)
	 * @param trgt  the target-model resource (weighted Petri net)
	 * @param corr  the correspondence-model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "place"
	}
	
	/**
	 * Forward pass: for every {@code pn.Place} in the source model, finds or
	 * creates the corresponding {@code pnw.Place} in the target model,
	 * synchronises {@code name} and {@code noOfTokens}, and adds the place
	 * to the elements list of the corresponding target {@link Net}.
	 */
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(Place))
			.forEach[p |
				val corr = p.getOrCreateCorrModelElement(ruleID)
				val targetPlace = corr.getOrCreateTargetElem(targetPackage.place) as pnw.Place => [name = p.name; noOfTokens = p.noOfTokens]				
				(p.net.corrModelElem.targetElement as Net).elements += targetPlace				
			]
	}
	
	/**
	 * Backward pass: for every {@code pnw.Place} in the target model, finds or
	 * creates the corresponding {@code pn.Place} in the source model,
	 * synchronises {@code name} and {@code noOfTokens}, and adds the place
	 * to the elements list of the corresponding source {@link pn.Net}.
	 */
	override targetToSource() {
		targetModel.allContents.filter(typeof(pnw.Place))
			.forEach[p |
				val corr = p.getOrCreateCorrModelElement(ruleID)
				val sourcePlace = corr.getOrCreateSourceElem(sourcePackage.place) as Place => [name = p.name; noOfTokens = p.noOfTokens]
				(p.net.corrModelElem.sourceElement as pn.Net).elements += sourcePlace
			]
	}

	/**
	 * Reconciles concurrent edits to {@code Place} pairs.
	 *
	 * <p>{@code name} (the identity/matching key) and {@code noOfTokens} (independent of the
	 * key) are resolved separately: {@code name} follows the same push-forward-on-change /
	 * pull-backward-otherwise logic as {@link Net2Net#synch()} (using {@link #corrToName});
	 * {@code noOfTokens} is compared on <em>both</em> sides against the last-known snapshot
	 * ({@link #corrToTokens}) so a source-only or target-only edit is never silently
	 * discarded, and a genuine conflict (both changed) lets the source win.</p>
	 */
	override void synch() {
		val placeList = sourceModel.allContents.filter(typeof(Place)).toList
		val unmatched = targetModel.allContents.filter(typeof(pnw.Place)).filter[p | p.corrModelElem === null].toList

		placeList.forEach [ p |
			val corr = p.getOrCreateCorrModelElement(ruleID)
			var target = corr.targetElement as pnw.Place
			if (target !== null) {
				unmatched.remove(target)
				if (corrToName.get(corr) != p.name)
					target.name = p.name
				else
					p.name = target.name

				val lastTokens = corrToTokens.get(corr)
				val sourceChanged = lastTokens === null || lastTokens != p.noOfTokens
				val targetChanged = lastTokens === null || lastTokens != target.noOfTokens
				if (sourceChanged)
					target.noOfTokens = p.noOfTokens
				else if (targetChanged)
					p.noOfTokens = target.noOfTokens
			} else {
				target = unmatched.findFirst[t | t.name == p.name]
				if (target !== null) {
					corr.targetElement = target
					elementsToCorr.put(target, corr)
					unmatched.remove(target)
					target.noOfTokens = p.noOfTokens
				} else {
					target = corr.getOrCreateTargetElem(targetPackage.place) as pnw.Place => [name = p.name; noOfTokens = p.noOfTokens]
					(p.net.corrModelElem.targetElement as Net).elements += target
				}
			}
			corrToName.put(corr, p.name)
			corrToTokens.put(corr, target.noOfTokens)
		]

		unmatched.forEach [ wp |
			val corr = wp.getOrCreateCorrModelElement(ruleID)
			val sp = corr.getOrCreateSourceElem(sourcePackage.place) as Place => [name = wp.name; noOfTokens = wp.noOfTokens]
			(wp.net.corrModelElem.sourceElement as pn.Net).elements += sp
			corrToName.put(corr, sp.name)
			corrToTokens.put(corr, sp.noOfTokens)
		]
	}
}