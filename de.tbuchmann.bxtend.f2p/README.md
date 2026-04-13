# Families-to-Persons — BXtend Bidirectional Transformation

## Table of Contents

1. [Overview](#overview)
2. [The Transformation Problem](#the-transformation-problem)
   - [Families Metamodel](#families-metamodel)
   - [Persons Metamodel](#persons-metamodel)
   - [Transformation Rules](#transformation-rules)
   - [Inherent Ambiguities](#inherent-ambiguities)
3. [BXtend Framework Concepts](#bxtend-framework-concepts)
4. [Solution Architecture](#solution-architecture)
   - [Project Layout](#project-layout)
   - [Correspondence Model](#correspondence-model)
   - [Rule Hierarchy](#rule-hierarchy)
   - [Transformation Directions](#transformation-directions)
5. [Decision Strategies](#decision-strategies)
6. [Key Design Decisions](#key-design-decisions)
7. [Building and Running](#building-and-running)
8. [Dependencies](#dependencies)

---

## Overview

This project implements a **bidirectional, incremental, and synchronising** model
transformation between a *Families* model and a *Persons* model using the
[BXtend](https://github.com/tbuchmann/bxtend) framework.

The transformation is a standard benchmark case in the bidirectional-transformation (BX)
community, defined and used by the
[BenchmarX](https://github.com/eMoflon/benchmarx) project.  This implementation
exercises all three propagation directions supported by BXtend:

| Direction | Method | Description |
|-----------|--------|-------------|
| **Forward** | `sourceToTarget()` | Families → Persons |
| **Backward** | `targetToSource()` | Persons → Families |
| **Synchronisation** | `synch()` | Reconcile concurrent edits in both models |

---

## The Transformation Problem

### Families Metamodel

```
FamilyRegister
  └─* Family  (name : String)
        ├─0..1 father    : FamilyMember
        ├─0..1 mother    : FamilyMember
        ├─*    sons      : FamilyMember
        └─*    daughters : FamilyMember

FamilyMember  (name : String)   // first name only
```

A `FamilyRegister` contains zero or more `Family` objects.  Each `Family` has a
**name** (the shared surname), an optional **father**, an optional **mother**, zero or
more **sons**, and zero or more **daughters**.  Every `FamilyMember` carries only a
**first name**; the family name is derived from the containing `Family`.

### Persons Metamodel

```
PersonRegister
  └─* Person  (name : String, birthday : Date)   // abstract
        ├── Male   extends Person
        └── Female extends Person
```

A `PersonRegister` contains a flat list of `Person` objects.  Each `Person` has a
**full name** stored in the convention `"<familyName>, <firstName>"` (e.g.
`"Simpson, Bart"`) and a **birthday** attribute.  Gender is encoded by the concrete
subtype: `Male` or `Female`.

### Transformation Rules

The correspondence between the two metamodels is:

| Families side | Persons side | Rule class |
|---|---|---|
| `FamilyRegister` | `PersonRegister` | `Register2Register` |
| `FamilyMember` as *father* or *son* | `Male` | `FatherSon2Male` |
| `FamilyMember` as *mother* or *daughter* | `Female` | `MotherDaughter2Female` |

**Name encoding:** A `FamilyMember` `"Bart"` in `Family "Simpson"` maps to a `Male`
with `name = "Simpson, Bart"`.

**Gender encoding:** The role in the `Family` (father/son → `Male`;
mother/daughter → `Female`) determines the concrete `Person` type.

### Inherent Ambiguities

The backward transformation (Persons → Families) is **not injective**: the same Persons
model can be mapped back to multiple valid Families models.  The key ambiguities are:

1. **Which family does a new person join?**  
   Multiple `Family` objects may share the same surname; or a brand-new family could be
   created.

2. **Is the person a parent or a child?**  
   A `Male` named `"Simpson, Homer"` could become the *father* or a *son* in the
   `Simpson` family.

3. **Should empty families be removed?**  
   After removing the last member from a family, should the empty `Family` object be
   deleted from the source model?

These three decisions are **not derivable from the data alone** and are therefore
externalised into a pluggable *Decision Strategy* (see
[Decision Strategies](#decision-strategies)).

---

## BXtend Framework Concepts

BXtend is a model-transformation framework built on top of EMF/Xtend that provides:

* **Three-way synchronisation** — forward, backward, and concurrent (synch) directions.
* **Correspondence model** — a separate, persisted model that records which source
  element is currently paired with which target element.
* **Rule-based architecture** — transformations are decomposed into small, focused
  `Elem2Elem` rule classes, each responsible for one metaclass pair.
* **Incremental propagation** — rules operate on the delta between the current state and
  the correspondence model rather than rebuilding the target from scratch.

---

## Solution Architecture

### Project Layout

```
de.tbuchmann.bxtend.f2p/
├── model/
│   ├── corresp.ecore          # Correspondence metamodel (Corr, Transformation)
│   └── corresp.genmodel       # EMF generator model
├── src/
│   └── de/tbuchmann/bxtend/f2p/rules/
│       ├── Elem2Elem.xtend                          # Abstract base rule
│       ├── Families2personsTransformation.xtend     # Top-level coordinator
│       ├── Register2Register.xtend                  # Root-register rule
│       ├── FamilyMember2Person.xtend                # Abstract member rule
│       ├── FatherSon2Male.xtend                     # Male-gender rule
│       ├── MotherDaughter2Female.xtend              # Female-gender rule
│       └── decisions/
│           ├── TargetToSourceDecision.xtend         # Decision strategy interface
│           ├── DefaultTargetToSourceDecision.xtend  # Automated default
│           ├── ConfigurableTargetToSourceDecision.xtend # Flag-based configuration
│           ├── UserTargetToSourceDecision.xtend     # Interactive Swing dialog
│           └── util/
│               └── Utils.xtend                      # Shared helper methods
└── META-INF/
    └── MANIFEST.MF
```

### Correspondence Model

The correspondence model (`corresp.ecore`) records the element-level pairings created
during the transformation:

```
Transformation
  └─* correspondences : Corr
        ├── sourceElement : EObject   (a Families element)
        ├── targetElement : EObject   (a Persons element)
        └── desc          : String    (rule identifier)

BasicElem  extends Corr              (currently the only concrete type)
```

Two static in-memory hash maps maintained in `Elem2Elem` provide O(1) lookup:

* `elementsToCorr : Map<EObject, Corr>` — from any source or target element to its
  correspondence entry.
* `corrToName : Map<Corr, String>` — from a correspondence entry to the name snapshot
  captured at the time of the last synchronisation.

These maps are rebuilt from the serialised correspondence model each time a rule is
constructed.

### Rule Hierarchy

```
Elem2Elem  (abstract)
├── Register2Register
└── FamilyMember2Person  (abstract)
    ├── FatherSon2Male
    └── MotherDaughter2Female
```

`Elem2Elem` provides:
- References to the three model `Resource` objects.
- Factory singletons for both metamodels and the correspondence metamodel.
- The in-memory correspondence index (`elementsToCorr`, `corrToName`).
- Helper methods: `getOrCreateCorrModelElement`, `getOrCreateSourceElem`,
  `getOrCreateTargetElem`.

`FamilyMember2Person` adds generic logic shared by both concrete member rules:
- `getOrCreateFamily` — selects or creates the target `Family` for a backward-transformed
  `Person`.
- `addToFamily` — inserts a `FamilyMember` into the parent or child slot according to the
  decision strategy, demoting an existing parent if necessary.
- `getOrCreatePersonElement` — retrieves or creates a `Person`, handling type changes
  (Male ↔ Female) with birthday preservation.
- `transformPerson` — full backward-transformation logic for a single `Person`.
- `addPerson` — full forward-transformation logic for a single `FamilyMember`.
- `synchFamilyMember` — synchronisation logic: finds the best match in the unmatched
  person list, or creates a new element when none exists.

### Transformation Directions

#### Forward — `sourceToTarget()`

```
Families2personsTransformation.sourceToTarget()
  1. Register2Register.sourceToTarget()
     → for each FamilyRegister: get/create PersonRegister, link via Corr
  2. MotherDaughter2Female.sourceToTarget()
     → for each mother/daughter: addPerson("MotherDaughter2Female")
  3. FatherSon2Male.sourceToTarget()
     → for each father/son:     addPerson("FatherSon2Male")
  4. Update familiesMap index
  5. deleteUnreferencedTargetElements()   // clean up orphaned Persons
```

#### Backward — `targetToSource()`

```
Families2personsTransformation.targetToSource()
  1. Register2Register.targetToSource()
     → for each PersonRegister: get/create FamilyRegister, link via Corr
  2. MotherDaughter2Female.targetToSource()
     → for each Female: getOrCreateCorrModelElement, transformPerson(female)
  3. FatherSon2Male.targetToSource()
     → for each Male:   getOrCreateCorrModelElement, transformPerson(male)
  4. deleteUnreferencedSourceElements()  // clean up orphaned FamilyMembers
```

#### Synchronisation — `synch()`

```
Families2personsTransformation.synch()
  1. Register2Register.synch()
     → pair registers, create missing ones on either side
  2. MotherDaughter2Female.synch()
     → collect unmatched Females
     → for each mother/daughter: synchFamilyMember(pList, "MotherDaughter2Female")
     → remaining unmatched Females → transformPerson (backward propagation)
  3. FatherSon2Male.synch()
     → collect unmatched Males
     → for each father/son:     synchFamilyMember(pList, "FatherSon2Male")
     → remaining unmatched Males → transformPerson (backward propagation)
  4. deleteUnreferencedSourceElements()
  5. deleteUnreferencedTargetElements()
```

The synchronisation strategy is **source-led**: it iterates over the source (Families)
model and tries to find a matching `Person` for each `FamilyMember`.  Unmatched
`Person` elements left in the pool after all members have been processed are treated as
having been added on the Persons side and are propagated back.

---

## Decision Strategies

The `TargetToSourceDecision` interface abstracts all ambiguous choices during the backward
transformation.  Three implementations are provided:

### `DefaultTargetToSourceDecision`

| Decision | Behaviour |
|---|---|
| Which family? | Always picks the first candidate; if none, creates a new one. |
| Parent or child? | Parent when the slot is free; otherwise child. |
| Delete empty family? | Always `true`. |
| Candidate list size | 1 (only one candidate needed). |

Designed for **automated testing and batch usage**.

### `ConfigurableTargetToSourceDecision`

Accepts four boolean flags at construction time:

| Flag | Effect |
|---|---|
| `alwaysNewFamily` | Every `Person` is always placed in a brand-new `Family`. |
| `preferParent` | Tries to fill the parent slot first; scans all candidates when looking for a free slot. |
| `forceParent` | Always sets as parent regardless of vacancy; may demote existing parent. |
| `deleteEmptyFamilies` | Configures whether empty families are deleted. |

Ideal for **parameterised test suites** that need to cover multiple policy combinations.

### `UserTargetToSourceDecision`

Before each transformation pass, presents Swing dialogs asking the user to choose:
- Always create a new family? (Yes/No)
- Prefer parent role? (Yes/No)
- Delete empty families? (Yes/No)

When multiple candidate families exist and the user preference does not resolve the
ambiguity, an additional dialog lets the user pick the exact family from a list.

Intended for **interactive tool demonstrations**.

---

## Key Design Decisions

### 1. Static Families Name Index (`familiesMap`)

Rather than scanning the entire `FamilyRegister` for every backward-transformed `Person`,
all `Family` objects are indexed by surname in a static `Map<String, List<Family>>`.
This reduces per-person lookup time from O(n) to O(1).  The map is shared across all
rule instances and is updated after every `sourceToTarget()` pass.

### 2. Pluggable Decision Strategy (Strategy Pattern)

The `TargetToSourceDecision` interface cleanly separates **transformation logic** from
**policy decisions**, following the Strategy design pattern.  Switching the policy
(e.g. from default to user-interactive) requires only a single `configure()` call on the
`Families2personsTransformation` instance.

### 3. Name-Based Incremental Matching in `synch()`

During synchronisation, `FamilyMember` elements are matched to `Person` elements by
full name (`"<familyName>, <firstName>"`).  This avoids false matches when elements are
renamed and correctly handles renaming as an atomic operation (delete + create).

### 4. Birthday Preservation Across Gender Changes

When the backward transformation changes the concrete type of a `Person` (e.g. a
`FatherSon2Male` correspondence is now tracked against a `Female`), the old element is
deleted and a fresh one of the correct type is created.  The `birthday` attribute is
read before deletion and re-applied to the new element, so information is not lost.

### 5. Empty-Family Lifecycle

After a `FamilyMember` is moved to a different family during backward propagation, the
original family may become empty (no father, no mother, no sons, no daughters).  The
decision strategy's `deleteEmptyFamily()` method decides whether to delete it.  This
allows policies ranging from "always clean up" to "keep even empty families".

### 6. Correspondence Model Persistence

The correspondence model is a first-class EMF resource that can be serialised to XMI and
reloaded across sessions.  This makes the transformation truly **incremental**: only the
elements that changed since the last serialisation need to be re-evaluated.

---

## Building and Running

This project is an Eclipse plug-in project and is built inside an Eclipse IDE with
the following tooling installed:

- Eclipse Modeling Tools (EMF, Ecore)
- Xtext / Xtend SDK
- BXtend framework (`de.ubt.ai1.m2m.bxtend`)

### Steps

1. Import the following projects into your Eclipse workspace:
   - `Families` (metamodel plug-in)
   - `Persons` (metamodel plug-in)
   - `de.tbuchmann.bxtend.f2p` (this project)
2. Ensure all plug-in dependencies are resolved (see `META-INF/MANIFEST.MF`).
3. Run Xtend code generation (the `xtend-gen/` folder is populated automatically by the
   Xtend builder).
4. Use `Families2personsTransformation` as the entry point, passing EMF `Resource` or
   `URI` instances for the source, target, and correspondence models.

```xtend
// Example: forward transformation from resource objects
val transformation = new Families2personsTransformation(sourceRes, targetRes, corrRes)
transformation.configure(new DefaultTargetToSourceDecision())
transformation.sourceToTarget()
targetRes.save(emptyMap)
corrRes.save(emptyMap)
```

```xtend
// Example: backward transformation with configurable policy
val transformation = new Families2personsTransformation(sourceRes, targetRes, corrRes)
transformation.configure(
    new ConfigurableTargetToSourceDecision(
        /* alwaysNewFamily */ false,
        /* preferParent    */ true,
        /* forceParent     */ false,
        /* deleteEmpty     */ true
    )
)
transformation.targetToSource()
sourceRes.save(emptyMap)
corrRes.save(emptyMap)
```

---

## Dependencies

| Bundle | Purpose |
|---|---|
| `org.eclipse.emf.ecore` | EMF core runtime |
| `Families` | Families metamodel plug-in |
| `Persons` | Persons metamodel plug-in |
| `org.eclipse.xtext.xbase.lib` | Xtend/Xbase runtime library |
| `org.eclipse.xtend.lib` | Xtend annotation processor support |
| `org.eclipse.xtend.lib.macro` | Xtend active annotation support |
| `de.ubt.ai1.m2m.bxtend` | BXtend framework |
