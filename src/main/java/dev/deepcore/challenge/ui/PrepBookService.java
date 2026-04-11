package dev.deepcore.challenge.ui;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BookMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Handles prep-book item creation, detection, and inventory management.
 */
public final class PrepBookService {
    private final NamespacedKey prepBookKey;

    /**
     * Creates a prep-book service.
     *
     * @param plugin plugin used to derive the prep-book metadata key
     */
    public PrepBookService(JavaPlugin plugin) {
        this.prepBookKey = new NamespacedKey(plugin, "prep-book");
    }

    /**
     * Gives a prep book to the player when they do not already have one.
     *
     * @param player player who should receive a prep book
     */
    public void giveIfMissing(Player player) {
        if (hasPrepBook(player)) {
            return;
        }

        player.getInventory().addItem(createPrepBook());
    }

    /**
     * Removes all prep book instances from the player's inventory.
     *
     * @param player player whose inventory should be stripped of prep books
     */
    public void removeFromInventory(Player player) {
        PlayerInventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (isPrepBook(stack)) {
                inventory.setItem(i, null);
            }
        }
        player.updateInventory();
    }

    /**
     * Returns whether the provided item stack is a tagged DeepCore prep book.
     *
     * @param itemStack item stack to test
     * @return true when the stack is a tagged prep book
     */
    public boolean isPrepBook(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType() != Material.WRITTEN_BOOK) {
            return false;
        }

        ItemMeta meta = itemStack.getItemMeta();
        if (meta == null) {
            return false;
        }

        Byte marker = meta.getPersistentDataContainer().get(prepBookKey, PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    private boolean hasPrepBook(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (isPrepBook(stack)) {
                return true;
            }
        }
        return false;
    }

    private ItemStack createPrepBook() {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        if (meta != null) {
            meta.setTitle("DeepCore Prep");
            meta.setAuthor("DeepCore");
            meta.addPage(ChatColor.DARK_GREEN + "DeepCore Prep\n\n" + ChatColor.BLACK
                    + "Right-click this book during prep to open challenge settings and ready up.");
            meta.getPersistentDataContainer().set(prepBookKey, PersistentDataType.BYTE, (byte) 1);
            book.setItemMeta(meta);
        }
        return book;
    }
}
