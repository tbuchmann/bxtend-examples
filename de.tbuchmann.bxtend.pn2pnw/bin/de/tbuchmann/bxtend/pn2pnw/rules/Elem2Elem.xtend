package de.tbuchmann.bxtend.pn2pnw.rules;

import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Corr
import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Pn2pnwFactory
import de.tbuchmann.bxtend.pn2pnw.correspondence.pn2pnw.Transformation
import java.util.Map
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import pn.PnFactory
import pn.PnPackage
import pnw.PnwFactory
import pnw.PnwPackage

/**
 * Abstract base class for all bidirectional transformation rules in the
 * Petrinet-to-PetrinetWeighted (Pn2Pnw) BXtend transformation.
 *
 * <p>Each concrete subclass implements one correspondence rule (e.g. Net↔Net,
 * Place↔Place, Transition↔Transition) and overrides
 * {@link #sourceToTarget()} and/or {@link #targetToSource()} to propagate
 * changes in the respective direction.</p>
 *
 * <p><b>Correspondence model:</b> Every matched pair of source and target
 * elements is recorded as a {@link Corr} object inside the shared
 * {@code corrModel} resource.  The static lookup map {@link #elementsToCorr}
 * allows O(1) retrieval of the correspondence for any model element during a
 * transformation pass.</p>
 *
 * <p><b>Lifecycle:</b> All rule instances belonging to the same transformation
 * run share the same three resources ({@code sourceModel}, {@code targetModel},
 * {@code corrModel}) and the same static {@code elementsToCorr} map.  The map
 * is populated from the persisted correspondence model in the constructor so
 * that incremental runs can reuse existing correspondences.</p>
 */
abstract class Elem2Elem {
	
	/** The EMF resource that holds the source (unweighted Petri net) model. */
	protected Resource sourceModel
	/** The EMF resource that holds the target (weighted Petri net) model. */
	protected Resource targetModel
	/**
	 * The EMF resource that holds the correspondence model, i.e. the
	 * {@link Transformation} root object containing all {@link Corr} links.
	 */
	protected Resource corrModel
	
	/** Factory for creating source-side ({@code pn}) model elements. */
	protected val sourceFactory = PnFactory::eINSTANCE
	/** Factory for creating target-side ({@code pnw}) model elements. */
	protected val targetFactory = PnwFactory::eINSTANCE
	/** Factory for creating correspondence model elements ({@code Corr}, {@code BasicElem}). */
	protected val corrFactory = Pn2pnwFactory::eINSTANCE
	/** Meta-model package for the source ({@code pn}) side. */
	protected val sourcePackage = PnPackage::eINSTANCE
	/** Meta-model package for the target ({@code pnw}) side. */
	protected val targetPackage = PnwPackage::eINSTANCE
	
	/**
	 * Identifies the rule type in correspondence model entries (e.g. {@code "root"},
	 * {@code "place"}, {@code "transition"}).  Set by each concrete subclass.
	 */
	protected var String ruleID
	
	/**
	 * Shared, static look-up table from any model element ({@code pn} or {@code pnw})
	 * to the {@link Corr} object that links it to its counterpart.
	 *
	 * <p>The map is populated once per transformation run from the persisted
	 * correspondence model, and updated whenever a new correspondence is created
	 * during source-to-target or target-to-source propagation.</p>
	 */
	protected static Map<EObject, Corr> elementsToCorr = newHashMap

	/**
	 * Shared, static map from a {@link Corr} to the identity key ({@code name}) of its
	 * source element as observed at the end of the last direction call. Used by
	 * {@link #synch()} implementations to detect whether the source-side identity changed
	 * since the last synchronisation (push forward) or not (pull backward).
	 */
	protected static Map<Corr, String> corrToName = newHashMap

	/**
	 * Shared, static map from a {@link Corr} to the last-known value of {@code Place.noOfTokens}
	 * (source) / {@code pnw.Place.noOfTokens} (target). Unlike {@link #corrToName}, this
	 * attribute can change independently on either side without affecting the correspondence's
	 * identity, so {@link Place2Place#synch()} compares both sides against this snapshot to
	 * decide whether to push, pull, or (if both changed) let the source win.
	 */
	protected static Map<Corr, Integer> corrToTokens = newHashMap

