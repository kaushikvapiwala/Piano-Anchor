# Learner model: the skill graph

The learner model (M2.4) decides what to practise next. Its first ingredient is the skill graph, which says what
depends on what. This note covers the graph only; reviews, storage and scheduling are added with their code.

## Skills and passages
A `Skill` (in `:core:model`) has a stable id, a title and a set of prerequisite ids. Ids are lowercase kebab-case
so they are safe as storage keys and readable in logs; reviews are stored against them, so they never change.
A `Passage` lists the ids of the skills it exercises. A passage with no skills is never scheduled. Skill titles
live with the built-in content in `:engine`, like passage titles. There is no weighting between skills and no
attribution of a finding to one skill within a passage: a run counts for every skill the passage lists.

## Validation
`SkillGraph` refuses a graph that cannot be learned in some order: a bad id, a duplicate id, an unknown
prerequisite, a skill that requires itself, or a cycle. Each error names the skill, and a cycle error lists the
cycle (`a -> c -> b -> a`, each skill needing the next).

## Order
`SkillGraph.skills` is a topological order, so every skill comes after its prerequisites. It uses Kahn's algorithm:
repeatedly take a skill with no unmet prerequisites and unlock the skills that waited on it. Among the ready
skills the smallest id goes first, so the order is the same on every run and on every platform. If none is ready
while skills remain, they form a cycle.

## Unlocking
`unlocked(learned)` returns, in that order, the skills not yet learned whose prerequisites are all learned. A skill
without prerequisites is unlocked from the start. It is a pure function of the graph and the learned set; it does
not look at due dates or daily limits, which belong to the scheduler.
