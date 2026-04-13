package de.tbuchmann.bxtend.pn2pnw.rules;

import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Corr
import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Pn2pnwFactory
import java.util.ArrayList
import java.util.List
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.util.EcoreUtil
import pnw.Transition

/**
 * Orchestrates the complete bidirectional transformation between an unweighted
 * Petri net ({@code pn}) and a weighted Petri net ({@code pnw}).
 *
 * <p>This class is the single public entry point for both transformation
 * directions.  It owns the three EMF resources required by every run:</p>
 * <ol>
 *   <li>{@code sourceModel} – the unweighted Petri net (PetriNet.ecore /
 *       {@code pn} package)</li>
 *   <li>{@code targetModel} – the weighted Petri net
 *       (PetriNetWeighted.ecore / {@code pnw} package)</li>
 *   <li>{@code corrModel}  – the correspondence model that tracks which
 *       source element is linked to which target element
 *       (corresp.ecore / {@code pn2pnw} package)</li>
 * </ol>
 *
 * <p><b>Rule execution order:</b> Rules are registered in a fixed order that
 * guarantees that containers exist before their contents are processed:</p>
 * <ol>
 *   <li>{@link Net2Net} – synchronises the root {@code Net} objects.</li>
 *   <li>{@link Place2Place} – synchronises all {@code Place} elements.</li>
 *   <li>{@link Transition2Transition} – synchronises all {@code Transition}
 *       elements <em>and</em> the arcs between them and places.</li>
 * </ol>
 *
 * <p><b>Deletion handling:</b> After all rules have run, the transformation
 * scans the correspondence model for entries whose source or target slot is
 * {@code null} (meaning the counterpart was deleted in the current edit) and
 * removes the dangling elements from the opposite model together with any
 * first-class edge objects that referenced the deleted transition.</p>
 *
 * <p><b>Incremental support:</b> Because rules call
 * {@code getOrCreateCorrModelElement} / {@code getOrCreateSourceElem} /
 * {@code getOrCreateTargetElem}, an existing correspondence is reused on
 * subsequent runs, making the transformation naturally incremental:
 * only attributes and references that have actually changed need to be
 * updated.</p>
 */
class Pn2pnwTransformation  {
	
	/** The source-model resource (unweighted Petri net, {@code pn} package). */
	Resource sourceModel
	/** The target-model resource (weighted Petri net, {@code pnw} package). */
	Resource targetModel
	/**
	 * The correspondence-model resource (tracks source↔target element pairs,
	 * {@code pn2pnw} package).
	 */
	Resource corrModel
	
	/** Ordered list of transformation rules executed in each pass. */
	List<Elem2Elem> rules = new ArrayList<Elem2Elem>();
	
	/**
	 * URI-based constructor: loads all three model resources from the given
	 * URIs using a fresh {@link ResourceSetImpl} and registers the rules in
	 * the required execution order.
	 *
	 * <p>If the correspondence resource is empty (first run), an empty
	 * {@link de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Transformation
	 * Transformation} root object is added before any rule is instantiated.</p>
	 *
	 * @param source         URI of the source model (unweighted Petri net)
	 * @param target         URI of the target model (weighted Petri net)
	 * @param correspondence URI of the correspondence model
	 */
	new(URI source, URI target, URI correspondence) {
		val ResourceSet set = new ResourceSetImpl();
		sourceModel = set.getResource(source, true)
		targetModel = set.getResource(target, true)
		corrModel = set.getResource(correspondence, true)
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(Pn2pnwFactory.eINSTANCE.createTransformation)	
		}