	/**
	 * Constructs the rule, wiring it to the shared model resources and
	 * pre-loading the {@link #elementsToCorr} map from the persisted
	 * correspondence model.
	 *
	 * @param src   the source-model resource (unweighted Petri net)
	 * @param trgt  the target-model resource (weighted Petri net)
	 * @param corr  the correspondence-model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		sourceModel = src
		targetModel = trgt
		corrModel = corr	
		ruleID = "base"
		(corrModel.contents.get(0) as Transformation).correspondences.forEach[c | 
			elementsToCorr.put(c.sourceElement, c)
			elementsToCorr.put(c.targetElement, c)
		]
	}
	
	/**
	 * Propagates changes from the source model to the target model.
	 * Concrete subclasses override this method to implement the forward
	 * direction of their specific correspondence rule.
	 */
	def void sourceToTarget() {
	}
	
	/**
	 * Propagates changes from the target model to the source model.
	 * Concrete subclasses override this method to implement the backward
	 * direction of their specific correspondence rule.
	 */
	def void targetToSource() {
	}

	/**
	 * Reconciles concurrent edits made to both the source and target models since the last
	 * synchronisation point. Concrete subclasses override this; the default implementation is
	 * a no-op.
	 */
	def void synch() {
	}

	/**
	 * Returns the {@link Corr} object associated with {@code obj}, or
	 * {@code null} if no correspondence has been established yet.
	 *
	 * @param obj a source-side or target-side model element
	 * @return the corresponding {@link Corr}, or {@code null}
	 */
	def getCorrModelElem(EObject obj) {
		elementsToCorr.get(obj)
	}

	/**
	 * Returns the existing {@link Corr} for {@code obj}, creating and
	 * registering a new one if none exists yet.
	 *
	 * <p>The newly created {@link Corr} is immediately added to the
	 * {@link Transformation#getCorrespondences() correspondences} list of
	 * the root object in the correspondence model and indexed in
	 * {@link #elementsToCorr}.</p>
	 *
	 * @param obj         a source-side or target-side model element
	 * @param description a short human-readable label stored in {@link Corr#getDesc()}
	 *                    (typically the {@link #ruleID} of the calling rule)
	 * @return the found or newly created {@link Corr}
	 */
	def getOrCreateCorrModelElement(EObject obj, String description) {
		var Corr corr = obj.getCorrModelElem
		if (corr === null) {
			corr = corrFactory.createBasicElem => [
				if (obj.eClass.EPackage instanceof PnPackage)
					sourceElement = obj
				if (obj.eClass.EPackage instanceof PnwPackage)
					targetElement = obj
				desc = description
			]
			(corrModel.contents.get(0) as Transformation).correspondences += corr
			elementsToCorr.put(corr.sourceElement, corr)
			elementsToCorr.put(corr.targetElement, corr)
		}
		return corr
	}
		

	/**
	 * Creates a new source-side ({@code pn}) model element of the given meta-class.
	 *
	 * @param clazz the {@link EClass} to instantiate
	 * @return the new, unattached source element
	 */
	def createSourceElement(EClass clazz) {
		sourceFactory.create(clazz)
	}
	
	/**
	 * Creates a new target-side ({@code pnw}) model element of the given meta-class.
	 *
	 * @param clazz the {@link EClass} to instantiate
	 * @return the new, unattached target element
	 */
	def createTargetElement(EClass clazz) {
		targetFactory.create(clazz)
	}
	
	/**
	 * Returns the source element linked by {@code corr}, creating and linking a
	 * new instance of {@code clazz} if the correspondence's source slot is empty.
	 *
	 * <p>Used during target-to-source propagation to obtain (or lazily create)
	 * the source counterpart of an existing target element.</p>
	 *
	 * @param corr  the correspondence whose source slot should be filled
	 * @param clazz the {@link EClass} to instantiate if no source element exists yet
	 * @return the existing or newly created source element
	 */
	def getOrCreateSourceElem(Corr corr, EClass clazz) {
		
		var EObject source  = corr.sourceElement
		if (corr.sourceElement === null){
			source = createSourceElement(clazz)
			corr.sourceElement = source
			elementsToCorr.put(corr.sourceElement, corr)
		}
		return source
	}

	/**
	 * Returns the target element linked by {@code corr}, creating and linking a
	 * new instance of {@code clazz} if the correspondence's target slot is empty.
	 *
	 * <p>Used during source-to-target propagation to obtain (or lazily create)
	 * the target counterpart of an existing source element.</p>
	 *
	 * @param corr  the correspondence whose target slot should be filled
	 * @param clazz the {@link EClass} to instantiate if no target element exists yet
	 * @return the existing or newly created target element
	 */
	def getOrCreateTargetElem(Corr corr, EClass clazz) {
		var EObject target = corr.targetElement 
		if (target === null) {
			target = createTargetElement(clazz)
			corr.targetElement = target
			elementsToCorr.put(corr.targetElement, corr)
		}
		return target
	}
	
	
}