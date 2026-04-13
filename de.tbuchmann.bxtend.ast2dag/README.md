# de.tbuchmann.bxtend.ast2dag

BXtend implementation of the bidirectional, incremental model transformation between
**ExpressionAST** (a strict binary expression tree) and **ExpressionDAG** (a directed
acyclic graph of the same expression, with structurally identical sub-trees shared as
a single node).

This project is the **BXtend tool plug-in** for the
[BenchmarxAstToDag](../../work/git/forschung/benchmarxUpdates/examples/asttodag/BenchmarxAstToDag)
test suite that is part of the [Benchmarx](https://github.com/eMoflon/benchmarx) framework
for evaluating bidirectional model transformation tools.

---

## Table of Contents

1. [Transformation Problem](#transformation-problem)
2. [Metamodels](#metamodels)
   - [ExpressionAST (source)](#expressionast-source)
   - [ExpressionDAG (target)](#expressiondag-target)
   - [Structural Difference](#structural-difference)
3. [Worked Example](#worked-example)
4. [Solution Architecture](#solution-architecture)
   - [Correspondence Model](#correspondence-model)
   - [Rule Hierarchy](#rule-hierarchy)
   - [Rule Execution Order](#rule-execution-order)
5. [Rule-by-Rule Description](#rule-by-rule-description)
   - [Model2Model](#model2model)
   - [Variable2Variable](#variable2variable)
   - [Number2Number](#number2number)
   - [Operator2Operator](#operator2operator)
6. [Incremental Synchronisation](#incremental-synchronisation)
7. [Deletion Handling](#deletion-handling)
8. [Project Layout](#project-layout)
9. [Dependencies](#dependencies)
10. [Running the Tests](#running-the-tests)

---

## Transformation Problem

An arithmetic expression such as `(a / 10 - b) * (a / 10 - b)` can be represented in
two structurally different ways:

| Representation | Description |
|---|---|
| **AST** (Abstract Syntax Tree) | A **strict binary tree**. Every node has exactly one parent. Duplicate sub-expressions appear as duplicate sub-trees. |
| **DAG** (Directed Acyclic Graph) | A **flat node pool** where structurally identical sub-expressions are **deduplicated** into a single node referenced by multiple parents. |

The transformation must synchronise both representations bidirectionally and
incrementally:

- **Forward (AST → DAG):** fold the tree into a DAG by merging structurally equal
  sub-trees into shared nodes.
- **Backward (DAG → AST):** unfold the DAG back into a tree by duplicating shared
  nodes for each referencing parent.

The core challenge is the **1-to-many** structural mapping: a single DAG node
corresponds to potentially many AST nodes (one for each occurrence in the tree).

---

## Metamodels

### ExpressionAST (source)

Package URI: `http://de.ubt.ai1.bw.qvt.examples.ast.ecore`

| Class / Enum | Description |
|---|---|
| `Model` | Root container; holds exactly **one** `expr` (the root `Expression`) via a containment reference. |
| `Expression` *(abstract)* | Base class; carries an optional `incrementalID : String` stability key, and back-references `leftInverse` / `rightInverse` (0..1 each) pointing to the single parent `Operator`. |
| `Operator` | Binary interior node; `left` and `right` are **containment** references (each node has exactly one parent → tree structure); `op : ArithmeticOperator`. |
| `Operand` *(abstract)* | Base class for leaf nodes. |
| `Variable` | Leaf carrying a string `name` attribute. |
| `Number` | Leaf carrying an integer `value` attribute. |
| `ArithmeticOperator` | Enum: `Add`, `Subtract`, `Multiply`, `Divide`. |

Because `Operator.left` and `Operator.right` are containment references, every
expression node has **exactly one** parent → the AST is a **tree**.

### ExpressionDAG (target)

Package URI: `http://de.ubt.ai1.bw.qvt.examples.dag.ecore`

| Class / Enum | Description |
|---|---|
| `Model` | Root container; holds a **flat list** `exprs [0..*]` of all `Expression` nodes (all elements at one level). |
| `Expression` *(abstract)* | Same `incrementalID` attribute; `leftInverse` / `rightInverse` are now **multi-valued** (0..*), so one node can be shared by many parent operators. |
| `Operator` | Binary interior node; `left` and `right` are **non-containment** cross-references → shared children are possible. |
| `Operand`, `Variable`, `Number`, `ArithmeticOperator` | Identical to the AST counterparts. |

### Structural Difference

| Feature | ExpressionAST | ExpressionDAG |
|---|---|---|
| Model structure | Tree (strict hierarchy) | Flat pool + cross-references |
| `Operator.left / right` | Containment (one parent) | Non-containment (many parents) |
| `leftInverse / rightInverse` | Single-valued (0..1) | Multi-valued (0..*) |
| Duplicate sub-expressions | Stored as duplicate sub-trees | Shared as a single node |
| Root expression | Single `expr` reference on `Model` | Root is the `Operator` with empty `leftInverse` and `rightInverse` |

---

## Worked Example

**Input AST** – expression `(a/10 - b) * (a/10 - b)`:

```
Multiply
├── Add (left)       ← first copy of (a/10 - b)
│   ├── Divide
│   │   ├── Variable "a"
│   │   └── Number 10
│   └── Subtract
│       ├── Variable "b"
│       └── Number 10
└── Add (right)      ← identical second copy
    ├── Divide
    │   ├── Variable "a"
    │   └── Number 10
    └── Subtract
        ├── Variable "b"
        └── Number 10
```

**Output DAG** (after forward propagation) – 7 nodes instead of 15:

```
exprs[0]  Variable  "a"        ← leftInverse: exprs[3]
exprs[1]  Variable  "b"        ← leftInverse: exprs[4]
exprs[2]  Number    10         ← rightInverse: exprs[3], exprs[4]
exprs[3]  Operator  Divide     left=exprs[0]  right=exprs[2]
exprs[4]  Operator  Subtract   left=exprs[1]  right=exprs[2]
exprs[5]  Operator  (Add)      left=exprs[3]  right=exprs[4]  ← shared by exprs[6]
exprs[6]  Operator  Multiply   left=exprs[5]  right=exprs[5]  ← both children are the same node
```

The `Number 10` node is shared by both `Divide` and `Subtract` operators, and the
entire `(a/10 - b)` sub-tree is shared as one `Operator` node referenced twice by `Multiply`.

---

## Solution Architecture

### Correspondence Model

The correspondence model (defined in `model/corresp.ecore`, package
`http://de.ubt.ai1.m2m.ast2dag/correspondence.ecore`) tracks the mapping between
AST and DAG elements across incremental runs:

```
Transformation
  └── correspondences [0..*] : Corr
        ├── BasicElem (1-to-1)      – used for Model root pairs
        │     sourceElement : EObject
        │     targetElement : EObject
        │     desc : String          – rule identifier
        └── MultiElem  (many-to-1)  – used for all expression nodes
              sourceElements [0..*] : EObject   – all AST copies
              targetElement  : EObject           – the single shared DAG node
              desc : String
```

`BasicElem` is used only for the `Model` root because each side has exactly one root.
`MultiElem` is used for all expression nodes because a single DAG node can correspond
to multiple AST copies.

A static `HashMap<EObject, Corr>` in `Elem2Elem` provides O(1) lookup from any model
element to its correspondence entry, in both directions (source → Corr and target → Corr).

### Rule Hierarchy

```
Elem2Elem  (abstract base)
├── Model2Model
├── Variable2Variable
├── Number2Number
└── Operator2Operator
```

`Elem2Elem` provides all shared infrastructure:
- references to the three EMF resources,
- factory instances for creating AST, DAG, and correspondence elements,
- `getOrCreateCorrModelElement` — look up or create a correspondence entry,
- `getOrCreateTargetElem` / `getOrCreateSourceElem` — look up or create the element
  on the opposite side of a correspondence,
- dispatched `put(Map, Corr)` helpers to index a new correspondence entry,
- `createSourceElement` / `createTargetElement` — delegate to the respective EMF factory.

### Rule Execution Order

**Forward pass (AST → DAG)** — bottom-up:

```
1. Model2Model         – create the DAG Model root
2. Variable2Variable   – map leaf Variable nodes (deduplicate by name)
3. Number2Number       – map leaf Number nodes (deduplicate by value)
4. Operator2Operator   – map interior Operator nodes (deduplicate by deep structural equality)
                          + wire DAG non-containment left/right references
```

Leaves must exist before operators can reference them, hence the bottom-up order.

**Backward pass (DAG → AST)** — top-down:

```
1. Model2Model         – create the AST Model root
2. Operator2Operator   – reconstruct the AST operator tree via iterative pre-order traversal
                          (expands shared DAG nodes into duplicate AST sub-trees)
3. Variable2Variable   – set Variable.name for each AST copy
4. Number2Number       – set Number.value for each AST copy
```

Operators must be placed in the tree before leaves can be assigned to their
`left`/`right` slots.

---

## Rule-by-Rule Description

### Model2Model

**File:** `src/de/tbuchmann/bxtend/ast2dag/rules/Model2Model.xtend`

The simplest rule.  Uses a `BasicElem` correspondence (1-to-1).

- **Forward:** For every `ast.Model` root, creates (or reuses) a corresponding
  `dag.Model` root and adds it to the target resource.
- **Backward:** For every `dag.Model` root, creates (or reuses) a corresponding
  `ast.Model` root and adds it to the source resource.

### Variable2Variable

**File:** `src/de/tbuchmann/bxtend/ast2dag/rules/Variable2Variable.xtend`

**Deduplication key:** `name` attribute — two `Variable` nodes with the same name
share one DAG node.

**Forward:** For each `ast.Variable`:
1. If no correspondence exists yet, call `addToTargetElem`: find an existing
   `dag.Variable` with the same name, or create a new one; add the AST node to the
   `MultiElem.sourceElements`.
2. If a correspondence exists and all source elements still agree on the name, update
   the DAG name in-place (or migrate to another DAG variable if one with the matching
   name now exists).
3. If the DAG name diverges, detach from the old correspondence and re-add.

**Backward:** For each `dag.Variable`, create one AST copy for each parent operator
reference (`leftInverse` / `rightInverse`).  Copies are distinguished by the predicate
`e.leftInverse == parentOperator` or `e.rightInverse == parentOperator`.

### Number2Number

**File:** `src/de/tbuchmann/bxtend/ast2dag/rules/Number2Number.xtend`

Structurally identical to `Variable2Variable`, with `value : int` as the deduplication
key instead of `name : String`.  All equal integer literals in the AST share a single
`dag.Number` node.

### Operator2Operator

**File:** `src/de/tbuchmann/bxtend/ast2dag/rules/Operator2Operator.xtend`

The most complex rule.  Handles the main structural mismatch between the tree and the DAG.

**Deduplication key (forward):** Deep structural equality — two AST operator sub-trees
are equal if they have the same `op` attribute and their left/right sub-trees are
recursively equal (dispatched `equalsToWithChilds` methods cover `Operator`, `Variable`,
and `Number`).

**Forward — two-phase approach:**

*Phase 1 – node mapping:*
For each `ast.Operator`, find a structurally equal `dag.Operator` (via `findTargetElem`)
or create a new one.  Register/update the `MultiElem` correspondence.  If the existing
correspondence entry diverges (different `op` or different sub-tree), the AST operator
is detached and re-added.

*Phase 2 – reference wiring (`setReferences`):*
Traverse the AST tree top-down and set the `dag.Operator.left` / `dag.Operator.right`
non-containment cross-references.  This second phase is necessary because the DAG child
nodes may not have been present in the correspondence map when phase 1 ran.

**Backward — iterative pre-order traversal:**

1. Find the single DAG root operator (the one with empty `leftInverse`/`rightInverse`).
2. Maintain two parallel worklists: one of DAG operators (`preOrder`) and one of
   corresponding AST operators (`preOrderSrc`).
3. At each step: copy the `op` attribute, then push the right and left DAG children
   (if they are operators) onto the front of both worklists.  Each new AST operator
   copy is identified by its back-pointer to the parent (`leftInverse == currentSrc`
   or `rightInverse == currentSrc`), so that re-entries of a shared DAG node produce
   distinct AST copies.

**Enum conversion:** `conformOperator` / `conformsTo` helpers convert between the
`ast.ArithmeticOperator` and `dag.ArithmeticOperator` enums (they are separate types
in different EMF packages but carry the same literals).

---

## Incremental Synchronisation

The `incrementalID` attribute on `Expression` (both AST and DAG) acts as a stable
identity key.  The BXtend framework uses the persisted correspondence model
(`corr.xmi`) to align elements between runs:

1. Before the first propagation, the correspondence model is empty.  All correspondences
   are created from scratch (batch mode).
2. On subsequent calls, `Elem2Elem`'s constructor re-indexes all existing `Corr` entries
   into the `elementsToCorr` map.  Rules check the map first; if an entry is found,
   only the changed attribute is updated in-place instead of re-creating the element.

---

## Deletion Handling

After every propagation pass, `Ast2dagTransformation` scans the correspondence model
for stale entries:

| Scenario | Detected by | Action |
|---|---|---|
| AST element deleted → forward pass | `BasicElem.sourceElement == null` or `MultiElem.sourceElements.empty` | Delete orphaned `dag` element and the correspondence entry |
| DAG element deleted → backward pass | `Corr.targetElement == null` | Delete all linked `ast` elements (all `MultiElem.sourceElements`) and the correspondence entry |
| Disconnected AST `Expression` (no model, no parent) | `leftInverse == null && rightInverse == null && model == null` | Deleted as a garbage-collection step |

Deletion is performed safely via `EcoreUtil.delete(e, true)` which removes cross-references.

---

## Project Layout

```
de.tbuchmann.bxtend.ast2dag/
├── META-INF/
│   └── MANIFEST.MF                        – OSGi bundle descriptor
├── model/
│   ├── corresp.ecore                       – correspondence metamodel
│   └── corresp.genmodel
├── src/
│   └── de/tbuchmann/bxtend/ast2dag/
│       ├── rules/
│       │   ├── Elem2Elem.xtend             – abstract base class: shared infrastructure
│       │   ├── Ast2dagTransformation.xtend – top-level orchestrator
│       │   ├── Model2Model.xtend           – Model ↔ Model rule
│       │   ├── Variable2Variable.xtend     – Variable ↔ Variable rule (dedup by name)
│       │   ├── Number2Number.xtend         – Number ↔ Number rule (dedup by value)
│       │   └── Operator2Operator.xtend     – Operator ↔ Operator rule (dedup by structure)
│       └── correspondence/
│           └── ast2dag/                    – EMF-generated Java code for corresp.ecore
│               ├── Ast2dagFactory.java
│               ├── Ast2dagPackage.java
│               ├── BasicElem.java
│               ├── MultiElem.java
│               ├── Corr.java
│               └── Transformation.java
├── xtend-gen/                              – auto-generated Java from Xtend sources
└── bxtend-ast2dag-1.0.0.jar               – compiled OSGi bundle
```

---

## Dependencies

| Bundle | Purpose |
|---|---|
| `org.eclipse.emf.ecore` | EMF core runtime (EObject, Resource, etc.) |
| `org.eclipse.xtend.lib` + `org.eclipse.xtext.xbase.lib` | Xtend runtime library |
| `de.ubt.ai1.m2m.bxtend` | BXtend framework base classes |
| `ExpressionAST` (≥ 0.1.0) | Source metamodel EMF plug-in |
| `ExpressionDAG` (≥ 0.1.0) | Target metamodel EMF plug-in |

---

## Running the Tests

The transformation is tested by the **BenchmarxAstToDag** test suite.  To run the
tests against this BXtend implementation:

1. Import the following projects into your Eclipse workspace:
   - `ExpressionAST`, `ExpressionDAG` (metamodel plug-ins)
   - `de.tbuchmann.bxtend.ast2dag` (this plug-in)
   - `BenchmarxAstToDag` (test suite)
   - `Benchmarx` (core framework)

2. Enable the `BXtendAst2Dag` tool adapter in
   `BenchmarxAstToDag/src/…/implementations/bxtend/BXtendAst2Dag.java`
   (register it in the `BXToolParameterResolver`).

3. Run the JUnit test classes as **JUnit Plug-in Tests**:
   - `batch/fwd/BatchForward`
   - `batch/bwd/BatchBackward`
   - `alignment_based/fwd/IncrementalForward`
   - `alignment_based/bwd/IncrementalBackward`

Test results are saved as XMI files under `results/BXtend/` inside the test project.
