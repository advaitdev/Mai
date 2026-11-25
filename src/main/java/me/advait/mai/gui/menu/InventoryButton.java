package me.advait.mai.gui.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.function.Consumer;
import java.util.function.Function;

/** One slot of an {@link InventoryGUI}: how its icon is made for the viewer, and what a click does. */
public final class InventoryButton {

    private Function<Player, ItemStack> iconCreator = p -> null;
    private Consumer<InventoryClickEvent> eventConsumer = e -> {};

    public InventoryButton creator(Function<Player, ItemStack> iconCreator) {
        this.iconCreator = iconCreator;
        return this;
    }

    public InventoryButton consumer(Consumer<InventoryClickEvent> eventConsumer) {
        this.eventConsumer = eventConsumer;
        return this;
    }

    public Function<Player, ItemStack> getIconCreator() { return iconCreator; }

    public Consumer<InventoryClickEvent> getEventConsumer() { return eventConsumer; }
}
