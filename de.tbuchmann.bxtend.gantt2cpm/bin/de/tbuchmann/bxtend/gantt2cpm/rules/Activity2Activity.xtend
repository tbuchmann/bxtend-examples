package de.tbuchmann.bxtend.gantt2cpm.rules

import cpm.Activity
import cpm.CPMNetwork
import cpm.Event
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr
import gantt.GanttDiagram
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.resource.Resource

/**
 * Transformation rule that maps a {@code gantt.Activity} to a {@code cpm.Activity}.
 *
 * <p><b>Metamodel correspondence:</b></p>
 * <pre>
 *   gantt.Activity  ←→  cpm.Activity
 *     name              name
 *     duration          duration
 *     (container: GanttDiagram)  ←→  (container: CPMNetwork)
 * </pre>
 *
 * <p><b>Structural mismatch:</b> In the CPM metamodel every {@code Activity} is an
 * arc in a directed graph whose nodes are {@link Event} objects.  Consequently,
 * creating a new CPM {@code Activity} always requires two fresh {@code Event}
 * instances – a <em>source event</em> (predecessor node) and a <em>target event</em>
 * (successor node).  This rule overrides {@link #getOrCreateTargetElem} to
 * handle this 1-to-3 creation atomically.</p>
 *
 * <p><b>Naming convention:</b> CPM activities whose {@code name} contains
 * {@code "->"} represent Gantt {@link gantt.Dependency} elements and are therefore
 * <em>excluded</em> from the backward pass in this rule (they are handled by
 * {@link Dependency2Activity} instead).  The forward pass applies the same
 * exclusion implicitly, because it only iterates over {@code gantt.Activity}
 * instances.</p>
 *
 * <p><b>Event numbering:</b> A static counter {@code i} tracks the highest
 * event number already present in the target model so that new events always
 * receive a unique, monotonically increasing {@code number}.</p>
 */
class Activity2Activity extends Elem2Elem {

	/**
	 * Monotonically increasing counter used to assign unique numbers to newly
	 * created {@link Event} instances.  On the first call to {@link #createEvent}
	 * within a JVM session the counter is initialised by scanning all existing
	 * events in the target model so that new events never clash with persisted ones.
	 */
	static var i = 0
	
	/**
	 * Constructs the rule and registers it under the rule identifier
	 * {@code "activity"}, which is stored in every {@link Corr} created by
	 * this rule.
	 *
	 * @param src  EMF resource holding the source (Gantt) model
	 * @param trgt EMF resource holding the target (CPM) model
	 * @param corr EMF resource holding the correspondence model
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "activity"
	}
	
	/**
	 * Propagates all {@code gantt.Activity} elements to the CPM target model
	 * (forward direction: Gantt → CPM).
	 *
	 * <p>For each {@code gantt.Activity} {@code a}:</p>
	 * <ol>
	 *   <li>Look up or create a {@link Corr} correspondence entry for {@code a}
	 *       (keyed by rule ID {@code "activity"}).</li>
	 *   <li>Look up or create the matching {@code cpm.Activity} via
	 *       {@link #getOrCreateTargetElem}, which also creates the two bounding
	 *       {@link Event} instances on first call.</li>
	 *   <li>Synchronise attributes: {@code name} and {@code duration}.</li>
	 *   <li>Ensure the activity and both its events are contained in the
	 *       {@link CPMNetwork} that corresponds to the Gantt diagram containing
	 *       {@code a}.  The network is resolved via the correspondence model.</li>
	 * </ol>
	 */
	override void sourceToTarget() {
		sourceModel.allContents.filter(typeof(gantt.Activity))
			.forEach[a |
				val corr = a.getOrCreateCorrModelElement(ruleID)
				val target = corr.getOrCreateTargetElem(targetPackage.activity) as Activity
				target.name = a.name
				target.duration = a.duration
				// Resolve the CPM container: traverse corr model from Gantt diagram to CPM network
				val net = a.diagram.corrModelElem.targetElement as CPMNetwork
				net.elements += target
				net.elements += target.sourceEvent
				net.elements += target.targetEvent
				corrToName.put(corr, a.name)
				corrToDuration.put(corr, target.duration)
			]
		//super.sourceToTarget()
	}
	
	/**
	 * Propagates {@code cpm.Activity} elements back to the Gantt source model
	 * (backward direction: CPM → Gantt).
	 *
	 * <p>Only CPM activities whose {@code name} does <em>not</em> contain
	 * {@code "->"} are processed here; those are plain Gantt activities.
	 * Activities with an arrow in their name represent Gantt dependencies and
	 * are handled by {@link Dependency2Activity}.</p>
	 *
	 * <p>For each qualifying {@code cpm.Activity} {@code a}:</p>
	 * <ol>
	 *   <li>Look up or create a {@link Corr} correspondence entry for {@code a}.</li>
	 *   <li>Look up or create the matching {@code gantt.Activity}.</li>
	 *   <li>Synchronise attributes: {@code name} and {@code duration}.</li>
	 *   <li>Place the activity in the {@link GanttDiagram} that corresponds to
	 *       the CPM network containing {@code a}.</li>
	 * </ol>
	 */
	override void targetToSource() {
		targetModel.allContents.filter(typeof(Activity)).filter(a | !(a.name.contains("->") ))
			.forEach[ a |
				val corr = a.getOrCreateCorrModelElement(ruleID)
				val source = corr.getOrCreateSourceElem(sourcePackage.activity) as gantt.Activity
				source.name = a.name
				source.duration = a.duration
				// Resolve the Gantt container: traverse corr model from CPM network to Gantt diagram
				val diag = a.network.corrModelElem.sourceElement as GanttDiagram
				diag.elements += source
				corrToName.put(corr, source.name)
				corrToDuration.put(corr, source.duration)
			]
		//super.targetToSource()
	}
	
