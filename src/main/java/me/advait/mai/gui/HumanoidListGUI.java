package me.advait.mai.gui;

import me.advait.mai.Catalog;
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

/**
 * GUI showing all humanoids in a paginated list.
 */
public class HumanoidListGUI implements InventoryHolder {

    public static final String TITLE_PREFIX = "Humanoids";
    private static final int ROWS = 6;
    private static final int SIZE = ROWS * 9;
    private static final int ITEMS_PER_PAGE = 45; // 5 rows for items, 1 row for navigation

    private final Player player;
    private final Inventory inventory;
    private int page;

    public HumanoidListGUI(Player player) {
        this(player, 0);
    }

    public HumanoidListGUI(Player player, int page) {
        this.player = player;
        this.page = page;
        this.inventory = Bukkit.createInventory(this, SIZE, getTitle());
        populate();
    }

    private String getTitle() {
        int totalPages = getTotalPages();
        if (totalPages <= 1) {
            return TITLE_PREFIX;
        }
        return TITLE_PREFIX + " (Page " + (page + 1) + "/" + totalPages + ")";
    }

    private int getTotalPages() {
        int count = Catalog.getInstance().getCount();
        return Math.max(1, (int) Math.ceil((double) count / ITEMS_PER_PAGE));
    }

    private void populate() {
        inventory.clear();

        List<Humanoid> humanoids = Catalog.getInstance().getAllHumanoids();
        int start = page * ITEMS_PER_PAGE;
        int end = Math.min(start + ITEMS_PER_PAGE, humanoids.size());

        // Populate humanoid items
        for (int i = start; i < end; i++) {
            Humanoid humanoid = humanoids.get(i);
            ItemStack item = createHumanoidItem(humanoid);
            inventory.setItem(i - start, item);
        }

        // Navigation row (bottom row)
        int navRowStart = 45;

        // Previous page button
        if (page > 0) {
            ItemStack prevItem = createNavigationItem(Material.ARROW, "&aPrevious Page", "Click to go to page " + page);
            inventory.setItem(navRowStart, prevItem);
        }

        // Info item (center)
        ItemStack infoItem = createInfoItem();
        inventory.setItem(navRowStart + 4, infoItem);

        // Next page button
        if (page < getTotalPages() - 1) {
            ItemStack nextItem = createNavigationItem(Material.ARROW, "&aNext Page", "Click to go to page " + (page + 2));
            inventory.setItem(navRowStart + 8, nextItem);
        }

        // Close button
        ItemStack closeItem = createNavigationItem(Material.BARRIER, "&cClose", "Click to close");
        inventory.setItem(navRowStart + 7, closeItem);
    }

    private ItemStack createHumanoidItem(Humanoid humanoid) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(colorize("&e" + humanoid.getName()));

            List<String> lore = new ArrayList<>();
            lore.add(colorize("&7UUID: &f" + humanoid.getUuid().toString().substring(0, 8) + "..."));

            if (humanoid.getMannequin() != null) {
                lore.add(colorize("&7Location: &f" + LocationSerializer.toReadableString(humanoid.getMannequin().getLocation())));
                lore.add(colorize("&7Status: &aSpawned"));
            } else {
                lore.add(colorize("&7Status: &cNot spawned"));
            }

            lore.add("");
            lore.add(colorize("&eClick to manage"));

            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createNavigationItem(Material material, String name, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(colorize(name));
            List<String> lore = new ArrayList<>();
            lore.add(colorize("&7" + description));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    private ItemStack createInfoItem() {
        ItemStack item = new ItemStack(Material.BOOK);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(colorize("&6Humanoid Manager"));
            List<String> lore = new ArrayList<>();
            lore.add(colorize("&7Total humanoids: &f" + Catalog.getInstance().getCount()));
            lore.add("");
            lore.add(colorize("&7Click a Humanoid head to"));
            lore.add(colorize("&7view and manage it."));
            meta.setLore(lore);
            item.setItemMeta(meta);
        }

        return item;
    }

    public void open() {
        player.openInventory(inventory);
    }

    public int getPage() {
        return page;
    }

    public void nextPage() {
        if (page < getTotalPages() - 1) {
            page++;
            inventory.clear();
            populate();
        }
    }

    public void previousPage() {
        if (page > 0) {
            page--;
            inventory.clear();
            populate();
        }
    }

    /**
     * Gets the humanoid at the given slot index.
     *
     * @param slot the inventory slot
     * @return the humanoid, or null if slot doesn't contain a humanoid
     */
    public Humanoid getHumanoidAtSlot(int slot) {
        if (slot < 0 || slot >= ITEMS_PER_PAGE) {
            return null;
        }

        List<Humanoid> humanoids = Catalog.getInstance().getAllHumanoids();
        int index = page * ITEMS_PER_PAGE + slot;

        if (index < humanoids.size()) {
            return humanoids.get(index);
        }

        return null;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    private static String colorize(String text) {
        return text.replace("&", "\u00A7");
    }
}
