package de.tbuchmann.bxtend.ast2dag.rules

import ast.Operator
import dag.DagPackage
import dag.Model
import dag.Variable
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource

/**
 * Transformation rule that synchronises {@code Variable} leaf nodes between the AST and DAG.
 *
 * <p>A {@code Variable} is an operand leaf whose sole data attribute is its string {@code name}.
 * Because identical variable names in the AST are deduplicated into a single DAG node, the
 * correspondence for a {@code dag.Variable} is a {@link MultiElem} that may reference several
 * {@code ast.Variable} instances (one for each occurrence in the expression tree).
 *
 * <h2>Deduplication key</h2>
 * Two variables are considered <em>equal</em> and thus share a DAG node if and only if their
 * {@code name} attributes are equal.  The helper {@link #findTargetElem} searches the DAG
 * model's flat expression list for a {@code dag.Variable} with the matching name.
 *
 * <h2>Forward pass (AST → DAG)</h2>
 * <ol>
 *   <li>If the AST variable has no correspondence yet, {@link #addToTargetElem} either finds
 *       an existing DAG variable with the same name or creates a new one, then links the AST
 *       variable into the {@link MultiElem}.sourceElements list.</li>
 *   <li>If a correspondence exists and <em>all</em> source elements in that correspondence
 *       agree on the current name (i.e. the name has not been changed), the rule checks
 *       whether a different DAG variable with the matching name already exists; if so, the
 *       AST variable migrates to that correspondence. Otherwise it updates the DAG name
 *       in-place.</li>
 *   <li>If the name of the DAG variable no longer matches the AST variable, the AST variable
 *       is removed from the current correspondence and re-added via {@link #addToTargetElem}
 *       to find or create the correct target node.</li>
 * </ol>
 *
 * <h2>Backward pass (DAG → AST)</h2>
 * For each {@code dag.Variable}:
 * <ul>
 *   <li>Variables with no parent operators (i.e. not referenced from any {@code Operator.left}
 *       or {@code Operator.right}) are mapped to a single AST variable owned directly by the
 *       {@code ast.Model}.</li>
 *   <li>For each parent operator that references this variable as a <em>left</em> child, one
 *       AST variable is created (or reused) per corresponding AST operator copy, identified by
 *       the predicate {@code e.leftInverse == leftParent}.</li>
 *   <li>Symmetrically for parent operators that reference this variable as a <em>right</em> child.</li>
 * </ul>
 */
class Variable2Variable extends Elem2Elem {

	/**
	 * Constructs the rule and sets the rule identifier to {@code "variable2variable"}.
	 *
	 * @param src  the source (AST) model resource
	 * @param trgt the target (DAG) model resource
	 * @param corr the correspondence model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "variable2variable"
	}
	
	/**
	 * Forward pass: iterates all {@code ast.Variable} nodes and ensures each is
	 * represented in the DAG by a (possibly shared) {@code dag.Variable} node with the
	 * same {@code name}.
	 */
	override sourceToTarget() {
		sourceModel.allContents.filter(typeof(ast.Variable))
			.forEach[v |
				val corr = v.getCorrModelElem  as MultiElem
				if(corr === null) {
					// No correspondence yet – create or reuse a DAG variable.
					v.addToTargetElem
				} else {
					val t = corr.targetElement as Variable
					if(corr.sourceElements.forall[it instanceof ast.Variable && (it as ast.Variable).name == v.name]) {
						// All sources agree on the name; check whether another DAG variable
						// with this name already exists and should absorb this source element.
						val newTarget = v.findTargetElem
						if(newTarget !== null)
							(newTarget.getCorrModelElem as MultiElem).sourceElements += v
						else
							t.name = v.name
					}
					// If the DAG variable name diverges from the AST variable name,
					// detach this source element and re-link it to the correct target.
					if(t.name != v.name) {
						corr.sourceElements -= v
						v.addToTargetElem
					}
				}
			]
	}
	
	/**
	 * Backward pass: iterates all {@code dag.Variable} nodes and reconstructs the
	 * corresponding AST variable copies, one per parent operator reference.
	 */
	override targetToSource() {
		targetModel.allContents.filter(typeof(Variable)).forEach[v |
			// Case 1: variable is not used by any operator → it maps to a top-level
			// AST variable owned directly by the model.
			if (v.leftInverse.empty && v.rightInverse.empty) {
				val corr = v.getOrCreateCorrModelElement(ruleID) as MultiElem;
				val src = corr.getOrCreateSourceElem(sourcePackage.variable, [true]) as ast.Variable;
				src.name = v.name;
				src.model = v.model.corrModelElem.sourceElement as ast.Model;
			}
			
			// Case 2: variable is the left child of one or more operators.
			// For each DAG operator copy we need a distinct AST variable.
			for (dag.Operator left : v.leftInverse) {
				val parentCorr = left.getOrCreateCorrModelElement(ruleID) as MultiElem;
				for (EObject leftParent : parentCorr.sourceElements) {
					val corr = v.getOrCreateCorrModelElement(ruleID) as MultiElem;
					val src = corr.getOrCreateSourceElem(sourcePackage.variable,
							[e | (e as ast.Variable).leftInverse == leftParent])
							as ast.Variable;
					src.name = v.name;
					src.leftInverse = leftParent as Operator;
				}
			}
			
			// Case 3: variable is the right child of one or more operators.
			for (dag.Operator right : v.rightInverse) {
				val parentCorr = right.getOrCreateCorrModelElement(ruleID) as MultiElem;
				for (EObject rightParent : parentCorr.sourceElements) {
					val corr = v.getOrCreateCorrModelElement(ruleID) as MultiElem;
					val src = corr.getOrCreateSourceElem(sourcePackage.variable,
							[e | (e as ast.Variable).rightInverse == rightParent])
							as ast.Variable;
					src.name = v.name;
					src.rightInverse = rightParent as Operator;
				}
			}
		]
	}
	
	/**
	 * Finds or creates a {@code dag.Variable} for the given AST variable and registers the
	 * link in the correspondence model.
	 *
	 * <p>If a {@code dag.Variable} with the same name already exists in the DAG model, the
	 * AST variable is added to that node's {@link MultiElem}.sourceElements (sharing the
	 * existing DAG node).  Otherwise, a new {@code dag.Variable} is created and placed in
	 * the DAG model's {@code exprs} list.
	 *
	 * @param e the AST variable to map into the DAG
	 */
	def private addToTargetElem(ast.Variable e) {
		var newTarget = e.findTargetElem
		if(newTarget === null) {
			newTarget = createTargetElement(DagPackage.eINSTANCE.variable) as Variable
		}
		val newCorr = newTarget.getOrCreateCorrModelElement(ruleID) as MultiElem
		newCorr.sourceElements += e
		newTarget.name = e.name
		newTarget.model = e.model.getCorrModelElem.targetElement as Model
		elementsToCorr.put(newCorr)
	}
	
	/**
	 * Searches the DAG model's flat expression list for an existing {@code dag.Variable}
	 * whose {@code name} matches that of the given AST variable.
	 *
	 * @param e the AST variable whose name is used as the search key
	 * @return the matching {@code dag.Variable}, or {@code null} if none exists
	 */
	def private findTargetElem(ast.Variable e) {
		(e.model.getCorrModelElem.targetElement as Model).exprs.filter(typeof(Variable)).findFirst[it.name == e.name]
	}
}