package de.tbuchmann.bxtend.set2oset.rules;

import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.util.EcoreUtil
import org.eclipse.emf.ecore.EObject
import de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Corr
import java.util.ArrayList
import java.util.List

import de.ubt.ai1.m2m.bidir.imperative.xtend.BXtendTransformation

/**
 * Orchestrator for the Set-to-OSet bidirectional transformation implemented with the
 * <em>BXtend</em> framework.
 *
 * <p>This class wires together the two EMF models (source and target) with a third
 * <em>correspondence model</em>, instantiates and orders the transformation rules, and
 * exposes the two top-level entry points {@link #sourceToTarget()} and
 * {@link #targetToSource()} that the Benchmarx tool adapter calls.</p>
 *
 * <h2>Architecture overview</h2>
 * <pre>
 *  ┌──────────────┐   sourceToTarget()   ┌─────────────────┐
 *  │  sets.MySet  │ ──────────────────►  │ osets.MyOrderedSet│
 *  │  (source)    │ ◄──────────────────  │    (target)      │
 *  └──────────────┘   targetToSource()   └─────────────────┘
 *         │                                      │
 *         └──────────────────┬───────────────────┘
 *                            │
 *                    ┌───────▼────────┐
 *                    │  Correspondence│
 *                    │    Model       │
 *                    │ (Transformation│
 *                    │  + Corr list)  │
 *                    └────────────────┘
 * </pre>
 *
 * <h2>Rule execution order</h2>
 * <p>Rules are executed in the order in which they appear in the {@code rules} list.  The
 * container rule must run first so that the correspondence entries for {@code MySet} /
 * {@code MyOrderedSet} are in place when the element rule navigates to them via
 * {@code eContainer.corrModelElem}:</p>
 * <ol>
 *   <li>{@link MySet2MyOrderedSet} – synchronises the root containers</li>
 *   <li>{@link Element2Element} – synchronises contained elements and maintains the
 *       doubly-linked list in {@code MyOrderedSet}</li>
 * </ol>
 *
 * <h2>Deletion handling (generated code modification)</h2>
 * <p>BXtend's generated deletion helpers ({@link #deleteUnreferencedTargetElements()} and
 * {@link #deleteUnreferencedSourceElements()}) were <strong>manually extended</strong> beyond
 * the generated scaffold to correctly handle the doubly-linked-list invariant maintained by
 * {@code osets.Element}.</p>
 *
 * <p>When a source element is deleted its corresponding target {@code osets.Element} must be
 * removed from the linked list before it is physically deleted from the model.  The generated
 * code would leave the list broken (the predecessor's {@code next} pointer would dangle) if
 * this re-linking step were omitted.  The fix repairs the list by patching the predecessor's
 * {@code next} reference to skip over the element being deleted:</p>
 * <pre>
 *   … ←→ prev ←→ toDelete ←→ next ←→ …
 *   becomes after deletion:
 *   … ←→ prev ←→ next ←→ …
 * </pre>
 * <p>The complementary {@code next → previous} re-link is handled automatically by EMF because
 * {@code next} and {@code previous} are declared as an <em>eOpposite</em> pair in
 * {@code OrderedSets.ecore}: setting {@code prev.next = toDelete.next} automatically updates
 * {@code toDelete.next.previous = prev}.</p>
 *
 * <p>No analogous re-linking is needed in {@link #deleteUnreferencedSourceElements()} because
 * the source metamodel ({@code Sets.ecore}) has no ordering structure.</p>
 *
 * <h2>Correspondence model initialisation</h2>
 * <p>Both constructors guarantee that the correspondence resource always contains a root
 * {@link de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Transformation} object.  If the
 * resource is freshly created (empty), a new root is added; otherwise the existing root (and
 * all its {@link Corr} links) is reused for incremental synchronisation.</p>
 */
class Set2osetTransformation {
	
	/** EMF resource holding the source ({@code MySet}) model. */
	Resource sourceModel
	/** EMF resource holding the target ({@code MyOrderedSet}) model. */
	Resource targetModel
	/** EMF resource holding the correspondence model (root: {@link de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Transformation}). */
	Resource corrModel
	