	/**
	 * Reconciles concurrent edits to plain {@code gantt.Activity} /
	 * {@code cpm.Activity} pairs (dependency arcs are excluded and handled by
	 * {@link Dependency2Activity#synch()}).
	 *
	 * <p>Algorithm, mirroring {@link Diagram2Network#synch()}:</p>
	 * <ol>
	 *   <li>Collect CPM activities without a correspondence yet into
	 *       {@code unmatched}.</li>
	 *   <li>For each Gantt activity: if already linked, resolve {@code name} and
	 *       {@code duration} independently against the last-known snapshot
	 *       ({@link #corrToName}/{@link #corrToDuration}) — each attribute is
	 *       pushed forward if it changed on the source, pulled backward if it
	 *       only changed on the target, and left as-is (source wins) if both
	 *       changed. Name additionally drives re-linking to a same-named
	 *       unmatched activity, or creation of a new one, when unlinked.</li>
	 *   <li>Any CPM activity still unmatched afterwards is treated as a
	 *       target-side insertion and transformed into a new Gantt activity.</li>
	 * </ol>
	 */
	override void synch() {
		val actList = sourceModel.allContents.filter(typeof(gantt.Activity)).toList
		val unmatched = targetModel.allContents.filter(typeof(Activity)).filter[a | !a.name.contains("->")]
			.filter[a | a.corrModelElem === null].toList

		actList.forEach [ a |
			val corr = a.getOrCreateCorrModelElement(ruleID)
			var target = corr.targetElement as Activity
			if (target !== null) {
				unmatched.remove(target)
				if (corrToName.get(corr) != a.name)
					target.name = a.name
				else
					a.name = target.name

				val lastDuration = corrToDuration.get(corr)
				val sourceChanged = lastDuration === null || lastDuration != a.duration
				val targetChanged = lastDuration === null || lastDuration != target.duration
				if (sourceChanged)
					target.duration = a.duration
				else if (targetChanged)
					a.duration = target.duration
			} else {
				target = unmatched.findFirst[t | t.name == a.name]
				if (target !== null) {
					corr.targetElement = target
					elementsToCorr.put(target, corr)
					unmatched.remove(target)
					target.duration = a.duration
				} else {
					target = corr.getOrCreateTargetElem(targetPackage.activity) as Activity
					target.name = a.name
					target.duration = a.duration
					val net = a.diagram.corrModelElem.targetElement as CPMNetwork
					if (!net.elements.contains(target)) {
						net.elements += target
						net.elements += target.sourceEvent
						net.elements += target.targetEvent
					}
				}
			}
			corrToName.put(corr, a.name)
			corrToDuration.put(corr, target.duration)
		]

		unmatched.forEach [ act |
			val corr = act.getOrCreateCorrModelElement(ruleID)
			val source = corr.getOrCreateSourceElem(sourcePackage.activity) as gantt.Activity => [
				name = act.name
				duration = act.duration
			]
			val diag = act.network.corrModelElem.sourceElement as GanttDiagram
			if (!diag.elements.contains(source))
				diag.elements += source
			corrToName.put(corr, source.name)
			corrToDuration.put(corr, source.duration)
		]
	}

	/**
	 * Overrides the default target-element creation to implement the
	 * 1-to-3 mapping: one Gantt {@code Activity} maps to one CPM
	 * {@code Activity} plus two bounding {@link Event} instances.
	 *
	 * <p>If the {@link Corr} already holds a target element, that element is
	 * returned unchanged (incremental update path).  On first creation the
	 * new {@code Activity} is linked to two freshly created events via
	 * {@code sourceEvent} and {@code targetEvent}.</p>
	 *
	 * @param corr  the correspondence entry for the element being processed
	 * @param clazz the target {@code EClass} (always {@code cpm.Activity} here)
	 * @return the existing or newly created {@code cpm.Activity}
	 */
	override getOrCreateTargetElem(Corr corr, EClass clazz) {
		var Activity target = corr.targetElement as Activity
		if (target === null) {
			target = createTargetElement(clazz) as Activity
			corr.targetElement = target
			elementsToCorr.put(target, corr)
			// Each CPM Activity needs exactly one source event and one target event
			target.sourceEvent = createEvent()
			target.targetEvent = createEvent()
		}
		return target
	}
	
	/**
	 * Creates a new {@link Event} with a unique, auto-incremented {@code number}.
	 *
	 * <p>The static counter {@code i} is lazily initialised on the very first
	 * call by scanning all existing {@link Event} instances in the target model
	 * and recording the current maximum number.  Subsequent calls simply
	 * increment and use the counter without re-scanning, ensuring O(1) cost
	 * for all but the first invocation per JVM session.</p>
	 *
	 * @return a new {@code cpm.Event} with a number greater than any
	 *         previously assigned event number
	 */
	private def createEvent() {
		// On first call, initialise counter from highest existing event number
		if (i == 0)
		targetModel.allContents.filter(typeof(Event))
			.forEach[e |
				if (e.number > i)
					i = e.number				
			]
		i++
		var e = targetFactory.createEvent => [number = i]
		return e
	}
}