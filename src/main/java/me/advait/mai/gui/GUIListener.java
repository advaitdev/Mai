package me.advait.mai.gui;

import me.advait.mai.Catalog;
import me.advait.mai.Mai;
import me.advait.mai.body.Humanoid;
import me.advait.mai.util.Messages;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Handles all GUI interactions for humanoid management.
 */
public class GUIListener implements Listener {

    // Players waiting to input a new name for a humanoid
    private final Map<UUID, UUID> pendingRenames = new HashMap<>();

    // Players waiting to confirm deletion
    private final Map<UUID, UUID> pendingDeletes = new HashMap<>();

    // Players currently viewing a humanoid's inventory (for saving on close)
    private final Map<UUID, UUID> viewingInventory = new HashMap<>();

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        Inventory inventory = event.getInventory();
        InventoryHolder holder = inventory.getHolder();

        // Handle HumanoidListGUI
        if (holder instanceof HumanoidListGUI listGUI) {
            event.setCancelled(true);
            handleListGUIClick(player, listGUI, event.getRawSlot());
            return;
        }

        // Handle HumanoidInfoGUI
        if (holder instanceof HumanoidInfoGUI infoGUI) {
            event.setCancelled(true);
            handleInfoGUIClick(player, infoGUI, event.getRawSlot());
            return;
        }

        // Handle viewing humanoid inventory
        String title = event.getView().getTitle();
        if (title.endsWith("'s Inventory")) {
            // Allow item movement in humanoid inventories
            return;
        }
    }

    private void handleListGUIClick(Player player, HumanoidListGUI gui, int slot) {
        // Navigation row is at slots 45-53
        if (slot == 45) {
            // Previous page
            gui.previousPage();
            return;
        }

        if (slot == 53) {
            // Next page
            gui.nextPage();
            return;
        }

        if (slot == 52) {
            // Close button
            player.closeInventory();
            return;
        }

        // Clicked on a humanoid
        if (slot >= 0 && slot < 45) {
            Humanoid humanoid = gui.getHumanoidAtSlot(slot);
            if (humanoid != null) {
                player.closeInventory();
                new HumanoidInfoGUI(player, humanoid).open();
            }
        }
    }

    private void handleInfoGUIClick(Player player, HumanoidInfoGUI gui, int slot) {
        Humanoid humanoid = gui.getHumanoid();

        switch (slot) {
            case HumanoidInfoGUI.SLOT_TELEPORT -> {
                if (humanoid.getMannequin() == null) {
                    Messages.sendMessage(player, "&cThis humanoid is not currently spawned!");
                    return;
                }
                player.closeInventory();
                player.teleport(humanoid.getMannequin().getLocation());
                Messages.sendMessage(player, "&aTeleported to humanoid '&e" + humanoid.getName() + "&a'");
            }

            case HumanoidInfoGUI.SLOT_INVENTORY -> {
                player.closeInventory();
                viewingInventory.put(player.getUniqueId(), humanoid.getUuid());
                player.openInventory(humanoid.getInventory());
            }

            case HumanoidInfoGUI.SLOT_RENAME -> {
                player.closeInventory();
                pendingRenames.put(player.getUniqueId(), humanoid.getUuid());
                Messages.sendMessage(player, "&eType the new name in chat, or type '&ccancel&e' to cancel.");
            }

            case HumanoidInfoGUI.SLOT_DELETE -> {
                if (pendingDeletes.containsKey(player.getUniqueId()) &&
                        pendingDeletes.get(player.getUniqueId()).equals(humanoid.getUuid())) {
                    // Confirmed deletion
                    player.closeInventory();
                    String name = humanoid.getName();
                    Catalog.getInstance().unregister(humanoid);
                    pendingDeletes.remove(player.getUniqueId());
                    Messages.sendMessage(player, "&aDeleted humanoid '&e" + name + "&a'");
                } else {
                    // First click - ask for confirmation
                    pendingDeletes.put(player.getUniqueId(), humanoid.getUuid());
                    Messages.sendMessage(player, "&c&lClick delete again to confirm deletion of '&e" + humanoid.getName() + "&c&l'");
                }
            }

            case HumanoidInfoGUI.SLOT_BACK -> {
                player.closeInventory();
                pendingDeletes.remove(player.getUniqueId());
                new HumanoidListGUI(player).open();
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        UUID playerUuid = player.getUniqueId();

        // Clear pending delete confirmation when closing
        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof HumanoidInfoGUI) {
            pendingDeletes.remove(playerUuid);
        }

        // Save humanoid inventory when player closes it
        if (viewingInventory.containsKey(playerUuid)) {
            UUID humanoidUuid = viewingInventory.remove(playerUuid);
            Humanoid humanoid = Catalog.getInstance().getByUuid(humanoidUuid);
            if (humanoid != null) {
                Catalog.getInstance().save(humanoid);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Handle rename input
        if (pendingRenames.containsKey(playerUuid)) {
            event.setCancelled(true);
            String input = event.getMessage().trim();

            UUID humanoidUuid = pendingRenames.remove(playerUuid);

            if (input.equalsIgnoreCase("cancel")) {
                // Run on main thread
                Mai.getInstance().getServer().getScheduler().runTask(Mai.getInstance(), () -> {
                    Messages.sendMessage(player, "&7Rename cancelled.");
                });
                return;
            }

            // Run on main thread
            Mai.getInstance().getServer().getScheduler().runTask(Mai.getInstance(), () -> {
                Humanoid humanoid = Catalog.getInstance().getByUuid(humanoidUuid);
                if (humanoid == null) {
                    Messages.sendMessage(player, "&cThat humanoid no longer exists!");
                    return;
                }

                if (Catalog.getInstance().nameExists(input) && !humanoid.getName().equalsIgnoreCase(input)) {
                    Messages.sendMessage(player, "&cA humanoid with that name already exists!");
                    return;
                }

                String oldName = humanoid.getName();
                humanoid.setName(input);
                Catalog.getInstance().save(humanoid);
                Messages.sendMessage(player, "&aRenamed humanoid from '&e" + oldName + "&a' to '&e" + input + "&a'");
            });
        }
    }

    /**
     * Cancels any pending actions for a player (useful for cleanup).
     */
    public void cancelPendingActions(Player player) {
        UUID uuid = player.getUniqueId();
        pendingRenames.remove(uuid);
        pendingDeletes.remove(uuid);
        viewingInventory.remove(uuid);
    }
}
