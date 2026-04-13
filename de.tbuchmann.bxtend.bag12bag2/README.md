# de.tbuchmann.bxtend.bag12bag2

**Bidirectional, incremental model transformation between Bag1 and Bag2**  
_Implemented with the [BXtend](https://github.com/eMoflon/BXtend) framework — extended to support many-to-one correspondences._

---

## Table of Contents

1. [Transformation Problem](#transformation-problem)
2. [Metamodels](#metamodels)
3. [Transformation Relation](#transformation-relation)
4. [BXtend Solution — Architecture](#bxtend-solution)
5. [Correspondence Model Extension: `MultiElem`](#correspondence-model-extension-multielem)
6. [Rule Pipeline](#rule-pipeline)
7. [Propagation and Deletion Handling](#propagation-and-deletion-handling)
8. [Project Structure](#project-structure)
9. [Integration with BenchmarX](#integration-with-benchmarx)
10. [Known Limitations](#known-limitations)

---

## Transformation Problem

The **Bag1 ↔ Bag2** transformation is a classic bidirectional model synchronisation benchmark defined in the [BenchmarX](https://github.com/eMoflon/benchmarx) framework.

The fundamental challenge is a **many-to-one compression / decompression**:

| Bag1 (uncompressed) | Bag2 (compressed) |
|---|---|
| Each occurrence of a value is a separate `Element` object | Each distinct value appears exactly once; the number of occurrences is stored as the `multiplicity` attribute |
| `Element(value="Beer")` × 5 | `Element(value="Beer", multiplicity=5)` × 1 |
| `Element(value="BeerGlass")` × 1 | `Element(value="BeerGlass", multiplicity=1)` × 1 |

This is a non-trivial **non-bijective** (asymmetric cardinality) synchronisation problem:
- A **forward** (Bag1 → Bag2) step must _group_ Bag1 elements by value and compute multiplicities.
- A **backward** (Bag2 → Bag1) step must _expand_ each Bag2 entry into the right number of individual Bag1 elements.
- **Incremental** runs must detect and propagate only the changes (insertions, deletions, value changes) without destroying unrelated elements or their identities.

---

## Metamodels

### Bag1 (`bags1`)  
Package URI: `http://de.ubt.ai1.bw.qvt.examples.bags1.ecore`

```
MyBag
  └─ elements : Element [0..*]  (containment, opposite: Element.bag)

Element
  ├─ value         : EString
  ├─ bag           : MyBag      (back-reference, opposite: MyBag.elements)
  └─ incrementalID : EString    (default: "default"; used by BenchmarX alignment)
```

`MyBag` is the root container. Every occurrence of a value is stored as a separate `Element` instance.

### Bag2 (`bags2`)  
Package URI: `http://de.ubt.ai1.bw.qvt.examples.bags2.ecore`

```
MyBag
  └─ elements : Element [0..*]  (containment, opposite: Element.bag)

Element
  ├─ value         : EString
  ├─ multiplicity  : EInt       (number of occurrences of this value)
  ├─ bag           : MyBag      (back-reference, opposite: MyBag.elements)
  └─ incrementalID : EString    (used by BenchmarX alignment)
```

`MyBag` is the root container. Each distinct value appears as a single `Element` instance; `multiplicity` records how many occurrences of that value exist.

---

## Transformation Relation

The transformation must maintain the following consistency relation between a Bag1 model `B1` and a Bag2 model `B2`:

```
B1.elements grouped by value  ≅  B2.elements

For every distinct value v:
  |{ e ∈ B1.elements | e.value = v }|  =  (e2 ∈ B2.elements where e2.value = v).multiplicity
```

Both models share the same single `MyBag` root (no structural re-shaping at the container level).

### Concrete example

**Bag1** (source):
```
MyBag
  ├─ Element(value="Beer")
  ├─ Element(value="Beer")
  ├─ Element(value="Beer")
  ├─ Element(value="Beer")
  ├─ Element(value="Beer")
  └─ Element(value="BeerGlass")
```

**Bag2** (target — consistent with the Bag1 above):
```
MyBag
  ├─ Element(value="Beer",      multiplicity=5)
  └─ Element(value="BeerGlass", multiplicity=1)
```

---

## BXtend-Solution

### What is BXtend?

**BXtend** is a Java/Xtend framework for bidirectional model synchronisation. It follows a rule-based, correspondence-model-driven approach:

1. A **correspondence model** (a dedicated EMF model conforming to `corresp.ecore`) stores explicit traceability links between source and target elements.
2. **Rules** (subclasses of the abstract `Elem2Elem` base class) read and update the correspondence model on each propagation step.
3. Each rule exposes two methods — `sourceToTarget()` and `targetToSource()` — that implement forward and backward propagation respectively.
4. The framework **does not** natively handle element deletions; deletions must be detected by scanning the correspondence model for dangling references after every propagation.

### Class Overview

```
Bag12bag2Transformation          ← entry point; orchestrates the rule pipeline
  │
  ├── Elem2Elem (abstract)       ← base class; manages resources, cache, corr model helpers
  │     ├── Bag2Bag              ← rule: MyBag ↔ MyBag (1-to-1, BasicElem)
  │     └── Element2Element      ← rule: Element ↔ Element (N-to-1, MultiElem)
  │
  └── Correspondence model (EMF)
        ├── Transformation       ← root; holds the list of Corr entries
        ├── Corr (abstract)      ← base correspondence type (1 source, 1 target, desc)
        ├── BasicElem extends Corr ← standard 1-to-1 BXtend correspondence
        └── MultiElem extends Corr ← manually added N-to-1 correspondence (see below)
```

---

## Correspondence Model Extension: `MultiElem`

The standard BXtend code generator produces a single correspondence type `Corr` with exactly **one** source element and one target element — a strict 1-to-1 link. This is insufficient for the Bag1 ↔ Bag2 problem, where multiple Bag1 `Element` objects with the same `value` must all be linked to a **single** Bag2 `Element`.

To address this, the correspondence metamodel (`model/corresp.ecore`) was manually extended with a new class **`MultiElem`**:

```
Corr (generated by BXtend)
  ├─ sourceElement  : EObject   (single source reference — inherited)
  ├─ targetElement  : EObject   (single target reference — inherited)
  └─ desc           : String    (rule identifier — inherited)

MultiElem extends Corr           ← MANUALLY ADDED
  └─ sourceElements : EObject[*] (list of source references)
```

`MultiElem` inherits `targetElement` (pointing to the one Bag2 `Element`) and adds `sourceElements` (pointing to all the Bag1 `Element` objects in the group). It also inherits `sourceElement` from `Corr` for compatibility with generic infrastructure that only knows about `Corr`.

The corresponding EMF-generated Java source files were also added manually:
- `MultiElem.java` (interface)
- `impl/MultiElemImpl.java` (implementation)
- Updates to `Bag12bag2Package`, `Bag12bag2PackageImpl`, `Bag12bag2Factory`, `Bag12bag2FactoryImpl`, `Bag12bag2AdapterFactory`, and `Bag12bag2Switch`

### Correspondence types in use

| Used for | Correspondence type | Cardinality |
|---|---|---|
| `MyBag` ↔ `MyBag` | `BasicElem` | 1 : 1 |
| Bag1 `Element(s)` ↔ Bag2 `Element` | `MultiElem` | N : 1 |

The distinction is made in `Elem2Elem.getOrCreateCorrModelElement()`:  
- If the object's `EClass` is `MyBag` (from either package) → create a `BasicElem`  
- Otherwise → create a `MultiElem`

---

## Rule Pipeline

### `Bag2Bag`

**Rule ID:** `"Bag2Bag"`  
**Correspondence type:** `BasicElem`  
**Direction:** both

Synchronises the single `MyBag` root containers. Because both models always have exactly one root:
- _Forward:_ finds or creates the Bag2 `MyBag` for each Bag1 `MyBag`, registers it in the target resource.
- _Backward:_ finds or creates the Bag1 `MyBag` for each Bag2 `MyBag`, registers it in the source resource.

This rule must run **before** `Element2Element` because the element rule needs the bag correspondence to resolve the `bag` cross-reference.

### `Element2Element`

**Rule ID:** `"Element2Element"`  
**Correspondence type:** `MultiElem`  
**Direction:** both

Implements the core many-to-one grouping logic.

#### Forward (`sourceToTarget`)

For each `bags1.Element`:

1. **No existing correspondence** → call `addToTargetElem(e)`:
   - Look for a Bag2 `Element` with the same `value` inside the already-mapped Bag2 `MyBag` (`findTargetElem`).
   - If found, join its `MultiElem` group; if not found, create a new Bag2 `Element`.
   - Set `value` and `bag` on the Bag2 element; register the Bag1 element in the cache.

2. **Correspondence exists, value unchanged** → keep the Bag2 element's `value` in sync (no-op if already equal).

3. **Correspondence exists, value changed** → remove the Bag1 element from the old group and re-add it with `addToTargetElem`.

After all elements have been processed, the `multiplicity` of every Bag2 `Element` is recomputed as `MultiElem.sourceElements.size`.

#### Backward (`targetToSource`)

For each `bags2.Element`:

1. Retrieve or create the `MultiElem` correspondence.
2. **Grow** the `sourceElements` list by adding new `bags1.Element` objects until `size == e.multiplicity`.
3. **Shrink** the list by deleting surplus `bags1.Element` objects (via `EcoreUtil.delete`) until `size == e.multiplicity`.
4. Set `value` and `bag` on every surviving Bag1 element.

---

## Propagation and Deletion Handling

### Entry Point: `Bag12bag2Transformation`

| Method | Description |
|---|---|
| `sourceToTarget()` | Forward propagation: runs all rules, then calls `deleteUnreferencedTargetElements()` |
| `targetToSource()` | Backward propagation: runs all rules, then calls `deleteUnreferencedSourceElements()` |
| `checkCorrespondences()` | Consistency hook (always returns `true` in this implementation) |

### Deletion Detection

BXtend propagation does **not** receive explicit delete notifications. Instead, after every propagation pass the correspondence model is scanned for dangling references:

| Method | Detects |
|---|---|
| `detectSourceDeletions()` | `BasicElem` where `sourceElement == null`; `MultiElem` where `sourceElements` is empty → Bag1 element(s) were deleted, so the Bag2 target must be removed |
| `detectTargetDeletions()` | `Corr` (any) where `targetElement == null` → Bag2 element was deleted, so the corresponding Bag1 element(s) must be removed |

Both methods return lazy iterators (Xtend filter views). The callers collect results into a `List` first to avoid `ConcurrentModificationException` during removal.

### In-Memory Cache: `elementsToCorr`

`Elem2Elem` maintains a `static Map<EObject, Corr>` that acts as a reverse index:

```
model element  →  its Corr correspondence
```

- Populated at construction time by re-indexing all existing `Corr` entries from the persisted correspondence model.
- Kept up to date whenever a new `Corr` is created.
- Declared `static` so that all rule instances share the same cache within one transformation session.
- Used by `getCorrModelElem(EObject)` — the primary look-up method used throughout the rules.

---

## Project Structure

```
de.tbuchmann.bxtend.bag12bag2/
├── model/
│   ├── corresp.ecore          ← Correspondence metamodel (BasicElem + manually added MultiElem)
│   └── corresp.genmodel       ← EMF generator model
├── src/
│   └── de/tbuchmann/bxtend/bag12bag2/
│       ├── correspondence/
│       │   └── bag12bag2/     ← EMF-generated Java classes for the correspondence model
│       │       ├── Corr.java
│       │       ├── BasicElem.java
│       │       ├── MultiElem.java          ← manually added
│       │       ├── Transformation.java
│       │       ├── Bag12bag2Factory.java
│       │       ├── Bag12bag2Package.java
│       │       ├── impl/
│       │       │   ├── CorrImpl.java
│       │       │   ├── BasicElemImpl.java
│       │       │   ├── MultiElemImpl.java  ← manually added
│       │       │   ├── TransformationImpl.java
│       │       │   ├── Bag12bag2FactoryImpl.java
│       │       │   └── Bag12bag2PackageImpl.java
│       │       └── util/
│       │           ├── Bag12bag2AdapterFactory.java
│       │           └── Bag12bag2Switch.java
│       └── rules/
│           ├── Elem2Elem.xtend              ← abstract base class (BXtend framework)
│           ├── Bag2Bag.xtend                ← rule: MyBag ↔ MyBag
│           ├── Element2Element.xtend        ← rule: Element(s) ↔ Element
│           └── Bag12bag2Transformation.xtend ← entry point / orchestrator
├── META-INF/
│   └── MANIFEST.MF
└── bxtend-bag12bag2-1.0.0.jar ← compiled artefact (exported for BenchmarX)
```

---

## Integration with BenchmarX

The transformation is exercised by the **BenchmarX** test suite located in the companion project `BenchmarxBag1ToBag2`. The BenchmarX adapter class is `BXtendBag12Bag2` (package `org.benchmarx.examples.bag12bag2.implementations.bxtend`).

The adapter:
1. Creates in-memory EMF resources for source, target, and correspondence models.
2. Instantiates `Bag12bag2Transformation(source, target, corr)`.
3. Delegates `performAndPropagateSourceEdit` → `bags2bags.sourceToTarget()`.
4. Delegates `performAndPropagateTargetEdit` → `bags2bags.targetToSource()`.

### Supported BenchmarX Tests

| # | Test Class | Method | Direction | Mode | What is tested |
|---|---|---|---|---|---|
| 1 | `BatchForward` | `testInitialiseSynchronisation` | fwd | Batch | Root elements created after init |
| 2 | `BatchForward` | `testCreateElement` | fwd | Batch | Forward creation of a single element |
| 3 | `BatchForward` | `testCreateMultipleElements` | fwd | Batch | Forward creation of two distinct groups |
| 4 | `BatchBackward` | `testCreateElement` | bwd | Batch | Backward creation of a single element |
| 5 | `BatchBackward` | `testCreateMultipleElements` | bwd | Batch | Backward expansion of two groups |
| 6 | `IncrementalForward` | `testIncrementalInserts` | fwd | Incremental | Adding elements to an existing Bag1 |
| 7 | `IncrementalForward` | `testIncrementalDeletions` | fwd | Incremental | Deleting elements from an existing Bag1 |
| 8 | `IncrementalForward` | `testIncrementalValueChangeOfOne` | fwd | Incremental | Changing the value of one Bag1 element |
| 9 | `IncrementalForward` | `testIncrementalValueChangeOfAll` | fwd | Incremental | Changing the value of all Bag1 elements simultaneously |
| 10 | `IncrementalForward` | `testStability` | fwd | Incremental | Hippocraticness: idle forward delta leaves Bag2 unchanged |
| 11 | `IncrementalBackward` | `testIncrementalInserts` | bwd | Incremental | Adding entries to an existing Bag2 |
| 12 | `IncrementalBackward` | `testIncrementalDeletions` | bwd | Incremental | Deleting entries from an existing Bag2 |
| 13 | `IncrementalBackward` | `testIncrementalValueChangeOfAll` | bwd | Incremental | Combined value and multiplicity changes in Bag2 |
| 14 | `IncrementalBackward` | `testStability` | bwd | Incremental | Hippocraticness: idle backward delta leaves Bag1 unchanged |

---

## Known Limitations

1. **No automatic handling of value changes in `MultiElem` groups during forward propagation when the entire group changes simultaneously.**  
   When _all_ Bag1 elements in a `MultiElem` group change their `value` to the same new value at once, the `sourceToTarget` logic in `Element2Element` may not detect this as a pure attribute update. Instead, each element is removed from the old group and re-added, which can result in the old Bag2 `Element` being left with `multiplicity=0` and a fresh one being created. The final model state is always correct, but the operation is less efficient than an in-place attribute update.

2. **Static cache is not reset between test runs.**  
   The `elementsToCorr` map in `Elem2Elem` is declared `static`. If multiple `Bag12bag2Transformation` instances are created within the same JVM (e.g. across BenchmarX test cases), stale cache entries may cause look-up errors. The BenchmarX adapter mitigates this by re-creating `Bag12bag2Transformation` for each test via `initiateSynchronisationDialogue()`.

3. **Deletion detection is post-hoc.**  
   BXtend does not receive explicit EMF deletion notifications. Deletions are inferred after each propagation pass by scanning for `null` references in the correspondence model. This means that a deletion is only cleaned up on the _next_ call to `sourceToTarget()` or `targetToSource()`, never pro-actively.

4. **`incrementalID` attribute is not synchronised.**  
   Both `bags1.Element` and `bags2.Element` carry an `incrementalID` attribute that BenchmarX uses for identity-based alignment in incremental tests. This implementation does not propagate `incrementalID` between models; alignment IDs must be set independently by the test harness.
