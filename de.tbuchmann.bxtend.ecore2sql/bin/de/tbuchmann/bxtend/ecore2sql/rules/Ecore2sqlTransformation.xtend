package de.tbuchmann.bxtend.ecore2sql.rules;

import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Corr
import de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Ecore2sqlFactory
import java.util.ArrayList
import java.util.List
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.EReference
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.util.EcoreUtil
import sql.Column
import sql.ForeignKey
import sql.Schema
import sql.Table

/**
 * Top-level orchestrator for the bidirectional, incremental Ecore-to-SQL transformation
 * implemented with the <em>BXtend</em> framework.
 *
 * <h3>Responsibility</h3>
 * <p>This class ties together all individual transformation rules (each a subclass of
 * {@link Elem2Elem}) into an ordered pipeline and exposes the two propagation directions
 * as the public API consumed by the benchmark harness ({@code BXtendEcore2SQL}):</p>
 * <ul>
 *   <li>{@link #sourceToTarget()} – propagates changes from the Ecore model to the SQL model.</li>
 *   <li>{@link #targetToSource()} – propagates changes from the SQL model back to the Ecore model.</li>
 * </ul>
 *
 * <h3>Rule pipeline</h3>
 * <p>Rules are executed in strict order.  Each rule depends on the correspondences established
 * by the preceding rule(s), so the order is not arbitrary:</p>
 * <ol>
 *   <li>{@link Package2Schema} – Ecore package → SQL schema (must run first; all other rules
 *       rely on the schema element).</li>
 *   <li>{@link Class2Table} – Ecore class → SQL table (must run before inheritance and
 *       structural-feature rules).</li>
 *   <li>{@link Generalization2Relation} – Ecore generalisation → foreign-key hierarchy
 *       (requires class tables from step 2).</li>
 *   <li>{@link Attribute2Attribute} – Ecore attribute → SQL column or auxiliary table
 *       (requires class tables from step 2).</li>
 *   <li>{@link EReference2Relation} – Ecore reference → FK column or relation table
 *       (requires class tables from step 2).</li>
 * </ol>
 *
 * <h3>Correspondence model initialisation</h3>
 * <p>Both constructors ensure that the correspondence resource contains a root
 * {@link de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Transformation Transformation}
 * object before any rule runs.  This object is the container for all
 * {@link de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Corr Corr} links created
 * during transformation.</p>
 *
 * <h3>Incremental deletion handling</h3>
 * <p>After the forward or backward rule pipeline finishes, a clean-up pass removes elements
 * that have become orphaned (i.e. their corresponding element on the other side was deleted
 * by the user and the correspondence now has a {@code null} slot):</p>
 * <ul>
 *   <li>{@link #deleteUnreferencedTargetElements()} – called at the end of
 *       {@link #sourceToTarget()}; removes SQL elements whose source Ecore element is gone,
 *       together with any dangling foreign keys.</li>
 *   <li>{@link #deleteUnreferencedSourceElements()} – called at the end of
 *       {@link #targetToSource()}; removes Ecore elements whose SQL element is gone, including
 *       any {@link EReference#getEOpposite() opposite} references.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * // Create the transformation wired to EMF Resources
 * val t = new Ecore2sqlTransformation(sourceResource, targetResource, corrResource);
 * // Initial batch forward propagation
 * t.sourceToTarget();
 * // Later, after editing the source model
 * t.sourceToTarget();
 * // Or after editing the target model
 * t.targetToSource();
 * </pre>
 */
class Ecore2sqlTransformation {
	
	/** The Ecore source model resource. */
	Resource sourceModel
	/** The SQL target model resource. */
	Resource targetModel
	/** The correspondence model resource. */
	Resource corrModel
	
	/**
	 * The ordered list of transformation rules.  Rules are applied left-to-right
	 * in both propagation directions.
	 */
	List<Elem2Elem> rules = new ArrayList<Elem2Elem>();		
	
