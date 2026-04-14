# bxtend-examples
Bidirectional Model Transformations written with the BXtend Framework

## About this repository

This repository collects a set of **Eclipse plug-ins** that implement
**bidirectional, incremental model transformations** with the
[BXtend](https://github.com/tbuchmann/bxtend) framework.

The examples cover different recurring model-transformation scenarios, including
schema adaptation, ordering, aggregation, graph sharing, and structural
translation between related metamodels. Several of them correspond to
well-known **Benchmarx** benchmark cases and can be used as compact reference
implementations for studying BXtend-based solutions.

Each plug-in contains its own detailed `README.md` with the full transformation
description, metamodel background, architectural decisions, and usage notes.
The purpose of this top-level README is only to provide orientation across the
repository.

## Included examples

| Project | Transformation | Main focus |
|---|---|---|
| `de.tbuchmann.bxtend.f2p` | Families ↔ Persons | classic benchmark, ambiguity handling |
| `de.tbuchmann.bxtend.ast2dag` | ExpressionAST ↔ ExpressionDAG | structural sharing |
| `de.tbuchmann.bxtend.ecore2sql` | Ecore ↔ SQL | schema mapping |
| `de.tbuchmann.bxtend.gantt2cpm` | Gantt ↔ CPM | scheduling models |
| `de.tbuchmann.bxtend.pdb12pdb2` | PDB1 ↔ PDB2 | schema incompatibility |
| `de.tbuchmann.bxtend.pn2pnw` | Petri Net ↔ Weighted Petri Net | model extension |
| `de.tbuchmann.bxtend.set2oset` | Set ↔ Ordered Set | ordering and repair logic |
| `de.tbuchmann.bxtend.bag12bag2` | Bag1 ↔ Bag2 | many-to-one correspondences |

## How to use this repository

Start with the example that best matches your transformation scenario and then
read the corresponding project-level `README.md`. The individual plug-in
documentation contains the real detail; the root README is mainly an entry
point and overview.
