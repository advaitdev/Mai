package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.body.Hands;
import me.advait.mai.body.Humanoid;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.config.MovementConfig;
import me.advait.mai.pathetic.debug.PathDebugLog;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Water-bucket clutch ("MLG"): drop any height and empty a water bucket on
 * the landing block just before touching down, then scoop the water back up.
 *
 * <p>Vanilla only spares the fall if the water is there before the landing
 * tick's move ({@code LivingEntity.checkFallDamage} rechecks water contact
 * after the move). The bucket is a right click that must hit the landing
 * block's top face within 4.5 blocks of the eyes, so there has to be a tick
 * that starts with the feet above the block and under ~2.8 blocks up.
 * Falling at 2-4 blocks a tick that window can fall between two ticks, and
 * the tick positions are fixed by the drop height, so the planner simulates
 * the fall ({@link #placementTick}) and the bot walks off or jumps off,
 * whichever gives it a tick in reach.
 */
public record WaterClutch() implements MovementType {

    private static final int WALK_OFF = 0;
    private static final int FALLING = 1;
    private static final int PLACED = 2;
    private static final int LANDED = 3;

    /** Eye height above the feet. */
    private static final double EYE = 1.62;
    /** Highest the feet may be above the landing block for the bucket to reach it. */
    private static final double MAX_PLACE_HEIGHT = Hands.REACH - EYE - 0.05;
    /** Lowest: the bot must still be above the block when it clicks. */
    private static final double MIN_PLACE_HEIGHT = 0.05;
    /** Ticks to wait on the ground for the water to be scoopable before moving on. */
    private static final int PICKUP_TIMEOUT = 12;
    /** Shorter drops take a heart or two at most and are left to FallUnsafe. */
    private static final int MIN_DROP = 6;

    @Override
    public String key() { return "water_clutch"; }

    /**
     * The airborne tick whose start has the feet in bucket reach above a
     * block {@code drop} blocks below, or -1 if every tick misses the
     * window. Walking off: the first airborne move is the resting -0.0784;
     * jumping: +0.42. Then {@code vy = (vy - 0.08) * 0.98}.
     */
    public static int placementTick(double drop, boolean jump, MovementConfig c) {
        double h = drop;
        double vy = jump ? c.getJumpVelocity() : -c.getGravity() * c.getVerticalDrag();
        for (int k = 0; k < 400; k++) {
            if (k > 0 && h > MIN_PLACE_HEIGHT && h <= MAX_PLACE_HEIGHT) return k;
            h += vy;
            if (h <= 0) return -1;
            vy = (vy - c.getGravity()) * c.getVerticalDrag();
        }
        return -1;
    }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();
        if (dy > -MIN_DROP) return false;
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1 || (dx == 0 && dz == 0)) return false;
        if (!WalkFlat.isStandable(current, ctx.materials())) return false;
        // The water goes into the landing cell, so it must be open air.
        Material landingCell = ctx.materials().getMaterial(current);
        if (BlockClassifier.isLiquid(landingCell)) return false;
        if (!FallSafe.isFallClear(previous, current, ctx.materials())) return false;
        double drop = -dy;
        return placementTick(drop, false, ctx.config()) >= 0 || placementTick(drop, true, ctx.config()) >= 0;
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        int blocks = Math.abs(current.getFlooredY() - previous.getFlooredY());
        // Fall time, plus the bucket swap, the click and scooping it back up.
        return ctx.config().fallCostTicks(blocks) + 12.0;
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.walk();
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        return caps.hasWaterBucket();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        return WalkFlat.isStandable(position, ctx.materials());
    }

    @Override
    public boolean allowsAutoUnstick() {
        return false;
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        double h = ctx.current.getY() - ctx.waypoint.y();
        switch (ctx.phase) {
            case WALK_OFF -> {
                holdBucket(ctx.humanoid);
                if (!ctx.onGround()) {
                    ctx.phase = FALLING;
                    return tick(ctx);
                }
                double[] dir = MovementExecutors.direction2D(ctx.current, ctx.waypoint);
                MovementExecutors.walk(ctx, false);
                boolean jump = placementTick(h, false, ctx.config) < 0;
                double along = ctx.velocity().getX() * dir[0] + ctx.velocity().getZ() * dir[1];
                if (jump && ctx.jumpCooldown == 0
                        && MovementExecutors.atEdgeAhead(ctx.current, dir, Math.max(0, along) + 0.1)) {
                    PathDebugLog.event("CLUTCH_JUMP_OFF drop=%.2f", h);
                    MovementExecutors.jump(ctx);
                }
                return MovementStatus.RUNNING;
            }
            case FALLING, PLACED -> {
                // Steer turns the view toward the landing and picks keys for
                // that yaw; keep it and only tip the view straight down.
                AirControl.steer(ctx, ctx.waypoint.x(), ctx.waypoint.z(), ctx.waypoint.y());
                ctx.body().look(ctx.entity().getLocation().getYaw(), 90f);
                boolean landed = ctx.onGround() || ctx.entity().isInWater();
                if (ctx.phase == FALLING && !landed && h > MIN_PLACE_HEIGHT && h <= MAX_PLACE_HEIGHT) {
                    holdBucket(ctx.humanoid);
                    var water = Hands.emptyBucket(ctx.entity());
                    PathDebugLog.event("CLUTCH_PLACE h=%.2f vy=%.3f ok=%s at=%s", h, ctx.velocity().getY(),
                            water != null, water == null ? "-" : water.getX() + "," + water.getY() + "," + water.getZ());
                    if (water != null) ctx.phase = PLACED;
                }
                if (landed) {
                    if (ctx.phase != PLACED) {
                        PathDebugLog.event("CLUTCH_MISSED h=%.2f", h);
                        return MovementStatus.FAILED;
                    }
                    ctx.phase = LANDED;
                    ctx.ticksOnMovement = 0;
                }
                return MovementStatus.RUNNING;
            }
            default -> {
                // Scoop the water back up: look down at it and click once
                // the right-click delay allows.
                ctx.body().look(ctx.entity().getLocation().getYaw(), 90f);
                if (Hands.fillBucket(ctx.entity())) {
                    PathDebugLog.event("CLUTCH_PICKUP");
                    return MovementStatus.SUCCESS;
                }
                return ctx.ticksOnMovement >= PICKUP_TIMEOUT ? MovementStatus.SUCCESS : MovementStatus.RUNNING;
            }
        }
    }

    /** Puts a water bucket (or the empty one after use) in the main hand. */
    private static void holdBucket(Humanoid h) {
        if (h.getEquipment() == null) return;
        Material held = h.getEquipment().getItemInMainHand().getType();
        if (held == Material.WATER_BUCKET) return;
        ItemStack[] inv = h.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            if (inv[i] != null && inv[i].getType() == Material.WATER_BUCKET) {
                h.setItemInMainHand(i);
                return;
            }
        }
    }

    @Override
    public boolean safeToCancel(TickContext ctx) {
        return ctx.phase == WALK_OFF;
    }
}
