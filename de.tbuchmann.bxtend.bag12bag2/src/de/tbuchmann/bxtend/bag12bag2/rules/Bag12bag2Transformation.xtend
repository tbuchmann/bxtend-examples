package de.tbuchmann.bxtend.bag12bag2.rules;

import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.BasicElem
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Corr
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.MultiElem
import java.util.ArrayList
import java.util.List
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.util.EcoreUtil
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Bag12bag2Factory

/**
 * Top-level orchestrator of the Bag1-to-Bag2 bidirectional, incremental model
 * transformation implemented with the BXtend framework.
 *
 * <h2>Overview</h2>
 * <p>This class is the single entry point for external clients (e.g. the
 * {@code BXtendBag12Bag2} BenchmarX adapter). It manages three EMF resources:</p>
 * <ul>
 *   <li><b>source model</b> – an instance of {@code Bags1.ecore} (the uncompressed bag)</li>
 *   <li><b>target model</b> – an instance of {@code Bags2.ecore} (the compressed bag)</li>
 *   <li><b>correspondence model</b> – an instance of {@code corresp.ecore} that records
 *       {@link BasicElem} (1-to-1) and {@link MultiElem} (N-to-1) links between source
 *       and target elements</li>
 * </ul>
 * <p>The transformation pipeline consists of a sequential list of {@link Elem2Elem}
 * rule objects that are applied in registration order:</p>
 * <ol>
 *   <li>{@link Bag2Bag} – synchronises the single root {@code MyBag} containers</li>
 *   <li>{@link Element2Element} – synchronises the individual {@code Element} objects,
 *       handling the many-to-one compression / decompression semantics</li>
 * </ol>
 *
 * <h2>Constructors</h2>
 * <p>Two constructors are provided so that the class can be used both standalone (with
 * file-system URIs) and within an in-memory EMF context (with pre-created resources):</p>
 * <ul>
 *   <li>{@link #Bag12bag2Transformation(URI, URI, URI)} – resolves or loads the three
 *       resources from the given URIs using a fresh {@link ResourceSet}.</li>
 *   <li>{@link #Bag12bag2Transformation(Resource, Resource, Resource)} – accepts
 *       already-loaded in-memory resources (used by the BenchmarX test adapter).</li>
 * </ul>
 * <p>In both cases, if the correspondence model is empty an empty
 * {@link de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Transformation} root
 * element is added to it so that rule constructors can safely read
 * {@code corrModel.contents.get(0)}.</p>
 *
 * <h2>Propagation</h2>
 * <ul>
 *   <li>{@link #sourceToTarget()} – applies all rules forward (Bag1 → Bag2), then
 *       removes any Bag2 elements whose source correspondence has been broken by a
 *       deletion in Bag1 (see {@link #deleteUnreferencedTargetElements()}).</li>
 *   <li>{@link #targetToSource()} – applies all rules backward (Bag2 → Bag1), then
 *       removes any Bag1 elements whose target correspondence has become {@code null}
 *       (see {@link #deleteUnreferencedSourceElements()}).</li>
 * </ul>
 *
 * <h2>Deletion Handling</h2>
 * <p>BXtend does not natively detect model deletions; instead, it detects dangling
 * correspondences after propagation and cleans up:
 * <ul>
 *   <li>{@link #detectSourceDeletions()} – finds {@link Corr} entries whose source
 *       side has gone {@code null} (i.e. the Bag1 element was deleted).</li>
 *   <li>{@link #detectTargetDeletions()} – finds {@link Corr} entries whose
 *       {@code targetElement} has gone {@code null} (i.e. the Bag2 element was deleted).</li>
 * </ul>
 * </p>
 *
 * @see Bag2Bag
 * @see Element2Element
 * @see Elem2Elem
 */
class Bag12bag2Transformation {
	
	/** The EMF resource holding the Bag1 (source) model. */
	Resource sourceModel
	/** The EMF resource holding the Bag2 (target) model. */
	Resource targetModel
	/** The EMF resource holding the correspondence model. */
	Resource corrModel
	
	/**
	 * Ordered list of transformation rules.  Rules are executed sequentially in the
	 * order they were added by {@link #addRules()}.
	 */
	List<Elem2Elem> rules = new ArrayList<Elem2Elem>();
	
	/**
	 * Constructs the transformation by loading the three models from their file-system
	 * URIs, initialising an empty correspondence root if necessary, and registering
	 * the rule pipeline.
	 *
	 * @param source        URI of the Bag1 source model resource
	 * @param target        URI of the Bag2 target model resource
	 * @param correspondence URI of the correspondence model resource
	 */
	new(URI source, URI target, URI correspondence) {
		val ResourceSet set = new ResourceSetImpl();
		sourceModel = set.getResource(source, true)
		targetModel = set.getResource(target, true)
		corrModel = set.getResource(correspondence, true)
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(Bag12bag2Factory.eINSTANCE.createTransformation)	
		}

