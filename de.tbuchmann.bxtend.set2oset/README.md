# de.tbuchmann.bxtend.set2oset

A **bidirectional, incremental model transformation** between an unordered set (`MySet`) and an
ordered set (`MyOrderedSet`), implemented with the **BXtend** framework as part of the
[Benchmarx](https://github.com/eMoflon/benchmarx) benchmark suite.

---

## Table of Contents

1. [Transformation Problem](#1-transformation-problem)
2. [Metamodels](#2-metamodels)
3. [Correspondence Model](#3-correspondence-model)
4. [BXtend Solution Overview](#4-bxtend-solution-overview)
5. [Project Structure](#5-project-structure)
6. [Class-by-Class Description](#6-class-by-class-description)
7. [Key Design Decision: Ordering Policy](#7-key-design-decision-ordering-policy)
8. [Manual Modification: Linked-List Repair on Deletion](#8-manual-modification-linked-list-repair-on-deletion)
9. [Incremental Synchronisation and Idempotency](#9-incremental-synchronisation-and-idempotency)
10. [Building and Running](#10-building-and-running)
11. [Integration with Benchmarx](#11-integration-with-benchmarx)

---

## 1. Transformation Problem

The transformation keeps a plain **unordered** set (`MySet`) consistent with an **ordered** set
(`MyOrderedSet`).  Both models hold the same collection of string-valued elements, but the target
adds _ordering information_ that has no counterpart in the source.

### Consistency relation

A source model (`MySet`) and a target model (`MyOrderedSet`) are **consistent** when:

- Both have the same `name` attribute.
- Every `sets.Element` in the source is represented by exactly one `osets.Element` in the target
  with the same `value`, and vice versa.
- The `osets.Element` objects form a valid, non-cyclic **doubly-linked list** via their `next` /
  `previous` cross-references.

The ordering of the elements in `MyOrderedSet` is an _extra_ piece of information that the source
metamodel (`Sets.ecore`) simply does not have; it is therefore **preserved** by the transformation
rather than overwritten.  In particular:

- **Forward** (source → target): newly added elements are appended at the _tail_ of the existing
  linked list; previously synchronised elements keep their current positions.
- **Backward** (target → source): the ordering information is silently discarded; only element
  values and set membership are propagated to the unordered source.

### Non-determinism

Because the source has no order concept, multiple target states may be equally valid for a given
source state.  For example, a source set `{A, B}` may map to either `A→B` or `B→A` in the target.
The BXtend implementation resolves this non-determinism with the _append-at-tail_ policy described
in [Section 7](#7-key-design-decision-ordering-policy).

---

## 2. Metamodels

### Source – `Sets.ecore` (package `sets`)

```
MySet
├── name : String
├── incrementalID : String   (auxiliary, not propagated by BXtend)
└── elements [0..*] ──containment──► Element
                                         └── value : String
```

`MySet.elements` is an **unordered** containment collection; there are no cross-references between
elements.

### Target – `OrderedSets.ecore` (package `osets`)

```
MyOrderedSet
├── name : String
├── incrementalID : String   (auxiliary)
└── elements [0..*] ──containment──► Element
                                         ├── value : String
                                         ├── next     ──► Element   (eOpposite of previous)
                                         └── previous ──► Element   (eOpposite of next)
```

The `next` / `previous` cross-references are declared as an **eOpposite pair** in the metamodel.
This means that when you set `A.next = B`, EMF automatically sets `B.previous = A` for free.
The BXtend implementation exploits this to keep list repair minimal (see
[Section 8](#8-manual-modification-linked-list-repair-on-deletion)).

### Correspondence model – `corresp.ecore` (package `set2oset`)

```
Transformation
└── correspondences [0..*] ──containment──► Corr (abstract)
                                                ├── sourceElement : EObject
                                                ├── targetElement : EObject
                                                └── desc : String
BasicElem ──extends──► Corr
```

The correspondence model records every matched (source, target) pair together with a human-readable
label (`desc`) that names the rule that established the link.

---

## 3. Correspondence Model

The BXtend framework maintains a **persistent correspondence model** alongside the source and
target models.  It is serialised to/from an XMI file (e.g. `corr.xmi`) and reloaded at the start
of every synchronisation step.

Each `Corr` object represents a bijection between one source element and one target element.
It serves two purposes:

1. **Incremental synchronisation** – by checking whether a correspondence already exists, each
   rule avoids re-creating elements that are already synchronised.
2. **Deletion detection** – when an element is deleted from one model its containment reference is
   cleared by EMF, which sets the corresponding field (`sourceElement` or `targetElement`) in the
   `Corr` object to `null`.  The transformation detects these null references after the rule loop
   and propagates the deletion to the other model.

An in-memory hash map (`elementsToCorr` in `Elem2Elem`) provides O(1) lookup of the `Corr` for any
source or target `EObject`.

---

## 4. BXtend Solution Overview

[BXtend](https://github.com/ubt-ai1/bxtend) is an Eclipse-based framework for writing
bidirectional, rule-based model transformations in the **Xtend** language.  The developer writes
a set of _rules_, each of which handles one concept in both directions; a central _orchestrator_
class manages the rule lifecycle, correspondence model, and deletion handling.

The BXtend-generated scaffold was used as the starting point and was **manually adapted** at two
points (highlighted in [Section 8](#8-manual-modification-linked-list-repair-on-deletion)):

- The generated deletion helper `deleteUnreferencedTargetElements()` was extended with a
  linked-list repair step that the generator does not produce for doubly-linked list metamodels.

### Overall call flow

```
BXtendSet2Oset (Benchmarx adapter)
    │
    ├── sourceToTarget()  ──►  Set2osetTransformation.sourceToTarget()
    │                              ├── MySet2MyOrderedSet.sourceToTarget()
    │                              ├── Element2Element.sourceToTarget()
    │                              └── deleteUnreferencedTargetElements()   ← also repairs linked list
    │
    └── targetToSource()  ──►  Set2osetTransformation.targetToSource()
                                   ├── MySet2MyOrderedSet.targetToSource()
                                   ├── Element2Element.targetToSource()
                                   └── deleteUnreferencedSourceElements()
```

---

## 5. Project Structure

```
de.tbuchmann.bxtend.set2oset/
├── model/
│   ├── corresp.ecore          # Ecore metamodel for the correspondence model
│   └── corresp.genmodel       # EMF generator model
├── src/
│   └── de/tbuchmann/bxtend/set2oset/
│       ├── correspondence/set2oset/   # EMF-generated Java code for the correspondence metamodel
│       │   ├── Corr.java
│       │   ├── BasicElem.java
│       │   ├── Transformation.java
│       │   ├── Set2osetFactory.java
│       │   ├── Set2osetPackage.java
│       │   ├── impl/
│       │   └── util/
│       └── rules/                     # Hand-written (+ BXtend-generated) transformation logic
│           ├── Elem2Elem.xtend        # Abstract base class for all rules
│           ├── MySet2MyOrderedSet.xtend  # Container rule: MySet ↔ MyOrderedSet
│           ├── Element2Element.xtend  # Element rule: sets.Element ↔ osets.Element
│           └── Set2osetTransformation.xtend  # Orchestrator
├── META-INF/
│   └── MANIFEST.MF
└── README.md                  # This file
```

---

## 6. Class-by-Class Description

### `Elem2Elem` (abstract base)

**File:** `src/.../rules/Elem2Elem.xtend`

Provides the shared rule infrastructure:

| Responsibility | Detail |
|---|---|
| Resource access | Holds references to the three EMF resources (`sourceModel`, `targetModel`, `corrModel`) |
| Factory / package access | Exposes `SetsFactory`, `OsetsFactory`, `Set2osetFactory`, `SetsPackage`, `OsetsPackage` |
| Correspondence cache | Static `Map<EObject, Corr> elementsToCorr` for O(1) lookup; populated on construction from the persisted correspondence model |
| `getOrCreateCorrModelElement(obj, desc)` | Returns an existing `Corr` for `obj` or creates a new one, storing it in the `Transformation` root and the lookup map |
| `getOrCreateSourceElem(corr, clazz)` | Returns or lazily creates the source side of a correspondence |
| `getOrCreateTargetElem(corr, clazz)` | Returns or lazily creates the target side of a correspondence |
| Rule ID | Abstract string `ruleID` stored in each `Corr.desc` for traceability |

### `MySet2MyOrderedSet` (container rule)

**File:** `src/.../rules/MySet2MyOrderedSet.xtend`

Maps the **root container** objects in both directions and synchronises the `name` attribute.

- **Forward:** for each `MySet` → look up or create the corresponding `MyOrderedSet`, copy `name`,
  add to `targetModel.contents`.
- **Backward:** for each `MyOrderedSet` → look up or create the corresponding `MySet`, copy
  `name`, add to `sourceModel.contents`.

_Must run **before** `Element2Element`_ so that container correspondences are available when
elements are assigned to their parent containers.

### `Element2Element` (element rule)

**File:** `src/.../rules/Element2Element.xtend`

Maps individual `sets.Element` ↔ `osets.Element` pairs and maintains the doubly-linked list.

- **Forward:** iterates over source elements; for each new element (no target correspondence yet)
  a new `osets.Element` is created and **appended at the tail** of the existing linked list by
  setting `target.previous = tail`.  Existing elements are not re-linked, preserving user-defined
  order.
- **Backward:** iterates over target elements; for each one, creates or retrieves the source
  element and copies `value`.  Ordering information is **not propagated back** (the source
  metamodel has no order concept).

### `Set2osetTransformation` (orchestrator)

**File:** `src/.../rules/Set2osetTransformation.xtend`

Wires everything together.  Two constructors accept either three `URI`s or three pre-loaded
`Resource` objects (the latter is used by the Benchmarx adapter).

Key methods:

| Method | Description |
|---|---|
| `addRules()` | Adds `MySet2MyOrderedSet` then `Element2Element` in the required order |
| `sourceToTarget()` | Runs all rules forward; calls `deleteUnreferencedTargetElements()` afterwards |
| `targetToSource()` | Runs all rules backward; calls `deleteUnreferencedSourceElements()` afterwards |
| `detectSourceDeletions()` | Finds `Corr` entries where `sourceElement == null` |
| `detectTargetDeletions()` | Finds `Corr` entries where `targetElement == null` |
| `deleteUnreferencedTargetElements()` | **Manually modified** – repairs linked list before deleting target elements (see Section 8) |
| `deleteUnreferencedSourceElements()` | Deletes source elements that lost their target counterpart; no linked-list repair needed |

---

## 7. Key Design Decision: Ordering Policy

The source metamodel (`Sets.ecore`) is unordered.  When a new element is added to the source and
propagated forward, the transformation must decide _where_ to insert it in the target linked list.
The BXtend implementation uses the **append-at-tail** policy:

1. Before processing any source element the rule scans the target model to find the current tail
   (the `osets.Element` whose `next` reference is `null`).
2. Every new target element is appended after that tail and becomes the new tail.

**Consequence:** User-applied reorderings on the target side are **respected**.  If the user
inverts the list from `A→B→C` to `C→B→A` and then adds `D` on the source side, the result will be
`C→B→A→D` — `D` is appended at the current tail (`A`), and the existing order is left untouched.

This policy satisfies the **hippocraticness** (HIPPO) property required by the Benchmarx test
suite: a target-only edit (e.g. reordering) that does not change set membership does not trigger
any modification on the source side.

---

## 8. Manual Modification: Linked-List Repair on Deletion

> **This is the most important deviation from the BXtend-generated template.**

### The problem

BXtend's code generator produces a generic deletion helper (`deleteUnreferencedTargetElements`)
that simply calls `EcoreUtil.delete(targetElement)` for every target element whose source
counterpart has been deleted.  This works correctly for regular containment hierarchies.

However, for the `osets` metamodel, `osets.Element` objects are connected via `next`/`previous`
cross-references that form a doubly-linked list.  After `EcoreUtil.delete(trg)` is called, the
predecessor's `next` pointer is set to `null` by EMF's containment removal, but the predecessor
still _physically points at nothing_ — the linked list is broken:

```
Before deletion of B:   A ←→ B ←→ C
After naive delete:     A →  ✗       C ←→ (nothing)
                        (A.next = null, C.previous = null, list is split)
```

### The fix

Before `EcoreUtil.delete(trg)` is called, the predecessor is re-linked to skip over the element
being deleted:

```xtend
if (c.targetElement instanceof osets.Element) {
    val osets.Element trg = c.targetElement as osets.Element
    if (trg.previous !== null) {
        trg.previous.next = trg.next   // re-link predecessor to successor
    }
}
```

Because `next` and `previous` are declared as an **eOpposite pair** in `OrderedSets.ecore`, the
single assignment `trg.previous.next = trg.next` automatically triggers the symmetric update
`trg.next.previous = trg.previous` inside EMF — no second assignment is needed.

```
Before deletion of B:   A ←→ B ←→ C
After repair + delete:  A ←→ C          ✓ (list is intact)
```

### Why no repair is needed in the backward direction

`deleteUnreferencedSourceElements` removes `sets.Element` objects from the source model.
The source metamodel (`Sets.ecore`) has **no `next`/`previous` cross-references**; `sets.Element`
objects are held only by the `MySet.elements` containment collection.  `EcoreUtil.delete` handles
regular containment removal correctly without any additional fix.

---

## 9. Incremental Synchronisation and Idempotency

Every rule is designed to be called **multiple times** on the same (possibly partially
synchronised) models without producing duplicate elements or corrupted state:

- The `getOrCreate…` helpers check whether a correspondence (or an element) already exists before
  creating a new one.
- The `elementsToCorr` lookup map is rebuilt from the persisted correspondence model at
  construction time, so incrementally reloaded rules have full knowledge of previously established
  links.
- New target elements receive a `previous` pointer only at creation time; subsequent
  `sourceToTarget()` calls leave existing elements in place, so user-defined ordering is not
  accidentally reset.

---

## 10. Building and Running

This project is an **Eclipse PDE plug-in**.  There is no standalone Maven/Gradle build.

### Prerequisites

- Eclipse IDE for Java and DSL developers with Xtend and BXtend installed.
- All dependent plug-ins declared in `META-INF/MANIFEST.MF` must be on the target platform:
  - `org.eclipse.emf.ecore`
  - `org.eclipse.xtend.lib`
  - `de.ubt.ai1.m2m.bxtend`
  - `OSet` (bundle `OSet;bundle-version="1.0.0"`)
  - `Set` (bundle `Set;bundle-version="1.0.0"`)

### Running the Benchmarx tests

The test suite lives in the companion project `BenchmarxSetToOSet`.

1. Import the project set: `File > Import > Team > Team Project Set` →  
   `examples/settooset/projectSet.psf`
2. Select `BenchmarxSetToOSet` → **Run As > JUnit Test**.

The Benchmarx tool adapter class is  
`org.benchmarx.examples.set2oset.implementations.bxtend.BXtendSet2Oset`.

---

## 11. Integration with Benchmarx

The class `BXtendSet2Oset` (in `BenchmarxSetToOSet`) adapts this transformation to the Benchmarx
`BXToolForEMF` interface:

| Benchmarx lifecycle call | BXtend action |
|---|---|
| `initiateSynchronisationDialogue()` | Creates empty EMF resources, adds a root `MySet`, constructs `Set2osetTransformation`, calls `sourceToTarget()` |
| `performAndPropagateSourceEdit(edit)` | Applies the edit lambda to the source model, then calls `set2oset.sourceToTarget()` |
| `performAndPropagateTargetEdit(edit)` | Applies the edit lambda to the target model, then calls `set2oset.targetToSource()` |
| `performIdleSourceEdit(edit)` | Applies the edit but does **not** propagate (used to set `incrementalID` as an alignment anchor) |
| `performIdleTargetEdit(edit)` | Applies the edit but does **not** propagate |

The Benchmarx test suite verifies the transformation against a shared set of named XMI model states
(stored in `BenchmarxSetToOSet/resources/`) using forward, backward, batch, and incremental test
scenarios.
