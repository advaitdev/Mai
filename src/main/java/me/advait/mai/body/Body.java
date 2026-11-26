package me.advait.mai.body;

import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.util.Vector;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The humanoid's keyboard and mouse: the only way anything moves the body.
 *
 * <p>Controllers press keys (W/A/S/D, jump, sprint, sneak) and turn the
 * view (any yaw/pitch, full float precision). Body writes those into the
 * entity's own vanilla input fields ({@code xxa}, {@code zza},
 * {@code jumping}) exactly as the client does for a real player, and the
 * server's {@code LivingEntity.travel()} turns them into motion: friction,
 * acceleration relative to facing, the jump impulse and sprint-jump boost,
 * gravity, drag and collisions all come from Minecraft itself, not from us.
 *
 * <p>Three gaps between a Mannequin and a player are closed here, each
 * copied from the vanilla source (26.2):
 * <ul>
 *   <li>Key input goes through the client's {@code LocalPlayer.modifyInput}:
 *       normalized, x0.98, x sneak factor, then the square-movement fix
 *       (straight input has length 0.98, diagonal 1.0).
 *   <li>Players copy the movement-speed attribute into {@code speed} every
 *       tick ({@code Player.aiStep}); a Mannequin never does, and its base
 *       attribute is 0.7 instead of the player's 0.1.
 *   <li>Airborne sprinting players get 0.026 air control
 *       ({@code Player.getFlyingSpeed}); {@code LivingEntity} hard-codes
 *       0.02. The missing 0.006 is added through the same
 *       {@code moveRelative} formula.
 * </ul>
 * Sprinting follows the client rules too: it starts only with W held and
 * stops only when W is released, in shallow water, or on a hard collision.
 * Asking to stop sprinting while holding W therefore costs a one-tick W
 * release, a w-tap, exactly like a player.
 *
 * <p>Keys are released at the start of every server tick, so a controller
 * that stops writing stops the bot (it coasts to a halt on friction). View
 * direction persists.
 */
public final class Body {

    /** {@code LocalPlayer.modifyInput}: every key impulse is scaled by this. */
    public static final float KEY_SCALE = 0.98F;
    /** {@code Player.createAttributes}: player movement speed. */
    public static final double PLAYER_MOVEMENT_SPEED = 0.1;
    /** {@code Player.getFlyingSpeed} while sprinting. */
    public static final float SPRINT_AIR_SPEED = 0.025999999F;
    /** {@code LivingEntity.getFlyingSpeed} for anything that isn't a player. */
    public static final float LIVING_AIR_SPEED = 0.02F;
    /** Vanilla sprint movement-speed modifier (+30%, multiplied total). */
    public static final double SPRINT_SPEED_FACTOR = 1.3;
    /** Default {@code sneaking_speed} attribute. */
    public static final float SNEAK_FACTOR = 0.3F;

    private static final Map<UUID, Body> BODIES = new ConcurrentHashMap<>();

    private static MethodHandle getHandle;
    private static VarHandle xxa, zza, horizontalCollision, minorHorizontalCollision, activatedTick;
    private static MethodHandle setJumping, setSprinting, isSprinting, setSpeed;

    private final LivingEntity entity;
    /**
     * A fake player: vanilla already gives it the player's air control,
     * swim steering and swim pose (Player.travel, Player.updatePlayerPose),
     * so only the client-side rules are applied here.
     */
    private final boolean player;

    private int forward;     // +1 W, -1 S
    private int right;       // +1 D, -1 A
    private boolean jump;
    private boolean sprint;
    private boolean sneak;

    /**
     * Sprint state when this tick began. The client evaluates its sprint
     * rules once per tick on the final keys; Body re-applies on every write,
     * so the outcome is computed from this plus the current keys and comes
     * out the same whatever order a controller presses them in.
     */
    private boolean sprintingAtTickStart;
    /**
     * {@code LocalPlayer.isHorizontalCollisionMinor} for last tick's move: the
     * bot still moved within 8 degrees of where its keys pushed, so a wall it
     * grazed doesn't end the sprint. A Mannequin inherits LivingEntity's
     * version, which calls every collision hard, so every brush with a
     * corner cost it the sprint.
     */
    private boolean minorCollision;
    private double lastX = Double.NaN, lastZ;
    /** Tick on which the game ended the sprint; it can't restart until the next one. */
    private int sprintBlockedTick = -1;

