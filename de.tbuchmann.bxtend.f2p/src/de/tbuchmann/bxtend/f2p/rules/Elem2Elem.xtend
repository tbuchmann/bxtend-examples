/**
 * Abstract base class for all bidirectional element-to-element transformation rules in
 * the Families-to-Persons BXtend transformation.
 *
 * <p>Every concrete rule (e.g. {@link Register2Register}, {@link FamilyMember2Person})
 * extends this class and provides implementations of at least one direction:
 * <ul>
 *   <li>{@link #sourceToTarget()} – propagates changes from the Families model to the
 *       Persons model (forward direction).</li>
 *   <li>{@link #targetToSource()} – propagates changes from the Persons model back to
 *       the Families model (backward direction).</li>
 *   <li>{@link #synch()} – reconciles concurrent edits made to both models
 *       (synchronisation direction).</li>
 * </ul>
 *
 * <p><b>Correspondence model</b><br>
 * The transformation maintains a correspondence (corr) model that records which Families
 * element is paired with which Persons element.  The two static maps
 * {@link #elementsToCorr} and {@link #corrToName} serve as an in-memory index over the
 * corr model so that lookups by EMF object are O(1).
 *
 * <p><b>Decision strategy</b><br>
 * Ambiguous decisions during the backward transformation are delegated to the injected
 * {@link TargetToSourceDecision} strategy object.
 */
package de.tbuchmann.bxtend.f2p.rules;

import Families.FamiliesFactory
import Families.FamiliesPackage
import Persons.PersonsFactory
import Persons.PersonsPackage
import de.tbuchmann.bxtend.f2p.correspondence.f2p.Corr
import de.tbuchmann.bxtend.f2p.correspondence.f2p.F2pFactory
import de.tbuchmann.bxtend.f2p.correspondence.f2p.Transformation
import de.tbuchmann.bxtend.f2p.rules.decisions.TargetToSourceDecision
import java.util.Map
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource

abstract class Elem2Elem {
	
	/** The Families (source) model resource. */
	protected Resource sourceModel
	/** The Persons (target) model resource. */
	protected Resource targetModel
	/** The correspondence model resource. */
	protected Resource corrModel
	
	/** Factory for creating Families model elements. */
	protected val sourceFactory = FamiliesFactory::eINSTANCE
	/** Factory for creating Persons model elements. */
	protected val targetFactory = PersonsFactory::eINSTANCE
	/** Factory for creating correspondence model elements. */
	protected val corrFactory = F2pFactory::eINSTANCE
	/** Families metamodel package (used for type checks). */
	protected val sourcePackage = FamiliesPackage::eINSTANCE
	/** Persons metamodel package (used for type checks). */
	protected val targetPackage = PersonsPackage::eINSTANCE
	
	/** Identifier string used to distinguish rule types in the correspondence model. */
	protected var String ruleID
	
	/** Strategy object for resolving ambiguous backward-transformation decisions. */
	protected TargetToSourceDecision decision
	
	/**
	 * Index from a source or target EMF object to its {@link Corr} correspondence entry.
	 * Shared across all rule instances (static) so all rules see the same correspondence
	 * state.
	 */
	protected static Map<EObject, Corr> elementsToCorr = newHashMap
	/**
	 * Index from a {@link Corr} correspondence to the descriptive name assigned when
	 * the correspondence was created.
	 */
	protected static Map<Corr, String> corrToName = newHashMap
	
	/**
	 * Constructs a new rule and builds the in-memory correspondence index from the
	 * serialised correspondence model.
	 *
	 * @param src  the Families source model resource
	 * @param trgt the Persons target model resource
	 * @param corr the correspondence model resource
	 * @param dec  the strategy for resolving backward-transformation decisions
	 */
	new(Resource src, Resource trgt, Resource corr, TargetToSourceDecision dec) {
		sourceModel = src
		targetModel = trgt
		corrModel = corr
		decision = dec
		ruleID = "base"
		(corrModel.contents.get(0) as Transformation).correspondences.forEach[c | 
			elementsToCorr.put(c.sourceElement, c)
			elementsToCorr.put(c.targetElement, c)
		]
	}
	
	/**
	 * Propagates changes from the source (Families) model to the target (Persons) model.
	 * Subclasses override this method to implement the forward direction.
	 * The default implementation is a no-op.
	 */
	def void sourceToTarget() {
	}
	
	/**
	 * Propagates changes from the target (Persons) model back to the source (Families)
	 * model.  Subclasses override this method to implement the backward direction.
	 * The default implementation is a no-op.
	 */
	def void targetToSource() {
	}
	
	/**
	 * Reconciles concurrent edits in both models.  Subclasses override this method to
	 * implement the synchronisation direction.  The default implementation is a no-op.
	 */
	def void synch() {}
	
	/**
	 * Replaces the current decision strategy with a new one.
	 *
	 * @param dec the new {@link TargetToSourceDecision} to use
	 */
	def void configure(TargetToSourceDecision dec) {
		decision = dec
	}
	
	/**
	 * Looks up the {@link Corr} correspondence entry for {@code obj}.
	 *
	 * @param obj the EMF object to look up
	 * @return the corresponding {@link Corr}, or {@code null} if none exists
	 */
	def getCorrModelElem(EObject obj) {
		elementsToCorr.get(obj)
	}

	/**
	 * Returns the existing {@link Corr} for {@code obj}, or creates and registers a new
	 * one if none exists yet.
	 *
	 * <p>The new correspondence is added to the root {@link Transformation} container in
	 * the correspondence model, and both the source and target index entries are updated.
	 *
	 * @param obj         the EMF object for which a correspondence is needed
	 * @param description a human-readable description stored in the correspondence
	 * @return the existing or newly created {@link Corr}
	 */
	def getOrCreateCorrModelElement(EObject obj, String description) {
		var Corr corr = obj.getCorrModelElem
		if (corr === null) {
			corr = corrFactory.createBasicElem => [
				if (obj.eClass.EPackage instanceof FamiliesPackage)
					sourceElement = obj
				if (obj.eClass.EPackage instanceof PersonsPackage)
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
	 * Creates a new source-side (Families) element of the given metaclass.
	 *
	 * @param clazz the EClass to instantiate
	 * @return the newly created {@link EObject}
	 */
	def createSourceElement(EClass clazz) {
		sourceFactory.create(clazz)
	}
	
	/**
	 * Creates a new target-side (Persons) element of the given metaclass.
	 *
	 * @param clazz the EClass to instantiate
	 * @return the newly created {@link EObject}
	 */
	def createTargetElement(EClass clazz) {
		targetFactory.create(clazz)
	}
	
	/**
	 * Returns the source element already stored in {@code corr}, or creates and stores
	 * a new source element of type {@code clazz} when the slot is empty.
	 *
	 * <p>The new element is also added to the {@link #elementsToCorr} index.
	 *
	 * @param corr  the correspondence whose source slot is checked
	 * @param clazz the EClass to instantiate when the slot is empty
	 * @return the existing or newly created source {@link EObject}
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
	 * Returns the target element already stored in {@code corr}, or creates and stores
	 * a new target element of type {@code clazz} when the slot is empty.
	 *
	 * <p>The new element is also added to the {@link #elementsToCorr} index.
	 *
	 * @param corr  the correspondence whose target slot is checked
	 * @param clazz the EClass to instantiate when the slot is empty
	 * @return the existing or newly created target {@link EObject}
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