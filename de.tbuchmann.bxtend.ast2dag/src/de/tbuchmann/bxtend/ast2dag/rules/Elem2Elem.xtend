package de.tbuchmann.bxtend.ast2dag.rules;

import ast.AstFactory
import ast.AstPackage
import dag.DagFactory
import dag.DagPackage
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Ast2dagFactory
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.BasicElem
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Transformation
import java.util.Map
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import java.util.function.Predicate

/**
 * Abstract base class for all BXtend transformation rules in the AST-to-DAG transformation.
 *
 * <p>Each concrete subclass implements one rule that synchronises a specific pair of element
 * types between the source (ExpressionAST) and target (ExpressionDAG) models.  Both
 * synchronisation directions are represented as overridable methods:
 * <ul>
 *   <li>{@link #sourceToTarget()} – propagates changes from the AST to the DAG</li>
 *   <li>{@link #targetToSource()} – propagates changes from the DAG back to the AST</li>
 * </ul>
 *
 * <h2>Correspondence model</h2>
 * The key challenge of the AST ↔ DAG transformation is the <em>structural mismatch</em>:
 * a single DAG node can be shared by multiple AST nodes (because identical sub-expressions
 * in the tree are deduplicated into one DAG node).  The correspondence model captures this
 * with two types:
 * <ul>
 *   <li>{@link BasicElem} – 1-to-1 correspondence, used only for Model root nodes</li>
 *   <li>{@link MultiElem} – many-to-1 correspondence, linking one target element to
 *       potentially many source elements (the duplicate AST sub-trees that share one
 *       DAG node)</li>
 * </ul>
 *
 * <p>The static {@link #elementsToCorr} map provides an O(1) lookup from any model
 * element (source or target) to its correspondence entry.  It is shared across all
 * rule instances for a given transformation run so that rules can look up links created
 * by other rules.
 */
abstract class Elem2Elem {
	
	/** The EMF resource containing the source (AST) model. */
	protected Resource sourceModel
	/** The EMF resource containing the target (DAG) model. */
	protected Resource targetModel
	/** The EMF resource containing the correspondence model. */
	protected Resource corrModel
	
	/** Factory for instantiating AST model elements. */
	protected val sourceFactory = AstFactory::eINSTANCE
	/** Factory for instantiating DAG model elements. */
	protected val targetFactory = DagFactory::eINSTANCE
	/** Factory for instantiating correspondence elements. */
	protected val corrFactory = Ast2dagFactory::eINSTANCE
	/** Reflective package for type-safe EClass references on the AST side. */
	protected val sourcePackage = AstPackage::eINSTANCE
	/** Reflective package for type-safe EClass references on the DAG side. */
	protected val targetPackage = DagPackage::eINSTANCE
	
	/**
	 * Identifies the rule within the correspondence model (stored in {@code Corr.desc}).
	 * Concrete subclasses set this in their constructors, e.g. {@code "variable2variable"}.
	 */
	protected var String ruleID
	
	/**
	 * Shared O(1) index: maps every model element (AST or DAG) to its {@link Corr}
	 * correspondence entry.  Declared {@code static} so that all rule instances created
	 * for the same transformation session share the same map.
	 */
	protected static Map<EObject, Corr> elementsToCorr = newHashMap
	
	/**
	 * Creates the rule and wires it to the three EMF resources.
	 * Also populates {@link #elementsToCorr} from any correspondences already persisted
	 * in the correspondence model, enabling incremental (alignment-based) operation.
	 *
	 * @param src  the source (AST) model resource
	 * @param trgt the target (DAG) model resource
	 * @param corr the correspondence model resource (must already contain a
	 *             {@link Transformation} root element)
	 */
	new(Resource src, Resource trgt, Resource corr) {
		sourceModel = src
		targetModel = trgt
		corrModel = corr	
		ruleID = "base"
		// Re-index all existing correspondences so incremental runs can look them up.
		(corrModel.contents.get(0) as Transformation).correspondences.forEach[c | 
			elementsToCorr.put(c)
		]
	}
	
	/**
	 * Propagates changes from the source (AST) model to the target (DAG) model.
	 * Subclasses override this method to implement their specific forward rule.
	 * The default implementation is a no-op.
	 */
	def void sourceToTarget() {
	}
	
	/**
	 * Propagates changes from the target (DAG) model to the source (AST) model.
	 * Subclasses override this method to implement their specific backward rule.
	 * The default implementation is a no-op.
	 */
	def void targetToSource() {
	}
	
	/**
	 * Looks up the correspondence entry for the given model element.
	 *
	 * @param obj any AST or DAG element
	 * @return the {@link Corr} that contains {@code obj}, or {@code null} if none exists yet
	 */
	def getCorrModelElem(EObject obj) {
		elementsToCorr.get(obj)
	}

