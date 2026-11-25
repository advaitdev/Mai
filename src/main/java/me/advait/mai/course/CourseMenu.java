package me.advait.mai.course;

import me.advait.mai.gui.menu.InventoryButton;
import me.advait.mai.gui.menu.InventoryGUI;
import me.advait.mai.util.Messages;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The course picker ({@code /mai course}): one item per course with its
 * times in the lore. Left click plays it, right click watches the bot run it.
 */
final class CourseMenu extends InventoryGUI {

    private static final int PER_PAGE = 45;

    private final CourseManager courses = CourseManager.getInstance();
    private final List<CourseManager.Info> infos;
    private int page;

    CourseMenu(Player viewer) {
        this.infos = courses.infos(viewer);
    }

    @Override
    protected Inventory createInventory() {
        int rows = Math.min(5, Math.max(1, (infos.size() + 8) / 9));
        return Bukkit.createInventory(null, (rows + 1) * 9, Component.text("Courses"));
    }

    @Override
    public void decorate(Player player) {
        int bar = getInventory().getSize() - 9;
        int pages = Math.max(1, (infos.size() + PER_PAGE - 1) / PER_PAGE);
        for (int i = page * PER_PAGE, slot = 0; i < infos.size() && slot < bar; i++, slot++) {
            CourseManager.Info info = infos.get(i);
            String name = info.course().name();
            addButton(slot, new InventoryButton()
                    .creator(p -> courseItem(info))
                    .consumer(e -> {
                        Player p = (Player) e.getWhoClicked();
                        p.closeInventory();
                        if (e.isRightClick()) courses.bot(p, name, 1);
                        else courses.play(p, name);
                    }));
        }
        if (page > 0) {
            addButton(bar, new InventoryButton()
                    .creator(p -> item(Material.ARROW, "Previous page", List.of()))
                    .consumer(e -> turn((Player) e.getWhoClicked(), -1)));
        }
        if (page < pages - 1) {
            addButton(bar + 8, new InventoryButton()
                    .creator(p -> item(Material.ARROW, "Next page", List.of()))
                    .consumer(e -> turn((Player) e.getWhoClicked(), 1)));
        }
        addButton(bar + 4, new InventoryButton().creator(p -> summaryItem()));
        if (courses.playing(player)) {
            addButton(bar + 6, new InventoryButton()
                    .creator(p -> item(Material.BARRIER, "Leave the course",
                            List.of(line("Back to where you were. Sneak + F does the same."))))
                    .consumer(e -> {
                        Player p = (Player) e.getWhoClicked();
                        p.closeInventory();
                        courses.quit(p);
                    }));
        }
        super.decorate(player);
    }

    private void turn(Player player, int by) {
        page += by;
        redecorate(player);
    }

    private ItemStack courseItem(CourseManager.Info info) {
        Course c = info.course();
        List<Component> lore = new ArrayList<>();
        lore.add(line(c.description()));
        double climb = c.endY() - c.startY();
        lore.add(field("Length", String.format("%.0f blocks", c.distance())
                + (Math.abs(climb) >= 3 ? String.format(climb > 0 ? ", %.0f up" : ", %.0f down", Math.abs(climb)) : "")));
        lore.add(Component.empty());
        lore.add(field("Your best", CourseManager.time(info.yourBest())
                + (info.yourRuns() > 0 ? " of " + info.yourRuns() + " run" + (info.yourRuns() == 1 ? "" : "s") : "")));
        if (info.humanBest() > 0 && info.humanBest() != info.yourBest()) {
            lore.add(field("Human best", CourseManager.time(info.humanBest()) + " by " + info.humanBestBy()));
        }
        lore.add(field("Bot best", CourseManager.time(info.botBest())
                + (info.botRuns() > 0 ? " of " + info.botRuns() + " run" + (info.botRuns() == 1 ? "" : "s") : "")));
        if (info.botBest() > 0 && info.humanBest() > 0) {
            int diff = info.humanBest() - info.botBest();
            lore.add(Component.text(diff > 0 ? "The bot leads by " + CourseManager.time(diff)
                            : diff < 0 ? "Humans lead by " + CourseManager.time(-diff) : "Tied",
                    diff > 0 ? Messages.ERROR : Messages.SUCCESS).decoration(TextDecoration.ITALIC, false));
            lore.add(line("The bot's best beats " + info.beats() + " of " + info.humanRuns() + " human run"
                    + (info.humanRuns() == 1 ? "" : "s")));
        }
        lore.add(Component.empty());
        lore.add(hint("Left click", "play"));
        lore.add(hint("Right click", "watch the bot"));
        return item(info.icon(), c.name(), lore);
    }

    private ItemStack summaryItem() {
        int both = 0, botAhead = 0, played = 0;
        for (CourseManager.Info i : infos) {
            if (i.humanBest() > 0) played++;
            if (i.humanBest() > 0 && i.botBest() > 0) {
                both++;
                if (i.botBest() < i.humanBest()) botAhead++;
            }
        }
        return item(Material.BOOK, "Human vs bot", List.of(
                field("Courses", Integer.toString(infos.size())),
                field("With a human time", Integer.toString(played)),
                field("Bot ahead on", botAhead + " of " + both),
                Component.empty(),
                line("In a course: F restarts, sneak + F leaves.")));
    }

    private static ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack stack = new ItemStack(material);
        stack.editMeta(meta -> {
            meta.displayName(Component.text(name, Messages.HIGHLIGHT).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return stack;
    }

    private static Component line(String text) {
        return Component.text(text, Messages.TEXT).decoration(TextDecoration.ITALIC, false);
    }

    private static Component field(String label, String value) {
        return colored(label + ": ", Messages.HEADER).append(colored(value, Messages.TEXT));
    }

    private static Component hint(String click, String what) {
        return colored(click, Messages.INFO).append(colored(" to " + what, Messages.TEXT));
    }

    private static Component colored(String text, TextColor color) {
        return Component.text(text, color).decoration(TextDecoration.ITALIC, false);
    }
}
