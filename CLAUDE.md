# Mai — Project Notes for Claude

## What this project is

Mai is a Paper Minecraft 1.21 plugin that spawns humanoid NPCs (Mannequins — `EntityType.MANNEQUIN`, the dedicated vanilla mob added in 1.21) and drives them with code. The long-term goal is to ship **MaiBench**: a public benchmark that pits AI agents against each other inside Minecraft and scores them on the milestones they unlock (mine wood, craft tools, kill dragon, etc.). The author plans to write a research paper on the results.

Today the plugin is the substrate the benchmark will run on. A brain (LLM-driven decision loop) will be plugged into the `HumanoidActionAgent` later — but **only once the body is reliable**. Movement bugs are gating everything.

## What's in the repo

- `src/main/java/me/advait/mai/`
    - `Mai.java` — plugin entry. Wires up `Catalog`, `Settings`, `PatheticAgent`, listeners, ACF commands.
    - `Catalog.java` — registry of all spawned `Humanoid`s. `register(name, location)` spawns. `unregister(humanoid)` despawns.
    - `body/Humanoid.java` — the NPC wrapper. Owns the Mannequin entity, equipment, inventory, action agent.
    - `command/HumanoidCommand.java` — `/h …` ACF subcommands. **All current handlers take `Player` — none are console-safe.**
    - `brain/action/` — action queue framework. `HumanoidActionAgent.addActions(...)` returns a `CompletableFuture<ActionResult>`. Actions = `HumanoidWalkToAction`, `HumanoidMineAction`, `HumanoidBuildAction`.
    - `pathetic/` — Baritone-inspired pathfinder + locomotion. **Recently rewritten — do not redesign without reading the existing types.**
        - `PatheticAgent.java` — `getAnnotatedPath(humanoid, from, to)` returns a `CompletableFuture<Optional<AnnotatedPath>>`.
        - `movement/` — `MovementType` sealed interface, `MovementRegistry`, `MovementExecutors` (shared physics primitives), `TickContext`.
        - `movement/types/` — locomotion: `WalkFlat`, `WalkDiagonal`, `StepUp`, `StepDown`, `FallSafe`, `FallUnsafe`, `SprintJump`, `LadderClimb`, `Swim`. World-editing (Baritone-style): `MineThrough`/`MineStepUp` (break blocks to tunnel through/up), `BridgePlace`/`PillarUp` (place blocks to bridge gaps / pillar up). Editing moves are gated on `HumanoidCapabilities.canMine`/`canBridge` (derived from inventory) and registered last so free locomotion always wins on cost ties. See "Block editing in the pathfinder" below.
        - `path/AnnotatedPath.java` — `simplify()` drops collinear waypoints. Don't re-classify across simplified spans — caused an infinite replan loop once. Mine/bridge moves use `Locomotion.MINE`/`BRIDGE` execution hints precisely so simplify keeps them per-cell (collapsing them strands the bot in a gap).
        - `debug/PathDebugLog.java` — gated by `HumanoidWalkToRunnable.isDebugMode()`. Toggle with `/h debug particles`.
- `mc-server/` — local dev server. `deploy.sh` builds, copies the jar, kills `server.jar` (carefully — see [[feedback_kill_caution]]), and restarts under `screen -dmS mc`.
- `src/main/resources/settings.yml` — all tunable movement constants under `movement:`. Don't hardcode physics — pipe through `MovementConfig`.

## Workflow

- Build: `mvn -f pom.xml clean package -q` (jar lands at `target/Mai-GIT.jar`).
- Deploy + restart: `./deploy.sh`. The script intentionally only kills processes matching `server\.jar` so the user's MC client (sharing port 25565) survives.
- Server console: attach with `screen -r mc`, detach with `Ctrl-A D`. Send commands programmatically with `screen -S mc -X stuff "<cmd>$(printf '\r')"`.
- Logs: tail `mc-server/logs/latest.log`. Path debug events grep as `PathDebug`.

## Obstacle test harness (`me.advait.mai.obstacle`)

