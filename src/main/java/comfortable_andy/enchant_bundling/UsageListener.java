package comfortable_andy.enchant_bundling;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;

import java.util.*;

public class UsageListener implements Listener {

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (EnchantBundlingMain.isBundle(event.getItem())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBundleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (!event.getClick().isMouseClick() || event.getClick().isShiftClick()) return;
        ItemStack bundleItem;
        ItemStack picking;
        if (EnchantBundlingMain.isBundle(event.getCursor())) {
            bundleItem = event.getCursor();
            picking = event.getCurrentItem();
        } else {
            if (EnchantBundlingMain.isBundle(event.getCurrentItem())) {
                bundleItem = event.getCurrentItem();
                picking = event.getCursor();
            } else return;
        }
        if (picking == null || picking.isEmpty()) {
            Bukkit.getScheduler().runTaskLater(EnchantBundlingMain.getInstance(), () -> {
                if (bundleItem == null || !(bundleItem.getItemMeta() instanceof BundleMeta bundleMeta)) return;
                bundleItem.setItemMeta(EnchantBundlingMain.showHighestLevel(
                                bundleMeta,
                                bundleMeta.getItems(),
                                EnchantBundlingMain.findCurrentBundleEnchant(bundleMeta)
                        )
                );
            }, 1);
            return;
        }
        if (picking.getType() == Material.ENCHANTED_BOOK) {
            insertBook(bundleItem, picking);
        }
        event.setCancelled(true);
    }

    public static boolean insertBook(ItemStack bundleItem, ItemStack inserting) {
        BundleMeta bundleMeta = (BundleMeta) bundleItem.getItemMeta();
        EnchantmentStorageMeta enchantMeta = (EnchantmentStorageMeta) inserting.getItemMeta();
        if (enchantMeta.getStoredEnchants().size() > 1) return false;
        Enchantment toMatch = EnchantBundlingMain
                .findCurrentBundleEnchant(bundleMeta);
        if (toMatch == null) {
            bundleMeta.setItems(
                    Collections.singletonList(inserting.clone())
            );
            bundleItem.setItemMeta(EnchantBundlingMain.showHighestLevel(
                    bundleMeta,
                    bundleMeta.getItems(),
                    new ArrayList<>(
                            enchantMeta.getStoredEnchants().keySet()
                    ).getFirst()
            ));
            inserting.setAmount(0);
            return true;
        }
        if (!enchantMeta.hasStoredEnchant(toMatch)) return false;
        List<ItemStack> books = new ArrayList<>(bundleMeta.getItems().stream()
                .filter(i -> i.getItemMeta() instanceof EnchantmentStorageMeta meta
                        && meta.hasStoredEnchant(toMatch))
                .sorted(Comparator
                        .comparingInt(a -> EnchantBundlingMain.getStoredLevel(a, toMatch))
                )
                .toList());

        books.add(inserting.clone());
        inserting.setAmount(0);
        int size = books.size();
        for (int i = 0; i < size; i++) {
            ItemStack outer = books.get(i);
            int outerLevel = EnchantBundlingMain.getStoredLevel(outer, toMatch);
            for (int j = 0; j < size; j++) {
                if (i == j) continue;
                ItemStack book = books.get(j);
                if (book == null) continue;
                int level = EnchantBundlingMain
                        .getStoredLevel(book, toMatch);
                if (level == outerLevel) {
                    book.editMeta(EnchantmentStorageMeta.class, m -> {
                        m.removeStoredEnchant(toMatch);
                        m.addStoredEnchant(toMatch, level + 1, true);
                    });
                    books.set(i, null);
                    break;
                }
            }
        }
        books.removeIf(Objects::isNull);
        bundleMeta.setItems(books);
        bundleItem.setItemMeta(EnchantBundlingMain.showHighestLevel(bundleMeta, books, toMatch));
        return true;
    }

}
