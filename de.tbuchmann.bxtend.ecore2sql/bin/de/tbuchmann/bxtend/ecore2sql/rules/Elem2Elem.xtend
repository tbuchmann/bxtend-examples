package de.tbuchmann.bxtend.ecore2sql.rules;

import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Corr
import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Ecore2sqlFactory
import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Transformation
import java.util.List
import java.util.Map
import org.eclipse.emf.ecore.EClass
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EcoreFactory
import org.eclipse.emf.ecore.EcorePackage
import org.eclipse.emf.ecore.resource.Resource
import sql.ModelElement
import sql.SqlFactory
import sql.SqlPackage

/**
 * Abstract base class for all bidirectional transformation rules in the Ecore-to-SQL BXtend transformation.
 *
 * <p>Each concrete subclass represents one rule of the transformation, implementing both the
 * forward direction ({@link #sourceToTarget}) and the backward direction ({@link #targetToSource}).
 * The BXtend approach is <em>correspondence-based</em>: every pair of corresponding source and target
 * elements is linked by a {@link Corr} object stored in a dedicated correspondence model.  This makes
 * the transformation <em>incremental</em> – on re-propagation only the elements that have actually
 * changed need to be updated, because the existing correspondences are reused.</p>
 *
 * <h3>Model roles</h3>
 * <ul>
 *   <li><b>sourceModel</b> – an Ecore model (instances of {@link org.eclipse.emf.ecore.EPackage},
 *       {@link org.eclipse.emf.ecore.EClass}, {@link org.eclipse.emf.ecore.EAttribute},
 *       {@link org.eclipse.emf.ecore.EReference}, etc.)</li>
 *   <li><b>targetModel</b> – a SQL model (instances of {@code sql.Schema}, {@code sql.Table},
 *       {@code sql.Column}, {@code sql.ForeignKey}, etc.)</li>
 *   <li><b>corrModel</b> – the correspondence model, a {@link Transformation} root containing
 *       a flat list of {@link Corr} objects, each linking exactly one source element to one
 *       target element together with a {@link Corr#desc descriptive tag} that identifies
 *       which rule created the correspondence.</li>
 * </ul>
 *
 * <h3>Helper protocol</h3>
 * <ul>
 *   <li>{@link #getOrCreateCorrModelElement} – looks up an existing {@link Corr} for a given model
 *       element or creates a fresh one when none exists yet.</li>
 *   <li>{@link #getOrCreateSourceElem} / {@link #getOrCreateTargetElem} – given a {@link Corr}, return
 *       the already-linked source/target element or instantiate a new one of the specified metaclass.</li>
 *   <li>{@link #addAnnotations} – attaches string-valued {@code Annotation} objects to SQL
 *       {@link ModelElement}s; these annotations carry the semantic metadata (e.g. {@code "class"},
 *       {@code "attribute"}, {@code "containment"}) that the backward direction uses to reconstruct
 *       the Ecore structure from SQL tables and columns.</li>
 * </ul>
 */
abstract class Elem2Elem {
	
	/** The Ecore source model resource. */
	protected Resource sourceModel
	/** The SQL target model resource. */
	protected Resource targetModel
	/** The correspondence model resource holding all {@link Corr} links. */
	protected Resource corrModel
	
	/** Factory used to create Ecore elements in the backward direction. */
	protected val sourceFactory = EcoreFactory::eINSTANCE
	/** Factory used to create SQL elements in the forward direction. */
	protected val targetFactory = SqlFactory::eINSTANCE
	/** Factory used to create new correspondence ({@link Corr}) objects. */
	protected val corrFactory = Ecore2sqlFactory::eINSTANCE
	/** The singleton Ecore metamodel package, used for metaclass look-ups. */
	protected val sourcePackage = EcorePackage::eINSTANCE
	/** The singleton SQL metamodel package, used for metaclass look-ups. */
	protected val targetPackage = SqlPackage::eINSTANCE
	
	/**
	 * Human-readable rule identifier stored in every {@link Corr#desc} created by this rule.
	 * Subclasses set this in their constructor, e.g. {@code "class2table"}.
	 */
	protected var String ruleID
	
	/** Legacy map kept for potential future optimisation (currently unused at runtime). */
	protected static Map<EObject, Corr> elementsToCorr = newHashMap
	
	/**
	 * Constructs an Elem2Elem rule wired to the three model resources.
	 *
	 * @param src  the Ecore source model resource
	 * @param trgt the SQL target model resource
	 * @param corr the correspondence model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		sourceModel = src
		targetModel = trgt
		corrModel = corr	
		ruleID = "base"
	}
	
	/**
	 * Forward propagation: transforms (a subset of) the source Ecore model into SQL elements.
	 * Subclasses override this to implement the rule-specific forward logic.
	 */
	def void sourceToTarget() {
	}
	
