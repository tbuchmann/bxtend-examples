package de.tbuchmann.bxtend.gantt2cpm.rules

import cpm.Activity
import cpm.CPMNetwork
import gantt.Dependency
import gantt.DependencyType
import gantt.GanttDiagram
import org.eclipse.emf.ecore.resource.Resource

/**
 * Transformation rule that maps a {@code gantt.Dependency} to a {@code cpm.Activity}.
 *
 * <p><b>The structural mismatch:</b> In a Gantt diagram, a {@link gantt.Dependency}
 * is an explicit edge object connecting two {@link gantt.Activity} nodes.  In the
 * CPM metamodel there are no explicit edge objects; instead, every scheduling
 * relationship between two milestone {@link cpm.Event}s is itself represented as
 * a {@code cpm.Activity} arc.  Therefore, one {@code gantt.Dependency} is mapped
 * to one {@code cpm.Activity} whose {@code sourceEvent} and {@code targetEvent}
 * are shared with the CPM activities that correspond to the Gantt predecessor and
 * successor activities.</p>
 *
 * <p><b>Naming convention:</b> The CPM activity produced by this rule is named
 * {@code "<predecessorName>-><successorName>"}, e.g. {@code "Design->Build"}.
 * This "arrow" pattern distinguishes dependency activities from plain activities
 * and is relied upon by {@link Activity2Activity} to exclude them from that
 * rule's backward pass.</p>
 *
 * <p><b>Dependency type mapping:</b> Gantt supports four dependency types that
 * describe which milestone events the dependency arc connects:</p>
 * <pre>
 *   StartStart → sourceEvent of predecessor  →  sourceEvent of successor
 *   StartEnd   → sourceEvent of predecessor  →  targetEvent of successor
 *   EndStart   → targetEvent of predecessor  →  sourceEvent of successor
 *   EndEnd     → targetEvent of predecessor  →  targetEvent of successor
 * </pre>
 *
 * <p><b>Duration / offset:</b> The {@code offset} attribute of a Gantt
 * {@code Dependency} (a lag or lead time in days) is stored in the CPM activity's
 * {@code duration} attribute and round-tripped without loss.</p>
 */
class Dependency2Activity extends Elem2Elem {

	/**
	 * Constructs the rule and registers it under the rule identifier
	 * {@code "dependency"}.  This ID is persisted in every {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr}
	 * created by this rule and is used during deletion detection to distinguish
	 * dependency correspondences from activity correspondences.
	 *
	 * @param src  EMF resource holding the source (Gantt) model
	 * @param trgt EMF resource holding the target (CPM) model
	 * @param corr EMF resource holding the correspondence model
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "dependency"
	}
	
	/**
	 * Propagates all {@code gantt.Dependency} elements to the CPM target model
	 * (forward direction: Gantt → CPM).
	 *
	 * <p>For each {@code gantt.Dependency} {@code d}:</p>
	 * <ol>
	 *   <li>Look up or create a {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr} for {@code d}.</li>
	 *   <li>Look up or create the corresponding {@code cpm.Activity}.</li>
	 *   <li>Resolve the CPM activities for the Gantt predecessor and successor
	 *       via the correspondence map, then wire the {@code sourceEvent} and
	 *       {@code targetEvent} of the new CPM dependency activity according to
	 *       the {@link DependencyType}.</li>
	 *   <li>Set the CPM activity name to {@code "<pred>-><succ>"} and its
	 *       {@code duration} to {@code d.offset}.</li>
	 *   <li>Place the CPM activity into the {@link CPMNetwork} corresponding to
	 *       the Gantt diagram that owns {@code d}.</li>
	 * </ol>
	 *
	 * <p><b>Pre-condition:</b> {@link Activity2Activity#sourceToTarget()} must have
	 * already run so that every {@code gantt.Activity} referenced by {@code d} has
	 * a corresponding {@code cpm.Activity} (and therefore populated events) in the
	 * correspondence model.</p>
	 */
	override void sourceToTarget() {
		sourceModel.allContents.filter(typeof(Dependency))
			.forEach[ d |
				var corr = d.getOrCreateCorrModelElement(ruleID)
				var target = corr.getOrCreateTargetElem(targetPackage.activity) as Activity
				updateDependencyTarget(target, d)
				target.duration = d.offset
				// Place the new CPM activity inside the correct CPMNetwork
				val net = (d.diagram.corrModelElem.targetElement as CPMNetwork)
				if (!net.elements.contains(target))
					net.elements += target
				corrToName.put(corr, target.name)
				corrToDuration.put(corr, target.duration)
			]
		//super.sourceToTarget()
	}

