package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.debug.PathDebugLog;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Location;
import org.bukkit.Material;

import java.util.List;

/**
 * Pillar straight up one block (dx=dz=0, dy=+1): jump and place a block under
 * the feet, landing on it one block higher. Lets the bot reach a target
 * directly above with no adjacent geometry.
 */
public record PillarUp() implements MovementType {

    @Override
    public String key() { return "pillar_up"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        if (dx != 0 || dz != 0 || dy != 1) return false;

        // Room to jump from the source (head clearance above the source head).
        Material aboveHead = ctx.materials().getMaterial(previous.getFlooredX(), previous.getFlooredY() + 2, previous.getFlooredZ());
        if (!BlockClassifier.isTraversable(aboveHead)) return false;

        // Destination column must be clear to occupy.
        Material feet = ctx.materials().getMaterial(current);
        Material head = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ());
        return BlockClassifier.isTraversable(feet) && BlockClassifier.isTraversable(head);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        return ctx.config().getStepUp() + ctx.config().getJumpPenalty() + ctx.capabilities().placementCost();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.bridge();
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        return caps.canBridge() && caps.canJump();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        Material feet = ctx.materials().getMaterial(position);
        Material head = ctx.materials().getMaterial(position.getFlooredX(), position.getFlooredY() + 1, position.getFlooredZ());
        return BlockClassifier.isTraversable(feet) && BlockClassifier.isTraversable(head);
    }

    @Override
    public List<PathPosition> toPlace(PathPosition current, PathPosition previous, PathContext ctx) {
        return List.of(PathPosition.of(current.getFlooredX(), current.getFlooredY() - 1, current.getFlooredZ()));
    }

    @Override
    public MovementStatus tick(TickContext ctx) {
        int bx = (int) Math.floor(ctx.waypoint.x());
        int by = (int) ctx.waypoint.y();
        int bz = (int) Math.floor(ctx.waypoint.z());
        double sourceY = by - 1;   // feet level we are pillaring up from

        Material pillarCell = ctx.entity().getWorld().getBlockAt(bx, by - 1, bz).getType();
        boolean placed = BlockClassifier.isSolid(pillarCell);

        // Already up on the placed block.
        if (placed && ctx.onGround() && ctx.current.getY() >= ctx.waypoint.y() - 0.1) {
            return MovementStatus.SUCCESS;
        }

        // On the ground at the source: hop straight up (no horizontal drift).
        if (!placed && ctx.onGround() && ctx.jumpCooldown == 0) {
            MovementExecutors.jumpVerticalOnly(ctx);
            return MovementStatus.RUNNING;
        }

        // Airborne and clear of the placement cell: place the pillar block.
        if (!placed && ctx.current.getY() > sourceY + 0.3) {
            Location target = new Location(ctx.entity().getWorld(), bx + 0.5, by - 1, bz + 0.5);
            if (!BridgePlace.placeSupport(ctx.humanoid, target)) {
                PathDebugLog.event("PILLAR_FAILED no placeable block at (%d,%d,%d)", bx, by - 1, bz);
                return MovementStatus.FAILED;
            }
            PathDebugLog.event("PILLAR_PLACED at (%d,%d,%d)", bx, by - 1, bz);
        }
        return MovementStatus.RUNNING;
    }

    @Override
    public boolean safeToCancel(TickContext ctx) {
        return ctx.onGround();
    }

    @Override
    public boolean allowsAutoUnstick() {
        return false;
    }
}
