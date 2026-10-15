---
status: aceita
date: 2026-10-15
---

# 0001 — Registrar decisões de arquitetura em ADRs

## Contexto e problema

O Papinho é construído em várias passadas, por pessoas e agentes diferentes, a partir do `PROMPT.md`.
Sem um registro das decisões, cada passada corre o risco de rediscutir ou desfazer escolhas já feitas.

## Decisão

Registrar toda decisão de arquitetura em `docs/adr/NNNN-<slug>.md`, no formato MADR, em pt-BR,
numerado sequencialmente e nunca renumerado. Uma decisão revertida ganha um novo ADR que substitui o
anterior (`status: substituída por NNNN`).

Na precedência do projeto, os ADRs ficam logo abaixo do `PROMPT.md` e acima de `AGENTS.md`,
`LEARNING.md` e dos guias.

## Consequências

- Boas: o motivo de cada escolha fica junto do código; novas passadas leem os ADRs antes de mudar algo.
- Ruins: exige disciplina para escrever o ADR junto da mudança.

## Alternativas consideradas

- Decisões só no histórico do Git: difíceis de achar e sem as alternativas descartadas.
- Wiki externa: fica dessincronizada do código.
