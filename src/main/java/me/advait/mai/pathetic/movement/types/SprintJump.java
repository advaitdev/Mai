package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.debug.PathDebugLog;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.util.Vector;

/**
 * Sprint-jump across a gap of 1-4 blocks (horizontal distance 2-5).
 * Supports flat jumps (dy=0), ascending (+1), and descending (-1).
 *
 * <p>Planning validates:
 * <ul>
 *   <li>Source has solid ground and 3-block headroom (room to jump)
 *   <li>Landing has solid ground and is standable
 *   <li>Intermediate columns are clear at feet+head+above-head level
 *   <li><b>There is a real gap</b>: at least one intermediate column has no
 *       floor at the source level. Without this A* happily "sprint-jumps"
 *       across flat walkable ground because the per-block cost is lower
 *       than walking — and a jump waypoint with no edge to take off from
 *       never completes (the bot oscillates around it, then stuck-replans
 *       from a standstill one block short of the real gap and walks off).
 * </ul>
 *
 * <p>Execution is a small state machine on {@link TickContext#phase}:
 * <pre>
 *   RUNWAY  — sprint toward the landing; on the last tick still on the
 *             takeoff block, jump if fast enough, else switch to RETREAT.
 *   AIR     — steer toward the landing; on touchdown verify we actually
 *             landed on/next to the landing block (else FAILED → replan).
 *   RETREAT — reached the edge too slow (e.g. after a replan put us right
 *             at the lip). Brake, back away to gain runway, then RUNWAY.
 *   FORCED  — nowhere to retreat (edge behind too). Jump at the edge with
 *             whatever speed we have; better than standing still forever.
 * </pre>
 */
public record SprintJump() implements MovementType {

    private static final int RUNWAY = 0;
    private static final int AIR = 1;
    private static final int RETREAT = 2;
    private static final int FORCED = 3;

    /** How far off the landing center a touchdown may be and still count. */
    private static final double LANDING_TOLERANCE = 1.2;

    @Override
    public String key() { return "sprint_jump"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        // dy must be -1, 0, or +1
        if (Math.abs(dy) > 1) return false;

        // Horizontal distance caps (vanilla 1.21 sprint-jump, verified
        // against MCPK "Longest Jumps"):
        //   dy= 0 → 4 blocks edge-to-edge (3-block gap) reliable
        //   dy=+1 → 3 blocks edge-to-edge (2-block gap) reliable
        //   dy=-1 → 4.5 blocks edge-to-edge (3-4 block gap) reliable
        // Anything beyond requires frame-perfect timing humans can hit
        // but an NPC can't guarantee — don't plan on it.
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        if (horizDist < 1.5) return false;
        if (dy == 1 && horizDist > 3.5) return false;
        if (dy == 0 && horizDist > 4.5) return false;
        if (dy == -1 && horizDist > 4.5) return false;

        MaterialProvider materials = ctx.materials();

        // Source must have 3-block headroom for jumping
        int sx = previous.getFlooredX(), sy = previous.getFlooredY(), sz = previous.getFlooredZ();
        if (!BlockClassifier.isTraversable(materials.getMaterial(sx, sy + 2, sz))) return false;

        // Landing must be standable
        if (!WalkFlat.isStandable(current, materials)) return false;

        // Validate the jump arc: intermediate columns must be clear, and at
        // least one of them must be a genuine gap.
        if (!validateArc(previous, dx, dy, dz, materials)) return false;

        // Physically achievable with the run-up the terrain provides?
        return runUpSuffices(previous, dx, dy, dz, horizDist, ctx);
    }

    /** Max blocks of run-up behind the takeoff block considered by the planner. */
    private static final int MAX_RUNUP_BLOCKS = 3;

