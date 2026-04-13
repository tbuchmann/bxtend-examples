# de.tbuchmann.bxtend.pdb12pdb2

## Overview

This Eclipse plug-in implements the **PDB1 ↔ PDB2** bidirectional model transformation (BX) using the **BXtend** framework.  It is one of several tool-specific solutions evaluated in the [Benchmarx](https://github.com/eMoflon/benchmarx) benchmark for bidirectional transformations.

The transformation synchronises two structurally similar but schema-incompatible *person-database* models:

| Model | Description |
|---|---|
| **PDB1** (`pdb1.ecore`) | "Split-name" database – each `Person` stores its name as separate `firstName` and `lastName` attributes. |
| **PDB2** (`pdb2.ecore`) | "Full-name" database – each `Person` stores its complete name in a single `name` attribute. |

---

## The Transformation Problem

### Metamodels

**PDB1** (`pdb1.ecore`)

```
Database
  name        : EString
  persons     : Person[*]  (containment, opposite: Person.database)

Person
  firstName   : EString
  lastName    : EString
  birthday    : EString
  placeOfBirth: EString
  id          : EString
  database    : Database   (opposite: Database.persons)
```

**PDB2** (`pdb2.ecore`)

```
Database
  name        : EString
  persons     : Person[*]  (containment, opposite: Person.database)

Person
  name        : EString       ← the key difference
  birthday    : EString
  placeOfBirth: EString
  id          : EString
  database    : Database   (opposite: Database.persons)
```

### Consistency Relation

Two models are considered *consistent* when:

- Every `pdb1.Database` is matched with a `pdb2.Database` with the same `name`.
- Every `pdb1.Person` in that database is matched with a `pdb2.Person` where:
  - `pdb2.Person.name == pdb1.Person.firstName + " " + pdb1.Person.lastName`
  - `birthday`, `placeOfBirth`, and `id` are equal in both persons.

### The Non-Determinism Challenge

The **backward** direction (PDB2 → PDB1) must split the single PDB2 `name` string back into `firstName` and `lastName`.  Because a name like `"Konrad Hermann Joseph Adenauer"` can be split at any of its three spaces, the split point is **inherently ambiguous**.

The BXtend solution addresses this with a pluggable **decision strategy**.

---

## BXtend Solution Architecture

The transformation is implemented as a set of **Xtend** classes inside the `de.tbuchmann.bxtend.pdb12pdb2.rules` package.  All artefacts follow the standard BXtend pattern:

```
de.tbuchmann.bxtend.pdb12pdb2/
├── model/
│   └── corresp.ecore                        Correspondence metamodel
└── src/
    └── de/tbuchmann/bxtend/pdb12pdb2/
        ├── correspondence/pdb12pdb2/         EMF-generated correspondence model code
        │   ├── Corr.java
        │   ├── BasicElem.java
        │   ├── Transformation.java
        │   ├── Pdb12pdb2Factory.java
        │   └── Pdb12pdb2Package.java
        └── rules/
            ├── Elem2Elem.xtend               Abstract base rule
            ├── Database2Database.xtend       Rule: pdb1.Database ↔ pdb2.Database
            ├── Person2Person.xtend           Rule: pdb1.Person ↔ pdb2.Person
            ├── Pdb12pdb2Transformation.xtend Top-level orchestrator
            └── decisions/
                ├── TargetToSourceDecision.xtend              Strategy interface
                └── ConfigurableTargetToSourceDecision.xtend  Built-in implementation
```

### Key Design Decisions

| Concern | Solution |
|---|---|
| **Incrementality** | A *correspondence model* (`corresp.ecore`) persists links between matched PDB1 and PDB2 elements across transformation runs. |
| **Deletion propagation** | After each propagation pass, "dangling" correspondences (where one side has been deleted) are collected and used to delete the orphaned counterpart. |
| **Non-determinism** | Injected `TargetToSourceDecision` strategy resolves the name-split ambiguity without hard-coding a policy inside the rule. |
| **Rule ordering** | `Database2Database` always runs before `Person2Person` so that the parent `Database` container exists in the target model before persons are linked to it. |

---

## Component Descriptions

### `Elem2Elem` (abstract base class)

The central infrastructure class shared by all rules.  It provides:

- References to the three EMF resources (`sourceModel`, `targetModel`, `corrModel`).
- EMF factory/package singletons for PDB1, PDB2, and the correspondence model.
- A **static, shared `elementsToCorr` map** (`Map<EObject, Corr>`) that gives O(1) lookup of the correspondence entry for any model element.  The map is populated in the constructor from the persisted correspondence model and updated on-the-fly when new correspondences are created.
- Helper methods:
  - `getOrCreateCorrModelElement(obj, desc)` – returns or lazily creates a `Corr` for an element.
  - `getOrCreateSourceElem(corr, clazz)` / `getOrCreateTargetElem(corr, clazz)` – return or lazily create the PDB1 / PDB2 element linked by a `Corr`.
- Abstract `sourceToTarget()` / `targetToSource()` hooks for subclasses.
- `configure(TargetToSourceDecision)` – injects the name-splitting strategy.

### `Database2Database`

Handles the symmetric `pdb1.Database ↔ pdb2.Database` mapping.

| Direction | Action |
|---|---|
| **Forward (PDB1 → PDB2)** | For each `pdb1.Database`: look up / create a `Corr`; look up / create the matching `pdb2.Database`; copy `name`; add to `targetModel.contents`. |
| **Backward (PDB2 → PDB1)** | Mirror image: copies `name` from `pdb2.Database` back to `pdb1.Database`. |

Because both metamodels define `Database` identically, this rule is perfectly symmetric.

### `Person2Person`

Handles the asymmetric `pdb1.Person ↔ pdb2.Person` mapping.

**Forward (PDB1 → PDB2)**

| PDB1 attribute | PDB2 attribute | Mapping |
|---|---|---|
| `firstName + " " + lastName` | `name` | Deterministic concatenation |
| `birthday` | `birthday` | Direct copy |
| `placeOfBirth` | `placeOfBirth` | Direct copy |
| `id` | `id` | Direct copy |
| `eContainer` (pdb1.Database) | `database` (pdb2.Database) | Resolved via `elementsToCorr` |

**Backward (PDB2 → PDB1)**

| PDB2 attribute | PDB1 attribute | Mapping |
|---|---|---|
| `name` | `firstName` + `lastName` | Split via `TargetToSourceDecision`; **only re-applied when the concatenated PDB1 name no longer matches the PDB2 name** (incremental guard) |
| `birthday` | `birthday` | Direct copy |
| `placeOfBirth` | `placeOfBirth` | Direct copy |
| `id` | `id` | Direct copy |
| `eContainer` (pdb2.Database) | `database` (pdb1.Database) | Resolved via `elementsToCorr` |

The incremental guard (`if source.firstName + " " + source.lastName != target.name`) ensures that a previously user-chosen split is not overwritten on subsequent incremental runs where only unrelated attributes changed.

### `Pdb12pdb2Transformation` (orchestrator)

The single entry point for callers.  It:

1. Loads (or accepts) the three EMF resources.
2. Bootstraps the correspondence model root if empty.
3. Instantiates and chains the rules in the correct order: `Database2Database` then `Person2Person`.
4. Applies the default `ConfigurableTargetToSourceDecision(-1)` (last-space split).
5. Exposes `sourceToTarget()` and `targetToSource()` methods that drive the rule chain and then invoke deletion propagation.

### `TargetToSourceDecision` (strategy interface)

```xtend
interface TargetToSourceDecision {
    def String getFirstName(String name)
    def String getLastName(String name)
}
```

Callers may provide their own implementation or configure the built-in one.

### `ConfigurableTargetToSourceDecision` (built-in strategy)

Splits a name at the n-th space, controlled by an integer `spacePosition` parameter:

| `spacePosition` | `firstName` | `lastName` |
|---|---|---|
| `0` | (empty) | complete name |
| `1` | first token | all remaining tokens |
| `n > 1` | first n tokens | all remaining tokens |
| `-1` (default) | all tokens except the last | last token |

**Example – `"Konrad Hermann Joseph Adenauer"`:**

| `spacePosition` | `firstName` | `lastName` |
|---|---|---|
| `0` | _(empty)_ | `Konrad Hermann Joseph Adenauer` |
| `1` | `Konrad` | `Hermann Joseph Adenauer` |
| `-1` | `Konrad Hermann Joseph` | `Adenauer` |

---

## Correspondence Model

The correspondence metamodel (`model/corresp.ecore`) defines:

```
Transformation
  correspondences : Corr[*]   (containment)

Corr
  sourceElement : EObject     (reference into PDB1 resource)
  targetElement : EObject     (reference into PDB2 resource)
  desc          : EString     (rule ID, e.g. "Database2Database")

BasicElem extends Corr
```

A single `Transformation` root object is persisted in the correspondence XMI file.  Every matched pair of elements is represented by one `BasicElem` entry.  When an element is deleted from one side, the corresponding `Corr.sourceElement` or `Corr.targetElement` reference becomes `null` (set by EMF automatically), which `Pdb12pdb2Transformation` detects during the deletion-propagation step.

---

## Incrementality and Deletion Propagation

BXtend achieves incrementality without change events or deltas: it relies on **correspondence-guided re-synchronisation**:

1. On each call to `sourceToTarget()` / `targetToSource()`, rules iterate over the *current* state of the source/target model.
2. For each element, `getOrCreateCorrModelElement` either re-uses an existing `Corr` (update scenario) or creates a new one (creation scenario).
3. After all rules have run, `detectSourceDeletions()` / `detectTargetDeletions()` scan for `Corr` entries with a `null` side.  These arise because EMF automatically clears cross-resource references when the referenced object is deleted via `EcoreUtil.delete`.
4. Orphaned elements on the other side are collected in a deletion list and removed with `EcoreUtil.delete(e, true)`, cascading into any contained children.

---

## Usage

### Programmatic (Java / Xtend)

```xtend
// Load from URIs (resources are loaded into a shared ResourceSet automatically)
val tx = new Pdb12pdb2Transformation(
    URI.createFileURI("path/to/source.xmi"),
    URI.createFileURI("path/to/target.xmi"),
    URI.createFileURI("path/to/corr.xmi")
)

// Optional: override the default last-space name-split strategy
tx.configure(new ConfigurableTargetToSourceDecision(1))  // split at first space

// Propagate source changes to target
tx.sourceToTarget()

// Propagate target changes to source
tx.targetToSource()
```

### Within Benchmarx

The `BenchmarxPdb1ToPdb2` test project wraps this transformation via the `BXtendPdb12Pdb2` adapter class, which implements the Benchmarx `BXTool` interface.  The adapter maps Benchmarx initialise / propagate / reset calls to the corresponding `Pdb12pdb2Transformation` methods.

---

## Dependencies

| Dependency | Role |
|---|---|
| `org.eclipse.emf.ecore` | Core EMF model management |
| `org.eclipse.xtend.lib` | Xtend runtime support |
| `org.eclipse.xtext.xbase.lib` | Xbase collection extensions |
| `de.ubt.ai1.m2m.bxtend` | BXtend framework base classes |
| `PDB1` (bundle) | PDB1 metamodel and generated code |
| `PDB2` (bundle) | PDB2 metamodel and generated code |

---

## Project Layout

```
de.tbuchmann.bxtend.pdb12pdb2/
├── META-INF/
│   └── MANIFEST.MF
├── model/
│   ├── corresp.ecore          Correspondence metamodel (Ecore)
│   └── corresp.genmodel       Genmodel for code generation
├── src/
│   └── de/tbuchmann/bxtend/pdb12pdb2/
│       ├── correspondence/    EMF-generated correspondence model code
│       └── rules/             Transformation rules (Xtend)
├── xtend-gen/                 Auto-generated Java from Xtend sources
├── bxtend-pdb12pdb2-1.0.0.jar Pre-built JAR (used by the Benchmarx project)
└── README.md                  This file
```

---

## Relation to Other Solutions

The Benchmarx benchmark evaluates several implementations of the same PDB1 ↔ PDB2 transformation:

| Implementation | Technology |
|---|---|
| `BXtendPdb12Pdb2` | **This project** – BXtend (Xtend + correspondence model) |
| `BXLangPdb12Pdb2` | BXLang declarative BX language |
| `BXAgentPdb12Pdb2` | BX-Agent (LLM-driven transformation agent) |
| `JavaPdb12Pdb2` | Plain Java (manual implementation) |
| `IBeXTGGPDB1ToPDB2` | eMoflon IBeX TGG rules |
| `MediniQVTPdb12Pdb2` | MediniQVT (QVT-Relations) |

All solutions are exercised against the same Benchmarx test suite in `BenchmarxPdb1ToPdb2`.
