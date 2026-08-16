package de.tbuchmann.bxtend.ast2dag.rules

import ast.ArithmeticOperator
import ast.Expression
import ast.Model
import ast.Number
import ast.Operator
import ast.Variable
import dag.DagPackage
import de.tbuchmann.bxtend.ast2dag.correspondence.ast2dag.MultiElem
import java.util.ArrayList
import java.util.List
import org.eclipse.emf.ecore.resource.Resource

/**
 * Transformation rule that synchronises {@code Operator} (interior) nodes between the AST and DAG.
 *
 * <p>An {@code Operator} is a binary node with a left child, a right child, and an
 * {@code op : ArithmeticOperator} attribute (Add, Subtract, Multiply, Divide).  This rule is the
 * most complex one in the transformation because it must handle the central structural mismatch
 * between the two metamodels:
 * <ul>
 *   <li>In the <b>AST</b>, every {@code Operator} node <em>contains</em> its children (containment
 *       references), so the model is a strict binary tree.</li>
 *   <li>In the <b>DAG</b>, the {@code left} and {@code right} references are non-containment
 *       cross-references, so a single child node can be shared by multiple parent operators,
 *       turning the structure into a DAG.</li>
 * </ul>
 *
 * <h2>Deduplication key (forward direction)</h2>
 * Two AST subtrees are considered <em>structurally equal</em> — and therefore map to the
 * <em>same</em> DAG operator node — if they have the same operator type and their respective
 * left and right subtrees are also structurally equal (recursively).  This deep structural
 * comparison is performed by the dispatched {@link #equalsToWithChilds(Expression, Expression)}
 * family of methods.
 *
 * <h2>Forward pass (AST → DAG)</h2>
 * The pass runs in two phases:
 * <ol>
 *   <li><b>Node mapping phase</b> – for each {@code ast.Operator}, a corresponding
 *       {@code dag.Operator} is found or created using the structural equality check.  The
 *       {@link MultiElem} correspondence groups all AST operators that map to the same DAG node.
 *       If an AST operator's correspondence diverges (different op or different subtree structure),
 *       it is detached and re-added.</li>
 *   <li><b>Reference wiring phase</b> ({@link #setReferences(Model)}) – traverses the AST tree
 *       top-down and sets the non-containment {@code dag.Operator.left} and {@code dag.Operator.right}
 *       cross-references to the DAG elements already mapped by earlier rules.  This second phase
 *       is needed because the child DAG nodes may not have been registered in the correspondence
 *       map until after the node mapping phase completes.</li>
 * </ol>
 *
 * <h2>Backward pass (DAG → AST)</h2>
 * The backward pass uses an <em>iterative pre-order traversal</em>:
 * <ol>
 *   <li>The single root {@code dag.Operator} (the one with empty {@code leftInverse} and
 *       {@code rightInverse}) is identified.  If there are zero roots, the pass is skipped;
 *       if there are more than one, an {@link AssertionError} is thrown because the DAG must
 *       have exactly one root.</li>
 *   <li>Two parallel worklists ({@code preOrder} and {@code preOrderSrc}) are maintained — one for
 *       DAG operators and one for their corresponding AST operator copies.</li>
 *   <li>At each step the current DAG operator's {@code op} attribute is copied to the AST side.
 *       Then the right and left DAG children (if they are themselves operators) are pushed onto
 *       the front of the worklists.  New AST operator copies are created in the correspondence
 *       model, identified by the predicate {@code e.leftInverse == currentSrc} or
 *       {@code e.rightInverse == currentSrc}, ensuring that duplicate sub-DAGs expand into
 *       distinct sub-trees in the AST.</li>
 * </ol>
 */
class Operator2Operator extends Elem2Elem {

	/**
	 * Constructs the rule and sets the rule identifier to {@code "operator2operator"}.
	 *
	 * @param src  the source (AST) model resource
	 * @param trgt the target (DAG) model resource
	 * @param corr the correspondence model resource
	 */
	new(Resource src, Resource trgt, Resource corr) {
		super(src, trgt, corr)
		ruleID = "operator2operator"
	}