    /**
     * Whether a bot could legitimately make this jump given the run-up
     * behind the takeoff block. Counts standable blocks behind the source
     * (opposite the jump direction, same level), simulates vanilla sprint
     * acceleration from a standstill over that distance, and checks the
     * resulting takeoff speed (ground speed + sprint-jump boost) against
     * {@link AirControl#takeoffSpeeds}' minimum. Pure function of the world
     * snapshot, so it is safe on the async pathfinding thread.
     *
     * <p>A standstill start is the conservative case: the bot may arrive
     * with momentum, but the planner can't know that, and a jump that only
     * works with momentum it might not have is exactly what used to strand
     * bots on 1x1 pillars.
     */
    private static boolean runUpSuffices(PathPosition src, int dx, int dy, int dz,
                                         double horizDist, PathContext ctx) {
        var c = ctx.config();
        int ux = Integer.signum(dx), uz = Integer.signum(dz);
        int blocksBehind = 0;
        for (int i = 1; i <= MAX_RUNUP_BLOCKS; i++) {
            PathPosition back = PathPosition.of(src.getFlooredX() - ux * i, src.getFlooredY(),
                    src.getFlooredZ() - uz * i);
            if (!WalkFlat.isStandable(back, ctx.materials())) break;
            blocksBehind++;
        }
        // Run distance: from the center of the farthest run-up block to the
        // lip, minus the edge-probe lead (half-width + a hair).
        double run = blocksBehind + 0.5 - 0.35;

        // Vanilla ground sprint: v' = v * groundDrag + accel, displacement v'.
        double slip = me.advait.mai.pathetic.BlockClassifier.slipperiness(
                ctx.materials().getMaterial(src.getFlooredX(), src.getFlooredY() - 1, src.getFlooredZ()));
        double groundDrag = slip * c.getAirDrag();
        double accel = c.getWalkAcceleration() * c.getSprintFactor()
                * (0.16277136 / (groundDrag * groundDrag * groundDrag));
        double v = 0, travelled = 0;
        while (travelled < run) {
            v = v * groundDrag + accel;
            travelled += v;
        }
        // Bot standing still on a lone block still gets one accel tick.
        if (v == 0) v = accel;

        double maxTakeoff = v + c.getSprintJumpBoost();
        // Same geometry the executor uses: the takeoff fires when the edge
        // probe (0.35 + this tick's travel) passes the lip, so the bot's
        // center is that far behind the lip, and the lip is 0.5 from the
        // source center.
        double dist = (horizDist - 0.5) + 0.35 + v;
        double[] need = AirControl.takeoffSpeeds(dist, dy, slip, MovementExecutors.SPRINT_AIR_ACCEL, c);
        return need != null && need[1] <= maxTakeoff;
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dz = current.getFlooredZ() - previous.getFlooredZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        int gap = Math.max(0, (int) Math.round(horizDist) - 1);

        // Priced as "sprinting the same distance, plus a per-jump overhead,
        // plus a risk term that grows with the gap". It must never be cheaper
        // per block than walking: if it were, A* would plan the LONGEST jump
        // that fits (even across flat ground), and a max-range jump over a
        // 1-block gap has no margin for error. With this shape the planner
        // picks the shortest jump that clears the gap, like a human would.
        return horizDist * ctx.config().getWalkFlat()
                + ctx.config().getSprintJumpBase()
                + gap * ctx.config().getSprintJumpPerGap()
                + ctx.config().getJumpPenalty()
                + horizDist * ctx.config().getHungerSprintCost();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dz = current.getFlooredZ() - previous.getFlooredZ();
        double horizDist = Math.sqrt(dx * dx + dz * dz);
        int gap = Math.max(0, (int) Math.round(horizDist) - 1);
        return ExecutionHint.sprintJump(gap);
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        return caps.canJump() && caps.canSprint();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        return WalkFlat.isStandable(position, ctx.materials());
    }

    // =========================================================================
    // Execution
    // =========================================================================

    @Override
    public MovementStatus tick(TickContext ctx) {
        switch (ctx.phase) {
            case AIR -> { return tickAir(ctx); }
            case RETREAT -> { return tickRetreat(ctx); }
            default -> { return tickRunway(ctx, ctx.phase == FORCED); }
        }
    }

