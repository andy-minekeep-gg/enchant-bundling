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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings("UnstableApiUsage")
public final class EnchantBundlingMain extends JavaPlugin {

    private static EnchantBundlingMain INST;

    public static final NamespacedKey ITEM_KEY = new NamespacedKey("enchant_bundling", "bundle");

    public static Enchantment getFirstInSet(Set<Enchantment> set) {
        return new ArrayList<>(set).getFirst();
    }

    @Override
    public void onEnable() {
        INST = this;
        getServer().getPluginManager().registerEvents(new UsageListener(), this);
        getServer().getPluginManager().registerEvents(new PickupListener(), this);
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

    public static @Nullable Enchantment findCurrentBundleEnchant(BundleMeta meta) {
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

    public static boolean isBundle(@Nullable ItemStack item) {
        return item != null
                && !item.isEmpty()
                && item.hasItemMeta()
                && Boolean.TRUE.equals(item.getItemMeta().getPersistentDataContainer().get(ITEM_KEY, PersistentDataType.BOOLEAN));
    }

    public static BundleMeta showHighestLevel(BundleMeta meta, List<ItemStack> books, Enchantment target) {
        ItemStack item = target == null ? null : books.stream()
                .max(Comparator.comparingInt(
                        a -> getStoredLevel(a, target))
                )
                .orElse(null);
        int level = item == null ? 0 : getStoredLevel(
                item,
                target
        );
        String content = "Highest: " + level;
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

    public static int getStoredLevel(ItemStack i, Enchantment enchantment) {
        return enchantment == null ? 0 : ((EnchantmentStorageMeta) i.getItemMeta()).getStoredEnchantLevel(enchantment);
    }

    public static EnchantBundlingMain getInstance() {
        return INST;
    }

}