	/**
	 * Forward pass: maps every {@code ast.Operator} to a (possibly shared) {@code dag.Operator},
	 * then wires the DAG cross-references via {@link #setReferences}.
	 */
	override sourceToTarget() {
		// findTargetElem's linear scan over every dag.Operator, each candidate compared via a
		// recursive equalsToWithChilds, turns "create n structurally distinct operators" into
		// something far worse than O(n^2) (a scan-with-recursive-comparison per element). Since
		// equalsToWithChilds is a pure structural equality (same op, same left/right subtrees,
		// recursively down to Number/Variable leaves), it can be replaced by a single
		// injective structural "signature" per subtree (a nested List, whose equals/hashCode
		// EMF/Java already computes structurally for us) and a plain map lookup. Scoped to this
		// one sourceToTarget() call for the same reason as Number2Number's cache: dag.Operator
		// objects are only ever created here, and never deleted mid-call.
		val dagModel = (sourceModel.contents.get(0) as Model).getCorrModelElem.targetElement as dag.Model
		val java.util.Map<Object, dag.Operator> signatureToTarget = newHashMap
		dagModel.exprs.filter(typeof(dag.Operator)).forEach[op |
			val c = op.getCorrModelElem as MultiElem
			if (c !== null && !c.sourceElements.empty) {
				signatureToTarget.put((c.sourceElements.get(0) as Operator).signature, op)
			}
		]

		sourceModel.allContents.filter(typeof(Operator)).forEach [ op |
			val corr = op.getCorrModelElem as MultiElem
			if (corr === null) {
				// No correspondence yet – find a structurally equal DAG operator or create a new one.
				op.addToTargetElem(signatureToTarget)
			} else {
				val targetOp = corr.targetElement as dag.Operator
				// If all sources agree on the operator type, propagate the type to the DAG side.
				if(corr.sourceElements.forall[it instanceof Operator && (it as Operator).op == op.op]) {
					targetOp.op = op.op.conformOperator
				}
				// If the type or subtree structure diverges, detach and re-map this AST operator.
				if (!op.op.conformsTo(targetOp.op) || !op.equalsToWithChilds(corr.sourceElements.get(0) as Operator) || targetOp != op.findTargetElem(signatureToTarget)) {
					corr.sourceElements -= op
					op.addToTargetElem(signatureToTarget)
				}
			}
		]
		// Phase 2: wire the non-containment left/right references on the DAG side.
		(sourceModel.contents.get(0) as Model).setReferences
	}

	/**
	 * Backward pass: reconstructs the AST operator tree from the DAG using iterative
	 * pre-order traversal, expanding shared DAG nodes into duplicate AST subtrees.
	 */
	override targetToSource() {
		// Collect the DAG root(s): operators that are not referenced by any parent.
		var List<dag.Operator> preOrder = new ArrayList<dag.Operator>();
		for (var it = targetModel.allContents.filter(typeof(dag.Operator)); it.hasNext(); ) {
			val dag.Operator op = it.next();
			if (op.leftInverse.empty && op.rightInverse.empty) {
				preOrder += op;
			}
		}
		
		if (preOrder.size == 0) {
			// Nothing to do – empty DAG.
			return;
		} else if (preOrder.size > 1) {
			throw new AssertionError("Dag has multiple root elements.");
		}
		
		// Parallel worklists: one DAG operator and one corresponding AST operator per entry.
		var List<Operator> preOrderSrc = new ArrayList<Operator>();
		val corrRoot = preOrder.get(0).getOrCreateCorrModelElement(ruleID) as MultiElem;
		val srcRoot = corrRoot.getOrCreateSourceElem(sourcePackage.operator, [true]) as Operator;
		preOrderSrc.add(srcRoot);
		srcRoot.model = preOrder.get(0).model.corrModelElem.sourceElement as Model;
		
		// Iterative pre-order traversal.
		while (!preOrder.empty) {
			val dag.Operator current = preOrder.remove(0);
			val Operator currentSrc = preOrderSrc.remove(0);
			// Copy operator type from DAG to AST.
			switch (current.op) {
				case ADD: currentSrc.op = ArithmeticOperator.ADD
				case DIVIDE: currentSrc.op =  ArithmeticOperator.DIVIDE
				case MULTIPLY: currentSrc.op = ArithmeticOperator.MULTIPLY
				case SUBTRACT: currentSrc.op = ArithmeticOperator.SUBTRACT
			}
			// Push right child first so that left is processed first (pre-order).
			if (current.right instanceof dag.Operator) {
				preOrder.add(0, current.right as dag.Operator);
				val corrRight = preOrder.get(0).getOrCreateCorrModelElement(ruleID) as MultiElem;
				// Identify the AST copy by its rightInverse pointer to the current AST parent.
				val srcRight = corrRight.getOrCreateSourceElem(
						sourcePackage.operator, [e | (e as Operator).rightInverse == currentSrc]) as Operator;
				preOrderSrc.add(0, srcRight);
				currentSrc.right = srcRight;
			}
			if (current.left instanceof dag.Operator) {
				preOrder.add(0, current.left as dag.Operator);
				val corrLeft = preOrder.get(0).getOrCreateCorrModelElement(ruleID) as MultiElem;
				// Identify the AST copy by its leftInverse pointer to the current AST parent.
				val srcLeft = corrLeft.getOrCreateSourceElem(
						sourcePackage.operator, [e | (e as Operator).leftInverse == currentSrc]) as Operator;
				preOrderSrc.add(0, srcLeft);
				currentSrc.left = srcLeft;
			}
		}
	}
	
