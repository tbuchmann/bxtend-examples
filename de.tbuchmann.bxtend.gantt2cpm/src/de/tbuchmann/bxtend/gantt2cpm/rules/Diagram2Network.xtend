package de.tbuchmann.bxtend.gantt2cpm.rules

import cpm.CPMNetwork
import gantt.GanttDiagram
import org.eclipse.emf.ecore.resource.Resource

/**
 * Transformation rule that maps the root {@code gantt.GanttDiagram} container to
 * the root {@code cpm.CPMNetwork} container (and vice versa).
 *
 * <p><b>Metamodel correspondence:</b></p>
 * <pre>
 *   gantt.GanttDiagram  ←→  cpm.CPMNetwork
 *     name                  name
 *     (root of source)      (root of target)
 * </pre>
 *
 * <p><b>Role in the transformation chain:</b> This rule <em>must</em> execute
 * before {@link Activity2Activity} and {@link Dependency2Activity} so that the
 * {@code CPMNetwork} / {@code GanttDiagram} containers exist in the target /
 * source model and in the correspondence map before any child elements try to
 * look them up.  The rule is therefore always added first to the rule list in
 * {@link Gantt2cpmTransformation}.</p>
 *
 * <p><b>Cardinality assumption:</b> Exactly one {@code GanttDiagram} / one
 * {@code CPMNetwork} is present in the respective model resource (accessed via
 * {@code contents.get(0)}).  Multi-root models are not supported by this rule.</p>
 *
 * <p><b>Rule identifier:</b> {@code "root"} – stored in the {@link Corr}
 * correspondence entry created for the diagram/network pair and used to
 * distinguish the root correspondence from element-level correspondences during
 * deletion detection.</p>
 */
class Diagram2Network extends Elem2Elem {

	/**
	 * Constructs the rule and registers it under the rule identifier {@code "root"}.
	 *
	 * @param src  EMF resource holding the source (Gantt) model
	 * @param trgt EMF resource holding the target (CPM) model
	 * @param corr EMF resource holding the correspondence model
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "root"
	}
	
	/**
	 * Propagates the {@code GanttDiagram} root element to the CPM target model
	 * (forward direction: Gantt → CPM).
	 *
	 * <p>Steps:</p>
	 * <ol>
	 *   <li>Retrieve the single {@link GanttDiagram} from the source resource.</li>
	 *   <li>Look up or create a {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr}
	 *       for the diagram (keyed by rule ID {@code "root"}).</li>
	 *   <li>Look up or create the corresponding {@link CPMNetwork}; copy {@code name}.</li>
	 *   <li>Ensure the network is added to the target resource's content root.</li>
	 * </ol>
	 */
	override void sourceToTarget() {
		val diag = sourceModel.contents.get(0) as GanttDiagram
 		val corr = diag.getOrCreateCorrModelElement(ruleID)
 		var net = corr.getOrCreateTargetElem(targetPackage.CPMNetwork) as CPMNetwork => [name = diag.name]
 		targetModel.contents += net
 		corrToName.put(corr, diag.name)
		//super.sourceToTarget()
	}
	
	/**
	 * Propagates the {@code CPMNetwork} root element back to the Gantt source model
	 * (backward direction: CPM → Gantt).
	 *
	 * <p>Steps:</p>
	 * <ol>
	 *   <li>Retrieve the single {@link CPMNetwork} from the target resource.</li>
	 *   <li>Look up or create a {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr}
	 *       for the network.</li>
	 *   <li>Look up or create the corresponding {@link GanttDiagram}; copy {@code name}.</li>
	 *   <li>Ensure the diagram is added to the source resource's content root.</li>
	 * </ol>
	 */
	override void targetToSource() {
		val net = targetModel.contents.get(0) as CPMNetwork
		val corr = net.getOrCreateCorrModelElement(ruleID)
		var diag = corr.getOrCreateSourceElem(sourcePackage.ganttDiagram) as GanttDiagram => [name = net.name]
		sourceModel.contents += diag
		corrToName.put(corr, diag.name)
		//super.targetToSource()
	}

	/**
	 * Reconciles the root {@code GanttDiagram} ↔ {@code CPMNetwork} pair.
	 *
	 * <p>Both models are single-root, so there is normally at most one unmatched
	 * element per side (e.g. after loading two previously unrelated models for
	 * the first time, or after a delete/recreate cycle). Algorithm:</p>
	 * <ol>
	 *   <li>If the diagram is already linked, either push its (changed) name to
	 *       the network or pull the network's name back into the diagram,
	 *       depending on which side changed since the last synchronisation
	 *       (tracked via {@link #corrToName}).</li>
	 *   <li>If unlinked, try to re-link to an unmatched network with the same
	 *       name; otherwise create a new network from the diagram.</li>
	 *   <li>Any network still unmatched afterwards is used to create a new
	 *       diagram (target-side insertion).</li>
	 * </ol>
	 */
	override void synch() {
		val diagList = sourceModel.contents.filter(typeof(GanttDiagram)).toList
		val unmatchedNets = targetModel.contents.filter(typeof(CPMNetwork)).filter[n | n.corrModelElem === null].toList

		diagList.forEach [ diag |
			val corr = diag.getOrCreateCorrModelElement(ruleID)
			var net = corr.targetElement as CPMNetwork
			if (net !== null) {
				unmatchedNets.remove(net)
				if (corrToName.get(corr) != diag.name)
					net.name = diag.name
				else
					diag.name = net.name
			} else {
				net = unmatchedNets.findFirst[n | n.name == diag.name]
				if (net !== null) {
					corr.targetElement = net
					elementsToCorr.put(net, corr)
					unmatchedNets.remove(net)
				} else {
					net = corr.getOrCreateTargetElem(targetPackage.CPMNetwork) as CPMNetwork => [name = diag.name]
					if (!targetModel.contents.contains(net))
						targetModel.contents += net
				}
			}
			corrToName.put(corr, diag.name)
		]

		unmatchedNets.forEach [ net |
			val corr = net.getOrCreateCorrModelElement(ruleID)
			val diag = corr.getOrCreateSourceElem(sourcePackage.ganttDiagram) as GanttDiagram => [name = net.name]
			if (!sourceModel.contents.contains(diag))
				sourceModel.contents += diag
			corrToName.put(corr, diag.name)
		]
	}
}