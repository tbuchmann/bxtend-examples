# BXtend Ecore-to-SQL Transformation

A **bidirectional, incremental model transformation** between the EMF Ecore metamodel
(`Ecore.ecore`) and a relational SQL metamodel (`SQL.ecore`), implemented with the
**BXtend** framework as part of the [Benchmarx](https://github.com/eMoflon/benchmarx)
benchmark suite.

---

## Table of Contents

1. [Transformation Problem](#transformation-problem)
2. [Metamodels](#metamodels)
3. [Mapping Rules Overview](#mapping-rules-overview)
4. [Detailed Mapping](#detailed-mapping)
   - [EPackage ↔ Schema](#epackage-schema)
   - [EClass ↔ Table](#eclass-table)
   - [Generalisation ↔ Foreign-Key Hierarchy](#generalisation-foreign-key-hierarchy)
   - [EAttribute ↔ Column / Auxiliary Table](#eattribute-column-auxiliary-table)
   - [EReference ↔ Foreign-Key Column / Relation Table](#ereference-foreign-key-column-relation-table)
5. [The Annotation Mechanism](#the-annotation-mechanism)
6. [BXtend Solution Architecture](#bxtend-solution-architecture)
   - [Correspondence Model](#correspondence-model)
   - [Rule Pipeline](#rule-pipeline)
   - [Incremental Deletion Handling](#incremental-deletion-handling)
7. [Source-File Guide](#source-file-guide)
8. [How to Run the Tests](#how-to-run-the-tests)
9. [Test Coverage](#test-coverage)
10. [Known Limitations](#known-limitations)

---

## Transformation Problem

Object-oriented models expressed in the EMF Ecore formalism and relational database
schemas expressed in SQL are two ubiquitous but structurally very different notations.
The **Ecore-to-SQL** case asks for a bidirectional mapping between them such that:

- A forward propagation (`sourceToTarget`) derives a SQL schema from an Ecore package.
- A backward propagation (`targetToSource`) reconstructs an Ecore package from a SQL schema.
- Both directions work **incrementally**: only those elements that have actually changed need to
  be updated; unchanged elements and their correspondences are preserved across runs.
- Both directions handle **deletions**: when an element is removed on one side the
  transformation automatically removes its counterpart on the other side.

The problem was originally specified and benchmarked in the Benchmarx framework. The
benchmark evaluates both batch and incremental behaviour (insertions, deletions, renames,
multiplicity changes, inheritance restructuring) in both the forward and backward directions.

---

## Metamodels

### Source: Ecore (EMF built-in)

The source metamodel is the built-in EMF `Ecore.ecore`. The transformation handles the
following Ecore concepts:

| Ecore concept | Description |
|---|---|
| `EPackage` | A namespace container for classifiers |
| `EClass` | A class (abstract or concrete) |
| `EAttribute` | A typed attribute of a class (single-valued or multi-valued) |
| `EReference` | An association between classes (containment or cross, uni- or bidirectional) |
| `ESuperTypes` | Single-inheritance generalisation between classes |

### Target: SQL (`SQL.ecore`)

The custom SQL metamodel is located at
`/examples/ecoretosql/metamodels/SQL/model/SQL.ecore`.
Key concepts:

| SQL concept | Description |
|---|---|
| `Schema` | Top-level container, equivalent to a database/schema |
| `Table` | A named table inside a schema |
| `Column` | A typed column inside a table (with optional `NOT NULL`, `UNIQUE`, `AUTO_INCREMENT` properties) |
| `PrimaryKey` | A primary-key constraint on one column |
| `ForeignKey` | A foreign-key constraint linking a column to a referenced table, with optional `ON DELETE CASCADE / SET NULL` events |
| `Annotation` | A string tag on any `ModelElement`; used extensively by this transformation to encode semantic metadata |

---

## Mapping Rules Overview

```
Ecore                     SQL
─────────────────────     ────────────────────────────────────────────
EPackage              →   Schema
EClass                →   Table  (+sentinel EObject table)
EClass (abstract)     →   Table  (annotated "abstract")
ESuperTypes           →   ForeignKey "superType" / "root"
EAttribute [0..1]     →   Column  (annotated "attribute", "single")
EAttribute [0..*]     →   Table   (annotated "attribute", "multi")
EReference containment→   Column  (FK from child table → parent table)
EReference cross [0..1]→  Column  (FK in source table, ON DELETE SET NULL)
EReference cross [0..*]→  Table   (relation table, annotated "cross", "multi")
EReference bidir cross →  Table   (single shared relation table)
```

---

## Detailed Mapping

### EPackage ↔ Schema

Each `EPackage` becomes a `Schema` with the same name.

In addition, a special sentinel table `EObject` is created once inside the schema. This
table has a single `id INT NOT NULL AUTO_INCREMENT PRIMARY KEY` column and acts as the
**global object identity table**: every class table has a foreign-key column in the
`EObject` table that guarantees a unique identity across the entire schema, replicating
the EMF object-identity semantics.

**Backward:** Each `Schema` (identified by the `"package"` annotation) becomes an
`EPackage`. Name, namespace prefix, and namespace URI are all set to the schema name.

---

### EClass ↔ Table

Each `EClass` (abstract or concrete) becomes a `Table` using the **class-per-table**
inheritance strategy. The table receives:

- A `PRIMARY KEY` column `id INT NOT NULL`.
- A `UNIQUE` foreign-key column `<ClassName>` in the `EObject` sentinel table, linking
  the class table into the global identity hierarchy.
- Two annotations: `"class"` and either `"abstract"` or `"concrete"`, so the backward
  transformation can faithfully reconstruct the `abstract` flag.

**Backward:** Tables annotated `"class"` (excluding `EObject`) become `EClass`
instances. The `abstract` flag is restored from the `"abstract"` annotation.

---

### Generalisation ↔ Foreign-Key Hierarchy

Ecore single inheritance (`ESuperTypes`) is represented at the SQL level by a
`ForeignKey` from a sub-class table's **primary-key column** to the super-class table.
Two mutually exclusive annotations on the key distinguish the two cases:

| Annotation on FK | Meaning |
|---|---|
| `"superType"` | This class has an explicit Ecore super-class |
| `"root"` | This class has no super-class; FK points to `EObject` sentinel |

The two annotations are always mutually exclusive: when the inheritance relationship
changes (e.g. a super-type is added or removed), the old key is deleted and the new
one is created.

**Backward:** Tables with a `"superType"` FK get the referenced table's class as their
`ESuperTypes` entry. Tables with only a `"root"` FK have their `ESuperTypes` cleared.

---

### EAttribute ↔ Column / Auxiliary Table

**Single-valued attributes** (`upperBound == 1`) become a `Column` inside the owner
class's table. The column type is derived from the Ecore primitive type:

| Ecore type | SQL type |
|---|---|
| `EInt`, `ELong` | `int` |
| `EBoolean` | `boolean` |
| `EDate` | `date` |
| `EString` | `varchar(30)` |
| `EDouble` | `double` |

Annotations on the column: `"attribute"`, `"single"`.

**Multi-valued attributes** (`upperBound != 1`) become a separate **auxiliary table**
named `<ClassName>_<attributeName>`. The table has:
- An `id INT NOT NULL` foreign-key column → owner class table.
- A `value <type>` column for the attribute values.

Annotations on the table: `"attribute"`, `"multi"`.

**Incremental change of multiplicity:** If an attribute's multiplicity changes between
single and multi (or vice versa), the old SQL element (Column or Table) is deleted and a
new one of the correct type is created while reusing the same correspondence link.

**Backward:** Columns with `"attribute"` → single-valued `EAttribute`; tables with
`"attribute"` + `"multi"` → multi-valued `EAttribute`.

---

### EReference ↔ Foreign-Key Column / Relation Table

This is the most complex mapping. Four cases are handled:

#### Case 1 – Containment references (uni- or bidirectional, single or multi)

An inverse `ForeignKey` `Column` is added to the **owned** class's table, pointing
back to the **owner** class table. The column name encodes the directionality:

| Directionality | Column name |
|---|---|
| Unidirectional | `<refName>_inverse` |
| Bidirectional (with EOpposite) | `<oppositeName>_inverse_<refName>` |

Annotations: `"containment"`, `"unidirectional"` or `"bidirectional"`, `"single"` or
`"multi"`.

#### Case 2 – Single-valued unidirectional cross-reference

A `ForeignKey` column named `<refName>` is added to the **source** class table,
pointing to the target class table. The `ON DELETE` action is `SET NULL`.

Annotations: `"single"`, `"unidirectional"`, `"cross"`.

#### Case 3 – Multi-valued unidirectional cross-reference

A separate **relation table** named `<OwnerClass>_<refName>` is created with:
- `id INT NOT NULL` FK → owner class table
- `reference` FK → target class table

Annotations: `"cross"`, `"multi"`, `"unidirectional"`.

#### Case 4 – Bidirectional cross-reference

A single shared **relation table** named
`<OwnerClass>_<refName>_inverse_<TargetClass>_<oppositeName>` is created with two
`NOT NULL` FK columns: `source` and `target`. The lexicographically smaller composite
name is used as the authoritative creator to prevent duplicate tables.

Additional multiplicity annotations (`"forwardSingle"` / `"forwardMulti"`,
`"backwardSingle"` / `"backwardMulti"`) allow the backward direction to reconstruct
the exact upper bounds of both ends.

---

## The Annotation Mechanism

Because the SQL metamodel is less expressive than Ecore, semantic information about the
Ecore source is encoded as `Annotation` objects (simple string tags) on SQL `ModelElement`s.
The backward direction reads these tags to decide how to reconstruct the Ecore structure.

The complete set of annotation values used:

| Tag | Applied to | Meaning |
|---|---|---|
| `"package"` | `Schema` | This schema was derived from an EPackage |
| `"class"` | `Table` | This table was derived from an EClass |
| `"abstract"` | `Table` | The original EClass is abstract |
| `"concrete"` | `Table` | The original EClass is concrete |
| `"superType"` | `ForeignKey` | FK encodes an Ecore super-type relationship |
| `"root"` | `ForeignKey` | FK encodes absence of a super-type (→ EObject) |
| `"attribute"` | `Column` / `Table` | Derived from an EAttribute |
| `"single"` | `Column` | Derived from a single-valued feature |
| `"multi"` | `Table` | Derived from a multi-valued feature |
| `"containment"` | `Column`, `ForeignKey` | Derived from a containment EReference |
| `"cross"` | `Column` / `Table` | Derived from a cross (non-containment) EReference |
| `"unidirectional"` | `Column` / `Table` / `ForeignKey` | Reference has no EOpposite |
| `"bidirectional"` | `Column` / `Table` / `ForeignKey` | Reference has an EOpposite |
| `"forwardSingle"` | `Table` | Forward end of bidir cross-ref is single-valued |
| `"forwardMulti"` | `Table` | Forward end of bidir cross-ref is multi-valued |
| `"backwardSingle"` | `Table` | Backward end of bidir cross-ref is single-valued |
| `"backwardMulti"` | `Table` | Backward end of bidir cross-ref is multi-valued |

---

## BXtend Solution Architecture

### Correspondence Model

BXtend maintains a **correspondence model** (`corresp.ecore`) alongside the source and
target models. The root element is a `Transformation` object that contains a flat list
of `Corr` links. Each `Corr` connects exactly one source element to one target element,
tagged with the `desc` string of the rule that created it.

The correspondence model is the key to incrementality: on re-propagation, rules reuse
existing `Corr` objects to find already-created counterparts and update them in-place
instead of re-creating them from scratch.

```
corresp.ecore
 └─ Transformation
     ├─ Corr { desc="root",         sourceElement=EPackage,  targetElement=Schema  }
     ├─ Corr { desc="class2table",  sourceElement=EClass,    targetElement=Table   }
     ├─ Corr { desc="class2table",  sourceElement=EClass,    targetElement=Table   }
     ├─ Corr { desc="attribute2attribute", sourceElement=EAttribute, targetElement=Column }
     ├─ Corr { desc="ereference2relation", sourceElement=EReference, targetElement=Table }
     └─ ...
```

### Rule Pipeline

The `Ecore2sqlTransformation` orchestrator executes rules in a fixed order. The order
matters because later rules depend on correspondences established by earlier ones:

```
sourceToTarget():
  1. Package2Schema          (EPackage → Schema + EObject table)
  2. Class2Table             (EClass → Table + PK + EObject FK)
  3. Generalization2Relation (ESuperTypes → superType/root FK)
  4. Attribute2Attribute     (EAttribute → Column or aux Table)
  5. EReference2Relation     (EReference → FK Column or relation Table)
  6. deleteUnreferencedTargetElements()   ← deletion clean-up

targetToSource():
  1. Package2Schema          (Schema → EPackage)
  2. Class2Table             (Table[class] → EClass)
  3. Generalization2Relation (FK[superType] → ESuperTypes)
  4. Attribute2Attribute     (Column[attribute] / Table[attribute+multi] → EAttribute)
  5. EReference2Relation     (Column/Table[cross/containment] → EReference)
  6. deleteUnreferencedSourceElements()   ← deletion clean-up
```

Each rule is an instance of a subclass of the abstract base class `Elem2Elem`, which
provides shared infrastructure: factory singletons, correspondence model access helpers
(`getOrCreateCorrModelElement`, `getOrCreateSourceElem`, `getOrCreateTargetElem`), and
the `addAnnotations` utility.

Class hierarchy of rules:

```
Elem2Elem
 ├─ Package2Schema
 └─ Class2Table
     ├─ Generalization2Relation
     ├─ Attribute2Attribute
     └─ EReference2Relation
```

`Class2Table` provides the `createForeignKeyAttr`, `createForeignKey`, `createColumn`,
and `eObjectTable` helpers that are reused by its subclasses.

### Incremental Deletion Handling

After every propagation pass, a clean-up phase detects orphaned correspondences (those
with a `null` source or target element) and removes the obsolete model elements:

- **Forward direction** (`deleteUnreferencedTargetElements`): SQL columns, tables, and
  their dangling foreign keys are removed when their Ecore counterpart has been deleted.
  Dangling `ForeignKey` objects (column or referenced table is `null`) are collected
  and purged as a safety net.
- **Backward direction** (`deleteUnreferencedSourceElements`): Ecore elements are
  removed when their SQL counterpart has been deleted. For `EReference`s, any
  `EOpposite` is also deleted to keep the source model consistent.

---

## Source-File Guide

| File | Rule ID | Purpose |
|---|---|---|
| `Elem2Elem.xtend` | `"base"` | Abstract base class; correspondence helpers, annotation utility |
| `Package2Schema.xtend` | `"root"` | `EPackage` ↔ `Schema` + `EObject` sentinel table |
| `Class2Table.xtend` | `"class2table"` | `EClass` ↔ `Table`; FK/column factory helpers |
| `Generalization2Relation.xtend` | `"generalization2relation"` | `ESuperTypes` ↔ `superType`/`root` FKs |
| `Attribute2Attribute.xtend` | `"attribute2attribute"` | `EAttribute` ↔ `Column` (single) or `Table` (multi) |
| `EReference2Relation.xtend` | `"ereference2relation"` | `EReference` ↔ FK column or relation table (4 cases) |
| `Ecore2sqlTransformation.xtend` | — | Orchestrator; rule pipeline, deletion clean-up |
| `model/corresp.ecore` | — | Correspondence metamodel (`Transformation`, `Corr`, `BasicElem`) |

---

## How to Run the Tests

The transformation is exercised by the JUnit 5 parameterised test suite in the
`BenchmarxEcoreToSQL` project. Tests are found under:

```
examples/ecoretosql/BenchmarxEcoreToSQL/src/org/benchmarx/examples/ecore2sql/testsuite/
  alignment_based/fwd/IncrementalForward.java   ← incremental forward tests
  alignment_based/bwd/IncrementalBackward.java  ← incremental backward tests
  batch/fwd/BatchForward.java                   ← batch forward tests
  batch/bwd/BatchBackward.java                  ← batch backward tests
```

The `BXtendEcore2SQL` adapter class wires the `Ecore2sqlTransformation` into the
Benchmarx harness:

```java
// Initialise – creates an empty EPackage and propagates forward (batch initialisation)
tool.initiateSynchronisationDialogue();

// Incremental forward: edit source, propagate
tool.performAndPropagateSourceEdit(edit -> helperEcore::createSimpleCompositeList);

// Incremental backward: edit target, propagate
tool.performAndPropagateTargetEdit(edit -> helperSQL::createNodeTable);
```

---

## Test Coverage

The following feature categories are covered by the benchmark test suite:

| Category | Direction | Description |
|---|---|---|
| Batch | fwd | Create a full Ecore package from scratch and derive the SQL schema |
| Batch | bwd | Create a full SQL schema from scratch and derive the Ecore package |
| Incremental inserts | fwd | Add new classes, attributes, references; verify new tables/columns appear |
| Incremental inserts | bwd | Add new tables, columns, FK constraints; verify new EClasses/EAttributes |
| Incremental deletions | fwd | Delete classes, attributes, references; verify cascading SQL deletions |
| Incremental deletions | bwd | Delete tables/columns; verify cascading Ecore deletions |
| Rename | fwd | Rename package, class, attribute, reference; verify SQL names update |
| Rename | bwd | Rename schema/table/column; verify Ecore names update |
| Multiplicity change | fwd | Change attribute from single-valued to multi-valued (Column → Table) |
| Multiplicity change | bwd | Change column to a multi-table; verify EAttribute upperBound |
| Generalisation | fwd | Add/remove super-types; verify FK hierarchy changes |
| Generalisation | bwd | Add/remove `superType` FK; verify `ESuperTypes` update |
| Move | fwd | Move structural features between classes |
| Hippocratic | fwd/bwd | Idle edits on one side must not change the other side |

---

## Known Limitations

- **Single inheritance only**: only the first element of `ESuperTypes` is mapped; multiple
  inheritance cannot be represented in the current SQL mapping.
- **Primitive types only**: `EAttribute.EType` must be one of `EInt`, `ELong`, `EBoolean`,
  `EDate`, `EString`, or `EDouble`. Enum types and custom data types are not supported.
- **Name-based look-up in backward direction**: the backward rules for multi-valued
  attributes and references rely on parsing table names (e.g. `<ClassName>_<refName>`) to
  find the owning class. Class and feature names must therefore not contain underscores.
- **One package per model**: the transformation assumes exactly one `EPackage` root in
  the source model and exactly one `Schema` root in the target model.
- **No operations**: `EOperation`s are not part of the mapping.