	/**
	 * Returns {@code true} if the AST {@code operator} type is the DAG counterpart of
	 * {@code operator2}.  Used to detect divergences between the AST and DAG attribute values.
	 */
	def private conformsTo(ArithmeticOperator operator,
		dag.ArithmeticOperator operator2) {
			operator2 == operator.conformOperator
	}
	
	/**
	 * Returns {@code true} if two AST {@link ArithmeticOperator} values are the same.
	 * Useful for comparing two AST operators without converting to the DAG enum.
	 */
	def private conformsTo(ArithmeticOperator operator,
		ArithmeticOperator operator2) {
		switch (operator) {
			case ADD: operator2 == ArithmeticOperator.ADD
			case DIVIDE: operator2 == ArithmeticOperator.DIVIDE
			case MULTIPLY: operator2 == ArithmeticOperator.MULTIPLY
			case SUBTRACT: operator2 == ArithmeticOperator.SUBTRACT
		}
	}
	
	/**
	 * Base case for the dispatched structural equality check.
	 * Returns {@code false} when the two expressions have different concrete types.
	 */
	def private dispatch boolean equalsToWithChilds(Expression e, Expression e2) {
		false
	}
	
	/**
	 * Recursively checks whether two AST {@link Operator} subtrees are structurally equal:
	 * same operator type, same left subtree, and same right subtree.
	 *
	 * @param operator  the first operator
	 * @param operator2 the second operator
	 * @return {@code true} if both subtrees are structurally identical
	 */
	def private dispatch boolean equalsToWithChilds(Operator operator,
		Operator operator2) {
		operator.op.conformsTo(operator2.op) 
		&& operator.left.equalsToWithChilds(operator2.left) 
		&& operator.right.equalsToWithChilds(operator2.right)
	}
	
	/**
	 * Structural equality check for two {@link Variable} leaves.
	 * Equal iff their {@code name} attributes are equal.
	 */
	def private dispatch boolean equalsToWithChilds(Variable a,
		Variable b) {
		a.name == b.name
	}
	
	/**
	 * Structural equality check for two {@link Number} leaves.
	 * Equal iff their {@code value} attributes are equal.
	 */
	def private dispatch boolean equalsToWithChilds(Number a,
		Number b) {
		a.value == b.value
	}

	/**
	 * Converts an AST {@link ArithmeticOperator} enum literal to its DAG counterpart.
	 *
	 * @param operator the AST arithmetic operator
	 * @return the equivalent {@link dag.ArithmeticOperator} literal
	 */
	def private getConformOperator(ArithmeticOperator operator) {
		switch (operator) {
			case ADD: dag.ArithmeticOperator.ADD
			case DIVIDE: dag.ArithmeticOperator.DIVIDE
			case MULTIPLY: dag.ArithmeticOperator.MULTIPLY
			case SUBTRACT: dag.ArithmeticOperator.SUBTRACT
		}
	}