	/**
	 * Returns the correspondence entry for {@code obj}, creating a new one if it does
	 * not already exist and registering it in the correspondence model.
	 *
	 * <p>The type of the new correspondence depends on the element:
	 * <ul>
	 *   <li>{@link BasicElem} is created for {@code Model} root elements (1-to-1).</li>
	 *   <li>{@link MultiElem} is created for all other elements (many-to-1), because
	 *       AST leaves and operators can be shared in the DAG.</li>
	 * </ul>
	 *
	 * @param obj         the element to look up or register
	 * @param description a human-readable label stored in {@link Corr#desc}, typically
	 *                    the rule ID of the calling rule
	 * @return the existing or newly created {@link Corr} entry
	 */
	def getOrCreateCorrModelElement(EObject obj, String description) {
		var Corr corr = obj.getCorrModelElem
		if (corr === null) {
			// Model root elements use a simple 1-to-1 BasicElem correspondence.
			if(obj.eClass == AstPackage::eINSTANCE.model || obj.eClass == DagPackage::eINSTANCE.model) 
			corr = corrFactory.createBasicElem => [
				if (obj.eClass.EPackage instanceof AstPackage)
					sourceElement = obj
				if (obj.eClass.EPackage instanceof DagPackage)
					targetElement = obj
				desc = description
			]
			else
			// All other elements use a many-to-1 MultiElem, because multiple AST nodes
			// can share a single DAG node.
			corr = corrFactory.createMultiElem => [
				if (obj.eClass.EPackage instanceof AstPackage)
					sourceElements += obj
				if (obj.eClass.EPackage instanceof DagPackage)
					targetElement = obj
				desc = description
			]
			(corrModel.contents.get(0) as Transformation).correspondences += corr
			elementsToCorr.put(corr)
		}
		return corr
	}
	
	/**
	 * Indexes a {@link MultiElem} correspondence into {@link #elementsToCorr}.
	 * Registers the target element and all source elements individually so that
	 * lookups work in both directions.
	 */	
	def protected dispatch put(Map<EObject, Corr> m, MultiElem corr) {
		elementsToCorr.put(corr.targetElement, corr)
		corr.sourceElements.forEach[elementsToCorr.put(it,corr)]
	}
	
	/**
	 * Indexes a {@link BasicElem} correspondence into {@link #elementsToCorr}.
	 * Registers both the single source element and the target element.
	 */
	def protected dispatch put(Map<EObject, Corr> m, BasicElem corr) {
		elementsToCorr.put(corr.sourceElement, corr)
		elementsToCorr.put(corr.targetElement, corr)
	}

	/**
	 * Creates a new AST (source) element of the given type using the AST factory.
	 *
	 * @param clazz the {@link EClass} descriptor for the element to create
	 * @return a freshly instantiated AST {@link EObject}
	 */
	def createSourceElement(EClass clazz) {
		sourceFactory.create(clazz)
	}
	
	/**
	 * Creates a new DAG (target) element of the given type using the DAG factory.
	 *
	 * @param clazz the {@link EClass} descriptor for the element to create
	 * @return a freshly instantiated DAG {@link EObject}
	 */
	def createTargetElement(EClass clazz) {
		targetFactory.create(clazz)
	}
	
	/**
	 * Returns the source element held in a {@link BasicElem} correspondence, creating
	 * it (and registering it) if it does not yet exist.
	 * Wrapped in a single-element list so callers can treat it uniformly with the
	 * {@link MultiElem} overload.
	 *
	 * @param corr  the 1-to-1 correspondence entry
	 * @param clazz the EClass to instantiate if no source element exists yet
	 * @return a list containing the (possibly new) source element
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
	 * Returns all source elements held in a {@link MultiElem} correspondence, creating
	 * the first one (and registering it) if the list is currently empty.
	 *
	 * @param corr  the many-to-1 correspondence entry
	 * @param clazz the EClass to instantiate when the source list is empty
	 * @return the live {@code sourceElements} list of the correspondence
	 */
	def dispatch getOrCreateSourceElem(MultiElem corr, EClass clazz) {
		if (corr.sourceElements.empty){
			corr.sourceElements += createSourceElement(clazz)
			elementsToCorr.put(corr.sourceElement, corr)
		}
		return corr.sourceElements
	}
	
	/**
	 * Finds the specific source element in a {@link MultiElem} correspondence that
	 * satisfies the given predicate, creating it if it does not exist yet.
	 * <p>This overload is used during backward propagation when multiple AST copies
	 * of the same shared DAG node must be distinguished by their structural context
	 * (e.g. which AST {@code Operator} is their parent).
	 *
	 * @param corr      the many-to-1 correspondence entry
	 * @param clazz     the EClass to instantiate if no matching element exists
	 * @param predicate a filter that identifies the desired element among the existing ones
	 * @return the matching (or newly created) source element
	 */
	def getOrCreateSourceElem(MultiElem corr, EClass clazz, Predicate<EObject> predicate) {
		var EObject source = corr.sourceElements.findFirst(predicate)
		if (source === null){
			source = createSourceElement(clazz)
			corr.sourceElements += source
			elementsToCorr.put(corr.sourceElement, corr)
		}
		return source
	}

	/**
	 * Returns the target (DAG) element held in the given correspondence, creating it
	 * (and registering it) if it does not yet exist.
	 *
	 * @param corr  any correspondence entry ({@link BasicElem} or {@link MultiElem})
	 * @param clazz the EClass to instantiate if no target element exists yet
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