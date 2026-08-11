package de.tbuchmann.bxtend.gantt2cpm.rules;

import cpm.CpmFactory
import cpm.CpmPackage
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Corr
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Gantt2cpmFactory
import de.tbuchmann.bxtend.gantt2cpm.correspondence.gantt2cpm.Transformation
import gantt.GanttFactory
import gantt.GanttPackage
import java.util.Map
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource

/**
 * Abstract base class for all Gantt ↔ CPM transformation rules.
 *
 * <p>This class provides the shared infrastructure that every concrete rule builds upon:</p>
 * <ul>
 *   <li><b>EMF resource handles</b> – references to the three model resources
 *       ({@code sourceModel}, {@code targetModel}, {@code corrModel}) injected at
 *       construction time.</li>
 *   <li><b>EMF factory / package singletons</b> – pre-fetched instances of
 *       {@link GanttFactory}, {@link CpmFactory}, the correspondence factory, and the
 *       corresponding package objects for creating and inspecting model elements.</li>
 *   <li><b>O(1) correspondence lookup</b> – a static, shared
 *       {@code Map<EObject, Corr>} ({@link #elementsToCorr}) that is eagerly
 *       populated from the persisted correspondence model during construction and
 *       is kept up to date whenever new correspondences are added at run time.</li>
 *   <li><b>Element look-up/creation helpers</b> – {@link #getOrCreateCorrModelElement},
 *       {@link #getOrCreateSourceElem}, and {@link #getOrCreateTargetElem} implement
 *       the "look up existing, create if absent" pattern that all rules rely on for
 *       incremental re-synchronisation.</li>
 *   <li><b>Abstract propagation hooks</b> – {@link #sourceToTarget()} and
 *       {@link #targetToSource()} are overridden by concrete subclasses to implement
 *       the rule-specific mapping logic.</li>
 * </ul>
 *
 * <p><b>Shared static map:</b> {@code elementsToCorr} is {@code static} so that the
 * combined lookup table is shared across all rule instances created for the same
 * transformation run.  Every constructor call pre-populates the map with the entries
 * already stored in the correspondence XMI file, ensuring that previously matched
 * elements are recognised as such on subsequent (incremental) runs.</p>
 *
 * <p><b>Rule identifier:</b> Each subclass must set the {@link #ruleID} field in its
 * constructor.  The ID is stored in the {@code desc} attribute of every {@link Corr}
 * created by that subclass and is used by
 * {@link Gantt2cpmTransformation#detectSourceDeletions()} /
 * {@link Gantt2cpmTransformation#detectTargetDeletions()} to distinguish
 * correspondence entries by origin rule.</p>
 */
abstract class Elem2Elem {
	
	/** EMF resource that holds the source (Gantt) model. */
	protected Resource sourceModel
	/** EMF resource that holds the target (CPM) model. */
	protected Resource targetModel
	/** EMF resource that holds the correspondence (Corr) model. */
	protected Resource corrModel
	
	/** EMF factory for creating new {@code gantt.*} elements. */
	protected val sourceFactory = GanttFactory::eINSTANCE
	/** EMF factory for creating new {@code cpm.*} elements. */
	protected val targetFactory = CpmFactory::eINSTANCE
	/** EMF factory for creating new correspondence model elements. */
	protected val corrFactory = Gantt2cpmFactory::eINSTANCE
	/** Package singleton for the Gantt metamodel (used to look up {@code EClass} descriptors). */
	protected val sourcePackage = GanttPackage::eINSTANCE
	/** Package singleton for the CPM metamodel (used to look up {@code EClass} descriptors). */
	protected val targetPackage = CpmPackage::eINSTANCE
	
	/**
	 * Rule identifier stored in the {@code desc} attribute of every {@link Corr}
	 * created by this rule.  Subclasses must override this in their constructor,
	 * e.g. {@code "root"}, {@code "activity"}, {@code "dependency"}.
	 */
	protected var String ruleID
	
	/**
	 * Shared, static map from any model element ({@code EObject}) to its
	 * {@link Corr} correspondence entry.
	 *
	 * <p>Both the source element and the target element of a {@code Corr} are
	 * inserted as keys so that a single {@link Map#get(Object)} suffices
	 * regardless of which side the caller holds.</p>
	 *
	 * <p>The map is populated in the constructor from the persisted correspondence
	 * model and is kept current by {@link #getOrCreateCorrModelElement} and
	 * {@link #getOrCreateTargetElem} / {@link #getOrCreateSourceElem} as new
	 * correspondences are lazily created during each transformation run.</p>
	 */
	protected static Map<EObject, Corr> elementsToCorr = newHashMap

	/**
	 * Shared, static map from a {@link Corr} to the identity key (typically the
	 * {@code name}) of its source element as observed at the end of the last
	 * {@link #synch()} pass.  Used by {@link #synch()} implementations to detect
	 * whether the source-side identity changed since the last synchronisation, in
	 * which case the change is propagated forward; otherwise target-side changes
	 * are pulled backward into the source element.
	 */
	protected static Map<Corr, String> corrToName = newHashMap

