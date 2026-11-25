package me.advait.mai.gui.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;

/** Which handler owns which open inventory. Menus are registered on open and forgotten on close. */
public final class GUIManager {

    private static final GUIManager INSTANCE = new GUIManager();

    public static GUIManager getInstance() { return INSTANCE; }

    private final Map<Inventory, InventoryHandler> active = new HashMap<>();

    private GUIManager() {}

    public void openGUI(InventoryGUI gui, Player player) {
        active.put(gui.getInventory(), gui);
        player.openInventory(gui.getInventory());
    }

    void handleClick(InventoryClickEvent event) {
        InventoryHandler handler = active.get(event.getInventory());
        if (handler != null) handler.onClick(event);
    }

    void handleDrag(InventoryDragEvent event) {
        if (active.containsKey(event.getInventory())) event.setCancelled(true);
    }

    void handleOpen(InventoryOpenEvent event) {
        InventoryHandler handler = active.get(event.getInventory());
        if (handler != null) handler.onOpen(event);
    }

    void handleClose(InventoryCloseEvent event) {
        Inventory inventory = event.getInventory();
        InventoryHandler handler = active.get(inventory);
        if (handler == null) return;
        handler.onClose(event);
        // Another viewer may still have it open.
        if (inventory.getViewers().size() <= 1) active.remove(inventory);
    }

    /** Closes every open menu (plugin disable: a menu left open would become a plain chest). */
    public void closeAll() {
        for (Inventory inventory : new java.util.ArrayList<>(active.keySet())) {
            for (var viewer : new java.util.ArrayList<>(inventory.getViewers())) viewer.closeInventory();
        }
        active.clear();
    }
}
