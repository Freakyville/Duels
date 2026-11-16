# Design: `cancel-if-moved-while-pending` (v3.5.0)

## Why
With `duel.cancel-if-moved: true`, the sender's position is recorded when they send `/duel`
and checked when the duel starts. The sender doesn't know if/when the target will accept
(up to `request.expiration` seconds), so they must stand on one block the whole time or the
duel gets cancelled. Players find this annoying.

## Goal
Let the sender move freely while a request is pending, while keeping the movement check
during the item-betting GUI (between accept and match start).

Non-goals: freezing players, a movement radius, changes to queues.
Anti-goal: changing behaviour for existing installs without opting in.

## Approaches considered
1. **New option; re-record sender location on accept** (picked) - fixes exactly the complaint,
   keeps the check where it is useful (betting GUI).
2. Movement radius instead of exact block - sender is still tethered.
3. Disable `cancel-if-moved` - no code, but loses the betting-GUI protection.

## Design
- New config `duel.cancel-if-moved-while-pending` (default `true` = current behaviour).
  Exposed as `Config#isCancelIfMovedWhilePending()`.
- 1v1 (`duel/subcommands/AcceptCommand`): when `cancel-if-moved` is on and the new option is
  off, call `settings.setBaseLoc(target)` (the sender) at accept time, before betting GUI /
  `startMatch`.
- Team duels (`teamduel/subcommands/AcceptCommand`): same, re-record the base location of
  every participant when the final accept arrives, before `startTeamMatch`.
- `build.gradle` version `3.4.0` -> `3.5.0`.