A deterministic, headless parkour/terrain test harness for verifying movement correctness. **This is plain Mai functionality — no LLM involved.** (MaiBench, the AI-agent benchmark, is a separate future repo.) It is the user's primary regression tool: build a small arena from a text file, spawn a humanoid, walk it to the goal, watch every tick, print a machine-parseable result, then tear down leaving no trace.

- **Files**: `ObstacleParser` → `ObstacleFile` (header + grid), `ArenaBuilder` (build/teardown, chunk pinning, snapshot invalidation), `ObstacleTester` (orchestration: run/batch/cancel/list/reload/tp, all `CommandSender`-safe), `ObstacleCommand` (ACF `/mai`), `ObstacleLog` (per-run debug log), `Result`.
- **Commands** (top-level `/mai`, separate from `/h` so the **console** can drive it without a `Player`): `mai test run <stem>`, `mai test all`, `mai test list`, `mai test cancel`, `mai test reload`, `mai test tp <stem>` (in-game only). Permission: **`mai.obstacle.run`** (console always has it; in-game defaults to op).
- **Result contract** (grep `[Obstacle] RESULT`): `[Obstacle] RESULT name=<stem> status=<PASS|FAIL|TIMEOUT|ERROR> ticks=<n> reason=<reason> humanoid=<uuid>`. Batch ends with `[Obstacle] BATCH passed=… failed=… timeout=… error=… total=… elapsed_ms=…`.
- **`.obstacle` format**: header (`key: value`) + `---` + whitespace grid. Rows north→south, cols west→east. Cells: integer N = N blocks on floor, `.` = floor only, `_` = void (no floor, fall-through), `S`/`E` = start/end (optional digit suffix = pillar height, e.g. `E4`). Exactly one `S` and one `E`. `#` lines are comments. Max 32×32. Optional header keys for editing tests: `bot_tool: <Material>` (e.g. a pickaxe — without it `canMine` is false), `bot_blocks: <Material> [count]` (stock for bridging/pillaring), `ceiling: <N>` (solid roof at `originY+N` over floored cells, encloses the course into a tunnel so mine-*through* is forced over climb-over). Seeds ship in `src/main/resources/obstacles/` and copy to `mc-server/plugins/Mai/obstacles/` on first load (only when the dir is empty — drop new files in and `mai test reload`; new bundled seeds are NOT auto-copied over an existing dir). The suite is ~128 fixtures spanning walks (flat/vertical/diagonal), staircases (up/down to height 10, pyramids, valley), gaps & sprint-jumps, pillar-jumps, corners/mazes, mining (wall thickness, tool tiers, materials, long tunnels), bridging (gap width, block materials, double gaps), and pillar-up — most generated by `tools/gen_obstacles.py` (rerun it to regenerate; it never overwrites the 11 hand-curated seeds). **Known gaps:** multi-block ledge *drops* (descend ≥2 blocks to an adjacent column) are deliberately absent because `FallSafe` never matches today — its `isFallClear` scans the *source* column (always solid underfoot) so the pathfinder returns `no_path`; descents are covered as staircases instead. Tight chained/mid-air jumps are also flaky (the bot overshoots or short-jumps non-deterministically), so jump fixtures use generous run-ups, landing platforms, and run-outs to stay reliable.
- **Arena world**: `settings.yml` → `obstacle.world` (`name`, `type` = `VOID`/`FLAT`/`NORMAL`, `auto_create`). On enable, an already-loaded world of that name is used as-is; otherwise a dedicated world is created/loaded with the chosen generator (`VoidGenerator` for VOID). Default is a dedicated `obstacles` VOID world. Point `name` at `world` to build in the overworld instead. Horizontal origin is `obstacle.arena_origin` (x/z, default 0/0); floor Y is per-file `origin_y` (default 120). Per-run logs land in `mc-server/plugins/Mai/obstacle-logs/`.
- **Two non-obvious correctness notes** (don't regress these):
  - Pathetic caches chunk snapshots in a **static, never-auto-refreshed** map (`FailingNavigationPointProvider.SNAPSHOTS_MAP`). The harness rewrites the same chunks every run, so `ArenaBuilder` calls `FailingNavigationPointProvider.invalidateChunk(...)` after build/teardown — otherwise pathfinding sees the previous arena and returns `no_path`.
  - The arena sits far from spawn; its chunks are pinned with plugin chunk tickets (kept for the plugin lifetime) so the humanoid ticks instead of going invalid (which reads as `died`). The batch waits `SETTLE_TICKS` before each run so a prior run's in-flight async pathfind can't re-cache a stale snapshot.

## Block editing in the pathfinder (mining & placing)

The pathfinder treats the world as **mutable**: it will break or place blocks as part of an A* route, and re-paths when the world changes. Do not "fix" this by stripping the editing moves — it's intentional.

- **Moves**: `MineThrough`/`MineStepUp` break the breakable block(s) in the way then walk/step in; `BridgePlace` places a support under the next cell across a gap; `PillarUp` jumps and places a block under the feet. Each `matches()` only fires on cells the natural moves reject (blocked-but-breakable, or floorless gap), so they never shadow free locomotion.
- **Execution reuses existing actuators**: mining drives `MannequinBlockBreaker` (multi-tick; the in-flight future lives on `TickContext.subAction`, reset by the driver on every waypoint advance/replan); placing drives `HumanoidBuildAction.placeBlock` (requires a solid neighbour to build against; decrements + writes the held stack back to the hand).
- **Mining/placing visuals**: both call `entity.swingMainHand()`; mining additionally broadcasts the vanilla block-cracking overlay each tick via `Player.sendBlockDamage(loc, progress, mannequin)` to players within 64 blocks, throttled to destroy-stage changes and cleared (progress 0) on cancel — a successful break clears it client-side on its own. Caveat: the swing animation may not render on a `Mannequin` (it is a display-oriented entity; `swingMainHand` already reaches the NMS swing broadcast with no `Mob` guard, so non-rendering is a client-side limitation, not something the server can fix). The crack overlay renders regardless since it is attached to the block. No-op when no players are nearby (e.g. the headless obstacle world).
- **Cost model** (all in ticks, summed with the existing costs): mining = `hardness * divisor / toolSpeed + durabilityPenalty` (`divisor` 30 with the right tool / 100 without); placing = `place_block_cost + equip_cost + value_weight * placementValue(block) / log2(count+2)`. Scarcity means the bot bridges with cobblestone (value 1, abundant), never a diamond block (value 4000) unless that's all it has. Unbreakable/protected blocks and "no block to place" cost `cost_inf` (never chosen). Constants live in `settings.yml` → `movement.{mine,place}` and `movement.pathfinding.{mining_enabled,bridge_enabled}`; `HumanoidCapabilities.from()` pre-extracts thread-safe tool/placement numbers on the main thread for the async cost processor.
- **Adaptivity**: `BlockClassifier` gained `isBreakable`/`hardness`/`placementValue`. `WorldChangeListener` (block break/place/burn/fade/decay/explode events) invalidates the pathfinder's static snapshot cache (`FailingNavigationPointProvider.invalidateChunk`) and flags nearby in-flight `HumanoidWalkToRunnable`s (`onWorldChange` → `worldDirty`) to replan immediately. The bot's own edits bypass Bukkit events, so `MannequinBlockBreaker` and `HumanoidBuildAction.placeBlock` invalidate the snapshot directly. `worldDirty` is a 4th replan trigger in the runnable alongside pathNull/lookahead/periodic.

## Conventions

- **Commit messages**: short single-sentence subject only, no body, no Claude footer. See [[feedback_commit_style]].
- **Pathfinding correctness**: when in doubt, surface a `PathDebugLog.event(...)` line. The debug log is the user's primary way to diagnose stuck bots.
- **Never snap the target to the ground** — long-term the bot needs to bridge to elevated targets. Resolve block-coord ambiguity (e.g. edge-standing players) by checking AABB corners, not by moving the target.
- **Physics constants** live in `settings.yml` → loaded into `MovementConfig`. Don't sprinkle magic numbers into movement types.
- **Console-safe APIs**: anything the harness or future CI needs must take `CommandSender`, not `Player`. Use `Bukkit.getWorlds().getFirst()` (or a configured arena world) when no player context exists.
