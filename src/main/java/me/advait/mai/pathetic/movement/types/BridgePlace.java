package me.advait.mai.pathetic.movement.types;

import de.bsommerfeld.pathetic.api.wrapper.PathPosition;
import me.advait.mai.body.Body;
import me.advait.mai.body.Hands;
import me.advait.mai.body.Humanoid;
import me.advait.mai.brain.action.mechanic.building.HumanoidBuildAction;
import me.advait.mai.pathetic.BlockClassifier;
import me.advait.mai.pathetic.PathContext;
import me.advait.mai.pathetic.capabilities.HumanoidCapabilities;
import me.advait.mai.pathetic.debug.PathDebugLog;
import me.advait.mai.pathetic.movement.*;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.util.Vector;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Bridge across a 1-block gap on the flat (dy=0): the destination feet/head
 * are clear but there's no floor below it. The bot places a support block
 * under the destination then walks onto it.
 */
public record BridgePlace() implements MovementType {

    @Override
    public String key() { return "bridge_place"; }

    @Override
    public boolean matches(PathPosition current, PathPosition previous, PathContext ctx) {
        int dx = current.getFlooredX() - previous.getFlooredX();
        int dy = current.getFlooredY() - previous.getFlooredY();
        int dz = current.getFlooredZ() - previous.getFlooredZ();

        if (dy != 0) return false;
        if (!((Math.abs(dx) == 1 && dz == 0) || (dx == 0 && Math.abs(dz) == 1))) return false;

        Material feet = ctx.materials().getMaterial(current);
        Material head = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() + 1, current.getFlooredZ());
        if (!BlockClassifier.isTraversable(feet) || !BlockClassifier.isTraversable(head)) return false;

