package de.tbuchmann.bxtend.ast2dag.rules;

import ast.Expression
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Ast2dagFactory
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.BasicElem
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.Corr
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem
import java.util.ArrayList
import java.util.List
import org.eclipse.emf.common.util.URI
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource
import org.eclipse.emf.ecore.resource.ResourceSet
import org.eclipse.emf.ecore.resource.impl.ResourceSetImpl
import org.eclipse.emf.ecore.util.EcoreUtil

/**
 * Top-level orchestrator for the bidirectional, incremental AST ↔ DAG transformation.
 *
 * <p>This class is the single entry point used by the BXtend adapter.  It owns the three
 * EMF {@link Resource} instances (source AST model, target DAG model, and correspondence
 * model) and two ordered lists of {@link Elem2Elem} rules – one for each propagation
 * direction.  Calling {@link #sourceToTarget()} or {@link #targetToSource()} iterates the
 * respective rule list in order and then performs housekeeping deletions.
 *
 * <h2>Rule ordering</h2>
 * <ul>
 *   <li><b>Forward (AST → DAG):</b> bottom-up order.
 *       {@link Model2Model} first creates the DAG root; then
 *       {@link Variable2Variable} and {@link Number2Number} map the leaves; finally
 *       {@link Operator2Operator} maps the interior nodes and wires their children.
 *       Leaves must be present before operators reference them.</li>
 *   <li><b>Backward (DAG → AST):</b> top-down order.
 *       {@link Model2Model} first creates the AST root; then
 *       {@link Operator2Operator} expands the DAG operators into tree operators
 *       (including duplicate sub-trees for shared DAG nodes); finally
 *       {@link Variable2Variable} and {@link Number2Number} populate the leaves.</li>
 * </ul>
 *
 * <h2>Deletion handling</h2>
 * After each propagation pass, stale correspondence entries (those whose source or target
 * element has been deleted from the model) are detected and the orphaned counterparts are
 * removed from both the opposite model and the correspondence model.
 *
 * <h2>Constructors</h2>
 * Two constructors are provided:
 * <ol>
 *   <li>URI-based – loads resources from the file system via a fresh {@link ResourceSet};
 *       suitable for stand-alone execution.</li>
 *   <li>Resource-based – accepts already-loaded EMF {@link Resource} objects; used by the
 *       BXtend adapter during Benchmarx tests.</li>
 * </ol>
 */
class Ast2dagTransformation {
	
	/** EMF resource holding the source (ExpressionAST) model. */
	Resource sourceModel
	/** EMF resource holding the target (ExpressionDAG) model. */
	Resource targetModel
	/** EMF resource holding the correspondence model (corresp.ecore instances). */
	Resource corrModel
	
	/** Ordered list of rules applied during forward propagation (AST → DAG). */
	List<Elem2Elem> rulesFwd = new ArrayList<Elem2Elem>();
	/** Ordered list of rules applied during backward propagation (DAG → AST). */
	List<Elem2Elem> rulesBwd = new ArrayList<Elem2Elem>();
	
	/**
	 * URI-based constructor: loads the three models from the file system.
	 *
	 * <p>A single shared {@link ResourceSet} is used so that cross-resource references
	 * (e.g. from the correspondence model to the AST/DAG models) resolve correctly.
	 * If the correspondence resource is empty (first run), a fresh {@code Transformation}
	 * root element is added so that rules can immediately start appending correspondences.
	 *
	 * @param source        URI of the source (AST) model
	 * @param target        URI of the target (DAG) model
	 * @param correspondence URI of the correspondence model
	 */
	new(URI source, URI target, URI correspondence) {
		val ResourceSet set = new ResourceSetImpl();
		sourceModel = set.getResource(source, true)
		targetModel = set.getResource(target, true)
		corrModel = set.getResource(correspondence, true)
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(Ast2dagFactory.eINSTANCE.createTransformation)	
		}