		// TODO: add your rules in the proper order to the 'rules' List	
		addRules			
	}
	
	/**
	 * Constructs the transformation from already-loaded in-memory resources,
	 * initialising an empty correspondence root if necessary, and registering
	 * the rule pipeline.
	 *
	 * <p>This constructor is used by the BenchmarX test adapter
	 * ({@code BXtendBag12Bag2}) which manages the EMF resource set externally.</p>
	 *
	 * @param source        the Bag1 source model resource
	 * @param target        the Bag2 target model resource
	 * @param correspondence the correspondence model resource
	 */
	new(Resource source, Resource target, Resource correspondence) {		
		sourceModel = source
		targetModel = target
		corrModel = correspondence
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(Bag12bag2Factory.eINSTANCE.createTransformation)	
		}
		
		// TODO: add your rules in the proper order to the 'rules' List
		addRules
	}
	
	/**
	 * Registers the ordered rule pipeline.
	 *
	 * <p>Rules must be registered in dependency order: {@link Bag2Bag} must run before
	 * {@link Element2Element} because {@code Element2Element} looks up the bag-level
	 * correspondences created by {@code Bag2Bag} (via {@link Elem2Elem#getCorrModelElem})
	 * when resolving cross-references between elements and their owning bags.</p>
	 */
	def addRules() {
		rules += new Bag2Bag(sourceModel,targetModel,corrModel)
		rules += new Element2Element(sourceModel,targetModel,corrModel)
	}
	
	/**
	 * Propagates all changes from Bag1 to Bag2 (forward direction).
	 *
	 * <p>Applies each rule in the pipeline in registration order; afterwards
	 * calls {@link #deleteUnreferencedTargetElements()} to remove any Bag2
	 * objects whose source counterpart was deleted.</p>
	 *
	 * <p>If the source model is empty, the propagation step is skipped (no rules
	 * are invoked), which prevents {@code NullPointerException}s when the
	 * correspondence model has not yet been bootstrapped.</p>
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
	 * Propagates all changes from Bag2 to Bag1 (backward direction).
	 *
	 * <p>Applies each rule in the pipeline in registration order; afterwards
	 * calls {@link #deleteUnreferencedSourceElements()} to remove any Bag1
	 * objects whose target counterpart was deleted.</p>
	 *
	 * <p>If the target model is empty, the propagation step is skipped.</p>
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
	 * Reconciles concurrent edits made to both the Bag1 and Bag2 models since the last
	 * synchronisation point.
	 *
	 * <p>Executes each rule's {@link Elem2Elem#synch()} in the same registration order as
	 * {@link #sourceToTarget()}/{@link #targetToSource()}, then cleans up dangling
	 * correspondences on both sides.</p>
	 */
	def void synch() {
		for (Elem2Elem e : rules)
			e.synch()

		// handle deletions
		deleteUnreferencedSourceElements
		deleteUnreferencedTargetElements
	}

	/**
	 * Placeholder consistency-check hook.
	 *
	 * <p>Always returns {@code true} in this implementation. Override or extend
	 * to add consistency invariants that should be verified after propagation.</p>
	 *
	 * @return {@code true} if the source and target models are mutually consistent
	 */
	def boolean checkCorrespondences() {
		true
	}
	
	/**
	 * Identifies correspondences in which the source side has been removed.
	 *
	 * <p>A {@link BasicElem}'s source is gone when {@code sourceElement == null}.
	 * A {@link MultiElem}'s source group is gone when {@code sourceElements} is empty.
	 * Both conditions signal that the corresponding Bag2 element should be deleted.</p>
	 *
	 * @return an iterator over all dangling {@link Corr} entries (source deleted)
	 */
	def detectSourceDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			(c instanceof BasicElem && c.sourceElement === null) || (c instanceof MultiElem && (c as MultiElem).sourceElements.empty)
		]
	}
	
	/**
	 * Identifies correspondences in which the target side has been removed.
	 *
	 * <p>Applies to both {@link BasicElem} and {@link MultiElem} correspondences:
	 * when {@code targetElement == null}, the associated source elements must be
	 * deleted from the Bag1 model.</p>
	 *
	 * @return an iterator over all dangling {@link Corr} entries (target deleted)
	 */
	def detectTargetDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			c.targetElement === null 
		]
	}
	
	/**
	 * Deletes Bag2 target elements (and their correspondences) that have lost all
	 * their Bag1 source counterparts.
	 *
	 * <p>Collects all affected target objects and correspondence entries in a list
	 * first to avoid {@link java.util.ConcurrentModificationException}s, then removes
	 * them all using {@link EcoreUtil#delete(EObject, boolean)} with the
	 * {@code recursive} flag set to {@code true} so that cross-references are
	 * cleaned up throughout the model.</p>
	 *
	 * <p><b>Extension point:</b> add handling of contained or referenced sub-elements
	 * in the {@code TODO} section inside this method if the target metamodel gains
	 * containment hierarchies deeper than the current flat structure.</p>
	 */
	def deleteUnreferencedTargetElements(){
		val List<EObject> deletionList = newArrayList; 
		
		detectSourceDeletions().forEach[c |
			// TODO: add handling of contained and referenced Elements here if appropriate			
			// end
			// A MultiElem's group can become empty (source-side deletion) while its
			// targetElement was *also* concurrently deleted directly on the target side
			// (see Element2Element#synch()); guard against adding that null twice.
			if (c.targetElement !== null)
				deletionList += c.targetElement
			deletionList += c
		]
		deletionList.forEach[e | EcoreUtil.delete(e, true)]
	}

	/**
	 * Deletes Bag1 source elements (and their correspondences) that have lost their
	 * Bag2 target counterpart.
	 *
	 * <p>For {@link MultiElem} correspondences, every source element in the group is
	 * added to the deletion list; for {@link BasicElem} correspondences the single
	 * source element is removed. The correspondence entry itself is also deleted.</p>
	 */
	def deleteUnreferencedSourceElements(){
		val List<EObject> deletionList = newArrayList; 
		
		detectTargetDeletions().forEach[c |
			if(c instanceof MultiElem)
				deletionList += c.sourceElements
			else
				deletionList += c.sourceElement
			deletionList += c
		]
		deletionList.forEach[e | EcoreUtil.delete(e, true)]
	}
}