package de.tbuchmann.bxtend.set2oset.rules;

import de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Corr
import de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Set2osetFactory
import de.tbuchmann.bxtend.set2oset.correspondence.set2oset.Transformation
import java.util.Map
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import osets.OsetsFactory
import osets.OsetsPackage
import sets.SetsFactory
import sets.SetsPackage

/**
 * Abstract base class for all bidirectional transformation rules in the Set-to-OSet BXtend
 * transformation.
 *
 * <p>Each concrete subclass represents one rule that maps a concept from the source metamodel
 * ({@code Sets.ecore}: {@code MySet}, {@code sets.Element}) to its counterpart in the target
 * metamodel ({@code OrderedSets.ecore}: {@code MyOrderedSet}, {@code osets.Element}) and
 * vice versa.  Subclasses override {@link #sourceToTarget()} for the forward direction and
 * {@link #targetToSource()} for the backward direction.</p>
 *
 * <h2>Correspondence model</h2>
 * <p>The BXtend framework maintains a third, <em>correspondence model</em> whose root object is a
 * {@link Transformation} that contains a flat list of {@link Corr} links.  Every {@code Corr}
 * pairs exactly one source {@link EObject} with exactly one target {@link EObject} and stores a
 * human-readable {@code desc} label (set to the rule ID of the creating rule).
 * A static {@link Map} ({@code elementsToCorr}) provides O(1) lookup in both directions so that
 * a rule can quickly find the correspondence for any source or target element it encounters.</p>
 *
 * <h2>Incremental execution</h2>
 * <p>Rules are designed to be called repeatedly on the same (possibly already partially
 * synchronised) models.  Before creating a new correspondence or a new model element they always
 * check whether one already exists ({@code getOrCreate…} helpers), making the rules idempotent
 * with respect to elements that have not changed since the last synchronisation step.</p>
 *
 * <h2>Inheritance hierarchy</h2>
 * <pre>
 * Elem2Elem  (this class – base, rule infrastructure)
 * ├── MySet2MyOrderedSet  (maps the container: MySet ↔ MyOrderedSet)
 * └── Element2Element     (maps individual elements: sets.Element ↔ osets.Element)
 * </pre>
 */
abstract class Elem2Elem {

	/** EMF resource holding the source ({@code MySet}) model. */
	protected Resource sourceModel
	/** EMF resource holding the target ({@code MyOrderedSet}) model. */
	protected Resource targetModel
	/**
	 * EMF resource holding the correspondence model whose root is a {@link Transformation}
	 * object that owns all {@link Corr} links.
	 */
	protected Resource corrModel

	/** Factory for creating new source-side (Sets) model elements. */
	protected val sourceFactory = SetsFactory::eINSTANCE
	/** Factory for creating new target-side (OSets) model elements. */
	protected val targetFactory = OsetsFactory::eINSTANCE
	/** Factory for creating new correspondence model elements ({@link Corr}, {@link Transformation}). */
	protected val corrFactory = Set2osetFactory::eINSTANCE
	/** EMF package descriptor for the source metamodel; used to resolve {@link EClass} literals. */
	protected val sourcePackage = SetsPackage::eINSTANCE
	/** EMF package descriptor for the target metamodel; used to resolve {@link EClass} literals. */
	protected val targetPackage = OsetsPackage::eINSTANCE

	/**
	 * Identifies this rule in the correspondence model.  Each {@link Corr} created by a rule
	 * stores this string as its {@code desc} attribute so that one can tell which rule established
	 * a particular correspondence.  Subclasses set this field in their constructor.
	 */
	protected var String ruleID

	/**
	 * Shared, static lookup table that maps any source or target {@link EObject} to its
	 * {@link Corr} in the correspondence model.  The map is populated lazily: whenever a new
	 * correspondence is created or an existing one is loaded from the persisted correspondence
	 * model, both the source element and the target element are inserted as keys.
	 *
	 * <p><b>Note:</b> Because the field is {@code static}, it is shared across all rule
	 * instances within the same JVM session. It is (re-)initialised from the persisted
	 * correspondence model during each constructor call.</p>
	 */
	protected static Map<EObject, Corr> elementsToCorr = newHashMap

	/**
	 * Shared, static map from a {@link Corr} to the identity key (the {@code name} of a
	 * {@code MySet}/{@code MyOrderedSet}, or the {@code value} of a {@code sets.Element}/
	 * {@code osets.Element}) of its source element, as observed at the end of the last
	 * direction call. Used by {@link #synch()} implementations to detect whether the
	 * source-side identity changed since the last synchronisation (push forward) or not
	 * (pull backward).
	 */
	protected static Map<Corr, String> corrToName = newHashMap