	/**
	 * Shared, static map from a {@link Corr} to the numeric attribute value
	 * ({@code Activity.duration} or, for dependency arcs, the {@code Dependency.offset}
	 * mirrored into {@code Activity.duration}) observed at the end of the last
	 * synchronisation.  Unlike the identity key ({@link #corrToName}), this attribute
	 * can change independently on either side without affecting the correspondence's
	 * identity, so {@code synch()} implementations compare <em>both</em> sides against
	 * this last-known value to decide whether to push, pull, or (if both changed) let
	 * the source win.
	 */
	protected static Map<Corr, Integer> corrToDuration = newHashMap

	/**
	 * Constructs the base rule, wires the three model resources, and populates
	 * {@link #elementsToCorr} from the persisted correspondence model.
	 *
	 * @param src  EMF resource holding the source (Gantt) model
	 * @param trgt EMF resource holding the target (CPM) model
	 * @param corr EMF resource holding the correspondence model
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
	 * Propagates changes from the source (Gantt) model to the target (CPM) model.
	 * Override in concrete rule subclasses; the default implementation is a no-op.
	 */
	def void sourceToTarget() {
	}
	
	/**
	 * Propagates changes from the target (CPM) model back to the source (Gantt) model.
	 * Override in concrete rule subclasses; the default implementation is a no-op.
	 */
	def void targetToSource() {
	}

	/**
	 * Reconciles concurrent edits made to both the source (Gantt) and target (CPM)
	 * models since the last synchronisation point.  Override in concrete rule
	 * subclasses; the default implementation is a no-op.
	 */
	def void synch() {
	}

	/**
	 * Looks up the {@link Corr} entry associated with the given model element.
	 *
	 * @param obj the source or target element whose correspondence is needed
	 * @return the {@link Corr} for {@code obj}, or {@code null} if none exists yet
	 */
	def getCorrModelElem(EObject obj) {
		elementsToCorr.get(obj)
	}

	/**
	 * Returns the existing {@link Corr} for {@code obj}, or creates a new one if
	 * none is found.
	 *
	 * <p>When creating a new {@code Corr}:</p>
	 * <ul>
	 *   <li>If {@code obj} belongs to the Gantt package it is stored as
	 *       {@code Corr.sourceElement}.</li>
	 *   <li>If {@code obj} belongs to the CPM package it is stored as
	 *       {@code Corr.targetElement}.</li>
	 *   <li>{@code desc} is set to the supplied {@code description} string
	 *       (typically the rule ID of the calling rule).</li>
	 *   <li>The new {@code Corr} is appended to the root {@link Transformation}
	 *       object in the correspondence model and both sides are registered in
	 *       {@link #elementsToCorr}.</li>
	 * </ul>
	 *
	 * @param obj         the model element to look up or register
	 * @param description a human-readable label (rule ID) stored in the new {@code Corr}
	 * @return the existing or newly created {@link Corr}
	 */
	def getOrCreateCorrModelElement(EObject obj, String description) {
		var Corr corr = obj.getCorrModelElem
		if (corr === null) {
			corr = corrFactory.createBasicElem => [
				if (obj.eClass.EPackage instanceof GanttPackage)
					sourceElement = obj
				if (obj.eClass.EPackage instanceof CpmPackage)
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
	 * Creates a new source (Gantt) model element of the given {@link EClass}.
	 *
	 * @param clazz the {@code EClass} descriptor of the element to create
	 * @return the newly instantiated {@code EObject}
	 */
	def createSourceElement(EClass clazz) {
		sourceFactory.create(clazz)
	}
	
	/**
	 * Creates a new target (CPM) model element of the given {@link EClass}.
	 *
	 * @param clazz the {@code EClass} descriptor of the element to create
	 * @return the newly instantiated {@code EObject}
	 */
	def createTargetElement(EClass clazz) {
		targetFactory.create(clazz)
	}
	
	/**
	 * Returns the source element linked by {@code corr}, or creates and links
	 * a new one if {@code corr.sourceElement} is {@code null}.
	 *
	 * <p>The newly created element is registered in {@link #elementsToCorr}.</p>
	 *
	 * @param corr  the correspondence entry to inspect / update
	 * @param clazz the {@code EClass} of the source element to create if absent
	 * @return the existing or newly created source {@code EObject}
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
	 * Returns the target element linked by {@code corr}, or creates and links
	 * a new one if {@code corr.targetElement} is {@code null}.
	 *
	 * <p>The newly created element is registered in {@link #elementsToCorr}.
	 * Subclasses that need to create additional related elements (e.g.
	 * {@link Activity2Activity} which must also create two {@link cpm.Event}
	 * instances) should override this method.</p>
	 *
	 * @param corr  the correspondence entry to inspect / update
	 * @param clazz the {@code EClass} of the target element to create if absent
	 * @return the existing or newly created target {@code EObject}
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