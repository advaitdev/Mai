package me.advait.mai.gui.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/**
 * A menu made of {@link InventoryButton}s. A subclass creates the inventory
 * and adds its buttons in {@link #decorate}; open it with
 * {@link GUIManager#openGUI}. Every click in the view is cancelled, so
 * nothing can be taken out or put in.
 */
public abstract class InventoryGUI implements InventoryHandler {

    private Inventory inventory;
    private final Map<Integer, InventoryButton> buttons = new HashMap<>();

    /** Created on first use, so a subclass's fields are set before {@link #createInventory} runs. */
    public Inventory getInventory() {
        if (inventory == null) inventory = createInventory();
        return inventory;
    }

    protected abstract Inventory createInventory();

    public void addButton(int slot, InventoryButton button) {
        buttons.put(slot, button);
    }

    /** Renders every button for this viewer. Override to add the buttons, then call super. */
    public void decorate(Player player) {
        Inventory inv = getInventory();
        inv.clear();
        buttons.forEach((slot, button) -> inv.setItem(slot, button.getIconCreator().apply(player)));
    }

    /** Drops every button and renders again (a page change, or the data behind the menu changed). */
    public void redecorate(Player player) {
        buttons.clear();
        decorate(player);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);
        // Raw slots: a click in the player's own inventory below has the same getSlot() numbers.
        if (event.getRawSlot() < 0 || event.getRawSlot() >= getInventory().getSize()) return;
        InventoryButton button = buttons.get(event.getRawSlot());
        if (button != null) button.getEventConsumer().accept(event);
    }

    @Override
    public void onOpen(InventoryOpenEvent event) {
        decorate((Player) event.getPlayer());
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
    }
}
