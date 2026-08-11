package de.tbuchmann.bxtend.gantt2cpm.rules;

import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.emf.ecore.EObject
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr
import java.util.ArrayList
import java.util.List

import cpm.Event

/**
 * Top-level orchestrator for the Gantt ↔ CPM bidirectional, incremental model
 * transformation implemented with BXtend.
 *
 * <p><b>Responsibilities:</b></p>
 * <ul>
 *   <li>Loads (or accepts) the three EMF resources: source (Gantt), target (CPM),
 *       and correspondence model.</li>
 *   <li>Bootstraps an empty {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Transformation}
 *       root object in the correspondence resource if none is present yet.</li>
 *   <li>Instantiates and chains the transformation rules in the correct
 *       execution order:
 *       <ol>
 *         <li>{@link Diagram2Network} – must run first to establish the root
 *             container correspondence before child rules access it.</li>
 *         <li>{@link Activity2Activity} – must run before {@link Dependency2Activity}
 *             because dependency wiring requires the CPM activities (and their
 *             events) to already exist in the correspondence model.</li>
 *         <li>{@link Dependency2Activity} – runs last; wires dependency arcs
 *             between already-resolved CPM events.</li>
 *       </ol>
 *   </li>
 *   <li>Exposes {@link #sourceToTarget()} and {@link #targetToSource()} as the
 *       sole public API – each method drives the complete rule chain and then
 *       triggers deletion propagation.</li>
 * </ul>
 *
 * <p><b>Incrementality &amp; deletion propagation:</b> After each rule-chain pass,
 * the orchestrator scans the correspondence model for "dangling" {@link Corr}
 * entries – i.e. entries whose source or target reference has become {@code null}
 * because the corresponding model element was deleted from one side.  The orphaned
 * element on the other side (and any isolated {@link Event} nodes) is then removed
 * via {@link EcoreUtil#delete(EObject, boolean)}.</p>
 *
 * <p><b>Two constructors:</b> One constructor accepts file {@link URI}s and loads
 * the resources itself (standalone use); the second accepts pre-loaded
 * {@link Resource} objects (used by the Benchmarx adapter
 * {@code BXtendGantt2CPM}, which manages the resource set externally).</p>
 */
class Gantt2cpmTransformation {
	
	/** EMF resource holding the source (Gantt) model. */
	Resource sourceModel
	/** EMF resource holding the target (CPM) model. */
	Resource targetModel
	/** EMF resource holding the correspondence model. */
	Resource corrModel
	
	/**
	 * Ordered list of transformation rules.  Rules are executed in insertion
	 * order during both {@link #sourceToTarget()} and {@link #targetToSource()}.
	 */
	List<Elem2Elem> rules = new ArrayList<Elem2Elem>();
	
