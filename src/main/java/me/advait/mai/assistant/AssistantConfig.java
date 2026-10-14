package me.advait.mai.assistant;

import me.advait.mai.Mai;
import org.bukkit.configuration.ConfigurationSection;

/**
 * Live view of {@code settings.yml -> assistant:}. Read on every use, so
 * {@code /mai reload} and {@code /mai tune} edits apply immediately.
 */
public final class AssistantConfig {

    private AssistantConfig() {}

    private static ConfigurationSection section() {
        ConfigurationSection s = Mai.getInstance().getSettingsFile().getConfiguration()
                .getConfigurationSection("assistant");
        return s;
    }

    private static double d(String key, double def) {
        ConfigurationSection s = section();
        return s == null ? def : s.getDouble(key, def);
    }

    private static boolean b(String key, boolean def) {
        ConfigurationSection s = section();
        return s == null ? def : s.getBoolean(key, def);
    }

    /** How close a follower stays (blocks). */
    public static double followDistance() { return d("follow.distance", 3.0); }

    /** Beyond this a follower teleports to its leader like a tamed wolf; 0 = never. */
    public static double followTeleportDistance() { return d("follow.teleport_distance", 48.0); }

    /** Attack reach measured from the eyes to the target's hitbox (vanilla 3.0). */
    public static double reach() { return d("combat.reach", 3.0); }

    /** Distance the fighter tries to hold from its target. */
    public static double preferredRange() { return d("combat.preferred_range", 2.6); }

    /** Beyond this the fighter pathfinds to the target instead of stepping directly. */
    public static double chaseDirectRange() { return d("combat.direct_range", 6.0); }

    public static boolean crits() { return b("combat.crits", true); }

    /**
     * Also hop on the approach, timed to fall into reach, and hold ground
     * against a charging target. Off by default: against a fighter that
     * rushes in it lost every duel (airborne you are hit first and pushed
     * back further). Crits while recharging in reach stay on via {@link #crits()}.
     */
    public static boolean approachCrits() { return b("combat.approach_crits", false); }

    public static boolean strafe() { return b("combat.strafe", false); }

    /** Back out of the target's reach while recharging, re-engage when charged. */
    public static boolean spaceOut() { return b("combat.space_out", false); }

    /** Release sprint after a sprint hit so the next hit gets sprint knockback again. */
    public static boolean wTap() { return b("combat.w_tap", true); }

    /** Fraction of a full attack charge needed before swinging (vanilla full = 1.0). */
    public static double minCharge() { return d("combat.min_charge", 0.9); }

    /** Guard: engage hostiles within this many blocks of the protected player. */
    public static double guardRadius() { return d("guard.radius", 12.0); }

    private static String s(String key, String def) {
        ConfigurationSection s = section();
        String v = s == null ? def : s.getString(key, def);
        return v == null ? def : v.trim();
    }

    /** Send chat to Claude instead of the keyword rules (needs an API key). */
    public static boolean llmEnabled() { return b("llm.enabled", false); }

    /** API key; empty means use the ANTHROPIC_API_KEY environment variable. */
    public static String llmApiKey() { return s("llm.api_key", ""); }

    public static String llmModel() { return s("llm.model", "claude-opus-5-5"); }

    /** low, medium or high. */
    public static String llmEffort() { return s("llm.effort", "low").toLowerCase(java.util.Locale.ROOT); }

    /** Model turns per chat message (each may call tools). */
    public static int llmMaxSteps() { return (int) d("llm.max_steps", 6); }

    /** How long to wait for a one-shot job before telling the model it's still running. */
    public static int llmToolWaitSeconds() { return (int) d("llm.tool_wait_seconds", 20); }
}