    private MovementStatus tickRunway(TickContext ctx, boolean forced) {
        // Defensive: if we're somehow already standing on the landing
        // (replan landed us there, or a trivially short hop), we're done.
        if (ctx.onGround() && MovementExecutors.reachedFully(ctx)) {
            return MovementStatus.SUCCESS;
        }

        if (!ctx.onGround()) {
            // Left the ground without an explicit jump (stepped off a lip,
            // or the previous waypoint's jump is still in flight). Treat as
            // airborne and steer for the landing.
            ctx.phase = AIR;
            return tickAir(ctx);
        }

        int gap = ctx.waypoint.hint() != null ? ctx.waypoint.hint().gapLength() : 0;
        double distToLanding = MovementExecutors.horizontalDistance(ctx.current, ctx.waypoint);
        double[] dir = MovementExecutors.direction2D(ctx.current, ctx.waypoint);

        // Runway: sprint toward the landing. Apply this tick's acceleration
        // FIRST — the velocity read back from the entity is the post-friction
        // value from last tick (~55% of the real per-tick displacement on
        // default blocks); only after adding this tick's input does it equal
        // the distance the bot will actually move this tick.
        MovementExecutors.groundAccelerate(ctx, ctx.config.getSprintFactor(), true);
        Vector vel = ctx.entity().getVelocity();
        double speed = MovementExecutors.horizontalSpeed(vel);
        // Only velocity along the jump direction counts toward takeoff.
        double alongSpeed = vel.getX() * dir[0] + vel.getZ() * dir[1];

        // Will this tick's movement carry our leading edge past the lip of
        // this block? Probe half-width + this tick's displacement + a hair.
        boolean atEdge = MovementExecutors.atEdgeAhead(ctx.current, dir, 0.35 + Math.max(0.0, alongSpeed));
        // The edge we care about is the one right before the landing — not
        // some unrelated lip far behind on a long runway.
        boolean edgeIsOurGap = distToLanding < gap + 2.6;

        if (atEdge && edgeIsOurGap) {
            // Physics-planned takeoff: how fast must we leave the lip to land
            // on the block center, given the airtime this height change gives
            // us and the air-control budget? The fastest legitimate takeoff
            // is current ground speed plus the vanilla sprint-jump boost.
            double dyJump = ctx.waypoint.y() - ctx.current.getY();
            double[] need = AirControl.takeoffSpeeds(distToLanding, dyJump,
                    MovementExecutors.slipperinessBelow(ctx.current),
                    MovementExecutors.SPRINT_AIR_ACCEL, ctx.config);
            double maxTakeoff = Math.max(0.0, alongSpeed) + ctx.config.getSprintJumpBoost();
            boolean feasible = need != null && need[1] <= maxTakeoff;
            if (feasible || forced) {
                double vSet = need == null ? maxTakeoff : Math.min(need[0], maxTakeoff);
                PathDebugLog.event("SPRINT_JUMP_TAKEOFF gap=%d along=%.3f vset=%.3f need=%.3f/%.3f max=%.3f dist=%.2f dy=%.1f forced=%s bot=(%.2f,%.2f,%.2f)",
                        gap, alongSpeed, vSet,
                        need == null ? -1.0 : need[0], need == null ? -1.0 : need[1], maxTakeoff,
                        distToLanding, dyJump, forced,
                        ctx.current.getX(), ctx.current.getY(), ctx.current.getZ());
                MovementExecutors.face(ctx.entity(), dir);
                ctx.setVelocity(new Vector(dir[0] * vSet, ctx.config.getJumpVelocity(), dir[1] * vSet));
                ctx.jumpCooldown = MovementExecutors.JUMP_COOLDOWN_TICKS;
                ctx.phase = AIR;
                return MovementStatus.RUNNING;
            }
            // Too slow to make it. Don't walk off the edge: kill horizontal
            // velocity now and back up for a run-up.
            PathDebugLog.event("SPRINT_JUMP_TOO_SLOW gap=%d along=%.3f need=%.3f max=%.3f dist=%.2f → retreat",
                    gap, alongSpeed, need == null ? -1.0 : need[1], maxTakeoff, distToLanding);
            ctx.setVelocity(new Vector(0, vel.getY(), 0));
            ctx.phase = RETREAT;
            suspendProgress(ctx);
            return MovementStatus.RUNNING;
        }
        return MovementStatus.RUNNING;
    }

    /**
     * Backing up is deliberate "negative progress" toward the waypoint; keep
     * the driver's stuck detector from reading it as a stall and replanning
     * out from under us.
     */
    private static void suspendProgress(TickContext ctx) {
        ctx.ticksSinceProgress = 0;
        ctx.bestHorizDistToWaypoint = Double.MAX_VALUE;
    }

    private MovementStatus tickRetreat(TickContext ctx) {
        int gap = ctx.waypoint.hint() != null ? ctx.waypoint.hint().gapLength() : 0;
        double distToLanding = MovementExecutors.horizontalDistance(ctx.current, ctx.waypoint);
        double[] dir = MovementExecutors.direction2D(ctx.current, ctx.waypoint);
        double[] back = {-dir[0], -dir[1]};
        suspendProgress(ctx);

        // Enough runway: the lip is ~(gap+1) from the landing center; we
        // want runwayFor(gap) blocks of ground behind the lip.
        if (distToLanding >= gap + 1.0 + runwayFor(gap)) {
            PathDebugLog.event("SPRINT_JUMP_RUNWAY_READY dist=%.2f", distToLanding);
            ctx.setVelocity(new Vector(0, ctx.velocity().getY(), 0));
            ctx.phase = RUNWAY;
            return MovementStatus.RUNNING;
        }

        if (!ctx.onGround()) {
            // Fell off something while backing up — nothing sensible left.
            PathDebugLog.event("SPRINT_JUMP_RETREAT_FELL");
            return MovementStatus.FAILED;
        }

        // Edge behind us: can't back up further. Jump from here with what
        // we can build on the way back to the lip.
        if (MovementExecutors.atEdgeAhead(ctx.current, back, 0.45)) {
            PathDebugLog.event("SPRINT_JUMP_NO_RUNWAY dist=%.2f → forced takeoff", distToLanding);
            ctx.setVelocity(new Vector(0, ctx.velocity().getY(), 0));
            ctx.phase = FORCED;
            return MovementStatus.RUNNING;
        }

        // Walk backward (no sprint: precision over speed).
        Vector vel = ctx.velocity();
        double accel = 0.1;
        ctx.setVelocity(new Vector(vel.getX() + back[0] * accel, vel.getY(), vel.getZ() + back[1] * accel));
        MovementExecutors.face(ctx.entity(), dir);   // keep looking at the gap
        return MovementStatus.RUNNING;
    }