	/**
	 * Constructs an {@code Elem2Elem} rule, wiring it to the three EMF resources that form the
	 * synchronisation state.
	 *
	 * <p>During construction the existing correspondences are read from the correspondence model
	 * and cached in {@link #elementsToCorr} so that incremental rule executions can find
	 * previously established links without scanning the whole model.</p>
	 *
	 * @param src  the EMF resource containing the source ({@code MySet}) model
	 * @param trgt the EMF resource containing the target ({@code MyOrderedSet}) model
	 * @param corr the EMF resource containing the correspondence model (root: {@link Transformation})
	 */
	new(Resource src, Resource trgt, Resource corr) {
		sourceModel = src
		targetModel = trgt
		corrModel = corr
		ruleID = "base"
		// Populate the in-memory lookup table from the persisted correspondence model so that
		// incremental executions can find correspondences established in earlier runs.
		(corrModel.contents.get(0) as Transformation).correspondences.forEach[c |
			elementsToCorr.put(c.sourceElement, c)
			elementsToCorr.put(c.targetElement, c)
		]
	}

	/**
	 * Forward propagation: applies this rule in the direction source → target.
	 * Concrete subclasses iterate over the relevant elements of {@link #sourceModel}, look up or
	 * create correspondences, and create or update the corresponding target elements.
	 * The default implementation is a no-op; subclasses must override it.
	 */
	def void sourceToTarget() {
	}

	/**
	 * Backward propagation: applies this rule in the direction target → source.
	 * Concrete subclasses iterate over the relevant elements of {@link #targetModel}, look up or
	 * create correspondences, and create or update the corresponding source elements.
	 * The default implementation is a no-op; subclasses must override it.
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
	 * Looks up the {@link Corr} that was established for the given model element.
	 *
	 * @param obj a source or target {@link EObject}
	 * @return the {@link Corr} previously registered for {@code obj}, or {@code null} if none
	 *         has been created yet
	 */
	def getCorrModelElem(EObject obj) {
		elementsToCorr.get(obj)
	}

	/**
	 * Looks up the {@link Corr} for {@code obj}, or creates a new one if none exists.
	 *
	 * <p>When a new {@link Corr} is created the method determines whether {@code obj} belongs
	 * to the source or the target metamodel by inspecting its {@link EClass#getEPackage()
	 * EPackage}, sets the appropriate half of the correspondence, stores the provided
	 * {@code description} as the rule ID label, and registers both halves in
	 * {@link #elementsToCorr}.</p>
	 *
	 * @param obj         the source or target element for which a correspondence is required
	 * @param description the rule ID label to attach to a newly created {@link Corr}
	 * @return the existing or newly created {@link Corr} for {@code obj}
	 */
	def getOrCreateCorrModelElement(EObject obj, String description) {
		var Corr corr = obj.getCorrModelElem
		if (corr == null) {
			corr = corrFactory.createBasicElem => [
				if (obj.eClass.EPackage instanceof SetsPackage)
					sourceElement = obj
				if (obj.eClass.EPackage instanceof OsetsPackage)
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
	 * Creates a new, unowned source-side model element of the specified type.
	 *
	 * @param clazz the {@link EClass} to instantiate (from the {@code sets} metamodel)
	 * @return the freshly created {@link EObject}
	 */
	def createSourceElement(EClass clazz) {
		sourceFactory.create(clazz)
	}

	/**
	 * Creates a new, unowned target-side model element of the specified type.
	 *
	 * @param clazz the {@link EClass} to instantiate (from the {@code osets} metamodel)
	 * @return the freshly created {@link EObject}
	 */
	def createTargetElement(EClass clazz) {
		targetFactory.create(clazz)
	}

	/**
	 * Returns the source element held by {@code corr}, creating and linking a new one if the
	 * source side of the correspondence is still empty (i.e. during backward propagation of a
	 * new target element).
	 *
	 * @param corr  the correspondence whose source element is needed
	 * @param clazz the {@link EClass} to use if a new source element must be created
	 * @return the existing or newly created source {@link EObject}
	 */
	def getOrCreateSourceElem(Corr corr, EClass clazz) {
		var EObject source = corr.sourceElement
		if (corr.sourceElement === null) {
			source = createSourceElement(clazz)
			corr.sourceElement = source
			elementsToCorr.put(corr.sourceElement, corr)
		}
		return source
	}

	/**
	 * Returns the target element held by {@code corr}, creating and linking a new one if the
	 * target side of the correspondence is still empty (i.e. during forward propagation of a
	 * new source element).
	 *
	 * @param corr  the correspondence whose target element is needed
	 * @param clazz the {@link EClass} to use if a new target element must be created
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