	/**
	 * Constructs the transformation by loading the three models from the given URIs.
	 * If the correspondence resource is empty a fresh
	 * {@link de.tbuchmann.bxtend.ecore2sql.correspondence.ecore2sql.Transformation Transformation}
	 * root object is created automatically.
	 *
	 * @param source         URI of the Ecore source model (e.g. {@code "ecore.ecore"})
	 * @param target         URI of the SQL target model (e.g. {@code "sql.xmi"})
	 * @param correspondence URI of the correspondence model (e.g. {@code "corr.xmi"})
	 */
	new(URI source, URI target, URI correspondence) {
		val ResourceSet set = new ResourceSetImpl();
		sourceModel = set.getResource(source, true)
		targetModel = set.getResource(target, true)
		corrModel = set.getResource(correspondence, true)
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(Ecore2sqlFactory.eINSTANCE.createTransformation)	
		}

		// TODO: add your rules in the proper order to the 'rules' List		
		rules.add(new Package2Schema(sourceModel, targetModel, corrModel))	
		rules.add(new Class2Table(sourceModel, targetModel, corrModel))
		rules.add(new Generalization2Relation(sourceModel, targetModel, corrModel))
		rules.add(new Attribute2Attribute(sourceModel, targetModel, corrModel))
		rules.add(new EReference2Relation(sourceModel, targetModel, corrModel))
	}
	
	/**
	 * Constructs the transformation from already-loaded EMF {@link Resource}s.
	 * This constructor is used by the benchmark harness ({@code BXtendEcore2SQL})
	 * which manages resource loading itself.
	 *
	 * @param source         the Ecore source model resource
	 * @param target         the SQL target model resource
	 * @param correspondence the correspondence model resource
	 */
	new(Resource source, Resource target, Resource correspondence) {		
		sourceModel = source
		targetModel = target
		corrModel = correspondence
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(Ecore2sqlFactory.eINSTANCE.createTransformation)	
		}
		
		// TODO: add your rules in the proper order to the 'rules' List
		rules.add(new Package2Schema(sourceModel, targetModel, corrModel))
		rules.add(new Class2Table(sourceModel, targetModel, corrModel))
		rules.add(new Generalization2Relation(sourceModel, targetModel, corrModel))
		rules.add(new Attribute2Attribute(sourceModel, targetModel, corrModel))
		rules.add(new EReference2Relation(sourceModel, targetModel, corrModel))
	}
	
	/**
	 * Propagates changes from the Ecore source model to the SQL target model.
	 *
	 * <p>Runs each rule's {@link Elem2Elem#sourceToTarget()} method in pipeline order,
	 * then calls {@link #deleteUnreferencedTargetElements()} to purge SQL elements whose
	 * Ecore counterpart has been deleted.</p>
	 *
	 * <p>If the source model is empty (e.g. during initialisation) the pipeline is skipped
	 * entirely to avoid null-pointer situations in the rules.</p>
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
	 * Propagates changes from the SQL target model back to the Ecore source model.
	 *
	 * <p>Runs each rule's {@link Elem2Elem#targetToSource()} method in pipeline order,
	 * then calls {@link #deleteUnreferencedSourceElements()} to purge Ecore elements whose
	 * SQL counterpart has been deleted.</p>
	 *
	 * <p>If the target model is empty the pipeline is skipped.</p>
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
	 * Propagates and reconciles concurrent edits made to both the Ecore source model and the
	 * SQL target model since the last synchronisation point.
	 *
	 * <p>Runs each rule's {@link Elem2Elem#synch()} in the same pipeline order as
	 * {@link #sourceToTarget()}/{@link #targetToSource()} (later rules depend on correspondences
	 * established by earlier ones), then cleans up dangling correspondences on both sides.</p>
	 */
	def void synch() {
		for (Elem2Elem e : rules)
			e.synch()

		// handle deletions
		deleteUnreferencedSourceElements
		deleteUnreferencedTargetElements
	}

	/**
	 * Placeholder consistency check; currently always returns {@code true}.
	 * May be extended in the future to verify that all correspondences are valid.
	 *
	 * @return {@code true}
	 */
	def boolean checkCorrespondences() {
		true
	}
	
	/**
	 * Returns all {@link Corr} objects in the correspondence model whose
	 * {@link Corr#getSourceElement() sourceElement} is {@code null}.
	 *
	 * <p>A {@code null} source element indicates that the user deleted the corresponding
	 * Ecore element, so the linked SQL element should also be removed from the target model.</p>
	 *
	 * @return an iterator over orphaned {@link Corr} objects (source-side deletions)
	 */
	def detectSourceDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			c.sourceElement === null
		]
	}
	
	/**
	 * Returns all {@link Corr} objects in the correspondence model whose
	 * {@link Corr#getTargetElement() targetElement} is {@code null}.
	 *
	 * <p>A {@code null} target element indicates that the user deleted the corresponding
	 * SQL element, so the linked Ecore element should also be removed from the source model.</p>
	 *
	 * @return an iterator over orphaned {@link Corr} objects (target-side deletions)
	 */
	def detectTargetDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			c.targetElement === null 
		]
	}
	
	/**
	 * Removes all SQL elements (and their dependent foreign keys) that have become orphaned
	 * because their corresponding Ecore source element was deleted.
	 *
	 * <p>The deletion algorithm:</p>
	 * <ol>
	 *   <li>For each {@link Corr} with a {@code null} source element:
	 *     <ul>
	 *       <li>If the target is a {@link Column}: collect all keys (foreign keys) on that
	 *           column for deletion.</li>
	 *       <li>If the target is a {@link Table}: collect all incoming foreign keys
	 *           ({@code referencingForeignKeys}) and the corresponding column in the
	 *           {@code EObject} sentinel table.</li>
	 *       <li>Add the target element and the {@link Corr} itself to the deletion list.</li>
	 *     </ul>
	 *   </li>
	 *   <li>Collect any remaining {@link ForeignKey}s that have a {@code null} column or
	 *       referenced table (dangling keys from prior partial deletions).</li>
	 *   <li>Delete all collected elements via {@link EcoreUtil#delete}.</li>
	 * </ol>
	 */
	def deleteUnreferencedTargetElements(){
		val List<EObject> deletionList = newArrayList; 
		val s = (targetModel.contents.get(0) as Schema)
		val eot = s.ownedTables.findFirst[t | t.name.equals("EObject")]
		detectSourceDeletions().forEach[c |
			if(c.targetElement instanceof Column) {
				val col = c.targetElement as Column
				deletionList += col.keys
			}
			if(c.targetElement instanceof Table) {
				val tab = c.targetElement as Table
				deletionList += tab.referencingForeignKeys
				deletionList += tab.referencingForeignKeys.findFirst[owningTable == eot]?.column
			}
			// TODO: add handling of contained and referenced Elements here if appropriate			
			// end
			deletionList += c.targetElement
			deletionList += c
		]
		deletionList += targetModel.allContents.filter(typeof(ForeignKey)).filter[k | k.column === null || k.referencedTable === null].toList
		deletionList.forEach[e | if(e!== null)EcoreUtil.delete(e, true)]
	}
	
	/**
	 * Removes all Ecore elements that have become orphaned because their corresponding
	 * SQL target element was deleted.
	 *
	 * <p>Special care is taken for {@link EReference}s that have an
	 * {@link EReference#getEOpposite() EOpposite}: the opposite reference is also
	 * collected for deletion so that the Ecore model remains consistent.</p>
	 *
	 * <p>After collection all elements are deleted via {@link EcoreUtil#delete}.</p>
	 */
	def deleteUnreferencedSourceElements(){
		val List<EObject> deletionList = newArrayList; 
		
		detectTargetDeletions().forEach[c |
			val source = c.sourceElement
			if(source instanceof EReference) {
				if(source.EOpposite !== null) {
					deletionList += source.EOpposite
				}
			}
			// TODO: add handling of contained and referenced Elements here if appropriate
			
			// end
			deletionList += c.sourceElement
			deletionList += c
		]
		// A corr's sourceElement can already be null here too (e.g. it was already
		// dangling on the source side from an earlier partial deletion, or synch()'s
		// re-run of sourceToTarget() churned through a redundant correspondence for a
		// bidirectional cross-reference); guard against EcoreUtil.delete(null, ...),
		// mirroring the equivalent guard in deleteUnreferencedTargetElements().
		deletionList.forEach[e | if (e !== null) EcoreUtil.delete(e, true)]
	}
}