    private MovementStatus tickAir(TickContext ctx) {
        AirControl.steer(ctx, ctx.waypoint.x(), ctx.waypoint.z(), ctx.waypoint.y(),
                MovementExecutors.SPRINT_AIR_ACCEL);

        // Fell below the landing level with nothing under us: this jump
        // missed. Report failure so the driver replans from wherever we
        // end up (if we end up anywhere).
        if (ctx.current.getY() < ctx.waypoint.y() - 1.5 && !ctx.onGround()) {
            PathDebugLog.event("SPRINT_JUMP_MISSED y=%.2f landingY=%.2f", ctx.current.getY(), ctx.waypoint.y());
            return MovementStatus.FAILED;
        }

        // Touchdown: on ground with non-positive Y velocity.
        if (ctx.onGround() && ctx.entity().getVelocity().getY() <= 0) {
            double dist = MovementExecutors.horizontalDistance(ctx.current, ctx.waypoint);
            double dy = Math.abs(ctx.current.getY() - ctx.waypoint.y());
            if (dist <= LANDING_TOLERANCE && dy < 1.5) {
                return MovementStatus.SUCCESS;
            }
            // Landed somewhere else (overshoot onto later floor, or short
            // onto an unexpected ledge). Replan from here rather than
            // pretending the waypoint was reached.
            PathDebugLog.event("SPRINT_JUMP_OFF_TARGET dist=%.2f dy=%.2f bot=(%.2f,%.2f,%.2f) wp=(%.2f,%.2f,%.2f)",
                    dist, dy, ctx.current.getX(), ctx.current.getY(), ctx.current.getZ(),
                    ctx.waypoint.x(), ctx.waypoint.y(), ctx.waypoint.z());
            return MovementStatus.FAILED;
        }
        return MovementStatus.RUNNING;
    }

    /** Blocks of ground behind the lip needed to reach {@link #minTakeoffSpeed}. */
    static double runwayFor(int gap) {
        return switch (gap) {
            case 0, 1 -> 1.5;
            case 2 -> 2.5;
            default -> 3.5;
        };
    }

    @Override
    public boolean safeToCancel(TickContext ctx) {
        // Don't replan mid-arc — would strand the bot in the air. Only
        // safe to swap paths while still on the ground.
        return ctx.phase != AIR && ctx.onGround();
    }

    @Override
    public boolean allowsAutoUnstick() {
        // Sprint-jump times its own jump; an external unstick hop during
        // the runway would kill our momentum and strand us short of the gap.
        return false;
    }

    /**
     * Intermediate columns must be traversable at feet/head/above-head of
     * the source level, and at least one must be floorless (a real gap).
     */
    private boolean validateArc(PathPosition src, int dx, int dy, int dz, MaterialProvider materials) {
        int steps = Math.max(Math.abs(dx), Math.abs(dz));
        int srcY = src.getFlooredY();
        boolean sawGap = false;

        // Check each intermediate column (excluding source and destination)
        for (int i = 1; i < steps; i++) {
            double t = (double) i / steps;
            int ix = src.getFlooredX() + (int) Math.round(dx * t);
            int iz = src.getFlooredZ() + (int) Math.round(dz * t);

            // The entity passes through at the source Y level during the jump.
            // Check feet, head, and above-head at source level (conservative —
            // the entity is airborne and may be higher).
            for (int yOff = 0; yOff <= 2; yOff++) {
                if (!BlockClassifier.isTraversable(materials.getMaterial(ix, srcY + yOff, iz))) {
                    return false;
                }
            }
            if (!BlockClassifier.isSolid(materials.getMaterial(ix, srcY - 1, iz))) {
                sawGap = true;
            }
        }
        return sawGap;
    }
}
