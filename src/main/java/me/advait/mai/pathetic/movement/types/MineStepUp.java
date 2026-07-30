package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Material;

import java.util.List;

/**
 * Mine a breakable obstruction one block up-and-forward (dy=+1), then step up
 * into it — the mining equivalent of {@link StepUp}. Used to tunnel upward
 * through walls/overhangs.
 */
public record MineStepUp() implements MovementType {

    @Override
    public String key() { return "mine_step_up"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        if (dy != 1) return false;
        if (!((Math.abs(dx) == 1 && dz == 0) || (dx == 0 && Math.abs(dz) == 1))) return false;

        Material below = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() - 1, current.getFlooredZ());
        if (!BlockClassifier.isSolid(below)) return false;

        Material feet = ctx.materials().getMaterial(current);
        Material head = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ());
        boolean feetBlocked = BlockClassifier.isSolid(feet);
        boolean headBlocked = BlockClassifier.isSolid(head);
        if (!feetBlocked && !headBlocked) return false;   // plain StepUp handles a clear step
        if (feetBlocked && !BlockClassifier.isBreakable(feet)) return false;
        if (headBlocked && !BlockClassifier.isBreakable(head)) return false;

        // Need clearance above the source head to jump.
        Material aboveHead = ctx.materials().getMaterial(previous.getFlooredX(), previous.getFlooredY() + 2, previous.getFlooredZ());
        return BlockClassifier.isTraversable(aboveHead);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        double cost = ctx.config().getStepUp() + ctx.config().getJumpPenalty();
        Material feet = ctx.materials().getMaterial(current);
        Material head = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ());
        if (BlockClassifier.isSolid(feet)) cost += ctx.capabilities().breakCost(feet, ctx.config());
        if (BlockClassifier.isSolid(head)) cost += ctx.capabilities().breakCost(head, ctx.config());
        return cost;
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.mine();
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        return caps.canMine() && caps.canJump();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        Material below = ctx.materials().getMaterial(position.getFlooredX(), position.getFlooredY() - 1, position.getFlooredZ());
        if (!BlockClassifier.isSolid(below)) return false;
        Material feet = ctx.materials().getMaterial(position);
        Material head = ctx.materials().getMaterial(position.getFlooredX(), position.getFlooredY() + 1, position.getFlooredZ());
        return (BlockClassifier.isTraversable(feet) || BlockClassifier.isBreakable(feet))
                && (BlockClassifier.isTraversable(head) || BlockClassifier.isBreakable(head));
    }

    @Override
    public List<PathPosition> toBreak(PathPosition current, PathPosition previous, PathContext ctx) {
        return List.of(current, PathPosition.of(current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ()));
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        int bx = (int) Math.floor(ctx.waypoint.x());
        int by = (int) ctx.waypoint.y();
        int bz = (int) Math.floor(ctx.waypoint.z());

        MovementStatus mining = MineThrough.driveMining(ctx, bx, by, bz);
        if (mining != null) return mining;

        // Cleared — step up into it (mirrors StepUp).
        boolean sprinting = ctx.speedFactor > 1.0;
        double horizDist = MovementExecutors.horizontalDistance(ctx.current, ctx.waypoint);
        boolean needsJump = ctx.waypoint.y() > ctx.current.getY() + 0.3;
        double jumpWindowMax = sprinting ? 2.3 : 1.6;
        double takeoffSpeed = sprinting ? 0.24 : 0.18;

        if (ctx.onGround() && ctx.jumpCooldown == 0 && needsJump
                && horizDist > 0.4 && horizDist < jumpWindowMax) {
            double[] dir = MovementExecutors.direction2D(ctx.current, ctx.waypoint);
            MovementExecutors.kickToward(ctx, dir, takeoffSpeed);
            MovementExecutors.jump(ctx, sprinting);
        }
        MovementExecutors.groundAccelerate(ctx, ctx.speedFactor, sprinting);

        if (MovementExecutors.reachedHorizontally(ctx) && ctx.current.getY() >= ctx.waypoint.y() - 0.1) {
            return MovementStatus.SUCCESS;
        }
        return MovementStatus.RUNNING;
    }

    @Override
    public boolean safeToCancel(TickContext ctx) {
        return (ctx.subAction == null || ctx.subAction.isDone()) && ctx.onGround();
    }

    @Override
    public boolean allowsAutoUnstick() {
        return false;
    }
}
