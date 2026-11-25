package me.advait.mai.gui.menu;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

/** Forwards inventory events to {@link GUIManager}. */
public final class MenuListener implements Listener {

    private final GUIManager manager = GUIManager.getInstance();

    @EventHandler
    public void onClick(InventoryClickEvent event) { manager.handleClick(event); }

    @EventHandler
    public void onDrag(InventoryDragEvent event) { manager.handleDrag(event); }

    @EventHandler
    public void onOpen(InventoryOpenEvent event) { manager.handleOpen(event); }

    @EventHandler
    public void onClose(InventoryCloseEvent event) { manager.handleClose(event); }
}
