# Challenges: ExpressionAST ↔ ExpressionDAG

## The challenge

The AST is a strict binary tree — every node has exactly one parent, so a repeated
sub-expression like `(a/10 - b) * (a/10 - b)` is stored as two separate, structurally
identical sub-trees. The DAG deduplicates: structurally equal sub-expressions collapse
into a single shared node referenced by multiple parents. This is a genuine
**1-to-many structural sharing** problem:

- **Forward (fold):** deep structural equality has to be computed recursively to
  decide which AST sub-trees map to the *same* DAG node.
- **Backward (unfold):** a single shared DAG node has to be expanded back into a
  distinct AST copy for *each* place it's referenced from.

A second, purely structural problem: the two directions need **opposite traversal
orders** — leaves must exist before operators can reference them during folding, but
operators must be placed in the tree before leaves can be assigned into their `left`/
`right` slots during unfolding. A single per-rule "do forward then backward" loop over
one ordered rule list cannot satisfy both orderings at once.

## How BXtend solved it

- **A `MultiElem` correspondence** (the same many-to-one structure used in
  `bag12bag2`) links one shared DAG node to all of its AST occurrences.
- **The orchestrator, not the rules, owns direction ordering:** since all four rules'
  existing `sourceToTarget()`/`targetToSource()` are already idempotent, self-healing
  get-or-create implementations, `Ast2dagTransformation.synch()` simply runs every
  rule's forward direction in `rulesFwd` order (leaves-before-operators) and then
  every rule's backward direction in `rulesBwd` order (operators-before-leaves),
  rather than trying to force a single combined per-rule loop.
- **Structural equality is dispatched recursively** (`equalsToWithChilds`) across
  `Operator`, `Variable`, and `Number`, and enum values are converted between the two
  metamodels' separate-but-identical `ArithmeticOperator` types via small conversion
  helpers.

## What broke in practice (found via the real Benchmarx suite, not by inspection)

The subtlest bug in the whole repository: the *test infrastructure's own*
`AstModelBuilder` replaces an already-occupied containment slot (`Model.expr`,
`Operator.left`/`right`) with a plain EMF setter when a test script re-invokes a
"create" helper on a non-empty model. A plain containment setter detaches the old
subtree from the resource — so it vanishes from `.allContents()` — without triggering
EMF's cross-reference cleanup the way `EcoreUtil.delete` does. The correspondence
model's references to that orphaned subtree were left dangling with a non-null-but-
detached element, invisible to the usual `=== null` deletion check, and could later be
silently resurrected by a backward pass. Fixed with a purge sweep (by
`eResource() === null`, not `=== null`) run before every propagation pass — needed in
*both* `synch()` and the plain `sourceToTarget()`/`targetToSource()` orchestrator
methods, since one failing test bypassed `synch()` entirely.
