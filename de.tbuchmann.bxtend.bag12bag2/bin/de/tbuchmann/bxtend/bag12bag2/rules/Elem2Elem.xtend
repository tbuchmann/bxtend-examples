package de.tbuchmann.bxtend.bag12bag2.rules;

import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Bag12bag2Factory
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Corr
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.Transformation
import java.util.Map
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import bags1.Bags1Package
import bags2.Bags2Package
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.MultiElem
import de.tbuchmann.bxtend.bag12bag2.correspondence.bag12bag2.BasicElem
import bags1.Bags1Factory
import bags2.Bags2Factory

/**
 * Abstract base class for all BXtend transformation rules in the Bag1-to-Bag2
 * bidirectional, incremental model transformation.
 *
 * <p>This class is part of the <em>BXtend</em> framework infrastructure and has been
 * extended beyond the standard BXtend generator output to introduce the
 * {@link MultiElem} correspondence type. The standard BXtend framework only generates
 * a single {@code Corr} type with one source element and one target element
 * (a 1-to-1 correspondence). The Bag1-to-Bag2 transformation, however, requires
 * <em>many-to-one</em> correspondences: multiple Bag1 {@code Element} objects sharing
 * the same {@code value} are all mapped to a single Bag2 {@code Element} with an
 * explicit {@code multiplicity} attribute. To represent this, {@code MultiElem} was
 * manually introduced in the correspondence metamodel ({@code corresp.ecore}) and the
 * corresponding EMF-generated Java interfaces/implementations were added to the
 * {@code correspondence} package.</p>
 *
 * <h2>Correspondence Model Overview</h2>
 * <pre>
 *   Transformation
 *     └─ correspondences : Corr[*]
 *          ├─ BasicElem  (extends Corr)
 *          │    ├─ sourceElement : EObject   (one Bag1 MyBag)
 *          │    ├─ targetElement : EObject   (one Bag2 MyBag)
 *          │    └─ desc : String             (rule identifier, e.g. "Bag2Bag")
 *          └─ MultiElem  (extends Corr)       ← manually added extension
 *               ├─ sourceElements : EObject[*] (N Bag1 Elements with same value)
 *               ├─ targetElement  : EObject    (one Bag2 Element with multiplicity=N)
 *               └─ desc : String               (rule identifier, e.g. "Element2Element")
 * </pre>
 *
 * <h2>In-Memory Cache</h2>
 * <p>The field {@link #elementsToCorr} is a <em>static</em>, shared {@link Map} that
 * acts as a reverse index from every model object (source or target) to its
 * {@link Corr} correspondence entry. This cache is populated once during construction
 * by iterating over the persisted correspondences and is kept up to date as new
 * correspondences are created at run time. The static scope means the cache is shared
 * across all rule instances within the same transformation execution.</p>
 *
 * <h2>Rule Identification</h2>
 * <p>Each concrete subclass sets the {@link #ruleID} string (e.g. {@code "Bag2Bag"},
 * {@code "Element2Element"}). The rule ID is stored in the {@code desc} attribute of
 * every {@link Corr} entry created by that rule, which allows other rules to
 * distinguish their own correspondences when iterating over the correspondence model.</p>
 *
 * @see Bag2Bag
 * @see Element2Element
 * @see Bag12bag2Transformation
 */
abstract class Elem2Elem {
	
	/** The EMF {@link Resource} that holds the Bag1 (source) model. */
	protected Resource sourceModel
	/** The EMF {@link Resource} that holds the Bag2 (target) model. */
	protected Resource targetModel
	/**
	 * The EMF {@link Resource} that holds the correspondence model.
	 * Its root object is a {@link Transformation} instance whose
	 * {@code correspondences} containment reference stores all {@link Corr} entries.
	 */
	protected Resource corrModel
	
	/** Factory for creating new Bag1 model elements. */
	protected val sourceFactory = Bags1Factory::eINSTANCE
	/** Factory for creating new Bag2 model elements. */
	protected val targetFactory = Bags2Factory::eINSTANCE
	/** Factory for creating new correspondence model elements ({@link BasicElem} / {@link MultiElem}). */
	protected val corrFactory = Bag12bag2Factory::eINSTANCE
	/** Metamodel package descriptor for Bag1; used to test {@code instanceof Bags1Package} at runtime. */
	protected val sourcePackage = Bags1Package::eINSTANCE
	/** Metamodel package descriptor for Bag2; used to test {@code instanceof Bags2Package} at runtime. */
	protected val targetPackage = Bags2Package::eINSTANCE
	
