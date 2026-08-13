# Challenges: Ecore ↔ SQL

## The challenge

Mapping an object-oriented Ecore package to a relational SQL schema is a classic but
genuinely hard schema-reshaping problem, not a 1:1 element mapping:

- A single-valued `EAttribute` becomes a `Column` on its owning class's table.
- A **multi-valued** `EAttribute` cannot be a plain column — it has to become its own
  join `Table`, complete with a foreign key back to the owner and a `value` column.
- Inheritance (`EReference`/generalisation) has to be flattened into foreign-key-based
  table relationships, since SQL has no native subtyping.
- All five rules (`Package2Schema`, `Class2Table`, `Generalization2Relation`,
  `Attribute2Attribute`, `EReference2Relation`) are interdependent — reshaping one
  attribute's cardinality can change which *kind* of target element (`Column` vs.
  `Table`) an existing correspondence should point to.

## How BXtend solved it

- **Dynamic re-typing at the correspondence level:** `Attribute2Attribute` detects when
  an attribute's cardinality has changed (single ↔ multi) and, if the existing target
  element is the wrong kind, deletes it and creates the correct one under the same
  correspondence, rather than trying to mutate a `Column` into a `Table` in place.
- **Deliberately scoped-down incremental design:** unlike most other examples in this
  repository, no `NonMonotonic` concurrent-edit test suite existed for this domain when
  `synch()` was implemented, and the five-rule interdependency made a full
  per-attribute conflict-resolution design too risky to guess at blind. `synch()` here
  reasserts the forward direction and absorbs new target-only elements — a lighter,
  safer subset of the pattern used elsewhere.
- **One genuine exception to source-wins:** this is the one example in the repository
  where the reference tool's own `Conflicts` test class documents
  `SyncConflictPolicy.TARGET_WINS` rather than the source-wins default used everywhere
  else — `Attribute2Attribute.synch()`'s branch order is flipped accordingly.

## What broke in practice (found via the real Benchmarx suite, not by inspection)

- `deleteUnreferencedSourceElements()` was missing the null-guard its sibling
  target-side method already had, causing an NPE on `EcoreUtil.delete(null, ...)` when
  a correspondence's source element was already dangling.
- The initial `synch()` treated a `null` "last synced" snapshot as ambiguous between
  "push" and "pull," which for a brand-new correspondence (target not yet named)
  attempted to pull an unset value and crashed with an NPE on `.split()` of a null
  string — fixed by making `null` always resolve to "push," never "pull."
- Once conflict resolution was implemented, it initially assumed source-wins like every
  other example, which resolved renames backwards for this domain until the
  `Conflicts` test class's own documented `TARGET_WINS` policy was found and applied.