		// Rules must be added in hierarchical order: Net before Place before Transition,
		// because each rule relies on the parent correspondence being already present.
		rules.add(new Net2Net(sourceModel, targetModel, corrModel));
		rules.add(new Place2Place(sourceModel, targetModel, corrModel));
		rules.add(new Transition2Transition(sourceModel, targetModel, corrModel));					
	}
	
	/**
	 * Resource-based constructor: uses already-loaded EMF {@link Resource}
	 * instances (e.g. when the caller manages the {@link ResourceSet} itself,
	 * as in the BenchmarX test harness).
	 *
	 * <p>If the correspondence resource is empty (first run), an empty
	 * {@link de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Transformation
	 * Transformation} root object is added before any rule is instantiated.</p>
	 *
	 * @param source         loaded source-model resource
	 * @param target         loaded target-model resource
	 * @param correspondence loaded correspondence-model resource
	 */
	new(Resource source, Resource target, Resource correspondence) {		
		sourceModel = source
		targetModel = target
		corrModel = correspondence
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(Pn2pnwFactory.eINSTANCE.createTransformation)	
		}
		
		// Rules must be added in hierarchical order: Net before Place before Transition.
		rules.add(new Net2Net(sourceModel, targetModel, corrModel));
		rules.add(new Place2Place(sourceModel, targetModel, corrModel));
		rules.add(new Transition2Transition(sourceModel, targetModel, corrModel));		
	}
	
	/**
	 * Runs the forward transformation (source → target).
	 *
	 * <p>Executes each rule's {@link Elem2Elem#sourceToTarget()} method in
	 * registration order, then invokes {@link #deleteUnreferencedTargetElements()}
	 * to clean up target elements whose source counterpart has been deleted.</p>
	 *
	 * <p>If the source model is empty the method returns immediately without
	 * modifying the target.</p>
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
	 * Runs the backward transformation (target → source).
	 *
	 * <p>Executes each rule's {@link Elem2Elem#targetToSource()} method in
	 * registration order, then invokes {@link #deleteUnreferencedSourceElements()}
	 * to clean up source elements whose target counterpart has been deleted.</p>
	 *
	 * <p>If the target model is empty the method returns immediately without
	 * modifying the source.</p>
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
	 * Placeholder for a post-transformation consistency check.
	 *
	 * @return {@code true} always (not yet implemented)
	 */
	def boolean checkCorrespondences() {
		true
	}
	
	/**
	 * Detects correspondences whose <em>source</em> element slot is {@code null},
	 * i.e. target elements that have lost their source counterpart (indicating a
	 * deletion on the source side during an incremental forward run).
	 *
	 * @return a lazy iterator over {@link Corr} entries with a {@code null}
	 *         {@code sourceElement}
	 */
	def detectSourceDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			c.sourceElement === null
		]
	}
	
	/**
	 * Detects correspondences whose <em>target</em> element slot is {@code null},
	 * i.e. source elements that have lost their target counterpart (indicating a
	 * deletion on the target side during an incremental backward run).
	 *
	 * @return a lazy iterator over {@link Corr} entries with a {@code null}
	 *         {@code targetElement}
	 */	
	def detectTargetDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			c.targetElement === null 
		]
	}
	
	/**
	 * Deletes target elements that are no longer referenced by any source element
	 * (forward deletion propagation).
	 *
	 * <p>For each correspondence with a {@code null} source element, the
	 * corresponding target element is scheduled for deletion.  If the target
	 * element is a {@link Transition}, its incident {@link pnw.PTEdge} and
	 * {@link pnw.TPEdge} objects are also scheduled for deletion first, so
	 * that edge containment is properly cleaned up before the transition is
	 * removed.  The correspondence entry itself is deleted as well.</p>
	 *
	 * <p>All deletions are performed via {@link EcoreUtil#delete(EObject, boolean)}
	 * with {@code recursive = true} to handle any remaining cross-references.</p>
	 */
	def deleteUnreferencedTargetElements(){
		val List<EObject> deletionList = newArrayList; 
		
		detectSourceDeletions().forEach[c |
			// Delete incident weighted edges before the transition itself to avoid
			// dangling references inside the pnw model.
			if (c.targetElement instanceof Transition) {
				for (edge : (c.targetElement as Transition).inPTEdges) {
					deletionList += edge
				}
				for (edge : (c.targetElement as Transition).outTPEdges) {
					deletionList += edge
				}
			}
			deletionList += c.targetElement
			deletionList += c
		]
		deletionList.forEach[e | EcoreUtil.delete(e, true)]
	}
	
	/**
	 * Deletes source elements that are no longer referenced by any target element
	 * (backward deletion propagation).
	 *
	 * <p>For each correspondence with a {@code null} target element, the
	 * corresponding source element and the correspondence entry itself are
	 * scheduled for deletion.  Deletions are performed via
	 * {@link EcoreUtil#delete(EObject, boolean)} with {@code recursive = true}.</p>
	 */
	def deleteUnreferencedSourceElements(){
		val List<EObject> deletionList = newArrayList; 
		
		detectTargetDeletions().forEach[c |
			deletionList += c.sourceElement
			deletionList += c
		]
		deletionList.forEach[e | EcoreUtil.delete(e, true)]
	}
}