	/**
	 * Human-readable identifier that distinguishes this rule from others.
	 * Stored in every {@link Corr#getDesc()} so that rules can filter their
	 * own correspondences when scanning the correspondence model.
	 * Concrete subclasses must overwrite the default value {@code "base"}.
	 */
	protected var String ruleID
	
	/**
	 * Shared reverse-index cache mapping every tracked model element to its
	 * {@link Corr} correspondence object.
	 *
	 * <p>The map is populated in two ways:
	 * <ol>
	 *   <li>At construction time, by iterating over the already-persisted
	 *       correspondences in the correspondence model (see {@link #put(Map, MultiElem)}
	 *       / {@link #put(Map, BasicElem)} dispatch methods).</li>
	 *   <li>Lazily at run time, whenever a new correspondence is created by
	 *       {@link #getOrCreateCorrModelElement(EObject, String)} or the
	 *       {@code getOrCreate*Elem} helpers.</li>
	 * </ol>
	 * Declared {@code static} so that the cache is shared across all rule
	 * instances that participate in the same transformation session.</p>
	 */
	protected static Map<EObject, Corr> elementsToCorr = newHashMap
	
	/**
	 * Constructs the rule, wires the three model resources, seeds {@link #ruleID}
	 * with the sentinel value {@code "base"}, and populates the {@link #elementsToCorr}
	 * cache from the already-persisted correspondences.
	 *
	 * @param src  the Bag1 source model resource
	 * @param trgt the Bag2 target model resource
	 * @param corr the correspondence model resource (root must be a {@link Transformation})
	 */
	new(Resource src, Resource trgt, Resource corr) {
		sourceModel = src
		targetModel = trgt
		corrModel = corr	
		ruleID = "base"
		// Re-index all existing correspondences into the in-memory cache so that
		// incremental runs can look up previously established links.
		(corrModel.contents.get(0) as Transformation).correspondences.forEach[c | 
			elementsToCorr.put(c)
		]
	}
	
	/**
	 * Propagates changes from the source (Bag1) model to the target (Bag2) model.
	 * Concrete subclasses override this method with their specific rule logic.
	 */
	def void sourceToTarget() {
	}
	
	/**
	 * Propagates changes from the target (Bag2) model back to the source (Bag1) model.
	 * Concrete subclasses override this method with their specific rule logic.
	 */
	def void targetToSource() {
	}
	
	/**
	 * Looks up the {@link Corr} entry for the given model object in the in-memory cache.
	 *
	 * @param obj any Bag1 or Bag2 model element
	 * @return the correspondence that covers {@code obj}, or {@code null} if none exists yet
	 */
	def getCorrModelElem(EObject obj) {
		elementsToCorr.get(obj)
	}

	/**
	 * Returns the existing {@link Corr} for {@code obj}, or creates and registers a new one.
	 *
	 * <p>The type of correspondence created depends on the metamodel package of {@code obj}:
	 * <ul>
	 *   <li>If {@code obj} is a {@code MyBag} instance (from either Bag1 or Bag2),
	 *       a {@link BasicElem} is created, because bags have a strict 1-to-1
	 *       correspondence (one Bag1 {@code MyBag} ↔ one Bag2 {@code MyBag}).</li>
	 *   <li>For all other element types (i.e. {@code Element} instances), a
	 *       {@link MultiElem} is created to accommodate the many-to-one grouping
	 *       required by the bag-compression semantics.</li>
	 * </ul>
	 * The new correspondence is appended to the {@link Transformation#getCorrespondences()}
	 * containment list and entered into the {@link #elementsToCorr} cache.</p>
	 *
	 * @param obj         the model element for which a correspondence is needed
	 * @param description the {@link Corr#getDesc()} label to assign (typically the {@link #ruleID})
	 * @return the found or newly created {@link Corr}
	 */
	def getOrCreateCorrModelElement(EObject obj, String description) {
		var Corr corr = obj.getCorrModelElem
		if (corr === null) {
			if(obj.eClass == Bags1Package::eINSTANCE.myBag || obj.eClass == Bags2Package::eINSTANCE.myBag) 
			// MyBag instances are 1-to-1: use the simpler BasicElem correspondence.
			corr = corrFactory.createBasicElem => [
				if (obj.eClass.EPackage instanceof Bags1Package)
					sourceElement = obj
				if (obj.eClass.EPackage instanceof Bags2Package)
					targetElement = obj
				desc = description
			]
			else
			// Element instances are N-to-1: use MultiElem to allow multiple Bag1
			// elements to share a single Bag2 element (same-value grouping).
			corr = corrFactory.createMultiElem => [
				if (obj.eClass.EPackage instanceof Bags1Package)
					sourceElements += obj
				if (obj.eClass.EPackage instanceof Bags2Package)
					targetElement = obj
				desc = description
			]
			(corrModel.contents.get(0) as Transformation).correspondences += corr
			elementsToCorr.put(corr)
		}
		return corr
	}
	
