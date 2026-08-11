package de.tbuchmann.bxtend.pn2pnw.rules

import org.eclipse.emf.ecore.resource.Resource
import pn.Net

/**
 * Bidirectional transformation rule that synchronises the root {@code Net}
 * element between the unweighted Petri net ({@code pn}) and the weighted
 * Petri net ({@code pnw}).
 *
 * <p>This is always the <em>first</em> rule executed during a transformation
 * pass, because places and transitions can only be added to a net that already
 * exists on both sides.</p>
 *
 * <p><b>Correspondence:</b></p>
 * <pre>
 *   pn.Net  ←→  pnw.Net
 *   Synchronised attributes: {@code name}
 * </pre>
 *
 * <p>The rule uses the rule identifier {@code "root"} to tag every
 * {@link de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Corr Corr} it
 * creates, allowing the correspondence model to distinguish net-level entries
 * from element-level entries.</p>
 */
class Net2Net extends Elem2Elem {

	/**
	 * Constructs the rule and sets the rule identifier to {@code "root"}.
	 *
	 * @param src   the source-model resource (unweighted Petri net)
	 * @param trgt  the target-model resource (weighted Petri net)
	 * @param corr  the correspondence-model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "root"
	}
	
	/**
	 * Forward pass: for every {@code pn.Net} in the source model, finds or
	 * creates the corresponding {@code pnw.Net} in the target model, and
	 * synchronises the {@code name} attribute.
	 *
	 * <p>The net object is added to the target resource's root-content list
	 * (which is idempotent for EMF resources – adding an already-contained
	 * object is a no-op).</p>
	 */
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(Net))
			.forEach[n |
				val corr = n.getOrCreateCorrModelElement(ruleID)
				val targetNet = corr.getOrCreateTargetElem(targetPackage.net) as pnw.Net
				targetNet.name = n.name
				targetModel.contents += targetNet
			]
	}
	
	/**
	 * Backward pass: for every {@code pnw.Net} in the target model, finds or
	 * creates the corresponding {@code pn.Net} in the source model, and
	 * synchronises the {@code name} attribute.
	 *
	 * <p>The net object is added to the source resource's root-content list
	 * (idempotent for EMF resources).</p>
	 */
	override targetToSource() {
		targetModel.allContents.filter(typeof(pnw.Net))
			.forEach[wn |
				val corr = wn.getOrCreateCorrModelElement(ruleID)
				val sourceNet = corr.getOrCreateSourceElem(sourcePackage.net) as Net
				sourceNet.name = wn.name
				sourceModel.contents += sourceNet
			]
	}

	/**
	 * Reconciles the root {@code Net} pair. Both models are single-root, so there is
	 * normally at most one unmatched element per side.
	 *
	 * <ol>
	 *   <li>If already linked, push the name forward when it changed on the source since
	 *       the last synchronisation ({@link #corrToName}), otherwise pull it backward.</li>
	 *   <li>If unlinked, re-link to an unmatched same-named net, or create a new one.</li>
	 *   <li>Any net still unmatched afterwards is used to create the missing counterpart
	 *       (target-side insertion).</li>
	 * </ol>
	 */
	override void synch() {
		val netList = sourceModel.allContents.filter(typeof(Net)).toList
		val unmatchedNets = targetModel.allContents.filter(typeof(pnw.Net)).filter[n | n.corrModelElem === null].toList

		netList.forEach [ n |
			val corr = n.getOrCreateCorrModelElement(ruleID)
			var target = corr.targetElement as pnw.Net
			if (target !== null) {
				unmatchedNets.remove(target)
				if (corrToName.get(corr) != n.name)
					target.name = n.name
				else
					n.name = target.name
			} else {
				target = unmatchedNets.findFirst[t | t.name == n.name]
				if (target !== null) {
					corr.targetElement = target
					elementsToCorr.put(target, corr)
					unmatchedNets.remove(target)
				} else {
					target = corr.getOrCreateTargetElem(targetPackage.net) as pnw.Net => [name = n.name]
					targetModel.contents += target
				}
			}
			corrToName.put(corr, n.name)
		]

		unmatchedNets.forEach [ wn |
			val corr = wn.getOrCreateCorrModelElement(ruleID)
			val n = corr.getOrCreateSourceElem(sourcePackage.net) as Net => [name = wn.name]
			sourceModel.contents += n
			corrToName.put(corr, n.name)
		]
	}
}