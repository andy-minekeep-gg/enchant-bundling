package comfortable_andy.enchant_bundling;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Set;

import static comfortable_andy.enchant_bundling.EnchantBundlingMain.findCurrentBundleEnchant;
import static comfortable_andy.enchant_bundling.EnchantBundlingMain.isBundle;

public class PickupListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        if (item.getType() == Material.ENCHANTED_BOOK) {
            if (canCompressBook(player, item))
                event.getItem().setItemStack(new ItemStack(Material.AIR));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onObtainItem(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String name = event.getAction().name();
        ItemStack tryAdd = null;
        if (name.contains("MOVE_TO_OTHER_INVENTORY")) {
            if (event.getView().getInventory(event.getRawSlot()) == event.getView().getBottomInventory()) return;
            tryAdd = event.getCurrentItem();
        }
        if (tryAdd == null) return;
        if (canCompressBook(player, tryAdd))
            tryAdd.setAmount(0);
    }

    private static boolean canCompressBook(Player player, ItemStack item) {
        if (!item.hasItemMeta() || !(item.getItemMeta() instanceof EnchantmentStorageMeta meta)) return false;
        if (meta.getStoredEnchants().size() != 1) return false;
        Set<Enchantment> set = meta.getStoredEnchants().keySet();
        Enchantment enchant = EnchantBundlingMain.getFirstInSet(set);
        ItemStack bundle = findBundleWithEnchant(player.getInventory(), enchant);
        if (bundle == null) return false;
        return UsageListener.insertBook(bundle, item);
    }

    private static @Nullable ItemStack findBundleWithEnchant(Inventory inventory, @NotNull Enchantment enchant) {
        for (@Nullable ItemStack item : inventory.getContents()) {
            if (!isBundle(item)) continue;
            BundleMeta meta = (BundleMeta) item.getItemMeta();
            Enchantment bundle = findCurrentBundleEnchant(meta);
            if (bundle == null || bundle.getKey().equals(enchant.getKey())) {
                return item;
            }
        }
        return null;
    }

}
