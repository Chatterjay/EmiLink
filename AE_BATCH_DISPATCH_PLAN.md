# AE Batch Dispatch Experiment

## Relationship to automatic workbench crafting

This experiment does not restore or replace EmiLink's BOM automatic workbench
crafting. That feature is disabled from 1.21.1 onward and must remain disabled
in later updates. This document only records a possible AE provider dispatch
experiment; it is not a user-facing automatic crafting feature and must not be
enabled as part of a release.

## Goal

Investigate an optional server-side AE2 extension for processing-pattern providers that explicitly support receiving one complete crafting order at once. The intent is to reduce unnecessary serial dispatching when multiple orders share a machine and the provider can safely queue or process the entire request.

## Scope and constraints

- This is an experiment only. Do not ship it until it has been tested against AE2 19.2.17 and real production networks.
- It must be server-side, disabled by default, and must not change normal behavior when EmiLink is absent from either side.
- Only providers that opt in through a stable interface or explicit allow-list may use batch dispatch. Never apply this to every `ICraftingProvider`.
- The normal AE2 crafting job must remain active. Once input is dispatched, the expected output total must be recorded in `waitingFor`; the job must not be cancelled merely because a provider accepted its inputs.

## Reference behavior

GTLCore's `CraftingCpuLogicMixin.executeCrafting` recognizes its ME pattern machine providers, extracts all inputs for the active pattern task in one pass, appends the expected result to `waitingFor`, then consumes the pattern task from the dispatch queue. This is a useful reference, but it is tightly coupled to GTLCore's machine classes and cannot be copied as a generic EmiLink implementation.

## Implementation direction

1. Verify AE2's current crafting CPU APIs and lifecycle against source code before adding mixins.
2. Define a small provider opt-in contract, including a capability to reject a batch without consuming items.
3. Dispatch only one eligible task at a time, with transactional extraction and rollback if the provider rejects or partially accepts the request.
4. Preserve `waitingFor`, CPU accounting, cancellation, suspend/resume, and completion behavior.
5. Audit compatibility with ExtendedAE and AdvancedAE, including split/virtual CPU implementations.
6. Add rate-limited debug logs describing eligibility, extraction, provider acceptance, rollback, and completion.

## Required testing

- Multiple orders sharing one ordinary machine must retain vanilla serial behavior.
- Multiple orders targeting an opted-in provider must complete with correct output accounting.
- Cancellation, missing input, provider failure, world unload, and server restart must not lose or duplicate items.
- Test both normal AE2 CPUs and ExtendedAE/AdvancedAE CPU variants.
- Measure dispatch time and tick cost under large queued orders before enabling the feature for users.
