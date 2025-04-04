//package me.advait.patheticcitizens.navigator;
//
//import com.google.common.collect.Lists;
//import me.advait.patheticcitizens.pathfinder.PatheticAgent;
//import net.citizensnpcs.Settings;
//import net.citizensnpcs.api.ai.AbstractPathStrategy;
//import net.citizensnpcs.api.ai.NavigatorParameters;
//import net.citizensnpcs.api.ai.TargetType;
//import net.citizensnpcs.api.ai.event.CancelReason;
//import net.citizensnpcs.api.astar.AStarMachine;
//import net.citizensnpcs.api.astar.pathfinder.*;
//import net.citizensnpcs.api.npc.NPC;
//import net.citizensnpcs.npc.ai.AStarNavigationStrategy;
//import net.citizensnpcs.npc.ai.NMSChunkBlockSource;
//import net.citizensnpcs.util.NMS;
//import net.citizensnpcs.util.Util;
//import org.bukkit.Effect;
//import org.bukkit.Location;
//import org.bukkit.Material;
//import org.bukkit.entity.EntityType;
//import org.bukkit.entity.LivingEntity;
//import org.bukkit.util.Vector;
//
//import java.util.List;
//
//public class PatheticNavigationStrategy extends AbstractPathStrategy {
//
//    private final Location destination;
//    private final NPC npc;
//    private final NavigatorParameters params;
//    private Path plan;
//    private Vector vector;
//    private static PatheticAgent ASTAR = PatheticAgent.getInstance();
//
//    public PatheticNavigationStrategy(NPC npc, Iterable<Vector> path, NavigatorParameters params) {
//        super(TargetType.LOCATION);
//        List<Vector> list = Lists.newArrayList(path);
//        this.params = params;
//        this.destination = ((Vector)list.get(list.size() - 1)).toLocation(npc.getStoredLocation().getWorld());
//        this.npc = npc;
//        this.plan = new Path(list);
//    }
//
//    public PatheticNavigationStrategy(NPC npc, Location dest, NavigatorParameters params) {
//        super(TargetType.LOCATION);
//        this.params = params;
//        this.destination = dest;
//        this.npc = npc;
//    }
//
//    public Location getCurrentDestination() {
//        return this.vector != null ? this.vector.toLocation(this.npc.getEntity().getWorld()) : this.destination.clone();
//    }
//
//    public Iterable<Vector> getPath() {
//        return this.plan == null ? null : this.plan.getPath();
//    }
//
//    public Location getTargetAsLocation() {
//        return this.destination;
//    }
//
//    public void stop() {
//        if (this.plan != null && this.params.debug()) {
//            Util.sendBlockChanges(this.plan.getBlocks(this.npc.getEntity().getWorld()), (Material)null);
//        }
//
//        this.plan = null;
//    }
//
//    public boolean update() {
//        if (this.planner != null) {
//            CancelReason reason = this.planner.tick(Settings.Setting.ASTAR_ITERATIONS_PER_TICK.asInt(), Settings.Setting.MAXIMUM_ASTAR_ITERATIONS.asInt());
//            this.plan = this.planner.plan;
//            if (reason == null && this.plan == null) {
//                return false;
//            }
//
//            this.setCancelReason(reason);
//            this.planner = null;
//        }
//
//        if (this.getCancelReason() == null && this.plan != null && !this.plan.isComplete()) {
//            if (this.vector == null) {
//                this.vector = this.plan.getCurrentVector();
//            }
//
//            Location loc = this.npc.getEntity().getLocation();
//            Location dest = Util.getCenterLocation(this.vector.toLocation(loc.getWorld()).getBlock());
//            double dX = dest.getX() - loc.getX();
//            double dZ = dest.getZ() - loc.getZ();
//            double dY = dest.getY() - loc.getY();
//            double xzDistance = Math.sqrt(dX * dX + dZ * dZ);
//            if (Math.abs(dY) < (double)1.0F && xzDistance <= this.params.distanceMargin()) {
//                this.plan.update(this.npc);
//                if (this.plan.isComplete()) {
//                    return true;
//                } else {
//                    this.vector = this.plan.getCurrentVector();
//                    return false;
//                }
//            } else {
//                if (this.params.debug()) {
//                    this.npc.getEntity().getWorld().playEffect(dest, Effect.ENDER_SIGNAL, 0);
//                }
//
//                if (this.npc.getEntity() instanceof LivingEntity && this.npc.getEntity().getType() != EntityType.ARMOR_STAND) {
//                    NMS.setDestination(this.npc.getEntity(), dest.getX(), dest.getY(), dest.getZ(), this.params.speedModifier());
//                } else {
//                    Vector dir = dest.toVector().subtract(this.npc.getEntity().getLocation().toVector()).normalize().multiply(0.2 * (double)this.params.speedModifier());
//                    boolean liquidOrInLiquid = MinecraftBlockExaminer.isLiquidOrInLiquid(loc.getBlock());
//                    if (dY >= (double)1.0F && xzDistance <= 0.4 || dY >= 0.2 && liquidOrInLiquid) {
//                        dir.add(new Vector((double)0.0F, (double)0.75F, (double)0.0F));
//                    }
//
//                    this.npc.getEntity().setVelocity(dir);
//                    Util.faceLocation(this.npc.getEntity(), dest);
//                }
//
//                this.plan.run(this.npc);
//                return false;
//            }
//        } else {
//            return true;
//        }
//    }
//
//}