	/**
	 * Wires {@code target}'s {@code sourceEvent}/{@code targetEvent} (from the
	 * predecessor/successor CPM activities, according to {@code d.dependencyType})
	 * and its {@code name}.  Does <em>not</em> touch {@code duration}/{@code offset} —
	 * that attribute is independent of the predecessor/successor identity and is
	 * resolved separately by each caller via {@link #corrToDuration}.  Shared by
	 * {@link #sourceToTarget()} and {@link #synch()} so the forward-direction
	 * mapping logic exists in one place.
	 *
	 * @param target the CPM dependency activity to update
	 * @param d      the Gantt dependency providing the new attribute values
	 */
	private def void updateDependencyTarget(Activity target, Dependency d) {
		val sourceActivity = d.predecessor
		val targetActivity = d.successor
		val cpmSourceActivity = sourceActivity.corrModelElem.targetElement as Activity
		val cpmTargetActivity = targetActivity.corrModelElem.targetElement as Activity
		// Wire sourceEvent/targetEvent according to the Gantt dependency type
		if (d.dependencyType == DependencyType.START_START) {
			target.sourceEvent = cpmSourceActivity.sourceEvent
			target.targetEvent = cpmTargetActivity.sourceEvent
		}
		else if (d.dependencyType == DependencyType.START_END) {
			target.sourceEvent = cpmSourceActivity.sourceEvent
			target.targetEvent = cpmTargetActivity.targetEvent
		}
		else if (d.dependencyType == DependencyType.END_START) {
			target.sourceEvent = cpmSourceActivity.targetEvent
			target.targetEvent = cpmTargetActivity.sourceEvent
		}
		else { // END_END
			target.sourceEvent = cpmSourceActivity.targetEvent
			target.targetEvent = cpmTargetActivity.targetEvent
		}
		// Name encodes the predecessor→successor relationship
		target.name = sourceActivity.name + "->" + targetActivity.name
	}
	
	/**
	 * Propagates {@code cpm.Activity} dependency arcs back to the Gantt source
	 * model (backward direction: CPM → Gantt).
	 *
	 * <p>Only CPM activities whose {@code name} contains {@code "->"} are
	 * processed here (plain activities are handled by {@link Activity2Activity}).
	 * The arrow-shaped name encodes both the predecessor and successor activity
	 * names, which are used to locate the corresponding CPM activities via
	 * {@link #findCPMActivity}.</p>
	 *
	 * <p>For each qualifying {@code cpm.Activity} {@code a}:</p>
	 * <ol>
	 *   <li>Look up or create a {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr} for {@code a}.</li>
	 *   <li>Look up or create the corresponding {@code gantt.Dependency}.</li>
	 *   <li>Determine the {@link DependencyType} by comparing the CPM source/target
	 *       events of the dependency arc against the source/target events of the
	 *       predecessor and successor CPM activities.</li>
	 *   <li>Set the Gantt dependency's {@code predecessor}, {@code successor},
	 *       {@code offset}, and {@code diagram} references.</li>
	 * </ol>
	 *
	 * <p><b>Pre-condition:</b> {@link Activity2Activity#targetToSource()} must have
	 * already run so that every referenced {@code cpm.Activity} has a corresponding
	 * {@code gantt.Activity} in the correspondence model.</p>
	 */
	override void targetToSource() {
		targetModel.allContents.filter(typeof(Activity)).filter(a | a.name.contains("->"))
			.forEach[ a |
				var corr = a.getOrCreateCorrModelElement(ruleID)
				var sourceDependency = corr.getOrCreateSourceElem(sourcePackage.dependency) as Dependency
				updateDependencySource(sourceDependency, a)
				sourceDependency.offset = a.duration
				corrToName.put(corr, a.name)
				corrToDuration.put(corr, sourceDependency.offset)
			]
		//super.targetToSource()
	}

	/**
	 * Reverse-engineers {@code d}'s {@code dependencyType}, {@code predecessor},
	 * {@code successor} and {@code diagram} from the CPM dependency activity
	 * {@code a} (whose arrow-shaped name encodes predecessor and successor).
	 * Does <em>not</em> touch {@code offset} — see {@link #updateDependencyTarget}
	 * for why that attribute is resolved independently by each caller. Shared by
	 * {@link #targetToSource()} and {@link #synch()}.
	 *
	 * @param d the Gantt dependency to update
	 * @param a the CPM dependency activity providing the new attribute values
	 */
	private def void updateDependencySource(Dependency d, Activity a) {
		val cpmSourceEvent = a.sourceEvent
		val cpmTargetEvent = a.targetEvent
		// Recover the predecessor/successor CPM activities from the encoded name
		val cpmSourceActivity = findCPMActivity(a.name.split("->").get(0))
		val cpmTargetActivity = findCPMActivity(a.name.split("->").get(1))
		val ganttSourceActivity = cpmSourceActivity.corrModelElem.sourceElement
		val ganttTargetActivity = cpmTargetActivity.corrModelElem.sourceElement
		// Reverse-engineer the DependencyType from which events are connected
		if (cpmSourceActivity.sourceEvent == cpmSourceEvent && cpmTargetActivity.sourceEvent == cpmTargetEvent)
			d.dependencyType = DependencyType.START_START
		else if (cpmSourceActivity.sourceEvent == cpmSourceEvent && cpmTargetActivity.targetEvent == cpmTargetEvent)
			d.dependencyType = DependencyType.START_END
		else if (cpmSourceActivity.targetEvent == cpmSourceEvent && cpmTargetActivity.sourceEvent == cpmTargetEvent)
			d.dependencyType = DependencyType.END_START
		else
			d.dependencyType = DependencyType.END_END
		d.predecessor = ganttSourceActivity as gantt.Activity
		d.successor = ganttTargetActivity as gantt.Activity
		d.diagram = a.network.corrModelElem.sourceElement as GanttDiagram
	}

