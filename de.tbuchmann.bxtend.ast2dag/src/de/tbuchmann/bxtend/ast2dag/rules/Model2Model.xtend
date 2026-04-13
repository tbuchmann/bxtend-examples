package de.tbuchmann.bxtend.ast2dag.rules

import ast.Model
import org.eclipse.emf.ecore.resource.Resource

/**
 * Transformation rule that synchronises the root {@code Model} elements of both metamodels.
 *
 * <p>This is always the <em>first</em> rule to run in both the forward and backward
 * propagation passes because all other rules require the root {@code Model} objects to exist
 * before they can assign containment or cross-reference relationships.
 *
 * <h2>Correspondence type</h2>
 * The root models are linked by a {@link de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.BasicElem}
 * correspondence (1-to-1): one AST {@code Model} ↔ one DAG {@code Model}.
 *
 * <h2>Forward (AST → DAG)</h2>
 * For each AST {@code Model} found in the source resource, the rule ensures a DAG {@code Model}
 * exists in the target resource.  Idempotent: if a correspondence already exists (incremental run),
 * the target element is reused.
 *
 * <h2>Backward (DAG → AST)</h2>
 * For each DAG {@code Model} found in the target resource, the rule ensures an AST {@code Model}
 * exists in the source resource.  Idempotent in the same way.
 */
class Model2Model extends Elem2Elem {

	/**
	 * Constructs the rule and sets the rule identifier to {@code "root"}.
	 *
	 * @param src  the source (AST) model resource
	 * @param trgt the target (DAG) model resource
	 * @param corr the correspondence model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "root"
	}
	
	/**
	 * Forward pass: for every AST {@code Model} root, creates (or reuses) a corresponding
	 * DAG {@code Model} root and adds it to the target resource.
	 */
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(Model))
			.forEach[m |
				val corr = m.getOrCreateCorrModelElement(ruleID)
				val target = corr.getOrCreateTargetElem(targetPackage.model)
				targetModel.contents += target
			]
	}
	
	/**
	 * Backward pass: for every DAG {@code Model} root, creates (or reuses) a corresponding
	 * AST {@code Model} root and adds it to the source resource.
	 */
	override targetToSource() {
		targetModel.allContents.filter(typeof(dag.Model))
			.forEach[m |
				val corr = m.getOrCreateCorrModelElement(ruleID)
				val source = corr.getOrCreateSourceElem(sourcePackage.model)
				sourceModel.contents += source
			]
	}
}