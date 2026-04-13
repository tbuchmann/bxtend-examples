# de.tbuchmann.bxtend.gantt2cpm

## Overview

This Eclipse plug-in implements the **Gantt ↔ CPM** bidirectional model transformation (BX) using the **BXtend** framework.  It is one of several tool-specific solutions evaluated in the [Benchmarx](https://github.com/eMoflon/benchmarx) benchmark for bidirectional transformations.

The transformation synchronises two complementary scheduling models:

| Model | Description |
|---|---|
| **Gantt** (`Gantt.ecore`) | Gantt chart model – activities with durations, and typed dependency edges between them. |
| **CPM** (`CPM.ecore`) | Critical Path Method (CPM) network – activities as arcs between milestone events. |

---

## The Transformation Problem

### Metamodels

**Gantt** (`Gantt.ecore`)

```
GanttDiagram
  name          : EString
  elements      : Element[*]  (containment, opposite: Element.diagram)
  incrementalID : EString

Element  (abstract)
  diagram       : GanttDiagram   (opposite: GanttDiagram.elements)

Activity extends Element
  name          : EString
  duration      : EInt
  outgoingDependencies : Dependency[*]   (opposite: Dependency.predecessor)
  incomingDependencies : Dependency[*]   (opposite: Dependency.successor)

Dependency extends Element
  predecessor   : Activity   (opposite: Activity.outgoingDependencies)
  successor     : Activity   (opposite: Activity.incomingDependencies)
  dependencyType: DependencyType   { StartStart, StartEnd, EndStart, EndEnd }
  offset        : EInt

DependencyType  (enum)
  StartStart  – predecessor starts → successor starts
  StartEnd    – predecessor starts → successor ends
  EndStart    – predecessor ends   → successor starts   (most common FS dependency)
  EndEnd      – predecessor ends   → successor ends
```

**CPM** (`CPM.ecore`)

```
CPMNetwork
  name          : EString
  elements      : Element[*]  (containment, opposite: Element.network)
  incrementalID : EString

Element  (abstract)
  network       : CPMNetwork   (opposite: CPMNetwork.elements)

Event extends Element
  number        : EInt
  outgoingActivities : Activity[*]   (opposite: Activity.sourceEvent)
  incomingActivities : Activity[*]   (opposite: Activity.targetEvent)

Activity extends Element
  name          : EString
  duration      : EInt
  sourceEvent   : Event   (opposite: Event.outgoingActivities)
  targetEvent   : Event   (opposite: Event.incomingActivities)
```

### Consistency Relation

Two models are considered *consistent* when every Gantt element has a matching CPM element as described in the following table:

| Gantt element | CPM element(s) | Attribute mapping |
|---|---|---|
| `GanttDiagram` | `CPMNetwork` | `name` is equal |
| `gantt.Activity` | `cpm.Activity` + 2 `cpm.Event`s | `name` and `duration` are equal; a fresh source event and target event are created for each activity |
| `gantt.Dependency` | `cpm.Activity` (arc) | `name = "<predecessor>-><successor>"`; `duration = offset`; `sourceEvent` / `targetEvent` are the events of the predecessor / successor CPM activities chosen according to `DependencyType` |

### The Structural Mismatch Challenge

The key challenge is a **structural asymmetry** between the two metamodels:

- In a **Gantt** model, activities are *nodes* and dependencies are *separate edge objects* connecting them.
- In a **CPM** network, everything is either a *node* (Event) or an *arc* (Activity).  Activities are arcs connecting event nodes; there are no separate dependency edge objects.

This means:
1. One `gantt.Activity` maps to *three* CPM elements: one `cpm.Activity` arc plus two bounding `cpm.Event` nodes.
2. One `gantt.Dependency` maps to *one* `cpm.Activity` arc that *shares* events with the CPM activities of its predecessor and successor.

The `DependencyType` attribute controls which events are shared:

```
StartStart: sourceEvent(pred) ─────────────── sourceEvent(succ)
StartEnd:   sourceEvent(pred) ─────────────── targetEvent(succ)
EndStart:   targetEvent(pred) ─────────────── sourceEvent(succ)   ← most common
EndEnd:     targetEvent(pred) ─────────────── targetEvent(succ)
```

### Naming Convention for Disambiguation

Because both Gantt activities and Gantt dependencies map to `cpm.Activity` arcs in the CPM network, the BXtend solution uses a naming convention to distinguish them during the backward pass:

- CPM activities derived from **Gantt activities** carry their original name (e.g. `"Design"`).
- CPM activities derived from **Gantt dependencies** carry an **arrow-encoded** name (e.g. `"Design->Build"`).

This simple convention avoids the need for typed correspondence subclasses.

---

## BXtend Solution Architecture

The transformation is implemented as a set of **Xtend** classes inside the `de.tbuchmann.bxtend.gantt2cpm.rules` package.  All artefacts follow the standard BXtend pattern:

```
de.tbuchmann.bxtend.gantt2cpm/
├── model/
│   ├── corresp.ecore                        Correspondence metamodel (Ecore)
│   └── corresp.genmodel                     Genmodel for code generation
└── src/
    └── de/tbuchmann/bxtend/gantt2cpm/
        ├── correspondence/gantt2cpm/         EMF-generated correspondence model code
        │   ├── Corr.java
        │   ├── BasicElem.java
        │   ├── Transformation.java
        │   ├── Gantt2cpmFactory.java
        │   └── Gantt2cpmPackage.java
        └── rules/
            ├── Elem2Elem.xtend               Abstract base rule (infrastructure)
            ├── Diagram2Network.xtend         Rule: GanttDiagram ↔ CPMNetwork
            ├── Activity2Activity.xtend       Rule: gantt.Activity ↔ cpm.Activity (+Events)
            ├── Dependency2Activity.xtend     Rule: gantt.Dependency ↔ cpm.Activity (arc)
            └── Gantt2cpmTransformation.xtend Top-level orchestrator
```

---

## Component Descriptions

### `Elem2Elem` (abstract base class)

The central infrastructure class shared by all rules.  It provides:

- References to the three EMF resources (`sourceModel`, `targetModel`, `corrModel`).
- EMF factory/package singletons for Gantt, CPM, and the correspondence model.
- A **static, shared `elementsToCorr` map** (`Map<EObject, Corr>`) that gives O(1) lookup of the correspondence entry for any model element.  Both the source and target side of each `Corr` are registered as keys.  The map is populated eagerly from the persisted correspondence XMI file during construction and is kept current as new correspondences are created at run time.
- Helper methods:
  - `getCorrModelElem(obj)` – looks up an existing `Corr`.
  - `getOrCreateCorrModelElement(obj, desc)` – returns or lazily creates a `Corr` for an element.
  - `getOrCreateSourceElem(corr, clazz)` / `getOrCreateTargetElem(corr, clazz)` – return or lazily create the Gantt / CPM element linked by a `Corr`.
  - `createSourceElement(clazz)` / `createTargetElement(clazz)` – factory shortcuts.
- Abstract `sourceToTarget()` / `targetToSource()` hooks for subclasses.
- A `ruleID` field set by each subclass that is stored in the `desc` attribute of every `Corr` created by that rule.

### `Diagram2Network`

Handles the symmetric root-container mapping `gantt.GanttDiagram ↔ cpm.CPMNetwork`.

| Direction | Action |
|---|---|
| **Forward (Gantt → CPM)** | Reads the single `GanttDiagram` from `sourceModel.contents.get(0)`; looks up / creates the matching `CPMNetwork`; copies `name`; adds to `targetModel.contents`. |
| **Backward (CPM → Gantt)** | Mirror image: reads the single `CPMNetwork`, looks up / creates the `GanttDiagram`, copies `name`. |

**Execution order:** This rule **must run first** so that the root container correspondence exists before `Activity2Activity` and `Dependency2Activity` try to resolve it.

### `Activity2Activity`

Handles the **1-to-3** mapping: one `gantt.Activity` maps to one `cpm.Activity` arc plus two bounding `cpm.Event` nodes.

**Forward (Gantt → CPM)**

| Gantt | CPM | Notes |
|---|---|---|
| `Activity.name` | `Activity.name` | Direct copy |
| `Activity.duration` | `Activity.duration` | Direct copy |
| (container `GanttDiagram`) | (container `CPMNetwork`) | Resolved via `elementsToCorr` |
| — | `Activity.sourceEvent` | Newly created `Event` with auto-incremented `number` |
| — | `Activity.targetEvent` | Newly created `Event` with auto-incremented `number` |

**Backward (CPM → Gantt)**

Only CPM activities whose `name` does **not** contain `"->"` are processed (dependency arcs are excluded).

| CPM | Gantt | Notes |
|---|---|---|
| `Activity.name` | `Activity.name` | Direct copy |
| `Activity.duration` | `Activity.duration` | Direct copy |
| (container `CPMNetwork`) | (container `GanttDiagram`) | Resolved via `elementsToCorr` |

**Event numbering:** A static counter `i` is lazily initialised by scanning all existing events in the target model for the highest current number, then increments for each new event to guarantee uniqueness.

### `Dependency2Activity`

Handles the **1-to-1** mapping between `gantt.Dependency` and a `cpm.Activity` arc that *shares* events with adjacent CPM activities.

**Forward (Gantt → CPM)**

| Gantt | CPM | Notes |
|---|---|---|
| `Dependency.predecessor.name + "->" + Dependency.successor.name` | `Activity.name` | Encodes relationship in name |
| `Dependency.offset` | `Activity.duration` | Lag/lead time |
| `DependencyType.StartStart` | `sourceEvent = pred.sourceEvent`, `targetEvent = succ.sourceEvent` | Event sharing |
| `DependencyType.StartEnd` | `sourceEvent = pred.sourceEvent`, `targetEvent = succ.targetEvent` | Event sharing |
| `DependencyType.EndStart` | `sourceEvent = pred.targetEvent`, `targetEvent = succ.sourceEvent` | Event sharing |
| `DependencyType.EndEnd` | `sourceEvent = pred.targetEvent`, `targetEvent = succ.targetEvent` | Event sharing |

**Pre-condition:** `Activity2Activity.sourceToTarget()` must have already executed so that every CPM activity (and its events) exists in the correspondence model.

**Backward (CPM → Gantt)**

Only CPM activities whose `name` contains `"->"` are processed here.  The arrow-encoded name is split to locate the predecessor and successor CPM activities, and the `DependencyType` is reverse-engineered by comparing which events are connected.

**Execution order:** This rule **must run after** `Activity2Activity`.

### `Gantt2cpmTransformation` (orchestrator)

The single entry point for callers.  It:

1. Loads (or accepts) the three EMF resources.
2. Bootstraps the correspondence model root if empty.
3. Instantiates and chains the rules in the mandatory order:
   `Diagram2Network` → `Activity2Activity` → `Dependency2Activity`.
4. Exposes `sourceToTarget()` and `targetToSource()` that drive the rule chain and then invoke deletion propagation.
5. Two constructors: one accepting file `URI`s (standalone use), one accepting pre-loaded `Resource` objects (Benchmarx adapter use).

---

## Correspondence Model

The correspondence metamodel (`model/corresp.ecore`) defines:

```
Transformation
  correspondences : Corr[*]   (containment)

Corr
  sourceElement : EObject     (reference into Gantt resource)
  targetElement : EObject     (reference into CPM resource)
  desc          : EString     (rule ID: "root" | "activity" | "dependency")

BasicElem extends Corr
```

A single `Transformation` root object is persisted in the correspondence XMI file.  Every matched pair of elements is represented by one `BasicElem` entry.  When an element is deleted from one side, the corresponding `Corr.sourceElement` or `Corr.targetElement` reference becomes `null` (set by EMF automatically via cross-resource reference resolution), which `Gantt2cpmTransformation` detects during the deletion-propagation step.

---

## Incrementality and Deletion Propagation

BXtend achieves incrementality without change events or deltas by relying on **correspondence-guided re-synchronisation**:

1. On each call to `sourceToTarget()` / `targetToSource()`, rules iterate over the *current* state of the source/target model.
2. For each element, `getOrCreateCorrModelElement` either re-uses an existing `Corr` (update / no-change scenario) or creates a new one (creation scenario).
3. After all rules have run, `detectSourceDeletions()` / `detectTargetDeletions()` scan for `Corr` entries with a `null` side.  These arise because EMF automatically clears cross-resource references when the referenced object is deleted via `EcoreUtil.delete`.
4. Orphaned elements on the other side are collected in a deletion list and removed with `EcoreUtil.delete(e, true)`, cascading into any contained children.

### Special case: orphaned `Event` nodes

When a `gantt.Activity` is deleted during the forward pass, its two bounding `cpm.Event` nodes may become isolated (no incoming or outgoing activity arcs remain).  After deleting the orphaned `cpm.Activity`, `deleteUnreferencedTargetElements()` performs a second scan to remove any `Event` with empty `outgoingActivities` and `incomingActivities` lists.

---

## Rule Execution Order

The three rules have strict ordering dependencies:

```
sourceToTarget():
  1. Diagram2Network      — creates CPMNetwork (needed by steps 2 & 3)
  2. Activity2Activity    — creates cpm.Activity + 2 cpm.Events per gantt.Activity
  3. Dependency2Activity  — wires cpm.Activity arcs using events from step 2

targetToSource():
  1. Diagram2Network      — creates GanttDiagram (needed by steps 2 & 3)
  2. Activity2Activity    — creates gantt.Activity per non-arrow cpm.Activity
  3. Dependency2Activity  — creates gantt.Dependency per arrow cpm.Activity
```

Violating this order results in `NullPointerException`s because rules look up correspondences created by earlier rules.

---

## Usage

### Programmatic (Java / Xtend) – standalone

```xtend
val tx = new Gantt2cpmTransformation(
    URI.createFileURI("path/to/gantt.xmi"),
    URI.createFileURI("path/to/cpm.xmi"),
    URI.createFileURI("path/to/corr.xmi")
)

// Propagate Gantt changes to CPM
tx.sourceToTarget()

// Propagate CPM changes back to Gantt
tx.targetToSource()
```

### Programmatic – pre-loaded resources (Benchmarx)

```xtend
val tx = new Gantt2cpmTransformation(sourceResource, targetResource, corrResource)
tx.sourceToTarget()
tx.targetToSource()
```

### Within Benchmarx

The `BenchmarxGanttToCPM` test project wraps this transformation via the `BXtendGantt2CPM` adapter class, which implements the Benchmarx `BXTool` interface.  The adapter maps Benchmarx initialise / propagate / reset calls to the corresponding `Gantt2cpmTransformation` methods:

| Benchmarx call | `Gantt2cpmTransformation` call |
|---|---|
| `initiateSynchronisationDialogue()` | creates resources, instantiates `Gantt2cpmTransformation`, calls `sourceToTarget()` |
| `performAndPropagateSourceEdit(edit)` | applies edit, calls `sourceToTarget()` |
| `performAndPropagateTargetEdit(edit)` | applies edit, calls `targetToSource()` |

---

## Dependencies

| Dependency | Role |
|---|---|
| `org.eclipse.emf.ecore` | Core EMF model management |
| `org.eclipse.xtend.lib` | Xtend runtime support |
| `org.eclipse.xtext.xbase.lib` | Xbase collection extensions |
| `de.ubt.ai1.m2m.bxtend` | BXtend framework base classes |
| `Gantt` (bundle) | Gantt metamodel and EMF-generated code |
| `CPM` (bundle) | CPM metamodel and EMF-generated code |

---

## Project Layout

```
de.tbuchmann.bxtend.gantt2cpm/
├── META-INF/
│   └── MANIFEST.MF
├── model/
│   ├── corresp.ecore          Correspondence metamodel (Ecore)
│   └── corresp.genmodel       Genmodel for EMF code generation
├── src/
│   └── de/tbuchmann/bxtend/gantt2cpm/
│       ├── correspondence/    EMF-generated correspondence model code
│       └── rules/             Transformation rules (Xtend)
│           ├── Elem2Elem.xtend
│           ├── Diagram2Network.xtend
│           ├── Activity2Activity.xtend
│           ├── Dependency2Activity.xtend
│           └── Gantt2cpmTransformation.xtend
├── xtend-gen/                 Auto-generated Java from Xtend sources
├── bxtend-gantt2cpm-1.0.0.jar Pre-built JAR (used by the Benchmarx project)
└── README.md                  This file
```

---

## Relation to Other Solutions

The Benchmarx benchmark evaluates several implementations of the same Gantt ↔ CPM transformation:

| Implementation | Technology |
|---|---|
| `BXtendGantt2CPM` | **This project** – BXtend (Xtend + correspondence model) |
| `BXLangGantt2Cpm` | BXLang declarative BX language |
| `BXAgentGantt2Cpm` | BX-Agent (LLM-driven transformation agent) |
| `IBeXTGGGantt2CPM` | eMoflon IBeX TGG rules |
| `MediniQVTGantt2CPM` | MediniQVT (QVT-Relations) |
| `PlainJavaUbtGantt2Cpm` | Plain Java (manual implementation) |

All solutions are exercised against the same Benchmarx test suite in `BenchmarxGanttToCPM`, covering batch forward/backward passes, incremental insertions, incremental deletions, attribute changes, and stability tests.
