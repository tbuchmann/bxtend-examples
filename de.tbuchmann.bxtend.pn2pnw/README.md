# BXtend – Petrinet to PetrinetWeighted (`de.tbuchmann.bxtend.pn2pnw`)

A bidirectional, incremental model transformation between an **unweighted Petri
net** (`pn`) and a **weighted Petri net** (`pnw`), implemented with the
**BXtend** framework in [Xtend](https://eclipse.dev/Xtext/xtend/).

---

## Table of Contents

1. [Transformation Problem](#1-transformation-problem)
2. [Metamodels](#2-metamodels)
3. [Correspondence Rules](#3-correspondence-rules)
4. [BXtend Solution Architecture](#4-bxtend-solution-architecture)
   - [Framework Overview](#framework-overview)
   - [Project Structure](#project-structure)
   - [Class Hierarchy](#class-hierarchy)
5. [Rule Descriptions](#5-rule-descriptions)
   - [Net2Net](#net2net)
   - [Place2Place](#place2place)
   - [Transition2Transition](#transition2transition)
6. [Transformation Orchestration](#6-transformation-orchestration)
7. [Deletion Propagation](#8-deletion-propagation)
8. [Integration with BenchmarX](#9-integration-with-benchmarx)

---

## 1. Transformation Problem

A **plain Petri net** models a concurrent system using places, transitions, and
arcs.  Arcs in the plain net are anonymous — they carry no additional
information beyond their source and target node.

A **weighted Petri net** is a superset: arcs become first-class *edge objects*
(`PTEdge`, `TPEdge`) that each carry an integer `weight` attribute.  The weight
indicates how many tokens are consumed from (or deposited into) a place when the
connected transition fires.

The **synchronisation task** is:

* **Forward (pn → pnw):** Translate every arc in the source net into a typed
  edge object in the target net, assigning a default weight of **1** for newly
  created edges while *preserving* any weight that has already been set in the
  target (hippocraticness).
* **Backward (pnw → pn):** Reconstruct a plain net from a weighted net by
  discarding the weight information and representing arcs as direct cross-
  references between `Transition` and `Place`.

Both directions must work **incrementally**: only the minimal set of changes
needed to re-establish consistency should be applied to the opposite model.

---

## 2. Metamodels

### Source – `pn` (PetriNet.ecore)

Package URI: `http://de.ubt.ai1.bw.qvt.examples.pn.ecore`

| Class          | Attributes / References                                                                 |
|----------------|-----------------------------------------------------------------------------------------|
| `NamedElement` | `name : EString` *(abstract base)*                                                      |
| `Net`          | `name : EString`, `incrementalID : EString`, `elements[]` → `NetElement`               |
| `NetElement`   | `net` → `Net` *(abstract base for Place and Transition)*                                |
| `Place`        | `name`, `noOfTokens : EInt = 1`, `trgP2T[]` → `Transition`, `srcT2P[]` → `Transition` |
| `Transition`   | `name`, `srcP2T[]` → `Place` *(incoming)*, `trgT2P[]` → `Place` *(outgoing)*           |

Arcs are encoded as **direct cross-references** between `Place` and
`Transition`; there are no explicit arc objects.

### Target – `pnw` (PetriNetWeighted.ecore)

Package URI: `http://de.ubt.ai1.bw.qvt.examples.pnw.ecore`

| Class          | Attributes / References                                                           |
|----------------|-----------------------------------------------------------------------------------|
| `NamedElement` | `name : EString` *(abstract base)*                                                |
| `Net`          | `name : EString`, `incrementalID : EString`, `elements[]` → `NetElement`         |
| `NetElement`   | `net` → `Net` *(abstract base)*                                                   |
| `Place`        | `name`, `noOfTokens : EInt = 1`, `outPTEdges[]` → `PTEdge`, `inTPEdges[]` → `TPEdge` |
| `Transition`   | `name`, `inPTEdges[]` → `PTEdge`, `outTPEdges[]` → `TPEdge`                      |
| `Edge`         | `weight : EInt = 1` *(abstract base for PTEdge and TPEdge)*                      |
| `PTEdge`       | `weight`, `fromPlace` → `Place`, `toTransition` → `Transition`                   |
| `TPEdge`       | `weight`, `fromTransition` → `Transition`, `toPlace` → `Place`                   |

Arcs are **first-class objects** with a `weight` attribute, contained in
`Net.elements[]` alongside places and transitions.

### Correspondence Model (corresp.ecore)

Package URI: `http://de.tbuchmann.bxtend.pn2pnw/correspondence.ecore`

| Class            | Attributes / References                                        |
|------------------|----------------------------------------------------------------|
| `Transformation` | `correspondences[]` → `Corr` *(root object)*                  |
| `Corr`           | `sourceElement : EObject`, `targetElement : EObject`, `desc : EString` |
| `BasicElem`      | *extends* `Corr` – used for all element-level correspondences |

Each `Corr` entry links exactly one source element to one target element.  The
`desc` field stores the rule identifier (`"root"`, `"place"`, `"transition"`)
to aid debugging.

---

## 3. Correspondence Rules

| Source element                   | Target element                        | Rule class             | Rule ID       |
|----------------------------------|---------------------------------------|------------------------|---------------|
| `pn.Net`                         | `pnw.Net`                             | `Net2Net`              | `"root"`      |
| `pn.Place`                       | `pnw.Place`                           | `Place2Place`          | `"place"`     |
| `pn.Transition`                  | `pnw.Transition`                      | `Transition2Transition`| `"transition"`|
| `pn.Transition.srcP2T[]` (ref.)  | `pnw.PTEdge` (fromPlace→toTransition) | `Transition2Transition`| —             |
| `pn.Transition.trgT2P[]` (ref.)  | `pnw.TPEdge` (fromTransition→toPlace) | `Transition2Transition`| —             |

Arcs (edge objects) are managed inside `Transition2Transition` rather than as
dedicated correspondence entries, because they are structurally derived from
place–transition cross-references.

---

## 4. BXtend Solution Architecture

### Framework Overview

**BXtend** is a lightweight bidirectional transformation framework built on top
of the Eclipse Modeling Framework (EMF) and the Xtend language.  The key
design decisions are:

* **Rule-based decomposition:** Each correspondence rule is an independent
  class that can focus on one pair of meta-classes.
* **Explicit correspondence model:** Matched element pairs are persisted as
  XMI, enabling incremental runs across separate JVM invocations.
* **No code generation:** Rules are hand-written Xtend classes; the framework
  provides only the abstract base class and the correspondence meta-model.

### Project Structure

```
de.tbuchmann.bxtend.pn2pnw/
├── META-INF/MANIFEST.MF          # OSGi bundle manifest; declares EMF & BXtend deps
├── model/
│   ├── corresp.ecore             # Correspondence meta-model
│   └── corresp.genmodel          # EMF generator model for correspondence classes
└── src/
    └── de/tbuchmann/bxtend/pn2pnw/
        ├── correspondence/       # EMF-generated correspondence model classes
        │   └── pn2pnw/
        │       ├── Corr.java
        │       ├── BasicElem.java
        │       ├── Transformation.java
        │       ├── Pn2pnwFactory.java
        │       ├── Pn2pnwPackage.java
        │       └── impl/ util/
        └── rules/                # Hand-written BXtend rules (Xtend source)
            ├── Elem2Elem.xtend         ← abstract base class
            ├── Net2Net.xtend           ← pn.Net  ↔  pnw.Net
            ├── Place2Place.xtend       ← pn.Place ↔ pnw.Place
            ├── Transition2Transition.xtend  ← pn.Transition ↔ pnw.Transition + arcs
            └── Pn2pnwTransformation.xtend   ← orchestrator / public API
```

### Class Hierarchy

```
Elem2Elem  (abstract)
│
├── Net2Net
├── Place2Place
└── Transition2Transition

Pn2pnwTransformation   (orchestrates the rules; public API)
```

---

## 5. Rule Descriptions

### Net2Net

**File:** `rules/Net2Net.xtend`  
**Rule ID:** `"root"`

Synchronises the top-level `Net` container in both directions.  This rule is
always executed **first**, because all other rules must add their elements to
an already-existing `Net` on the opposite side.

| Direction     | Action                                                                   |
|---------------|--------------------------------------------------------------------------|
| Forward       | For each `pn.Net`, find/create `pnw.Net`; sync `name`.                  |
| Backward      | For each `pnw.Net`, find/create `pn.Net`; sync `name`.                  |

### Place2Place

**File:** `rules/Place2Place.xtend`  
**Rule ID:** `"place"`

Synchronises place nodes and their token counts.  Depends on `Net2Net` to
have already established the `Net` correspondences so that the place can be
added to the correct `Net.elements[]` list.

| Direction     | Action                                                                                           |
|---------------|--------------------------------------------------------------------------------------------------|
| Forward       | For each `pn.Place`, find/create `pnw.Place`; sync `name`, `noOfTokens`; add to target net.     |
| Backward      | For each `pnw.Place`, find/create `pn.Place`; sync `name`, `noOfTokens`; add to source net.     |

### Transition2Transition

**File:** `rules/Transition2Transition.xtend`  
**Rule ID:** `"transition"`

The most complex rule.  Besides synchronising transition nodes themselves, it
reconciles the arc representations between the two metamodels.

**Forward arc reconciliation (per transition `t`):**

1. Collect the transition's current `inPTEdges` and `outTPEdges` as
   *unreferenced candidates* (potential deletions).
2. For each place `p` in `t.srcP2T` (incoming arcs):
   - Look up `p`'s corresponding `pnw.Place`.
   - If no `PTEdge` from that place to `t`'s `pnw.Transition` exists, create
     one with `weight = 1`.
   - Remove the matching edge from the unreferenced-candidate list.
3. Repeat for `t.trgT2P` (outgoing arcs) using `TPEdge`.
4. Delete all edges that remained in the unreferenced-candidate list (they
   correspond to arcs that were removed from the source).

**Backward arc reconciliation (per transition `tr`):**

1. Collect the transition's current `srcP2T` and `trgT2P` as unreferenced
   candidates.
2. For each `PTEdge` in `tr.inPTEdges`, add the corresponding `pn.Place` to
   `sourceTransition.srcP2T`; remove it from candidates.
3. For each `TPEdge` in `tr.outTPEdges`, add the corresponding `pn.Place` to
   `sourceTransition.trgT2P`; remove it from candidates.
4. Remove all remaining candidates from the source transition's reference
   lists (they correspond to arcs that were deleted on the target side).

---

## 6. Transformation Orchestration

**File:** `rules/Pn2pnwTransformation.xtend`

`Pn2pnwTransformation` is the **sole public API** for callers.  It:

* Accepts either three `URI`s (loads resources itself) or three pre-loaded
  `Resource` objects (BenchmarX test harness mode).
* Bootstraps an empty `Transformation` root in the correspondence resource
  when it is used for the first time.
* Registers rules in the mandatory order: `Net2Net → Place2Place →
  Transition2Transition`.
* Exposes `sourceToTarget()` and `targetToSource()` as the two entry points.
* After each pass, delegates to `deleteUnreferencedTargetElements()` /
  `deleteUnreferencedSourceElements()` to propagate deletions.

```
Caller
  │
  ├─ sourceToTarget()
  │     ├─ Net2Net.sourceToTarget()
  │     ├─ Place2Place.sourceToTarget()
  │     ├─ Transition2Transition.sourceToTarget()
  │     └─ deleteUnreferencedTargetElements()
  │
  └─ targetToSource()
        ├─ Net2Net.targetToSource()
        ├─ Place2Place.targetToSource()
        ├─ Transition2Transition.targetToSource()
        └─ deleteUnreferencedSourceElements()
```

---

## 7. Incremental Behaviour & Hippocraticness

The transformation is **incremental by design**:

* `getOrCreateCorrModelElement(obj, desc)` returns the *existing* `Corr`
  from the static `elementsToCorr` map if one has been established in a
  previous run, so matched elements are never re-created.
* `getOrCreateTargetElem(corr, clazz)` / `getOrCreateSourceElem(corr, clazz)`
  only instantiate a new model element when the correspondence slot is still
  empty.
* Attribute synchronisation (e.g. `targetNet.name = n.name`) is always
  applied, but EMF change notifications suppress redundant store operations
  when the value has not changed.

**Hippocraticness (arc weights):** When a forward pass encounters a
`PTEdge`/`TPEdge` that already exists in the target (found via
`findFirst[...]`), the edge is *not* recreated; its `weight` value is left
unchanged.  A new edge is only created when none exists yet, and in that case
the weight defaults to **1**.

---

## 8. Deletion Propagation

EMF does not automatically propagate deletions across model boundaries.
The orchestrator handles this explicitly:

### Forward – `deleteUnreferencedTargetElements()`

Iterates over `Corr` entries whose `sourceElement` is `null` (EMF sets cross-
references to `null` when the referenced object is deleted from the resource).
For each such entry:

1. If the target element is a `Transition`, all incident `PTEdge` and `TPEdge`
   objects are scheduled for deletion first (to avoid dangling references
   inside the `pnw` model).
2. The target element itself is scheduled for deletion.
3. The `Corr` entry is scheduled for deletion.

All scheduled objects are deleted via `EcoreUtil.delete(e, true)`.

### Backward – `deleteUnreferencedSourceElements()`

Analogous: iterates over `Corr` entries whose `targetElement` is `null` and
deletes the corresponding source element together with the `Corr` entry.

---

## 9. Integration with BenchmarX

The transformation is registered as a **BXtend** tool in the
`BenchmarxPetrinetToPetrinetWeighted` test suite via `BXtendPn2Pnw.java`,
which implements the `BXToolForEMF<pn.Net, pnw.Net, Decisions>` adapter:

| BenchmarX lifecycle method              | Delegation                                      |
|-----------------------------------------|-------------------------------------------------|
| `initiateSynchronisationDialogue()`     | Creates in-memory resources; runs initial `sourceToTarget()`. |
| `performAndPropagateSourceEdit(edit)`   | Applies edit, then calls `pn2pnw.sourceToTarget()`. |
| `performAndPropagateTargetEdit(edit)`   | Applies edit, then calls `pn2pnw.targetToSource()`. |
| `performIdleSourceEdit(edit)`           | Applies edit only (no propagation).             |
| `performIdleTargetEdit(edit)`           | Applies edit only (no propagation).             |

The test suite covers four categories:

| Category              | Direction | Description                                           |
|-----------------------|-----------|-------------------------------------------------------|
| `BatchForward`        | fwd       | Creates complete source nets from scratch.            |
| `BatchBackward`       | bwd       | Creates complete target nets from scratch.            |
| `IncrementalForward`  | fwd       | Edits an existing source net (add/remove/change).     |
| `IncrementalBackward` | bwd       | Edits an existing target net (add/remove/change).     |

---

## 10. Building & Running

### Prerequisites

* Eclipse IDE with **Xtend** and **EMF** plugins installed.
* The following plug-in projects must be present in the same workspace:
  * `Petrinet` (source meta-model)
  * `PetrinetWeighted` (target meta-model)

### Compile

The project is an OSGi plug-in.  Open it in Eclipse and trigger a workspace
build (`Project → Build All`).  Xtend sources are compiled to Java by the
Xtend builder and placed in `xtend-gen/`.

### Run as part of BenchmarX

1. Open `BenchmarxPetrinetToPetrinetWeighted` in the same Eclipse workspace.
2. Right-click on any test class in
   `src/.../testsuite/batch/fwd/BatchForward.java` (or the other test classes)
   and choose **Run As → JUnit Test**.
3. Select **BXtend** in the tool parameter resolver to run only the BXtend
   implementation.

### Standalone usage

```java
// Obtain or create three EMF Resources in a shared ResourceSet
Resource source = ...;  // pn.xmi  – pn.Net root
Resource target = ...;  // pnw.xmi – pnw.Net root (may be empty)
Resource corr   = ...;  // corr.xmi – may be empty on first run

Pn2pnwTransformation t = new Pn2pnwTransformation(source, target, corr);

// Forward
t.sourceToTarget();

// Backward
t.targetToSource();

// Persist
source.save(null);
target.save(null);
corr.save(null);
```