        // The gap: no floor below the destination (WalkFlat would handle a real floor).
        Material below = ctx.materials().getMaterial(current.getFlooredX(), current.getFlooredY() - 1, current.getFlooredZ());
        return !BlockClassifier.isSolid(below);
    }

    @Override
    public double computeCost(PathPosition current, PathPosition previous, PathContext ctx) {
        return ctx.config().getWalkFlat() + ctx.capabilities().placementCost();
    }

    @Override
    public ExecutionHint executionHint(PathPosition current, PathPosition previous) {
        return ExecutionHint.bridge();
    }

    @Override
    public boolean isAllowed(HumanoidCapabilities caps) {
        return caps.canBridge();
    }

    @Override
    public boolean canReachAsEndpoint(PathPosition position, PathContext ctx) {
        // Bridging supports floating/elevated targets — never require a floor.
        Material feet = ctx.materials().getMaterial(position);
        Material head = ctx.materials().getMaterial(position.getFlooredX(), position.getFlooredY() + 1, position.getFlooredZ());
        return BlockClassifier.isTraversable(feet) && BlockClassifier.isTraversable(head);
    }

    @Override
    public List<PathPosition> toPlace(PathPosition current, PathPosition previous, PathContext ctx) {
        return List.of(PathPosition.of(current.getFlooredX(), current.getFlooredY() - 1, current.getFlooredZ()));
    }

    /** How far the center may hang past the edge before the hitbox loses the block (half-width 0.3). */
    private static final double HANG_LIMIT = 0.29;
    /** Center must be at least this far past the edge for the eyes to see the block's side face. */
    private static final double SEE_FACE = 0.02;
    /** View pitch while bridging, looking down past the heels at the edge. */
    private static final float BRIDGE_PITCH = 78f;

    /**
     * Speedbridging, the way players do it: face back along the bridge,
     * look down, and walk backwards (S). Once the center hangs past the
     * edge, the side face of the block underfoot is in view below the heels;
     * click it to place the next block, and keep backing onto it. Each tick
     * the bot predicts its displacement (vanilla ground model) and holds S,
     * nothing, or W to brake, so its center never passes the point where the
     * hitbox leaves the block without a block there. Every placement goes
     * through {@link Hands}: in reach, the eyes hitting that face, the
     * 4-tick click delay, the cell clear of hitboxes.
     */
    @Override
    public MovementStatus tick(TickContext ctx) {
        int bx = (int) Math.floor(ctx.waypoint.x());
        int by = (int) ctx.waypoint.y();
        int bz = (int) Math.floor(ctx.waypoint.z());
        int[] d = direction(ctx, bx, bz);
        float backYaw = Body.yawOf(-d[0], -d[1]);

        // Where the center is along the bridge, relative to the far edge of
        // the block we stand on (the target cell's near edge).
        double edge = d[0] != 0 ? (d[0] > 0 ? bx : bx + 1) : (d[1] > 0 ? bz : bz + 1);
        double pos = d[0] != 0 ? ctx.current.getX() : ctx.current.getZ();
        double past = (pos - edge) * (d[0] != 0 ? d[0] : d[1]);

        org.bukkit.block.Block supportCell = ctx.entity().getWorld().getBlockAt(bx, by - 1, bz);
        boolean placed = BlockClassifier.isSolid(supportCell.getType());
        if (!placed && past > SEE_FACE && past <= HANG_LIMIT + 0.01) {
            if (ensurePlaceableInHand(ctx.humanoid) == null) {
                PathDebugLog.event("BRIDGE_FAILED no placeable block for (%d,%d,%d)", bx, by - 1, bz);
                return MovementStatus.FAILED;
            }
            org.bukkit.block.Block against = supportCell.getRelative(-d[0], 0, -d[1]);
            BlockFace face = faceOf(d);
            if (Hands.placeBlock(ctx.entity(), against, face)) {
                placed = true;
                PathDebugLog.event("BRIDGE_PLACED at (%d,%d,%d) past=%.2f", bx, by - 1, bz, past);
            }
        }
        if (!placed) {
            ctx.body().look(backYaw, BRIDGE_PITCH);
        }

        // Keys. Facing back, S moves along the bridge and W brakes.
        Vector v = ctx.velocity();
        double along = v.getX() * d[0] + v.getZ() * d[1];
        double accel = Body.groundAccel(MovementExecutors.slipperinessBelow(ctx.current), false);
        int forward;
        if (placed || past + along + accel <= HANG_LIMIT) forward = -1;
        else if (past + along <= HANG_LIMIT) forward = 0;
        else forward = 1;
        ctx.body().keys(forward, strafeToCenter(ctx, d, backYaw)).sprint(false);

        if (placed && past >= -0.1) return MovementStatus.SUCCESS;
        if (!placed && !ctx.onGround()) {
            PathDebugLog.event("BRIDGE_FELL past=%.2f", past);
            return MovementStatus.FAILED;
        }
        return MovementStatus.RUNNING;
    }

    /** Unit step from the previous cell to the target (cardinal; see {@link #matches}). */
    private static int[] direction(TickContext ctx, int bx, int bz) {
        int fx, fz;
        if (ctx.previousWaypoint != null) {
            fx = bx - (int) Math.floor(ctx.previousWaypoint.x());
            fz = bz - (int) Math.floor(ctx.previousWaypoint.z());
        } else {
            fx = bx - (int) Math.floor(ctx.current.getX());
            fz = bz - (int) Math.floor(ctx.current.getZ());
        }
        if (Math.abs(fx) >= Math.abs(fz)) return new int[]{Integer.signum(fx), 0};
        return new int[]{0, Integer.signum(fz)};
    }

    private static BlockFace faceOf(int[] d) {
        if (d[0] > 0) return BlockFace.EAST;
        if (d[0] < 0) return BlockFace.WEST;
        return d[1] > 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /** Strafe key that pulls the center back onto the bridge's centerline. */
    private static int strafeToCenter(TickContext ctx, int[] d, float yaw) {
        double centerline = d[0] != 0 ? Math.floor(ctx.waypoint.z()) + 0.5 : Math.floor(ctx.waypoint.x()) + 0.5;
        double lateral = d[0] != 0 ? ctx.current.getZ() : ctx.current.getX();
        double vel = d[0] != 0 ? ctx.velocity().getZ() : ctx.velocity().getX();
        double off = lateral + vel - centerline;
        if (Math.abs(off) < 0.08) return 0;
        double accel = Body.groundAccel(MovementExecutors.slipperinessBelow(ctx.current), false);
        int best = 0;
        double bestErr = Math.abs(off);
        for (int r = -1; r <= 1; r += 2) {
            double[] a = Body.accel(0, r, false, yaw, accel / Body.KEY_SCALE);
            double err = Math.abs(off + (d[0] != 0 ? a[1] : a[0]));
            if (err < bestErr) { bestErr = err; best = r; }
        }
        return best;
    }

    @Override
    public boolean allowsAutoUnstick() {
        return false;
    }

    /** Ensures a placeable block is in hand and places it at {@code target}. */
    static boolean placeSupport(Humanoid humanoid, Location target) {
        ItemStack block = ensurePlaceableInHand(humanoid);
        if (block == null) return false;
        return HumanoidBuildAction.placeBlock(humanoid.getEntity(), target, block);
    }

    /**
     * Returns a placeable block in the main hand: the currently-held stack if
     * it's already a block, otherwise the cheapest (lowest placement-value)
     * block from the inventory swapped into hand. Null if none available.
     */
    public static ItemStack ensurePlaceableInHand(Humanoid humanoid) {
        if (humanoid.getEquipment() != null) {
            ItemStack main = humanoid.getEquipment().getItemInMainHand();
            if (isPlaceable(main)) return main;
        }
        Inventory inv = humanoid.getInventory();
        ItemStack[] contents = inv.getContents();
        int bestSlot = -1;
        double bestValue = Double.MAX_VALUE;
        for (int i = 0; i < contents.length; i++) {
            if (!isPlaceable(contents[i])) continue;
            double value = BlockClassifier.placementValue(contents[i].getType());
            if (value < bestValue) { bestValue = value; bestSlot = i; }
        }
        if (bestSlot == -1) return null;
        humanoid.setItemInMainHand(bestSlot);
        return humanoid.getEquipment() != null ? humanoid.getEquipment().getItemInMainHand() : null;
    }

    private static boolean isPlaceable(ItemStack item) {
        return item != null && item.getAmount() > 0 && item.getType().isBlock() && !item.getType().isAir();
    }
}
