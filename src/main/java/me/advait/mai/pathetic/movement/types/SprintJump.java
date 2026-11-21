package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.body.Body;
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
 *   RUNWAY  — sprint toward the landing; jump on the tick that lands on
 *             the center (early) or at the lip; if the run-up left can't
 *             reach takeoff speed and there is room to stop, RETREAT.
 *   AIR     — steer toward the landing; on touchdown verify we actually
 *             landed on/next to the landing block (else FAILED → replan).
 *   RETREAT — too slow for the gap (e.g. after a replan put us right
 *             at the lip). Hold S to back away for runway, then RUNWAY.
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

        // Soul sand, honey and other slow blocks cap the run-up speed far
        // below what the takeoff model assumes; never plan a jump off one.
        if (BlockClassifier.speedMultiplier(materials.getMaterial(sx, sy - 1, sz)) < 1.0) return false;

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
        // Sprint from a standstill at the center of the farthest run-up block;
        // the lip is 0.5 past the source center. Same takeoff trigger and
        // ground model as the executor (SprintJump.predictTakeoff).
        double lip = blocksBehind + 0.5;
        double slip = me.advait.mai.pathetic.BlockClassifier.slipperiness(
                ctx.materials().getMaterial(src.getFlooredX(), src.getFlooredY() - 1, src.getFlooredZ()));
        double groundDrag = slip * c.getAirDrag();
        double firstTick = me.advait.mai.body.Body.groundAccel(slip, true);
        double[] takeoff = predictTakeoff(firstTick, lip, slip, groundDrag, true, c.getSprintJumpBoost());

        double dist = blocksBehind + horizDist - takeoff[1];
        double[] need = AirControl.takeoffSpeeds(dist, dy, slip, MovementExecutors.SPRINT_AIR_ACCEL, c);
        return need != null && need[1] <= takeoff[0];
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

        // Runway: hold W and sprint toward the landing.
        MovementExecutors.walk(ctx, true);
        boolean sprinting = ctx.body().sprinting();
        double slip = MovementExecutors.slipperinessBelow(ctx.current);
        double groundDrag = slip * ctx.config.getAirDrag();
        double boost = sprinting ? ctx.config.getSprintJumpBoost() : 0;
        Vector vel = ctx.velocity();
        // This tick's displacement along the jump if we keep running: the
        // post-friction velocity read at tick start plus this tick's W.
        double along = vel.getX() * dir[0] + vel.getZ() * dir[1] + Body.groundAccel(slip, sprinting);
        double takeoff = Math.max(0.0, along) + boost;
        double dyJump = ctx.waypoint.y() - ctx.current.getY();
        double[] need = AirControl.takeoffSpeeds(distToLanding, dyJump, slip,
                MovementExecutors.SPRINT_AIR_ACCEL, ctx.config);

        // The edge we care about is the one right before the landing — not
        // some unrelated lip far behind on a long runway.
        boolean edgeIsOurGap = distToLanding < gap + 2.6;
        // Last tick on the takeoff block: this tick's move would carry the
        // center past the lip by more than the hitbox can hang over it.
        boolean atEdge = MovementExecutors.atEdgeAhead(ctx.current, dir,
                Math.max(0.0, along) - MovementExecutors.TAKEOFF_EDGE_LEAD);
        // Already fast enough that jumping now lands on the center with half
        // the air control in reserve: jump before the lip instead of
        // overshooting. Only from the takeoff block itself, whose headroom
        // the planner checked.
        boolean early = edgeIsOurGap && distToLanding <= gap + 1.5 && need != null && takeoff >= need[0];

        if (early || (atEdge && edgeIsOurGap)) {
            PathDebugLog.event("SPRINT_JUMP_TAKEOFF gap=%d along=%.3f takeoff=%.3f need=%.3f/%.3f dist=%.2f dy=%.1f early=%s forced=%s sprint=%s bot=(%.2f,%.2f,%.2f)",
                    gap, along, takeoff,
                    need == null ? -1.0 : need[0], need == null ? -1.0 : need[1],
                    distToLanding, dyJump, early, forced, sprinting,
                    ctx.current.getX(), ctx.current.getY(), ctx.current.getZ());
            MovementExecutors.jump(ctx);
            ctx.phase = AIR;
            return MovementStatus.RUNNING;
        }

        // Approaching our gap: will the run-up left reach the minimum takeoff
        // speed by the lip? If not, and we can still stop on friction before
        // the edge, back off for a longer run-up now. Past that point the
        // only move left is to jump at the lip anyway.
        if (edgeIsOurGap && !forced) {
            double lip = MovementExecutors.edgeDistance(ctx.current, dir);
            double[] atLip = predictTakeoff(Math.max(0.0, along), lip, slip, groundDrag, sprinting,
                    ctx.config.getSprintJumpBoost());
            double[] needAtLip = AirControl.takeoffSpeeds(distToLanding - atLip[1], dyJump, slip,
                    MovementExecutors.SPRINT_AIR_ACCEL, ctx.config);
            boolean makesIt = needAtLip != null && needAtLip[1] <= atLip[0];
            if (!makesIt && stoppingDistance(Math.max(0.0, along), slip, groundDrag) < lip + 0.2) {
                PathDebugLog.event("SPRINT_JUMP_TOO_SLOW gap=%d along=%.3f lip=%.2f takeoff=%.3f need=%.3f dist=%.2f → retreat",
                        gap, along, lip, atLip[0], needAtLip == null ? -1.0 : needAtLip[1], distToLanding);
                ctx.phase = RETREAT;
                suspendProgress(ctx);
                ctx.body().keys(-1, 0).sprint(false);
            }
        }
        return MovementStatus.RUNNING;
    }

    /**
     * Keep sprinting from here to the lip {@code lip} blocks ahead: returns
     * {@code {takeoff speed, distance covered before takeoff}}, using the
     * same trigger as the executor ({@link MovementExecutors#TAKEOFF_EDGE_LEAD}).
     * Also the planner's run-up model.
     */
    static double[] predictTakeoff(double disp, double lip, double slip, double groundDrag,
                                           boolean sprintingNow, double jumpBoost) {
        double accel = Body.groundAccel(slip, true);
        double s = 0;
        for (int t = 0; t < 60; t++) {
            // A sprint requested this tick is on from the next tick (W held).
            double boost = sprintingNow || t > 0 ? jumpBoost : 0;
            if (s + disp > lip + MovementExecutors.TAKEOFF_EDGE_LEAD) return new double[]{disp + boost, s};
            s += disp;
            disp = disp * groundDrag + accel;
        }
        return new double[]{disp + jumpBoost, s};
    }

    /** How far the bot slides holding S from this tick's displacement {@code disp}. */
    private static double stoppingDistance(double disp, double slip, double groundDrag) {
        double brake = Body.groundAccel(slip, false);
        double total = 0;
        double v = disp - brake;
        for (int t = 0; t < 40 && v > 0; t++) {
            total += v;
            v = v * groundDrag - brake;
        }
        return total;
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
            ctx.phase = RUNWAY;
            return tickRunway(ctx, false);
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
            ctx.phase = FORCED;
            return tickRunway(ctx, true);
        }

        // Walk backward facing the gap (S, no sprint: precision over speed).
        MovementExecutors.face(ctx.entity(), dir);
        ctx.body().keys(-1, 0).sprint(false);
        return MovementStatus.RUNNING;
    }

    private MovementStatus tickAir(TickContext ctx) {
        AirControl.steer(ctx, ctx.waypoint.x(), ctx.waypoint.z(), ctx.waypoint.y());

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
