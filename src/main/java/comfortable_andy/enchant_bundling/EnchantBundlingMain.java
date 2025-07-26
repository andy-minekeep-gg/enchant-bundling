package comfortable_andy.enchant_bundling;

import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class EnchantBundlingMain extends JavaPlugin implements Listener {

    public static final NamespacedKey ITEM_KEY = new NamespacedKey("enchant_bundling", "bundle");

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e -> {
            Commands commands = e.registrar();
            commands.register(
                    Commands
                            .literal("bundle")
                            .requires(c -> sender(c).hasPermission("enchant_bundling.command"))
                            .executes(c -> {
                                if (!(sender(c.getSource()) instanceof InventoryHolder holder)) return 0;
                                holder.getInventory().addItem(makeBundle());
                                return 1;
                            })
                            .then(Commands
                                    .argument("target", ArgumentTypes.player())
                                    .executes(c -> {
                                        Player target = c.getArgument("target", Player.class);
                                        if (target == null) {
                                            return 0;
                                        }
                                        target.getInventory().addItem(makeBundle());
                                        return 1;
                                    })
                            )
                            .build()
            );
        });
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBundleClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        if (!event.getClick().isLeftClick() || event.getClick().isShiftClick()) return;
        ItemStack bundleItem;
        ItemStack picking;
        if (isBundle(event.getCursor())) {
            bundleItem = event.getCursor();
            picking = event.getCurrentItem();
        } else {
            if (isBundle(event.getCurrentItem())) {
                bundleItem = event.getCurrentItem();
                picking = event.getCursor();
            } else {
                bundleItem = null;
                picking = null;
            }
        }
        if (picking == null || picking.isEmpty()) {
            if (bundleItem == null) return;
            System.out.println("update item info");
            BundleMeta bundleMeta = (BundleMeta) bundleItem.getItemMeta();
            bundleItem.setItemMeta(showHighestLevel(
                            bundleMeta,
                            bundleMeta.getItems(),
                            findCurrentBundleEnchant(bundleMeta)
                    )
            );
            return;
        }
        BundleMeta bundleMeta = (BundleMeta) bundleItem.getItemMeta();
        if (picking.getType() == Material.ENCHANTED_BOOK) {
            EnchantmentStorageMeta enchantMeta = (EnchantmentStorageMeta) picking.getItemMeta();
            if (enchantMeta.getStoredEnchants().size() > 1) {
                event.setCancelled(true);
                return;
            }
            Enchantment toMatch = findCurrentBundleEnchant(bundleMeta);
            System.out.println("matching " + toMatch);
            if (toMatch == null) {
                event.setCancelled(true);
                bundleMeta.setItems(
                        Collections.singletonList(picking.clone())
                );
                bundleItem.setItemMeta(showHighestLevel(bundleMeta, bundleMeta.getItems(), toMatch));
                picking.setAmount(0);
                return;
            }
            if (!enchantMeta.hasStoredEnchant(toMatch)) {
                event.setCancelled(true);
                return;
            }
            List<ItemStack> books = new ArrayList<>(bundleMeta.getItems().stream()
                    .filter(i -> i.getItemMeta() instanceof EnchantmentStorageMeta meta
                            && meta.hasStoredEnchant(toMatch))
                    .sorted(Comparator.comparingInt(a -> getStoredLevel(a, toMatch)))
                    .toList());
            int addingLevel = getStoredLevel(picking, toMatch);
            for (ItemStack book : books) {
                int level = getStoredLevel(book, toMatch);
                if (level == addingLevel) {
                    book.editMeta(EnchantmentStorageMeta.class, m -> {
                        m.removeStoredEnchant(toMatch);
                        m.addStoredEnchant(toMatch, level + 1, true);
                    });
                    bundleMeta.setItems(books);
                    bundleItem.setItemMeta(showHighestLevel(bundleMeta, books, toMatch));
                    if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR)
                        event.setCancelled(true);
                    picking.setAmount(0);
                    return;
                }
            }
            books.add(picking.clone());
            bundleMeta.setItems(books);
            bundleItem.setItemMeta(showHighestLevel(bundleMeta, books, toMatch));
            if (event.getAction() == InventoryAction.SWAP_WITH_CURSOR)
                event.setCancelled(true);
            picking.setAmount(0);
        }
    }

    private static @Nullable Enchantment findCurrentBundleEnchant(BundleMeta meta) {
        return meta.getItems().stream()
                .filter(i -> i.getItemMeta() instanceof EnchantmentStorageMeta m
                        && m.getStoredEnchants().size() == 1)
                .map(i -> {
                            EnchantmentStorageMeta m = (EnchantmentStorageMeta) i.getItemMeta();
                            List<Enchantment> list = new ArrayList<>(m.getStoredEnchants().keySet());
                            return list.getFirst();
                        }
                )
                .findFirst().orElse(null);
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (isBundle(event.getItem())) event.setCancelled(true);
    }


    private static CommandSender sender(CommandSourceStack stack) {
        return stack.getExecutor() != null ? stack.getExecutor() : stack.getSender();
    }

    public static ItemStack makeBundle() {
        ItemStack stack = new ItemStack(Material.BUNDLE);
        stack.editMeta(BundleMeta.class, meta -> {
            meta.getPersistentDataContainer().set(ITEM_KEY, PersistentDataType.BOOLEAN, true);
            meta.displayName(Component.text("Compression Bundle").decoration(TextDecoration.ITALIC, false));
            meta.setRarity(ItemRarity.UNCOMMON);
        });
        return stack;
    }

    public static boolean isBundle(ItemStack item) {
        return item != null
                && !item.isEmpty()
                && item.hasItemMeta()
                && Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.BOOLEAN));
    }

    public static BundleMeta showHighestLevel(BundleMeta meta, List<ItemStack> books, Enchantment target) {
        ItemStack item = books.stream()
                .max(Comparator.comparingInt(
                        a -> getStoredLevel(a, target))
                )
                .orElse(null);
        if (item == null) return meta;
        String content = "Highest: " + getStoredLevel(
                item,
                target
        );
        meta.lore(Collections.singletonList(
                        Component
                                .text(content, NamedTextColor.WHITE)
                                .decoration(
                                        TextDecoration.ITALIC,
                                        TextDecoration.State.FALSE
                                )
                )
        );
        return meta;
    }

    private static int getStoredLevel(ItemStack i, Enchantment enchantment) {
        return enchantment == null ? 0 : ((EnchantmentStorageMeta) i.getItemMeta()).getStoredEnchantLevel(enchantment);
    }

}