		addRulesFwd
		addRulesBwd		
	}
	
	/**
	 * Resource-based constructor: accepts already-loaded EMF resources.
	 *
	 * <p>Used by the BXtend adapter during Benchmarx test execution where the framework
	 * manages the resource lifecycle.  Initialises the correspondence model root element
	 * if needed and registers all rules.
	 *
	 * @param source        already-loaded source (AST) EMF resource
	 * @param target        already-loaded target (DAG) EMF resource
	 * @param correspondence already-loaded correspondence EMF resource
	 */
	new(Resource source, Resource target, Resource correspondence) {
		sourceModel = source
		targetModel = target
		corrModel = correspondence
		
		if (corrModel.contents.size == 0) {
			corrModel.contents.add(Ast2dagFactory.eINSTANCE.createTransformation)	
		}
		
		addRulesFwd
		addRulesBwd
	}
	
	/**
	 * Populates {@link #rulesFwd} with the four forward rules in bottom-up order:
	 * Model → Variables → Numbers → Operators.
	 * Leaves are created before operator rules try to reference them.
	 */
	def private void addRulesFwd() {
		// Bottom-up: create the root, then leaves, then interior operator nodes.
		rulesFwd.add(new Model2Model(sourceModel, targetModel, corrModel))
		rulesFwd.add(new Variable2Variable(sourceModel, targetModel, corrModel))
		rulesFwd.add(new Number2Number(sourceModel, targetModel, corrModel))
		rulesFwd.add(new Operator2Operator(sourceModel, targetModel, corrModel))
	}
	
	/**
	 * Populates {@link #rulesBwd} with the four backward rules in top-down order:
	 * Model → Operators → Variables → Numbers.
	 * Operators must be reconstructed before leaves are assigned to their children.
	 */
	def private void addRulesBwd() {
		// Top-down: create the root, expand operators (building the tree structure),
		// then set leaf attributes.
		rulesBwd.add(new Model2Model(sourceModel, targetModel, corrModel))
		rulesBwd.add(new Operator2Operator(sourceModel, targetModel, corrModel))
		rulesBwd.add(new Variable2Variable(sourceModel, targetModel, corrModel))
		rulesBwd.add(new Number2Number(sourceModel, targetModel, corrModel))
	}
	
	/**
	 * Runs all forward rules (AST → DAG) in order and then removes any target elements
	 * that are no longer backed by a source element.
	 *
	 * <p>The method is a no-op when the source model is empty (nothing to propagate).
	 */
	def void sourceToTarget() {
		if (sourceModel.contents.size != 0)
		for (Elem2Elem e : rulesFwd) {
			e.sourceToTarget()
		}
		
		// Remove DAG elements whose AST counterparts have been deleted.
		deleteUnreferencedTargetElements
	}
	
	/**
	 * Runs all backward rules (DAG → AST) in order and then removes any source elements
	 * that are no longer backed by a target element.
	 *
	 * <p>The method is a no-op when the target model is empty (nothing to propagate).
	 */
	def void targetToSource() {		
		if (targetModel.contents.size != 0)
		for (Elem2Elem e: rulesBwd) {
			e.targetToSource()
		}
		
		// Remove AST elements whose DAG counterparts have been deleted.
		deleteUnreferencedSourceElements
	}
	
	/**
	 * Placeholder consistency check.
	 *
	 * @return always {@code true} – full consistency checking is not yet implemented
	 */
	def boolean checkCorrespondences() {
		true
	}
	
	/**
	 * Scans the correspondence model for entries whose source side is absent.
	 * <ul>
	 *   <li>A {@link BasicElem} entry is stale when its single {@code sourceElement} is {@code null}.</li>
	 *   <li>A {@link MultiElem} entry is stale when its {@code sourceElements} list is empty.</li>
	 * </ul>
	 *
	 * @return a lazy iterator of stale {@link Corr} entries (source deleted, target still alive)
	 */
	def detectSourceDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			(c instanceof BasicElem && c.sourceElement === null) || (c instanceof MultiElem && (c as MultiElem).sourceElements.empty)
		]
	}
	
	/**
	 * Scans the correspondence model for entries whose target side has been deleted,
	 * i.e. where {@code targetElement} is {@code null}.
	 *
	 * @return a lazy iterator of stale {@link Corr} entries (target deleted, source still alive)
	 */
	def detectTargetDeletions() {
		corrModel.allContents.filter(typeof(Corr)).filter[ c |
			c.targetElement === null 
		]
	}
	
	/**
	 * Deletes target (DAG) elements that have become dangling after a forward
	 * propagation pass removed their corresponding AST source elements.
	 *
	 * <p>Both the orphaned target element and the now-useless correspondence entry are
	 * queued for deletion, then removed together via {@link EcoreUtil#delete}.
	 */
	def deleteUnreferencedTargetElements(){
		val List<EObject> deletionList = newArrayList; 
		
		detectSourceDeletions().forEach[c |
			// Collect the orphaned DAG element and its correspondence entry.
			deletionList += c.targetElement
			deletionList += c
		]
		deletionList.forEach[e | EcoreUtil.delete(e, true)]
	}
	
	/**
	 * Deletes source (AST) elements that have become dangling after a backward
	 * propagation pass removed their corresponding DAG target elements.
	 *
	 * <p>For {@link MultiElem} correspondences all source elements are collected;
	 * for {@link BasicElem} correspondences only the single source element is collected.
	 * Additionally, any {@link Expression} node that has lost all structural links
	 * (no parent operator, no model reference) is detected and removed to prevent leaks.
	 */
	def deleteUnreferencedSourceElements(){
		val List<EObject> deletionList = newArrayList; 
		
		detectTargetDeletions().forEach[c |
			if(c instanceof MultiElem)
				c.sourceElements.forEach[ e | deletionList += e]
				
			else
				deletionList += c.sourceElement
			deletionList += c
		]
		// Also clean up any Expression nodes that are fully disconnected from the tree
		// (can happen when shared sub-trees are deduplicated and old copies remain).
		sourceModel.allContents.filter(typeof(Expression))
				.filter[e | e.leftInverse === null && e.rightInverse === null && e.model === null]
				.forEach[deletionList += it];
		deletionList.forEach[e | EcoreUtil.delete(e, true)]
	}
}