package me.advait.mai.pathetic;

import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.movement.MaterialProvider;

/**
 * The full set of inputs a movement type needs to classify, cost, or
 * validate a transition. Bundles the three things we used to thread
 * through every method as separate arguments:
 *
 * <ul>
 *   <li>{@link HumanoidCapabilities} — the bot's snapshot at pathfinding time
 *   <li>{@link MovementConfig} — tuning constants from settings.yml
 *   <li>{@link MaterialProvider} — block material lookups (async-safe
 *       during pathfinding, main-thread {@code World} access during
 *       annotation and feasibility checks)
 * </ul>
 *
 * <p>One {@code PathContext} is constructed per call site (per async
 * pathfinder edge evaluation, per annotation pass, per feasibility check).
 * It's an immutable record so there's no risk of leaking state across
 * concurrent searches.
 */
public record PathContext(
        HumanoidCapabilities capabilities,
        MovementConfig config,
        MaterialProvider materials
) {}
