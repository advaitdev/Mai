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

        // On the ground at the source: hop straight up with no keys held.
        // Wait for leftover momentum to die on friction first, or the hop
        // drifts off the pillar cell.
        if (!placed && ctx.onGround() && ctx.jumpCooldown == 0) {
            // Over the middle of the column first (a pillar is 1x1), then
            // still, then a straight hop with no keys held.
            if (MovementExecutors.horizontalDistance(ctx.current, ctx.waypoint) > 0.25) {
                MovementExecutors.walk(ctx, false);
                return MovementStatus.RUNNING;
            }
            ctx.body().keys(0, 0);
            if (MovementExecutors.horizontalSpeed(ctx.velocity()) > 0.03) return MovementStatus.RUNNING;
            MovementExecutors.jump(ctx);
            return MovementStatus.RUNNING;
        }

        // Airborne: look straight down, and once the feet have cleared the
        // cell (vanilla won't place a block into the bot's own hitbox),
        // click the top of the block below.
        if (!placed && !ctx.onGround()) {
            ctx.body().aim(ctx.entity().getLocation().getYaw(), 90f);
            if (ctx.current.getY() >= by + 0.001) {
                if (BridgePlace.ensurePlaceableInHand(ctx.humanoid) == null) {
                    PathDebugLog.event("PILLAR_FAILED no placeable block at (%d,%d,%d)", bx, by - 1, bz);
                    return MovementStatus.FAILED;
                }
                org.bukkit.block.Block below = ctx.entity().getWorld().getBlockAt(bx, by - 2, bz);
                if (me.advait.mai.body.Hands.placeBlock(ctx.entity(), below, org.bukkit.block.BlockFace.UP)) {
                    PathDebugLog.event("PILLAR_PLACED at (%d,%d,%d) feet=%.2f", bx, by - 1, bz, ctx.current.getY());
                }
            }
        }
        if (!placed && ctx.onGround() && ctx.current.getY() < sourceY + 0.5 && ctx.ticksOnMovement > 20) {
            PathDebugLog.event("PILLAR_FAILED landed without placing at (%d,%d,%d)", bx, by - 1, bz);
            return MovementStatus.FAILED;
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