	/**
	 * Backward propagation: transforms (a subset of) the SQL target model back into Ecore elements.
	 * Subclasses override this to implement the rule-specific backward logic.
	 */
	def void targetToSource() {
	}
	
	/**
	 * Looks up the {@link Corr} object whose {@code sourceElement} or {@code targetElement}
	 * equals {@code obj}.
	 *
	 * @param obj any source or target model element
	 * @return the corresponding {@link Corr}, or {@code null} if none exists yet
	 */
	def getCorrModelElem(EObject obj) {
		(corrModel.contents?.get(0) as Transformation).correspondences.findFirst[corr |	corr.sourceElement == obj || corr.targetElement == obj]
	}

	/**
	 * Returns the existing {@link Corr} for {@code obj}, or creates and registers a new
	 * {@link de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.BasicElem BasicElem}
	 * correspondence if none exists yet.
	 *
	 * <p>The new correspondence is automatically wired to either the {@code sourceElement}
	 * or {@code targetElement} slot depending on the metamodel package of {@code obj}.</p>
	 *
	 * @param obj         the model element for which a correspondence is needed
	 * @param description a short label identifying the creating rule (stored in {@link Corr#desc})
	 * @return the (possibly newly created) {@link Corr} object
	 */
	def getOrCreateCorrModelElement(EObject obj, String description) {
		var Corr corr = obj.getCorrModelElem
		if (corr === null) {
			corr = corrFactory.createBasicElem => [
				if (obj.eClass.EPackage instanceof EcorePackage)
					sourceElement = obj
				if (obj.eClass.EPackage instanceof SqlPackage)
					targetElement = obj
				desc = description
			]
			(corrModel.contents.get(0) as Transformation).correspondences += corr	
		}
		return corr
	}
		
	/**
	 * Creates a new Ecore element of the given metaclass using the Ecore factory.
	 *
	 * @param clazz the {@link EClass} to instantiate
	 * @return a freshly created Ecore {@link EObject}
	 */
	def createSourceElement(EClass clazz) {
		sourceFactory.create(clazz)
	}
	
	/**
	 * Creates a new SQL element of the given metaclass using the SQL factory.
	 *
	 * @param clazz the {@link EClass} to instantiate
	 * @return a freshly created SQL {@link EObject}
	 */
	def createTargetElement(EClass clazz) {
		targetFactory.create(clazz)
	}
	
	/**
	 * Returns the source element linked by {@code corr}, creating and linking a new instance
	 * of {@code clazz} when the source slot is still empty.
	 *
	 * @param corr  the correspondence whose source element is needed
	 * @param clazz the Ecore metaclass to instantiate if no source element exists yet
	 * @return the existing or newly created source element
	 */
	def getOrCreateSourceElem(Corr corr, EClass clazz) {
		
		var EObject source  = corr.sourceElement
		if (corr.sourceElement === null){
			source = createSourceElement(clazz)
			corr.sourceElement = source
		}
		return source
	}

	/**
	 * Returns the target element linked by {@code corr}, creating and linking a new instance
	 * of {@code clazz} when the target slot is still empty.
	 *
	 * @param corr  the correspondence whose target element is needed
	 * @param clazz the SQL metaclass to instantiate if no target element exists yet
	 * @return the existing or newly created target element
	 */
	def getOrCreateTargetElem(Corr corr, EClass clazz) {
		var EObject target = corr.targetElement 
		if (target === null) {
			target = createTargetElement(clazz)
			corr.targetElement = target
		}
		return target
	}
	
	/**
	 * Adds the given string annotations to a SQL {@link ModelElement}, skipping any string
	 * that is already present.  The special strings {@code "unidirectional"} and
	 * {@code "bidirectional"} are treated as mutually exclusive: if the opposite annotation
	 * already exists it is updated in-place instead of adding a duplicate.
	 *
	 * <p>Annotations are the primary mechanism by which the SQL model encodes the semantic
	 * context of each element so that the backward transformation can reconstruct the
	 * appropriate Ecore construct (e.g. distinguish a column that came from an EAttribute
	 * from one that came from an EReference).</p>
	 *
	 * @param owner   the SQL element to annotate
	 * @param strings the list of annotation strings to attach
	 */	
	def void addAnnotations(ModelElement owner, List<String> strings) {
		strings.forEach[ s |
			switch(s) {
				case "unidirectional": {
					val annot = owner.ownedAnnotations.findFirst[annotation == "bidirectional"]
					if(annot !== null) annot.annotation = s
				}
				case "bidirectional": {
					val annot = owner.ownedAnnotations.findFirst[annotation == "unidirectional"]
					if(annot !== null) annot.annotation = s
				}
			}
			if (owner.ownedAnnotations.findFirst[a | a.annotation.equals(s)] === null) {
				val an = targetFactory.createAnnotation => [annotation = s]
				owner.ownedAnnotations += an
			}
		]
	}	
}