    /** Velocity added this tick for the sprint air-control gap, so re-applying can replace it. */
    private Vector airAssist = new Vector();
    private int airAssistTick = -1;

    /**
     * How the head turns ({@code settings.yml -> movement.view}). A turn
     * covers {@code gain} of what is left each tick, at least {@code minTurn}
     * and at most {@code maxTurn} degrees, so the view sweeps round like a
     * mouse instead of snapping. {@code maxTurn} 180 with {@code gain} 1
     * and no noise is an instant, exact view.
     */
    public record View(float maxTurn, float gain, float minTurn, float noiseYaw, float noisePitch,
                       boolean naturalPitch, double gazeDistance) {
        public static final View EXACT = new View(180F, 1F, 180F, 0F, 0F, false, 4.0);
    }

    private static volatile View view = new View(45F, 0.5F, 4F, 0.5F, 1.5F, true, 4.0);

    public static void view(View v) { view = v; }

    public static View view() { return view; }

    /** View at the start of this tick: what a turn this tick starts from, however often it is asked for. */
    private float tickYaw, tickPitch;
    /** How far the view turned last tick (degrees of yaw, signed). */
    private float lastTurn;

    public float lastTurn() { return lastTurn; }

    /** Slow wander added to where the head is pointed (degrees), new each tick. */
    private float noiseYaw, noisePitch;
    /** Seeded the same for every body, so a run repeats exactly. */
    private final java.util.Random wander = new java.util.Random(0x4D6169L);
    /** The yaw this tick's keys are relative to, when the view is still turning toward it. */
    private float keyYaw;
    private boolean remap;
    /** A controller chose the pitch this tick; a plain turn keeps it. */
    private boolean pitchChosen;
    private float naturalPitch;
    private boolean naturalPitchKnown;
    /** The keys the body really holds this tick (after {@link #remap}). */
    private int heldForward, heldRight;

    private Body(LivingEntity entity) {
        this.entity = entity;
        this.player = entity instanceof org.bukkit.entity.Player;
        this.sprintingAtTickStart = (boolean) call(isSprinting);
        var at = entity.getLocation();
        this.tickYaw = at.getYaw();
        this.tickPitch = at.getPitch();
    }

    public static Body of(LivingEntity entity) {
        return BODIES.computeIfAbsent(entity.getUniqueId(), id -> new Body(entity));
    }

    // =========================================================================
    // Setup
    // =========================================================================

