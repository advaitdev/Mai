package me.advait.mai.body.fake;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * A server-side player with no client: a real {@code ServerPlayer} (player
 * physics, hunger, inventory, advancements, attacks) whose movement the
 * server simulates itself from the keys {@code Body} presses.
 *
 * <p>For a real player the client simulates movement and the server only
 * checks the positions it is sent: {@code ServerPlayer.tick} runs no physics,
 * and the connection's tick runs {@code doTick} (the Player tick: aiStep,
 * travel, food) and then puts the player back where it started. So this
 * player runs {@code doTick} itself, from its own entity tick, and its
 * connection never ticks.
 */
final class FakePlayer extends ServerPlayer {

    FakePlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation info) {
        super(server, level, profile, info);
    }

    /**
     * The server owns this player's movement: {@code Entity.move} then checks
     * fall damage and updates the ground flag as it does for a mob (for a
     * real player both come from its move packets). Vanilla's own test
     * players do the same.
     */
    @Override
    public boolean isClientAuthoritative() {
        return false;
    }

    /**
     * A real player's sprint-jump boost is added by its client on every
     * jump. The server's copy of the jump (run for statistics and hunger
     * when a client reports one) only adds the boost if 250 ms of real time
     * have passed since the last, so that a client can't stack boosts. Here
     * the server's copy is the only one: without this, quick repeat jumps
     * (under a two-high ceiling) lost the boost, depending on the wall clock.
     */
    @Override
    public void jumpFromGround() {
        lastJumpTime = 0L;
        super.jumpFromGround();
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) return;
        double x = getX(), y = getY(), z = getZ();
        doTick();
        if (isRemoved()) return;
        // What the connection does with a moved player: walking/sprinting
        // exhaustion and statistics, and the chunk loader's position.
        checkMovementStatistics(getX() - x, getY() - y, getZ() - z);
        level().getChunkSource().move(this);
    }
}