	/**
	 * Dispatch method that indexes a {@link MultiElem} correspondence into the cache.
	 *
	 * <p>Registers both the single target element and every source element so that
	 * any of them can be used as a look-up key.</p>
	 *
	 * @param m    the cache map (unused; present only to satisfy the Xtend dispatch signature)
	 * @param corr the {@link MultiElem} to index
	 */
	def protected dispatch put(Map<EObject, Corr> m, MultiElem corr) {
		elementsToCorr.put(corr.targetElement, corr)
		corr.sourceElements.forEach[elementsToCorr.put(it,corr)]
	}
	
	/**
	 * Dispatch method that indexes a {@link BasicElem} correspondence into the cache.
	 *
	 * <p>Registers both the source and target elements so that either can serve as
	 * a look-up key.</p>
	 *
	 * @param m    the cache map (unused; present only to satisfy the Xtend dispatch signature)
	 * @param corr the {@link BasicElem} to index
	 */
	def protected dispatch put(Map<EObject, Corr> m, BasicElem corr) {
		elementsToCorr.put(corr.sourceElement, corr)
		elementsToCorr.put(corr.targetElement, corr)
	}
		

	/**
	 * Creates a new Bag1 (source) model element of the given metamodel class.
	 *
	 * @param clazz the {@link EClass} descriptor (e.g. {@code Bags1Package.eINSTANCE.element})
	 * @return the newly created {@link EObject}
	 */
	def createSourceElement(EClass clazz) {
		sourceFactory.create(clazz)
	}
	
	/**
	 * Creates a new Bag2 (target) model element of the given metamodel class.
	 *
	 * @param clazz the {@link EClass} descriptor (e.g. {@code Bags2Package.eINSTANCE.element})
	 * @return the newly created {@link EObject}
	 */
	def createTargetElement(EClass clazz) {
		targetFactory.create(clazz)
	}
	
	/**
	 * Dispatch variant for {@link BasicElem}: ensures that the single source element
	 * of a 1-to-1 correspondence exists, creating it if necessary.
	 *
	 * <p>Returns a single-element list for interface uniformity with the
	 * {@link MultiElem} dispatch variant.</p>
	 *
	 * @param corr  the {@link BasicElem} correspondence
	 * @param clazz the {@link EClass} to instantiate if the source element is missing
	 * @return a list containing the (possibly newly created) source element
	 */
	def dispatch getOrCreateSourceElem(BasicElem corr, EClass clazz) {
		var source = <EObject>newArrayList
		if (corr.sourceElement === null){
			corr.sourceElement = createSourceElement(clazz)
			elementsToCorr.put(corr.sourceElement, corr)
		}
		source += corr.sourceElement
		return source
	}
	
	/**
	 * Dispatch variant for {@link MultiElem}: ensures that the source-elements list
	 * of a many-to-one correspondence is non-empty, creating the first element if
	 * the list is still empty.
	 *
	 * <p>This is a bootstrap helper used during backward propagation when a Bag2
	 * {@code Element} has been encountered for the first time and no Bag1 element
	 * has been assigned to the correspondence yet.</p>
	 *
	 * @param corr  the {@link MultiElem} correspondence
	 * @param clazz the {@link EClass} to instantiate for the first source element
	 * @return the (potentially modified) list of all source elements in this correspondence
	 */
	def dispatch getOrCreateSourceElem(MultiElem corr, EClass clazz) {
		if (corr.sourceElements.empty){
			corr.sourceElements += createSourceElement(clazz)
			elementsToCorr.put(corr.sourceElement, corr)
		}
		return corr.sourceElements
	}

	/**
	 * Ensures that the target element of the given correspondence exists, creating a
	 * new one of the specified class if it is still {@code null}.
	 *
	 * <p>Works for both {@link BasicElem} and {@link MultiElem} correspondences because
	 * both inherit {@code targetElement} from the base {@link Corr} type.</p>
	 *
	 * @param corr  the correspondence whose target element is required
	 * @param clazz the {@link EClass} to instantiate if the target element is missing
	 * @return the (possibly newly created) target {@link EObject}
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