	/**
	 * Finds or creates a {@code dag.Operator} for the given AST operator and registers the
	 * correspondence link.
	 *
	 * <p>An existing DAG operator is reused when it is structurally equal to {@code o} and its
	 * correspondence entry is non-empty.  Otherwise a new {@code dag.Operator} is created and
	 * added to the DAG model's {@code exprs} list.
	 *
	 * @param o the AST operator to map into the DAG
	 */
	def private addToTargetElem(Operator o, java.util.Map<Object, dag.Operator> signatureToTarget) {
		var newTarget = o.findTargetElem(signatureToTarget)
		if (newTarget === null) {
			newTarget = createTargetElement(
				DagPackage.eINSTANCE.operator) as dag.Operator
		}
		val newCorr = newTarget.getOrCreateCorrModelElement(ruleID) as MultiElem
		newCorr.sourceElements += o
		newTarget.op = o.op.getConformOperator
		newTarget.model = targetModel.contents.get(0) as dag.Model
		elementsToCorr.put(newCorr)
		signatureToTarget.put(o.signature, newTarget)
	}

	/**
	 * Looks up a {@code dag.Operator} whose first registered source element is structurally
	 * equal to {@code o}, via the per-call {@code signatureToTarget} cache (keyed by
	 * {@link #signature}, an injective encoding of {@link #equalsToWithChilds}). Re-verifies
	 * the candidate is still live (non-orphaned) and structurally equal before trusting it,
	 * exactly mirroring the original linear-scan's own guard and comparison.
	 *
	 * @param o the AST operator used as the structural search key
	 * @return the matching {@code dag.Operator}, or {@code null} if none exists
	 */
	def private findTargetElem(Operator o, java.util.Map<Object, dag.Operator> signatureToTarget) {
		val candidate = signatureToTarget.get(o.signature)
		if (candidate !== null) {
			val candidateCorr = candidate.getCorrModelElem as MultiElem
			if (!candidateCorr.sourceElements.empty
					&& (candidateCorr.sourceElements.get(0) as Operator).equalsToWithChilds(o)) {
				return candidate
			}
		}
		null
	}

	/**
	 * Injective structural signature of an AST expression subtree, matching
	 * {@link #equalsToWithChilds}'s recursive definition exactly: two subtrees are
	 * structurally equal iff their signatures are {@code equal()}. Using a nested
	 * {@link java.util.List} means Java's own structural {@code equals}/{@code hashCode}
	 * do the recursive comparison, so this can be used directly as a {@code HashMap} key.
	 */
	def private dispatch Object signature(Number n) {
		#["N", n.value]
	}

	def private dispatch Object signature(Variable v) {
		#["V", v.name]
	}

	def private dispatch Object signature(Operator o) {
		#["O", o.op, o.left.signature, o.right.signature]
	}
	
	/**
	 * Phase-2 reference wiring entry point for the AST {@link Model} root.
	 * Delegates to the root {@code expr} if one exists.
	 *
	 * @param model the AST model root
	 */
	def private dispatch void setReferences(Model model) {
		model.expr?.setReferences
	}
	
	/**
	 * Phase-2 reference wiring for an {@link Operator} node.
	 * Sets the non-containment {@code dag.Operator.left} and {@code dag.Operator.right}
	 * cross-references to the DAG elements already registered in the correspondence map,
	 * then recurses into the left and right children.
	 *
	 * @param o the AST operator whose DAG counterpart needs its children wired
	 */
	def private dispatch void setReferences(Operator o) {
		val target = (o.getCorrModelElem.targetElement as dag.Operator)
		target.left = o.left.getCorrModelElem.targetElement as dag.Expression
		target.right = o.right.getCorrModelElem.targetElement as dag.Expression
		o.left.setReferences
		o.right.setReferences
	}
	
	/**
	 * Phase-2 reference wiring base case for non-operator {@link Expression} nodes (leaves).
	 * Leaves have no children to wire, so this is a no-op.
	 *
	 * @param e a leaf expression node
	 */
	def private dispatch void setReferences(Expression e) {
		// Leaf nodes carry no child references – nothing to wire.
	}
}