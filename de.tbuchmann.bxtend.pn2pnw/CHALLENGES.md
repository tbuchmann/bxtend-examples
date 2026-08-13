# Challenges: Petri Net ↔ Weighted Petri Net

## The challenge

A plain Petri net's arcs are anonymous — just cross-references between `Place` and
`Transition` with no data of their own. A weighted Petri net promotes each arc to a
first-class `PTEdge`/`TPEdge` object carrying an integer `weight`. This is a genuine
model-extension problem: the forward direction has to invent new edge objects for
arcs that don't have one yet, while the backward direction has to throw the weight
information away and reconstruct plain cross-references.

The subtlety is **hippocraticness**: a newly-created edge should get a sensible
default weight (1), but an edge that already exists in the target — and whose weight
may have been changed independently since — must not have that weight clobbered just
because the transformation ran again.

## How BXtend solved it

- **Edges are managed inside `Transition2Transition`**, not as their own
  correspondence rule, because they're structurally derived from the
  `Place↔Transition` cross-references rather than being independent model elements.
- **New vs. existing edges are distinguished via the correspondence model**: a fresh
  edge gets the default weight; an edge already linked to a correspondence keeps
  whatever weight it currently has, following the same "identity key vs. independent
  attribute" split used in the rest of the repository (here, `weight` is the
  independent attribute, tracked separately from arc identity).
- **Backward propagation is intentionally lossy** — it doesn't try to preserve or infer
  a weight on the plain side, since the plain metamodel has nowhere to put it.

## What broke in practice

Nothing. This was the one example in the repository whose `synch()` implementation
passed the entire real Benchmarx `NonMonotonic`/`MonotonicCreating`/
`MonotonicDeleting`/`Conflicts` suite on the very first run, with no fixes needed —
the design derived directly from reading the real test fixtures up front (rather than
guessing at the edit semantics) held up without surprises.