	/**
	 * Reconciles concurrent edits to {@code gantt.Dependency} ↔ {@code cpm.Activity}
	 * (arrow-named) pairs.
	 *
	 * <p>Identity key: {@code "<predecessorName>-><successorName>"}. Requires
	 * {@link Activity2Activity#synch()} to have already run in the same pass so
	 * that {@code d.predecessor}/{@code d.successor} already have CPM-side
	 * correspondences.</p>
	 *
	 * <p>Algorithm, mirroring {@link Activity2Activity#synch()}: link/re-link by
	 * key, push the structural fields (events/name) forward on a key change or
	 * pull them backward otherwise, resolve {@code offset}/{@code duration}
	 * independently against {@link #corrToDuration} (same push/pull/source-wins
	 * logic as {@link Activity2Activity#synch()}, since it can change on either
	 * side without affecting the key), and turn any still-unmatched CPM
	 * dependency activity into a new Gantt dependency.</p>
	 */
	override void synch() {
		val depList = sourceModel.allContents.filter(typeof(Dependency)).toList
		val unmatched = targetModel.allContents.filter(typeof(Activity)).filter[a | a.name.contains("->")]
			.filter[a | a.corrModelElem === null].toList

		depList.forEach [ d |
			val corr = d.getOrCreateCorrModelElement(ruleID)
			val key = d.predecessor.name + "->" + d.successor.name
			var target = corr.targetElement as Activity
			if (target !== null) {
				unmatched.remove(target)
				if (corrToName.get(corr) != key)
					updateDependencyTarget(target, d)
				else
					updateDependencySource(d, target)

				val lastDuration = corrToDuration.get(corr)
				val sourceChanged = lastDuration === null || lastDuration != d.offset
				val targetChanged = lastDuration === null || lastDuration != target.duration
				if (sourceChanged)
					target.duration = d.offset
				else if (targetChanged)
					d.offset = target.duration
			} else {
				target = unmatched.findFirst[t | t.name == key]
				if (target !== null) {
					corr.targetElement = target
					elementsToCorr.put(target, corr)
					unmatched.remove(target)
					updateDependencyTarget(target, d)
					target.duration = d.offset
				} else {
					target = corr.getOrCreateTargetElem(targetPackage.activity) as Activity
					updateDependencyTarget(target, d)
					target.duration = d.offset
					val net = d.diagram.corrModelElem.targetElement as CPMNetwork
					if (!net.elements.contains(target))
						net.elements += target
				}
			}
			corrToName.put(corr, key)
			corrToDuration.put(corr, target.duration)
		]

		unmatched.forEach [ a |
			val corr = a.getOrCreateCorrModelElement(ruleID)
			val d = corr.getOrCreateSourceElem(sourcePackage.dependency) as Dependency
			updateDependencySource(d, a)
			d.offset = a.duration
			val diag = a.network.corrModelElem.sourceElement as GanttDiagram
			if (!diag.elements.contains(d))
				diag.elements += d
			corrToName.put(corr, a.name)
			corrToDuration.put(corr, d.offset)
		]
	}

	/**
	 * Finds the unique {@code cpm.Activity} in the target model whose {@code name}
	 * exactly equals the given {@code name}.
	 *
	 * <p>This lookup is used during the backward pass to map the activity names
	 * encoded in a dependency arc's {@code name} (e.g. {@code "Design->Build"})
	 * back to the actual {@code cpm.Activity} objects.</p>
	 *
	 * @param name the exact activity name to search for (no arrow, plain name)
	 * @return the first matching {@code cpm.Activity}, or {@code null} if none found
	 */
	def findCPMActivity(String name) {
		targetModel.allContents.filter(typeof(Activity)).findFirst(a | a.name.equals(name))
	}
}