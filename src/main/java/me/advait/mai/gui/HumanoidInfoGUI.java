package me.advait.mai.gui;

import me.advait.mai.body.Humanoid;
import me.advait.mai.file.serialization.LocationSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * GUI showing detailed information and actions for a specific humanoid.
 */
public class HumanoidInfoGUI implements InventoryHolder {

    public static final String TITLE_PREFIX = "Humanoid: ";
    private static final int SIZE = 27; // 3 rows

    // Slot positions
    public static final int SLOT_HEAD = 4;           // Top row center - humanoid info
    public static final int SLOT_TELEPORT = 11;      // Middle row - teleport button
    public static final int SLOT_INVENTORY = 12;     // Middle row - view inventory
    public static final int SLOT_RENAME = 13;        // Middle row - rename
    public static final int SLOT_DELETE = 14;        // Middle row - delete
    public static final int SLOT_BACK = 22;          // Bottom row center - back button

    private final Player player;
    private final Humanoid humanoid;
    private final Inventory inventory;

    public HumanoidInfoGUI(Player player, Humanoid humanoid) {
        this.player = player;
        this.humanoid = humanoid;
        this.inventory = Bukkit.createInventory(this, SIZE, TITLE_PREFIX + humanoid.getName());
        populate();
    }

    private void populate() {
        // Fill with glass panes
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < SIZE; i++) {
            inventory.setItem(i, filler);
        }

        // Humanoid head with info
        inventory.setItem(SLOT_HEAD, createHumanoidHead());

        // Action buttons
        inventory.setItem(SLOT_TELEPORT, createTeleportButton());
        inventory.setItem(SLOT_INVENTORY, createInventoryButton());
        inventory.setItem(SLOT_RENAME, createRenameButton());
        inventory.setItem(SLOT_DELETE, createDeleteButton());

        // Back button
        inventory.setItem(SLOT_BACK, createBackButton());
    }

    private ItemStack createHumanoidHead() {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(colorize("&6" + humanoid.getName()));

            List<String> lore = new ArrayList<>();
            lore.add(colorize("&7UUID: &f" + humanoid.getUuid().toString()));
            lore.add("");

            if (humanoid.getMannequin() != null) {
                lore.add(colorize("&7Location: &f" + LocationSerializer.toReadableString(humanoid.getMannequin().getLocation())));
                lore.add(colorize("&7Health: &f" + String.format("%.1f", humanoid.getMannequin().getHealth())));
                lore.add(colorize("&7Status: &aSpawned"));
            } else {
                lore.add(colorize("&7Status: &cNot spawned"));
            }

            // Inventory info
            int itemCount = 0;
            for (ItemStack invItem : humanoid.getInventory().getContents()) {
                if (invItem != null && invItem.getType() != Material.AIR) {
                    itemCount++;
                }
            }
            lore.add("");
            lore.add(colorize("&7Inventory items: &f" + itemCount + "/36"));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createTeleportButton() {
        List<String> lore = new ArrayList<>();
        lore.add(colorize("&7Click to teleport to"));
        lore.add(colorize("&7this Humanoid's location."));

        if (humanoid.getMannequin() == null) {
            lore.add("");
            lore.add(colorize("&cHumanoid not spawned!"));
        }

        return createItem(Material.ENDER_PEARL, "&aTeleport", lore);
    }

    private ItemStack createInventoryButton() {
        List<String> lore = new ArrayList<>();
        lore.add(colorize("&7Click to view and edit"));
        lore.add(colorize("&7this Humanoid's inventory."));

        return createItem(Material.CHEST, "&eView Inventory", lore);
    }

    private ItemStack createRenameButton() {
        List<String> lore = new ArrayList<>();
        lore.add(colorize("&7Click to rename this Humanoid."));
        lore.add(colorize("&7Type the new name in chat."));

        return createItem(Material.NAME_TAG, "&bRename", lore);
    }

    private ItemStack createDeleteButton() {
        List<String> lore = new ArrayList<>();
        lore.add(colorize("&7Click to delete this Humanoid."));
        lore.add("");
        lore.add(colorize("&c&lThis action cannot be undone!"));

        return createItem(Material.BARRIER, "&cDelete", lore);
    }

    private ItemStack createBackButton() {
        List<String> lore = new ArrayList<>();
        lore.add(colorize("&7Return to Humanoid list."));

        return createItem(Material.ARROW, "&7Back", lore);
    }

    private ItemStack createItem(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(colorize(name));
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }

        return item;
    }

    public void open() {
        player.openInventory(inventory);
    }

    public Humanoid getHumanoid() {
        return humanoid;
    }

    public UUID getHumanoidUuid() {
        return humanoid.getUuid();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static String colorize(String text) {
        return text.replace("&", "\u00A7");
    }
}