	/**
	 * Ordered list of rules to be applied during a synchronisation step.
	 * The list is populated by {@link #addRules()} and must not be modified afterwards.
	 */
	List<Elem2Elem> rules = new ArrayList<Elem2Elem>();
	
	/**
	 * Constructs the transformation from three EMF {@link URI}s, loading the corresponding
	 * resources from the default {@link ResourceSetImpl}.
	 *
	 * <p>If the correspondence resource is empty (first run) a fresh
	 * {@link de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Transformation} root is
	 * created so that rules can immediately add {@link Corr} entries.</p>
	 *
	 * @param source       URI of the source model XMI file
	 * @param target       URI of the target model XMI file
	 * @param correspondence URI of the correspondence model XMI file
	 */
	new(URI source, URI target, URI correspondence) {
		val ResourceSet set = new ResourceSetImpl();
		sourceModel = set.getResource(source, true)
		targetModel = set.getResource(target, true)
		corrModel = set.getResource(correspondence, true)
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Set2osetFactory.eINSTANCE.createTransformation)	
		}

		addRules	
	}
	
	/**
	 * Constructs the transformation from three already-loaded EMF {@link Resource}s.
	 *
	 * <p>This constructor is used by the Benchmarx tool adapter
	 * ({@code BXtendSet2Oset}) which manages its own {@link ResourceSet} and passes the
	 * pre-loaded resources directly.</p>
	 *
	 * <p>If the correspondence resource is empty a fresh root is created as in the URI-based
	 * constructor.</p>
	 *
	 * @param source        the EMF resource containing the source model
	 * @param target        the EMF resource containing the target model
	 * @param correspondence the EMF resource containing the correspondence model
	 */
	new(Resource source, Resource target, Resource correspondence) {		
		sourceModel = source
		targetModel = target
		corrModel = correspondence
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Set2osetFactory.eINSTANCE.createTransformation)	
		}
		
		addRules
	}
	
	/**
	 * Registers the transformation rules in their required execution order:
	 * <ol>
	 *   <li>{@link MySet2MyOrderedSet} – must run first to establish container correspondences</li>
	 *   <li>{@link Element2Element} – relies on the container correspondences being present</li>
	 * </ol>
	 */
	def addRules() {
		rules += new MySet2MyOrderedSet(sourceModel, targetModel, corrModel)
		rules += new Element2Element(sourceModel, targetModel, corrModel)
	}
	
	/**
	 * Forward propagation entry point: propagates source-side changes to the target model.
	 *
	 * <p>Each rule in {@link #rules} is executed in order.  After the rules have run,
	 * {@link #deleteUnreferencedTargetElements()} removes any target elements whose
	 * corresponding source element has been deleted (i.e. whose {@link Corr} now has a
	 * {@code null} {@code sourceElement}).</p>
	 *
	 * <p>If the source model is empty the method is a no-op (guards against a completely
	 * uninitialised source resource).</p>
	 */
	def void sourceToTarget() {
		if (sourceModel.contents.size != 0)
		for (Elem2Elem e : rules) {
			e.sourceToTarget()
		}
		
		// Remove target elements that no longer have a source counterpart.
		deleteUnreferencedTargetElements
	}
	
	/**
	 * Backward propagation entry point: propagates target-side changes to the source model.
	 *
	 * <p>Each rule in {@link #rules} is executed in order.  After the rules have run,
	 * {@link #deleteUnreferencedSourceElements()} removes any source elements whose
	 * corresponding target element has been deleted (i.e. whose {@link Corr} now has a
	 * {@code null} {@code targetElement}).</p>
	 *
	 * <p>If the target model is empty the method is a no-op.</p>
	 */
	def void targetToSource() {		
		if (targetModel.contents.size != 0)
		for (Elem2Elem e: rules) {
			e.targetToSource()
		}
		
		// Remove source elements that no longer have a target counterpart.
		deleteUnreferencedSourceElements
	}
	
	/**
	 * Checks whether the current source and target models are mutually consistent according
	 * to the correspondence model.
	 *
	 * @return {@code true} always (placeholder – full consistency checking not yet implemented)
	 */
	def boolean checkCorrespondences() {
		true
	}
	
	/**
	 * Returns an iterator over all {@link Corr} entries in the correspondence model whose
	 * {@code sourceElement} has been set to {@code null} — i.e. correspondences that record
	 * a deletion on the source side.
	 *
	 * <p>These correspondences signal that the corresponding target element must be deleted
	 * during forward propagation.</p>
	 *
	 * @return a lazy iterator of {@link Corr}s with {@code sourceElement == null}
	 */
	def detectSourceDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			c.sourceElement === null
		]
	}
	
	/**
	 * Returns an iterator over all {@link Corr} entries in the correspondence model whose
	 * {@code targetElement} has been set to {@code null} — i.e. correspondences that record
	 * a deletion on the target side.
	 *
	 * <p>These correspondences signal that the corresponding source element must be deleted
	 * during backward propagation.</p>
	 *
	 * @return a lazy iterator of {@link Corr}s with {@code targetElement == null}
	 */
	def detectTargetDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			c.targetElement === null 
		]
	}
	
	/**
	 * Deletes target elements that have lost their source counterpart (forward-direction
	 * deletion handler).
	 *
	 * <p>This method is called at the end of {@link #sourceToTarget()} to propagate source-side
	 * deletions to the target model.  It iterates over all correspondences detected by
	 * {@link #detectSourceDeletions()} and performs the following steps for each:</p>
	 *
	 * <ol>
	 *   <li><strong>Linked-list repair (manually added):</strong> If the target element is an
	 *       {@code osets.Element}, its predecessor ({@code previous}) is re-linked to its
	 *       successor ({@code next}) before the element is physically removed.  This keeps the
	 *       doubly-linked list in {@code MyOrderedSet} consistent after the deletion.
	 *       <br/>Specifically, {@code trg.previous.next} is set to {@code trg.next}.
	 *       EMF's opposite-reference machinery automatically updates
	 *       {@code trg.next.previous = trg.previous} as a side-effect, so only one side of
	 *       the re-link needs to be written explicitly.
	 *       <br/><b>Note:</b> This block is a <em>manual modification</em> of the BXtend
	 *       generated template. Without it, the linked list would be left with a dangling
	 *       pointer after a forward deletion.</li>
	 *   <li>Collect the target element and the {@link Corr} object into a deletion list.</li>
	 *   <li>After all correspondences have been processed, call {@link EcoreUtil#delete}
	 *       on every collected object so that EMF can properly clean up cross-references.</li>
	 * </ol>
	 */
	def deleteUnreferencedTargetElements(){
		val List<EObject> deletionList = newArrayList; 
		
		detectSourceDeletions().forEach[c |
			// ── Manual modification: repair the doubly-linked list before removing the element ──
			// When an osets.Element is deleted its predecessor's 'next' pointer must be patched
			// to skip over it; otherwise the list is broken after the deletion.  EMF's eOpposite
			// mechanism takes care of the symmetric 'previous' update automatically.
			if (c.targetElement instanceof osets.Element) {
				val osets.Element trg = c.targetElement as osets.Element
				if (trg.previous !== null) {
					trg.previous.next = trg.next
				}
			}
			// ── End of manual modification ──
			deletionList += c.targetElement
			deletionList += c
		]
		deletionList.forEach[e | EcoreUtil.delete(e, true)]
	}
	
	/**
	 * Deletes source elements that have lost their target counterpart (backward-direction
	 * deletion handler).
	 *
	 * <p>This method is called at the end of {@link #targetToSource()} to propagate target-side
	 * deletions to the source model.  It collects the source element and the {@link Corr} for
	 * every correspondence detected by {@link #detectTargetDeletions()}, then deletes them all
	 * via {@link EcoreUtil#delete}.</p>
	 *
	 * <p>No linked-list repair is needed here because {@code sets.Element} has no ordering
	 * structure in the source metamodel.</p>
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