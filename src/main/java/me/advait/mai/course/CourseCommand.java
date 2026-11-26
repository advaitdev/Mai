package me.advait.mai.course;

import co.aikar.commands.BaseCommand;
import co.aikar.commands.annotation.CommandAlias;
import co.aikar.commands.annotation.CommandCompletion;
import co.aikar.commands.annotation.CommandPermission;
import co.aikar.commands.annotation.Description;
import co.aikar.commands.annotation.Optional;
import co.aikar.commands.annotation.Subcommand;
import co.aikar.commands.annotation.Syntax;
import me.advait.mai.util.Messages;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** {@code /mai course ...}: real-terrain courses for human baselines and bot runs. */
@CommandAlias("mai")
public class CourseCommand extends BaseCommand {

    private static final CourseManager C = CourseManager.getInstance();

    private static Player player(CommandSender sender) {
        if (sender instanceof Player p) return p;
        Messages.sendMessage(sender, "&cThis one is in-game only.");
        return null;
    }

    @Subcommand("course play")
    @CommandPermission("mai.obstacle.run")
    @CommandCompletion("@courses")
    @Syntax("<course>")
    @Description("Run a course yourself; every finished run is timed and logged")
    public void onPlay(CommandSender sender, String course) {
        Player p = player(sender);
        if (p != null) C.play(p, course);
    }

    @Subcommand("course retry")
    @CommandPermission("mai.obstacle.run")
    @Description("Back to the start (same as the swap-hands key)")
    public void onRetry(CommandSender sender) {
        Player p = player(sender);
        if (p != null) C.retry(p);
    }

    @Subcommand("course quit")
    @CommandPermission("mai.obstacle.run")
    @Description("Leave the course and go back where you were")
    public void onQuit(CommandSender sender) {
        Player p = player(sender);
        if (p != null) C.quit(p);
    }

    @Subcommand("course discard")
    @CommandPermission("mai.obstacle.run")
    @Description("Don't count your last run")
    public void onDiscard(CommandSender sender) {
        Player p = player(sender);
        if (p != null) C.discard(p);
    }

    @Subcommand("course list")
    @CommandPermission("mai.obstacle.run")
    @Description("List courses with the best human and bot times")
    public void onList(CommandSender sender) {
        C.list(sender);
    }

    @Subcommand("course stats")
    @CommandPermission("mai.obstacle.run")
    @CommandCompletion("@courses")
    @Syntax("[course]")
    @Description("Human vs bot times")
    public void onStats(CommandSender sender, @Optional String course) {
        C.stats(sender, course);
    }

    @Subcommand("course bot")
    @CommandPermission("mai.obstacle.run")
    @CommandCompletion("@courses @nothing")
    @Syntax("<course> [times]")
    @Description("A bot runs the course (in-game, you watch as a spectator)")
    public void onBot(CommandSender sender, String course, @Optional Integer times) {
        C.bot(sender, course, times == null ? 1 : times);
    }

    @Subcommand("course botall")
    @CommandPermission("mai.obstacle.run")
    @Syntax("[times]")
    @Description("A bot runs every course")
    public void onBotAll(CommandSender sender, @Optional Integer times) {
        C.botAll(sender, times == null ? 1 : times);
    }

    @Subcommand("course hunt")
    @CommandPermission("mai.obstacle.run")
    @CommandCompletion("@courses @nothing")
    @Syntax("<course> [lead]")
    @Description("A bot hunts a replay of the fastest human run, starting [lead] ticks behind it")
    public void onHunt(CommandSender sender, String course, @Optional Integer lead) {
        C.hunt(sender, course, lead == null ? 40 : lead);
    }

    @Subcommand("course huntall")
    @CommandPermission("mai.obstacle.run")
    @Syntax("[lead]")
    @Description("A bot hunts the fastest human run of every course")
    public void onHuntAll(CommandSender sender, @Optional Integer lead) {
        C.huntAll(sender, lead == null ? 40 : lead);
    }

    @Subcommand("course cancel")
    @CommandPermission("mai.obstacle.run")
    @Description("Stop bot runs after the current one")
    public void onCancel(CommandSender sender) {
        C.cancel(sender);
    }

    @Subcommand("course new")
    @CommandPermission("mai.obstacle.run")
    @Syntax("<name>")
    @Description("Start a course where you stand, facing where you look")
    public void onNew(CommandSender sender, String name) {
        Player p = player(sender);
        if (p != null) C.newDraft(p, name);
    }

    @Subcommand("course end")
    @CommandPermission("mai.obstacle.run")
    @Syntax("[margin]")
    @Description("Finish the course on the block you stand on and save it")
    public void onEnd(CommandSender sender, @Optional Integer margin) {
        Player p = player(sender);
        if (p != null) C.endDraft(p, margin == null ? 12 : Math.max(2, Math.min(margin, 32)));
    }

    @Subcommand("course generate")
    @CommandPermission("mai.obstacle.run")
    @Syntax("<name> <biome> [cross|climb|descend|water] [length]")
    @Description("Cut a course out of generated terrain")
    public void onGenerate(CommandSender sender, String name, String biome, @Optional String mode, @Optional Integer length) {
        C.generate(sender, name, biome, mode, length == null ? 48 : Math.max(16, Math.min(length, 96)));
    }

    @Subcommand("course delete")
    @CommandPermission("mai.obstacle.run")
    @CommandCompletion("@courses")
    @Syntax("<course>")
    public void onDelete(CommandSender sender, String course) {
        C.delete(sender, course);
    }

    @Subcommand("course reload")
    @CommandPermission("mai.obstacle.run")
    public void onReload(CommandSender sender) {
        Messages.sendMessage(sender, "&aLoaded &e" + C.reload() + "&a course(s).");
    }

    @Subcommand("course")
    @CommandPermission("mai.obstacle.run")
    @Description("Open the course menu")
    public void onMenu(CommandSender sender) {
        if (sender instanceof Player) C.list(sender);
        else onHelp(sender);
    }

    @Subcommand("course help")
    @CommandPermission("mai.obstacle.run")
    public void onHelp(CommandSender sender) {
        Messages.header(sender, "Courses");
        Messages.help(sender, "/mai course", "The course menu: times, play, watch the bot");
        Messages.help(sender, "/mai course play <course>", "Run it yourself, timed");
        Messages.help(sender, "/mai course retry", "Back to the start (or press F)");
        Messages.help(sender, "/mai course discard", "Don't count your last run");
        Messages.help(sender, "/mai course quit", "Leave the course (or sneak + F)");
        Messages.help(sender, "/mai course bot <course> [times]", "Watch the bot run it");
        Messages.help(sender, "/mai course botall [times]", "The bot runs every course");
        Messages.help(sender, "/mai course stats [course]", "Human vs bot times");
        Messages.help(sender, "/mai course new <name>", "Make one: start where you stand");
        Messages.help(sender, "/mai course end [margin]", "...and finish where you stand");
        Messages.help(sender, "/mai course generate <name> <biome> [mode] [length]", "Cut one from generated terrain");
    }
}