    /**
     * Resolves the vanilla members Body drives. Called on enable so a
     * Minecraft update that renames them fails loudly at startup instead of
     * leaving every bot frozen.
     */
    public static void init() {
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> craft = Class.forName("org.bukkit.craftbukkit.entity.CraftLivingEntity");
            Class<?> living = Class.forName("net.minecraft.world.entity.LivingEntity");
            Class<?> nmsEntity = Class.forName("net.minecraft.world.entity.Entity");
            getHandle = lookup.findVirtual(craft, "getHandle", MethodType.methodType(living));
            xxa = lookup.findVarHandle(living, "xxa", float.class);
            zza = lookup.findVarHandle(living, "zza", float.class);
            horizontalCollision = lookup.findVarHandle(nmsEntity, "horizontalCollision", boolean.class);
            minorHorizontalCollision = lookup.findVarHandle(nmsEntity, "minorHorizontalCollision", boolean.class);
            activatedTick = lookup.findVarHandle(nmsEntity, "activatedTick", long.class);
            setJumping = lookup.findVirtual(living, "setJumping", MethodType.methodType(void.class, boolean.class));
            setSprinting = lookup.findVirtual(living, "setSprinting", MethodType.methodType(void.class, boolean.class));
            isSprinting = lookup.findVirtual(nmsEntity, "isSprinting", MethodType.methodType(boolean.class));
            setSpeed = lookup.findVirtual(living, "setSpeed", MethodType.methodType(void.class, float.class));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Body: this server's Minecraft internals don't match ("
                    + Bukkit.getMinecraftVersion() + "): " + e, e);
        }
    }

    /** Gives a freshly spawned body a player's movement attributes. */
    public static void prepare(LivingEntity entity) {
        AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) speed.setBaseValue(PLAYER_MOVEMENT_SPEED);
        of(entity).apply();
    }

    /**
     * Start of every server tick: release all keys. A key a controller
     * wants held is pressed again during the tick; if nothing presses W,
     * the sprint ends as it would for a player letting go of W.
     *
     * <p>Also keeps the body out of Paper's entity activation range. An
     * entity far from every player that stands still on the ground for a
     * while stops ticking (and so ignores its keys) until a check every 20
     * ticks. A player never does, and a humanoid with no player nearby (the
     * obstacle harness, a headless benchmark) has to behave like one.
     */
    public static void releaseAll() {
        long now = Bukkit.getCurrentTick();
        BODIES.values().removeIf(b -> {
            if (!b.entity.isValid()) return true;
            activatedTick.set(b.handle(), now + 1);
            b.lastTick = b.describe();
            b.sprintingAtTickStart = b.sprinting();
            b.updateMinorCollision();
            b.forward = 0;
            b.right = 0;
            b.jump = false;
            b.sprint = false;
            b.sneak = false;
            b.startView();
            b.apply();
            return false;
        });
    }

    public static void forget(LivingEntity entity) {
        BODIES.remove(entity.getUniqueId());
    }

    // =========================================================================
    // Keyboard and mouse
    // =========================================================================

    /** Movement keys: {@code forward} +1 = W, -1 = S; {@code right} +1 = D, -1 = A. */
    public Body keys(int forward, int right) {
        this.forward = Integer.signum(forward);
        this.right = Integer.signum(right);
        apply();
        return this;
    }

    /** Release every movement key (view and sneak unchanged). */
    public Body release() {
        forward = 0;
        right = 0;
        jump = false;
        apply();
        return this;
    }

    /** Hold the jump key this tick. Vanilla jumps if on the ground (or rises in water / on a ladder). */
    public Body jump() {
        jump = true;
        apply();
        return this;
    }

    /**
     * Whether the bot wants to be sprinting. True starts a sprint if the
     * rules allow it; false while sprinting with W held releases W for this
     * tick (a w-tap), the only way a player stops a sprint.
     */
    public Body sprint(boolean sprint) {
        this.sprint = sprint;
        apply();
        return this;
    }

    /**
     * The game ended the sprint (vanilla {@code Player.attack} does this after
     * a sprint hit). It can start again from the next tick.
     */
    public Body endSprint() {
        sprintingAtTickStart = false;
        sprintBlockedTick = Bukkit.getCurrentTick();
        call(setSprinting, false);
        apply();
        return this;
    }

    /** Hold shift this tick: slower keys, and in water it sinks ({@code LocalPlayer.aiStep}). */
    public Body sneak(boolean sneak) {
        this.sneak = sneak;
        apply();
        return this;
    }

    private void startView() {
        var at = entity.getLocation();
        lastTurn = wrap(at.getYaw() - tickYaw);
        tickYaw = at.getYaw();
        tickPitch = at.getPitch();
        View v = view;
        // Ornstein-Uhlenbeck: wanders within about the configured amplitude, without jumping tick to tick.
        noiseYaw = v.noiseYaw() <= 0 ? 0 : noiseYaw * 0.9F + (float) wander.nextGaussian() * v.noiseYaw() * 0.436F;
        noisePitch = v.noisePitch() <= 0 ? 0 : noisePitch * 0.9F + (float) wander.nextGaussian() * v.noisePitch() * 0.436F;
        remap = false;
        pitchChosen = false;
        naturalPitchKnown = false;
    }

    private static float wrap(float degrees) {
        float d = degrees % 360F;
        if (d >= 180F) d -= 360F;
        if (d < -180F) d += 360F;
        return d;
    }

    /** One tick of turning from {@code from} toward {@code goal} (see {@link View}). Pure: controllers that predict use it too. */
    public static float turnStep(float from, float goal, View v) {
        float e = wrap(goal - from), a = Math.abs(e);
        if (a <= v.minTurn()) return from + e;
        float step = Math.min(a, Math.min(v.maxTurn(), Math.max(v.minTurn(), v.gain() * a)));
        return from + Math.signum(e) * step;
    }

    /** Where the view will point this tick if turned toward {@code yaw} (what {@link #turn} returns), without turning it. */
    public float previewTurn(float yaw) {
        return turnStep(tickYaw, yaw + noiseYaw, view);
    }

    /**
     * The pitch of someone watching where they are going: at the ground
     * {@code gazeDistance} ahead (lower when it drops away, level at a wall).
     */
    private float naturalPitch(float yaw) {
        if (naturalPitchKnown) return naturalPitch;
        naturalPitchKnown = true;
        View v = view;
        var at = entity.getLocation();
        double rad = Math.toRadians(yaw), d = v.gazeDistance();
        double px = at.getX() - Math.sin(rad) * d, pz = at.getZ() + Math.cos(rad) * d;
        int bx = (int) Math.floor(px), bz = (int) Math.floor(pz), feet = (int) Math.floor(at.getY() + 0.01);
        double top = at.getY() - 3;
        var world = at.getWorld();
        if (world.isChunkLoaded(bx >> 4, bz >> 4)) {
            for (int by = feet + 1; by >= feet - 5; by--) {
                if (by < world.getMinHeight() || by >= world.getMaxHeight()) continue;
                if (!world.getBlockAt(bx, by, bz).isPassable()) {
                    top = by + 1;
                    break;
                }
            }
        }
        double eye = at.getY() + entity.getEyeHeight();
        naturalPitch = (float) Math.max(-15, Math.min(60, Math.toDegrees(Math.atan2(eye - top, d))));
        return naturalPitch;
    }

    /** This tick's pitch for a turn that doesn't choose one. */
    private float restingPitch(float yaw) {
        if (pitchChosen || !view.naturalPitch()) return entity.getLocation().getPitch();
        return turnStep(tickPitch, naturalPitch(yaw) + noisePitch, view);
    }

    private void set(float yaw, float pitch) {
        entity.setRotation(yaw, Math.max(-90F, Math.min(90F, pitch)));
    }

    /**
     * Turn the view toward {@code yaw} like a person does (see {@link View})
     * and set the pitch. Movement keys pressed this tick are relative to
     * {@code yaw}: while the head is still coming round they are swapped
     * for the keys nearest that direction (see {@link #apply}).
     */
    public Body look(float yaw, float pitch) {
        pitchChosen = true;
        set(previewTurn(yaw), pitch);
        keyYaw = yaw;
        remap = true;
        apply();
        return this;
    }

    /** Like {@link #look}, with the pitch turning as gradually as the yaw (looking at something, not steering by it). */
    public Body gaze(float yaw, float pitch) {
        pitchChosen = true;
        set(previewTurn(yaw), turnStep(tickPitch, pitch + noisePitch, view));
        keyYaw = yaw;
        remap = true;
        apply();
        return this;
    }

    /** Point the view exactly, at once: a click that has to land on a block face, a clutch. Keys are relative to it. */
    public Body aim(float yaw, float pitch) {
        pitchChosen = true;
        set(yaw, pitch);
        remap = false;
        apply();
        return this;
    }

    /** Exact yaw (one a controller already worked out with {@link #previewTurn}); the pitch is left to the body. */
    public Body aimYaw(float yaw) {
        set(yaw, restingPitch(yaw));
        remap = false;
        apply();
        return this;
    }

    /**
     * Turn toward {@code yaw} like {@link #yaw} and return where the view
     * now points. Keys pressed after this are relative to the returned yaw
     * (for controllers that choose keys by predicting from the real view).
     */
    public float turn(float yaw) {
        float actual = previewTurn(yaw);
        set(actual, restingPitch(actual));
        remap = false;
        apply();
        return actual;
    }

    /** Turn horizontally toward {@code yaw}; the pitch settles on the ground ahead unless a controller chose one this tick. */
    public Body yaw(float yaw) {
        float actual = previewTurn(yaw);
        set(actual, restingPitch(actual));
        keyYaw = yaw;
        remap = true;
        apply();
        return this;
    }

    /** Turn to face a horizontal direction {@code (dx, dz)}. */
    public Body face(double dx, double dz) {
        if (dx == 0 && dz == 0) return this;
        return yaw(yawOf(dx, dz));
    }

    public static float yawOf(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }

    /**
     * The input the entity ticked with last tick, e.g. {@code keys=WD+jump+sprint yaw=87.30 pitch=12.00}.
     * Keys are released at the start of each tick, so this is what a
     * per-tick log written early in the tick should show.
     */
    public String lastTick() { return lastTick; }

    private String lastTick = "keys=- yaw=0.00 pitch=0.00";

    private String describe() {
        StringBuilder k = new StringBuilder();
        if (heldForward > 0) k.append('W');
        if (heldForward < 0) k.append('S');
        if (heldRight < 0) k.append('A');
        if (heldRight > 0) k.append('D');
        if (k.isEmpty()) k.append('-');
        if (jump) k.append("+jump");
        if (sprinting()) k.append("+sprint");
        if (sneak) k.append("+sneak");
        var loc = entity.getLocation();
        return String.format("keys=%s yaw=%.2f pitch=%.2f", k, loc.getYaw(), loc.getPitch());
    }

    /**
     * What an entity stands in, for the tick logs: {@code slip=0.60} is the
     * slipperiness of the block that carries it (what its next tick's
     * friction and ground acceleration come from), followed by
     * {@code in=water} or {@code in=ladder} where movement isn't the plain
     * walking and jumping {@code tools/physics_check.py} checks.
     */
    public static String surface(LivingEntity e) {
        var at = e.getLocation();
        var world = at.getWorld();
        int y = (int) Math.floor(at.getY() - 0.5000001);
        org.bukkit.block.Block under = world.getBlockAt(at.getBlockX(), y, at.getBlockZ());
        if (under.isPassable()) {
            // Center over air: the block that holds the body up (Entity.getOnPos).
            double half = e.getWidth() / 2, best = Double.MAX_VALUE;
            for (int bx = (int) Math.floor(at.getX() - half); bx <= (int) Math.floor(at.getX() + half); bx++) {
                for (int bz = (int) Math.floor(at.getZ() - half); bz <= (int) Math.floor(at.getZ() + half); bz++) {
                    org.bukkit.block.Block b = world.getBlockAt(bx, y, bz);
                    if (b.isPassable()) continue;
                    double d = Math.pow(bx + 0.5 - at.getX(), 2) + Math.pow(bz + 0.5 - at.getZ(), 2);
                    if (d < best) {
                        best = d;
                        under = b;
                    }
                }
            }
        }
        String s = String.format("slip=%.2f", under.getType().isBlock() ? under.getType().getSlipperiness() : 0.6F);
        if (e.isInWater() || e.isInLava()) s += " in=water";
        else if (e.isClimbing()) s += " in=ladder";
        return s;
    }

    /** Whether the last move ran into a wall (vanilla {@code horizontalCollision}; {@code minor} = a glancing one that keeps a sprint). */
    public String collision() {
        if (!entity.isValid()) return "-";
        Object h = handle();
        if (!(boolean) horizontalCollision.get(h)) return "-";
        return minorCollision ? "minor" : "hard";
    }

    public int forward() { return forward; }
    public int right() { return right; }
    public boolean sprinting() { return (boolean) call(isSprinting); }

    /** Whether it was sprinting when this tick began (keys are released at tick start, which reads as not sprinting until W is pressed again). */
    public boolean sprintingAtTickStart() { return sprintingAtTickStart; }

    // =========================================================================
    // Physics queries (for controllers that predict; nothing here moves the body)
    // =========================================================================

    /**
     * The client's key-to-input transform. Returns {@code {left, forward}}
     * (vanilla {@code xxa}, {@code zza}) for the given keys.
     */
    public static float[] input(int forward, int right, boolean sneak) {
        float x = -Integer.signum(right), z = Integer.signum(forward);
        float len = (float) Math.sqrt(x * x + z * z);
        if (len == 0) return new float[]{0, 0};
        x /= len;
        z /= len;
        x *= KEY_SCALE;
        z *= KEY_SCALE;
        if (sneak) {
            x *= SNEAK_FACTOR;
            z *= SNEAK_FACTOR;
        }
        // modifyInputSpeedForSquareMovement
        float length = (float) Math.sqrt(x * x + z * z);
        float dx = x / length, dz = z / length;
        float ax = Math.abs(dx), az = Math.abs(dz);
        float tan = az > ax ? ax / az : az / ax;
        float modified = Math.min(length * (float) Math.sqrt(1 + tan * tan), 1.0F);
        return new float[]{dx * modified, dz * modified};
    }

    /**
     * World-space acceleration that {@code moveRelative(speed, input)} adds
     * for these keys at this yaw: {@code {ax, az}}.
     */
    public static double[] accel(int forward, int right, boolean sneak, float yaw, double speed) {
        float[] in = input(forward, right, sneak);
        double x = in[0], z = in[1];
        double lenSq = x * x + z * z;
        if (lenSq < 1.0E-7) return new double[]{0, 0};
        if (lenSq > 1.0) {
            double len = Math.sqrt(lenSq);
            x /= len;
            z /= len;
        }
        x *= speed;
        z *= speed;
        double rad = Math.toRadians(yaw);
        double sin = Math.sin(rad), cos = Math.cos(rad);
        return new double[]{x * cos - z * sin, z * cos + x * sin};
    }

    /**
     * Ground acceleration per tick from holding W ({@code getFrictionInfluencedSpeed}
     * times the client key scale), for a block of the given friction.
     */
    public static double groundAccel(double blockFriction, boolean sprinting) {
        double speed = PLAYER_MOVEMENT_SPEED * (sprinting ? SPRINT_SPEED_FACTOR : 1.0);
        double influenced = blockFriction > 0.6
                ? speed * (0.21600002 / (blockFriction * blockFriction * blockFriction))
                : speed;
        return influenced * KEY_SCALE;
    }

    /** Air acceleration per tick from holding W. */
    public static double airAccel(boolean sprinting) {
        return (sprinting ? SPRINT_AIR_SPEED : LIVING_AIR_SPEED) * KEY_SCALE;
    }

    // =========================================================================
    // Writing to the entity
    // =========================================================================

    private static final int[] OCTANT_FORWARD = {1, 1, 0, -1, -1, -1, 0, 1};
    private static final int[] OCTANT_RIGHT = {0, 1, 1, 1, 0, -1, -1, -1};

    private void apply() {
        if (!entity.isValid()) return;
        Object h = handle();

        // Sprint rules (LocalPlayer.canStartSprinting / shouldStopRunSprinting),
        // from the tick-start state and the current keys.
        boolean was = sprintingAtTickStart;
        boolean shallow = entity.isInWater() && !entity.isUnderWater();
        boolean hardCollision = (boolean) horizontalCollision.get(h) && !minorCollision;
        // Keys are WASD relative to the view. While the head is still turning
        // toward the yaw the controller asked for, hold the keys nearest the
        // direction it meant (W toward a point to the right becomes W+D, or D).
        int forward = this.forward, right = this.right;
        if (remap && (forward != 0 || right != 0)) {
            float off = wrap(keyYaw - entity.getLocation().getYaw());
            if (Math.abs(off) > 22.5F) {
                double angle = Math.toDegrees(Math.atan2(right, forward)) + off;
                int octant = Math.floorMod((int) Math.round(angle / 45.0), 8);
                forward = OCTANT_FORWARD[octant];
                right = OCTANT_RIGHT[octant];
            }
        }
        int effectiveForward = forward;
        if (was && !sprint && forward > 0) effectiveForward = 0;   // w-tap to stop
        heldForward = effectiveForward;
        heldRight = right;
        boolean sprinting = was;
        boolean blocked = sprintBlockedTick == Bukkit.getCurrentTick();
        // LocalPlayer.hasEnoughFoodToStartSprinting: a hungry player can't start.
        boolean fed = !(entity instanceof org.bukkit.entity.Player p) || p.getFoodLevel() > 6 || p.getAllowFlight();
        if (!was && sprint && effectiveForward > 0 && !sneak && !shallow && !blocked && fed) sprinting = true;
        else if (was && entity.isSwimming()) {
            // shouldStopSwimSprinting: a swimmer keeps the sprint at the
            // surface; it ends out of the water or letting go of W mid-water.
            if (!entity.isInWater() || (effectiveForward <= 0 && !entity.isOnGround() && !sneak)) sprinting = false;
        } else if (was && (effectiveForward <= 0 || shallow || hardCollision)) sprinting = false;
        if (sprinting != (boolean) call(isSprinting)) call(setSprinting, sprinting);

        if (entity.isSneaking() != sneak) entity.setSneaking(sneak);
        float[] in = input(effectiveForward, right, sneak);
        xxa.set(h, in[0]);
        zza.set(h, in[1]);
        call(setJumping, jump);

        AttributeInstance speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) call(setSpeed, (float) speed.getValue());

        if (!player) applyAirAssist(effectiveForward, sprinting);
        applySwimming();
    }

    /** {@code LivingEntity.goDownInWater}, applied by the client while shift is held in water. */
    private static final double GO_DOWN_IN_WATER = -0.04;

    /** Vertical velocity added this tick by the swim pitch steering, so re-applying can replace it. */
    private double swimAssist = 0;
    private int swimAssistTick = -1;

    /**
     * {@code Player.travel} while swimming (sprinting in water): vertical
     * speed is pulled toward the view's vertical component, so a swimmer
     * dives by looking down and surfaces by looking up. And
     * {@code Player.updatePlayerPose}: a swimmer is in the 0.6-tall
     * swimming pose, and stays in it (crawling) where there's no room to
     * stand. A Mannequin gets the swimming flag from vanilla but neither of
     * these.
     */
    private void applySwimming() {
        int tick = Bukkit.getCurrentTick();
        Vector v = entity.getVelocity();
        boolean replace = swimAssistTick == tick && swimAssist != 0;
        if (replace) v.setY(v.getY() - swimAssist);
        double assist = 0;
        if (!player && entity.isSwimming() && entity.getVehicle() == null) {
            double lookY = -Math.sin(Math.toRadians(entity.getLocation().getPitch()));
            double multiplier = lookY < -0.2 ? 0.085 : 0.06;
            var loc = entity.getLocation();
            boolean waterAboveHead = loc.getWorld().getBlockAt(
                    loc.getBlockX(), (int) Math.floor(loc.getY() + 1.0 - 0.1), loc.getBlockZ()).isLiquid();
            if (lookY <= 0 || jump || waterAboveHead) assist = (lookY - v.getY()) * multiplier;
        }
        // LocalPlayer.aiStep: holding shift in water swims down.
        if (sneak && entity.isInWater()) assist += GO_DOWN_IN_WATER;
        if (replace || assist != 0) entity.setVelocity(v.setY(v.getY() + assist));
        swimAssist = assist;
        swimAssistTick = tick;
        if (!player) updatePose();
    }

    private static final double MINOR_COLLISION_ANGLE = 0.13962634f;

    private void updateMinorCollision() {
        org.bukkit.Location loc = entity.getLocation();
        double mx = loc.getX() - lastX, mz = loc.getZ() - lastZ;
        lastX = loc.getX();
        lastZ = loc.getZ();
        Object h = handle();
        double f = Math.toRadians(loc.getYaw());
        double sin = Math.sin(f), cos = Math.cos(f);
        double x = (float) xxa.get(h), z = (float) zza.get(h);
        double g = x * cos - z * sin, k = z * cos + x * sin;
        double i = g * g + k * k, j = mx * mx + mz * mz;
        minorCollision = !Double.isNaN(mx) && i >= 1.0E-5f && j >= 1.0E-5f
                && Math.acos(Math.max(-1, Math.min(1, (g * mx + k * mz) / Math.sqrt(i * j)))) < MINOR_COLLISION_ANGLE;
    }

    private void updatePose() {
        org.bukkit.entity.Pose pose = entity.getPose();
        if (entity.isSwimming()) {
            if (pose != org.bukkit.entity.Pose.SWIMMING) entity.setPose(org.bukkit.entity.Pose.SWIMMING, true);
        } else if (pose == org.bukkit.entity.Pose.SWIMMING) {
            // Stand up only where a standing body fits (else keep crawling).
            var loc = entity.getLocation();
            boolean room = !loc.getWorld().getBlockAt(loc.getBlockX(), (int) Math.floor(loc.getY() + 1.2), loc.getBlockZ())
                    .getType().isSolid();
            if (room) entity.setPose(org.bukkit.entity.Pose.STANDING, false);
        }
    }

    /** Player.getFlyingSpeed gap: 0.026 - 0.02 of extra air control while sprinting. */
    private void applyAirAssist(int effectiveForward, boolean sprinting) {
        int tick = Bukkit.getCurrentTick();
        Vector v = entity.getVelocity();
        if (airAssistTick == tick) v.subtract(airAssist);
        Vector assist = new Vector();
        if (sprinting && !entity.isOnGround() && !entity.isInWater() && !entity.isInLava()) {
            double[] a = accel(effectiveForward, right, sneak, entity.getLocation().getYaw(),
                    SPRINT_AIR_SPEED - LIVING_AIR_SPEED);
            assist = new Vector(a[0], 0, a[1]);
        }
        if (airAssistTick == tick || assist.lengthSquared() > 0) {
            entity.setVelocity(v.add(assist));
        }
        airAssist = assist;
        airAssistTick = tick;
    }

    private Object handle() {
        try {
            return getHandle.invoke(entity);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }

    private Object call(MethodHandle m, Object... args) {
        try {
            Object[] full = new Object[args.length + 1];
            full[0] = handle();
            System.arraycopy(args, 0, full, 1, args.length);
            return m.invokeWithArguments(full);
        } catch (Throwable t) {
            throw new IllegalStateException(t);
        }
    }
}
