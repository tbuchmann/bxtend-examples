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
}