	/**
	 * Constructs the transformation from file URIs, loading all three resources
	 * into a fresh {@link ResourceSetImpl}.
	 *
	 * <p>If the correspondence resource is empty (first run), a root
	 * {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Transformation}
	 * object is created automatically.</p>
	 *
	 * @param source       URI of the Gantt model XMI file
	 * @param target       URI of the CPM model XMI file
	 * @param correspondence URI of the correspondence model XMI file
	 */
	new(URI source, URI target, URI correspondence) {
		val ResourceSet set = new ResourceSetImpl();
		sourceModel = set.getResource(source, true)
		targetModel = set.getResource(target, true)
		corrModel = set.getResource(correspondence, true)
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Gantt2cpmFactory.eINSTANCE.createTransformation)	
		}

		// Rules must be added in the correct execution order:
		// 1. Diagram2Network  – establishes the root container correspondence
		// 2. Activity2Activity – creates CPM activities and their bounding events
		// 3. Dependency2Activity – wires dependency arcs using the already-created events
		rules.add(new Diagram2Network(sourceModel, targetModel, corrModel))
		rules.add(new Activity2Activity(sourceModel, targetModel, corrModel))
		rules.add(new Dependency2Activity(sourceModel, targetModel, corrModel))			
	}
	
	/**
	 * Constructs the transformation from already-loaded EMF {@link Resource} objects.
	 *
	 * <p>This constructor is used by the Benchmarx adapter {@code BXtendGantt2CPM},
	 * which manages its own {@code ResourceSet} and passes in the three resources
	 * directly.  If the correspondence resource is empty (first run), a root
	 * {@link de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Transformation}
	 * object is created automatically.</p>
	 *
	 * @param source        pre-loaded Gantt model resource
	 * @param target        pre-loaded CPM model resource
	 * @param correspondence pre-loaded correspondence model resource
	 */
	new(Resource source, Resource target, Resource correspondence) {		
		sourceModel = source
		targetModel = target
		corrModel = correspondence
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Gantt2cpmFactory.eINSTANCE.createTransformation)	
		}
		
		// Rules must be added in the correct execution order (see URI constructor for rationale)
		rules.add(new Diagram2Network(sourceModel, targetModel, corrModel))
		rules.add(new Activity2Activity(sourceModel, targetModel, corrModel))
		rules.add(new Dependency2Activity(sourceModel, targetModel, corrModel))
	}
	
	/**
	 * Drives the forward propagation pass (Gantt → CPM).
	 *
	 * <p>Executes each rule's {@link Elem2Elem#sourceToTarget()} in order, then
	 * calls {@link #deleteUnreferencedTargetElements()} to remove CPM elements
	 * whose Gantt counterpart was deleted.</p>
	 *
	 * <p>The pass is skipped entirely when the source model is empty
	 * (i.e. {@code sourceModel.contents} is empty) to avoid
	 * {@code IndexOutOfBoundsException} on the root access in
	 * {@link Diagram2Network}.</p>
	 */
	def void sourceToTarget() {
		if (sourceModel.contents.size != 0)
		for (Elem2Elem e : rules) {
			e.sourceToTarget()
		}
		
		// handle deletions
		deleteUnreferencedTargetElements
	}
	
	/**
	 * Drives the backward propagation pass (CPM → Gantt).
	 *
	 * <p>Executes each rule's {@link Elem2Elem#targetToSource()} in order, then
	 * calls {@link #deleteUnreferencedSourceElements()} to remove Gantt elements
	 * whose CPM counterpart was deleted.</p>
	 *
	 * <p>The pass is skipped entirely when the target model is empty.</p>
	 */
	def void targetToSource() {		
		if (targetModel.contents.size != 0)
		for (Elem2Elem e: rules) {
			e.targetToSource()
		}
		
		// handle deletions
		deleteUnreferencedSourceElements
	}

	/**
	 * Drives the synchronisation pass, reconciling concurrent edits made to both
	 * the Gantt and the CPM model since the last synchronisation point.
	 *
	 * <p>Executes each rule's {@link Elem2Elem#synch()} in order (the same order
	 * used for {@link #sourceToTarget()}/{@link #targetToSource()}, since
	 * {@link Dependency2Activity} depends on correspondences already established
	 * by {@link Activity2Activity}), then cleans up dangling correspondences on
	 * both sides.
	 */
	def void synch() {
		for (Elem2Elem e : rules)
			e.synch()

		// handle deletions
		deleteUnreferencedSourceElements
		deleteUnreferencedTargetElements
	}

	/**
	 * Verifies that the correspondence model is in a consistent state.
	 *
	 * <p>Currently always returns {@code true}; reserved for future consistency
	 * checks (e.g. verifying that every {@link Corr} has both a source and a
	 * target element after a synchronisation pass).</p>
	 *
	 * @return {@code true} if the correspondence model is consistent
	 */
	def boolean checkCorrespondences() {
		true
	}
	
	/**
	 * Collects all {@link Corr} entries in the correspondence model whose
	 * {@code sourceElement} reference is {@code null}.
	 *
	 * <p>A {@code null} source element indicates that the corresponding Gantt
	 * element was deleted, either by the user or by a previous propagation pass.
	 * EMF automatically sets cross-resource references to {@code null} when the
	 * referenced object is removed from its resource via
	 * {@link EcoreUtil#delete(EObject, boolean)}.</p>
	 *
	 * @return a lazy iterator over dangling {@link Corr} entries (source side deleted)
	 */
	def detectSourceDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			/*c.desc  == description && */c.sourceElement === null
		]
	}
	
	/**
	 * Collects all {@link Corr} entries in the correspondence model whose
	 * {@code targetElement} reference is {@code null}.
	 *
	 * <p>A {@code null} target element indicates that the corresponding CPM
	 * element was deleted.</p>
	 *
	 * @return a lazy iterator over dangling {@link Corr} entries (target side deleted)
	 */
	def detectTargetDeletions(/*String description*/) {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			c.targetElement === null /* && c.desc == description*/
		]
	}
	
	/**
	 * Removes CPM elements that have become orphaned because their corresponding
	 * Gantt source element was deleted.
	 *
	 * <p>Algorithm:</p>
	 * <ol>
	 *   <li>Iterate over all {@link Corr} entries detected by
	 *       {@link #detectSourceDeletions()}.</li>
	 *   <li>Collect the orphaned CPM target elements and the dangling {@code Corr}
	 *       objects into a deletion list.</li>
	 *   <li>Delete everything in the list via {@link EcoreUtil#delete(EObject, boolean)}
	 *       (cascades into contained children).</li>
	 *   <li>After activity deletion, perform a second pass to remove any
	 *       {@link Event} nodes that have no remaining incoming or outgoing
	 *       activity arcs (isolated events that would otherwise leave a
	 *       structurally invalid CPM network).</li>
	 * </ol>
	 */
	def deleteUnreferencedTargetElements(){
		val List<EObject> deletionList = newArrayList; 
		
		detectSourceDeletions().forEach[c |
			// TODO: add handling of contained and referenced Elements here if appropriate			
			// end
			deletionList += c.targetElement
			deletionList += c
		]
		deletionList.forEach[e | EcoreUtil.delete(e, true)]
		
		// After deleting Activities, remove Events that have no remaining in- or out-arcs
		// (isolated Events arise when the last Activity referencing them is deleted)
		val elemsToDelete = targetModel.allContents.filter(typeof(Event))
			.filter[ e | (e.outgoingActivities.size == 0 && e.incomingActivities.size == 0)].toList
			
			elemsToDelete.forEach[ev | EcoreUtil.delete(ev, true)]
	}
	
	/**
	 * Removes Gantt elements that have become orphaned because their corresponding
	 * CPM target element was deleted.
	 *
	 * <p>Algorithm:</p>
	 * <ol>
	 *   <li>Iterate over all {@link Corr} entries detected by
	 *       {@link #detectTargetDeletions()}.</li>
	 *   <li>Collect the orphaned Gantt source elements and the dangling {@code Corr}
	 *       objects into a deletion list.</li>
	 *   <li>Delete everything in the list via {@link EcoreUtil#delete(EObject, boolean)}.</li>
	 * </ol>
	 */
	def deleteUnreferencedSourceElements(){
		val List<EObject> deletionList = newArrayList; 
		
		detectTargetDeletions().forEach[c |
			// TODO: add handling of contained and referenced Elements here if appropriate
			
			// end
			deletionList += c.sourceElement
			deletionList += c
		]
		deletionList.forEach[e | EcoreUtil.delete(e, true)]
	}
}