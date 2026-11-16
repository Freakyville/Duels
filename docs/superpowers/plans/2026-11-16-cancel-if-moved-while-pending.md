# cancel-if-moved-while-pending Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `duel.cancel-if-moved-while-pending` so senders can move while their request is pending; release as 3.5.0.

**Architecture:** New boolean in `Config`; both accept commands re-record the base location (`Settings#setBaseLoc`) of the request sender(s) at accept time when the option is off. The existing check in `DuelManager` is unchanged.

**Tech Stack:** Java 8, Spigot API, Gradle 4-era build with shadow plugin.

**Spec:** `docs/superpowers/specs/2026-11-16-cancel-if-moved-while-pending-design.md`

## Global Constraints
- Config key: `duel.cancel-if-moved-while-pending`, default `true` (keeps current behaviour).
- Only has effect when `duel.cancel-if-moved` is `true`.
- Version `3.5.0` in `build.gradle`.
- No automated test infrastructure exists in this repo (no `src/test`, Bukkit-bound code). Verification = compile + manual server test.

## Review Focus
- Option off + item betting on: moving during betting GUI must still cancel (both players' locations recorded at accept).
- Option on (default): behaviour identical to 3.4.0 (sender's send-time location is checked).
- `cancel-if-moved: false`: new option has no effect.
- Team duel: re-recording must cover all participants' locations who have one recorded; players without one stay unchecked (as today).
- Sender in another world at accept time: re-record uses current location, no NPE.

---

### Task 1: Config option + version bump

**Files:**
- Modify: `build.gradle:13` -> `version '3.5.0'`
- Modify: `duels-plugin/src/main/resources/config.yml` (after `cancel-if-moved`)
- Modify: `duels-plugin/src/main/java/me/realized/duels/config/Config.java` (field near line 112, load near line 291)

**Interfaces:**
- Produces: `boolean Config#isCancelIfMovedWhilePending()` (Lombok `@Getter` like the neighbouring field).

- [ ] Step 1: add YAML block (comment + `cancel-if-moved-while-pending: true`) under `cancel-if-moved`.
- [ ] Step 2: add field `cancelIfMovedWhilePending`, loaded with `configuration.getBoolean("duel.cancel-if-moved-while-pending", true)`.
- [ ] Step 3: bump version.
- [ ] Step 4: commit `feat: add cancel-if-moved-while-pending option, bump to 3.5.0`.

### Task 2: Re-record sender location on accept

**Files:**
- Modify: `duels-plugin/src/main/java/me/realized/duels/command/commands/duel/subcommands/AcceptCommand.java` (before the `isItemBetting` branch, ~line 116)
- Modify: `duels-plugin/src/main/java/me/realized/duels/command/commands/teamduel/subcommands/AcceptCommand.java` (inside the final-accept block, before `startTeamMatch`)

**Interfaces:**
- Consumes: `Config#isCancelIfMovedWhilePending()`, `Config#isCancelIfMoved()`, `Settings#setBaseLoc(Player)`.

- [ ] Step 1: 1v1: `if (config.isCancelIfMoved() && !config.isCancelIfMovedWhilePending()) settings.setBaseLoc(target);` (`target` = request sender). Also set `settings.setBaseLoc(player)` for the non-betting path is unnecessary (checked same tick).
- [ ] Step 2: team: same condition, call `settings.setBaseLoc(p)` for every player in `allyTeam` and `targetTeam` after the null check.
- [ ] Step 3: `./gradlew build` (or IDE compile) — expected: compiles. If the legacy build can't resolve deps (jcenter/http repos), report it and verify by compiling against cached jars instead.
- [ ] Step 4: Manual test on a test server: (a) option true -> sender moves, target accepts -> "player-moved" cancel; (b) option false -> sender moves, accept -> duel starts; (c) option false + item betting -> move during GUI -> cancel.
- [ ] Step 5: commit `feat: allow movement while duel request is pending`.
