package de.tbuchmann.bxtend.ast2dag.rules

import dag.DagPackage
import dag.Model
import dag.Operator
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem
import org.eclipse.emf.ecore.EObject
import org.eclipse.emf.ecore.resource.Resource

/**
 * Transformation rule that synchronises {@code Number} leaf nodes between the AST and DAG.
 *
 * <p>A {@code Number} is an operand leaf whose sole data attribute is its integer {@code value}.
 * Like {@link Variable2Variable}, this rule handles the DAG's <em>deduplication</em> semantics:
 * multiple {@code ast.Number} nodes with the same integer value may share a single
 * {@code dag.Number} node, so the correspondence is always a {@link MultiElem} (many-to-1).
 *
 * <h2>Deduplication key</h2>
 * Two number nodes are considered <em>structurally equal</em> if their {@code value} attributes
 * are equal.  The private helper {@link #findTargetElem} searches the DAG model's flat expression
 * list for an existing {@code dag.Number} with the matching value.
 *
 * <h2>Forward pass (AST → DAG)</h2>
 * <ol>
 *   <li>If the AST number has no correspondence yet, {@link #addToTargetElem} either finds an
 *       existing DAG number with the same value or creates a new one, then adds the AST number
 *       to the {@link MultiElem}.sourceElements list.</li>
 *   <li>If a correspondence exists and all source elements agree on the current value (unchanged),
 *       the rule checks whether another DAG number with the matching value already exists; if so,
 *       the AST number migrates to that correspondence.  Otherwise it updates the DAG value in-place.</li>
 *   <li>If the DAG number's value diverges from the AST number, the AST number is detached from
 *       its current correspondence and re-linked via {@link #addToTargetElem}.</li>
 * </ol>
 *
 * <h2>Backward pass (DAG → AST)</h2>
 * For each {@code dag.Number}:
 * <ul>
 *   <li>Numbers with no parent operators map to a single AST number owned directly by the
 *       {@code ast.Model}.</li>
 *   <li>For each parent operator that references this number as a <em>left</em> child, one AST
 *       number copy is created (or reused) per corresponding AST operator copy, identified by
 *       the predicate {@code e.leftInverse == leftParent}.</li>
 *   <li>Symmetrically for parent operators that reference this number as a <em>right</em> child.</li>
 * </ul>
 */
class Number2Number extends Elem2Elem {

	/**
	 * Constructs the rule and sets the rule identifier to {@code "number2number"}.
	 *
	 * @param src  the source (AST) model resource
	 * @param trgt the target (DAG) model resource
	 * @param corr the correspondence model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "number2number"
	}
	
	/**
	 * Forward pass: iterates all {@code ast.Number} nodes and ensures each is represented
	 * in the DAG by a (possibly shared) {@code dag.Number} node with the same {@code value}.
	 */
	override sourceToTarget() {
		// findTargetElem's linear scan of the DAG model's exprs list, repeated for every
		// AST number, turns "create n distinct numbers" into O(n^2). Since dag.Number
		// objects are only ever created inside addToTargetElem (never elsewhere, and
		// never deleted mid-call - deletions only happen in the orchestrator's cleanup
		// pass between calls), a cache scoped to this single sourceToTarget() call is
		// airtight: seed it once from whatever dag.Number nodes already exist, then keep
		// it in sync with every number this call itself creates or reuses.
		val dagModel = (sourceModel.contents.get(0) as ast.Model).getCorrModelElem.targetElement as Model
		val java.util.Map<Integer, dag.Number> valueToTarget = newHashMap
		dagModel.exprs.filter(typeof(dag.Number)).forEach[num | valueToTarget.put(num.value, num)]

		sourceModel.allContents.filter(typeof(ast.Number))
			.forEach[n |
				val corr = n.getCorrModelElem  as MultiElem
				if(corr === null) {
					// No correspondence yet – find an existing DAG number or create a new one.
					n.addToTargetElem(valueToTarget)
				} else {
					val t = corr.targetElement as dag.Number
					if(corr.sourceElements.forall[it instanceof Number && (it as ast.Number).value == n.value]) {
						// All sources agree on the value; check for an existing DAG number that
						// now matches this value (e.g. a parallel branch just set the same value).
						val newTarget = valueToTarget.get(n.value)
						if(newTarget !== null)
							(newTarget.getCorrModelElem as MultiElem).sourceElements += n
						else
							t.value = n.value
					}
					// Detach and re-link if the DAG value diverges from the AST value.
					if(t.value != n.value) {
						corr.sourceElements -= n
						n.addToTargetElem(valueToTarget)
					}
				}
			]
	}
	
	/**
	 * Backward pass: iterates all {@code dag.Number} nodes and reconstructs the
	 * corresponding AST number copies, one per parent operator reference.
	 */
	override targetToSource() {
		targetModel.allContents.filter(typeof(dag.Number)).forEach[n |
			// Case 1: number not used by any operator → top-level model ownership.
			if (n.leftInverse.empty && n.rightInverse.empty) {
				val corr = n.getOrCreateCorrModelElement(ruleID) as MultiElem;
				val src = corr.getOrCreateSourceElem(sourcePackage.number, [true]) as ast.Number;
				src.value = n.value;
				src.model = n.model.corrModelElem.sourceElement as ast.Model;
			}
			
			// Case 2: number is the left child of one or more operators.
			for (Operator left : n.leftInverse) {
				val parentCorr = left.getOrCreateCorrModelElement(ruleID) as MultiElem;
				for (EObject leftParent : parentCorr.sourceElements) {
					val corr = n.getOrCreateCorrModelElement(ruleID) as MultiElem;
					val src = corr.getOrCreateSourceElem(
							sourcePackage.number, [e | (e as ast.Number).leftInverse == leftParent]) as ast.Number;
					src.value = n.value;
					src.leftInverse = leftParent as ast.Operator;
				}
			}
			
			// Case 3: number is the right child of one or more operators.
			for (Operator right : n.rightInverse) {
				val parentCorr = right.getOrCreateCorrModelElement(ruleID) as MultiElem;
				for (EObject rightParent : parentCorr.sourceElements) {
					val corr = n.getOrCreateCorrModelElement(ruleID) as MultiElem;
					val src = corr.getOrCreateSourceElem(
							sourcePackage.number, [e | (e as ast.Number).rightInverse == rightParent]) as ast.Number;
					src.value = n.value;
					src.rightInverse = rightParent as ast.Operator;
				}
			}
		]
	}
	
	/**
	 * Finds or creates a {@code dag.Number} for the given AST number and registers the
	 * link in the correspondence model.
	 *
	 * <p>If a {@code dag.Number} with the same value already exists in the DAG model's
	 * {@code exprs} list, the AST number is added to that node's correspondence (sharing
	 * the existing DAG node).  Otherwise a new {@code dag.Number} is created and placed in
	 * the DAG model.
	 *
	 * @param e the AST number to map into the DAG
	 */
	def private addToTargetElem(ast.Number e, java.util.Map<Integer, dag.Number> valueToTarget) {
		var newTarget = valueToTarget.get(e.value)
		if(newTarget === null) {
			newTarget = createTargetElement(DagPackage.eINSTANCE.number) as dag.Number
		}
		val newCorr = newTarget.getOrCreateCorrModelElement(ruleID) as MultiElem
		newCorr.sourceElements += e
		newTarget.value = e.value
		newTarget.model = e.model.getCorrModelElem.targetElement as Model
		elementsToCorr.put(newCorr)
		valueToTarget.put(e.value, newTarget)
	}
}