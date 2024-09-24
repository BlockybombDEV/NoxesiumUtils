package me.superneon4ik.noxesiumutils;

import com.noxcrew.noxesium.api.protocol.rule.ServerRuleIndices;
import com.noxcrew.noxesium.paper.api.rule.GraphicsType;
import com.noxcrew.noxesium.paper.api.rule.ServerRules;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.*;
import lombok.Getter;
import me.superneon4ik.noxesiumutils.listeners.NoxesiumBukkitListener;
import me.superneon4ik.noxesiumutils.modules.ModrinthUpdateChecker;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.slf4j.LoggerFactory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class NoxesiumUtils extends JavaPlugin {
    @Getter private static NoxesiumUtils plugin;
    @Getter private static final ModrinthUpdateChecker updateChecker = new ModrinthUpdateChecker("noxesiumutils");
    @Getter private static HookedNoxesiumManager manager;
    @Getter private static ServerRules serverRules;

    private static final Map<String, Integer> booleanServerRules = new HashMap<>() {{
        put("disableSpinAttackCollisions", ServerRuleIndices.DISABLE_SPIN_ATTACK_COLLISIONS);
        put("cameraLocked", ServerRuleIndices.CAMERA_LOCKED);
        put("disableVanillaMusic", ServerRuleIndices.DISABLE_VANILLA_MUSIC);
        put("disableBoatCollisions", ServerRuleIndices.DISABLE_BOAT_COLLISIONS);
        put("disableUiOptimizations", ServerRuleIndices.DISABLE_UI_OPTIMIZATIONS);
        put("showMapInUi", ServerRuleIndices.SHOW_MAP_IN_UI);
        put("disableDeferredChunkUpdates", ServerRuleIndices.DISABLE_DEFERRED_CHUNK_UPDATES);
        put("disableMapUi", ServerRuleIndices.DISABLE_MAP_UI);

        // TODO: Implement Noxcrew's recommendations before enabling
        // https://github.com/Noxcrew/noxesium/blob/4b3f93fe6886eac60dbfffa6cb125e1e5a31886a/api/src/main/java/com/noxcrew/noxesium/api/protocol/rule/ServerRuleIndices.java#L85
        put("enableSmootherClientTrident", ServerRuleIndices.ENABLE_SMOOTHER_CLIENT_TRIDENT);
    }};

    private static final Map<String, Integer> integerServerRules = new HashMap<>() {{
        put("heldItemNameOffset", ServerRuleIndices.HELD_ITEM_NAME_OFFSET);
        put("riptideCoyoteTime", ServerRuleIndices.RIPTIDE_COYOTE_TIME);
    }};
    
    private static final Map<String, Integer> allServerRules = new HashMap<>() {{
        putAll(booleanServerRules); 
        putAll(integerServerRules);
        put("handItemOverride", ServerRuleIndices.HAND_ITEM_OVERRIDE);
        put("overrideGraphicsMode", ServerRuleIndices.OVERRIDE_GRAPHICS_MODE);
        put("customCreativeItems", ServerRuleIndices.CUSTOM_CREATIVE_ITEMS);
        put("qibBehaviors", ServerRuleIndices.QIB_BEHAVIORS);
    }};

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        
        manager = new HookedNoxesiumManager(this, LoggerFactory.getLogger("NoxesiumPaperManager"));
        manager.register();
        serverRules = new ServerRules(manager);

        registerCommands();

        // Register Bukkit listener
        getServer().getPluginManager().registerEvents(new NoxesiumBukkitListener(), this);

        // Check for updates
        if (getConfig().getBoolean("checkForUpdates")) updateChecker.beginChecking(5 * 60 * 20);

    }

    @Override
    public void onDisable() {
        manager.unregister();
    }

    @SuppressWarnings({"unsafe", "unchecked"})
    private void registerCommands() {
        CommandAPI.registerCommand(NoxesiumUtilsCommand.class);

        List<CommandAPICommand> subcommands = new LinkedList<>();
        
        // Reset option for all ServerRules
        allServerRules.forEach((String name, Integer index) -> {
            subcommands.add(
                    new CommandAPICommand(name)
                            .withArguments(
                                    new EntitySelectorArgument.ManyPlayers("players"),
                                    new LiteralArgument("reset")
                            )
                            .executes((sender, args) -> {
                                var players = (Collection<Player>) args.get("players");
                                resetServerRule(sender, players, index);
                            })
            );
        });
        
        // Reset all ServerRules
        subcommands.add(
                new CommandAPICommand("reset")
                        .withArguments(
                                new EntitySelectorArgument.ManyPlayers("players"),
                                new LiteralArgument("allServerRules")
                        )
                        .executes((sender, args) -> {
                            var players = (Collection<Player>) args.get("players");
                            if (players == null) return;
                            AtomicInteger updates = new AtomicInteger();
                            players.forEach(player -> {
                                allServerRules.forEach((String name, Integer index) -> {
                                    var rule = NoxesiumUtils.getManager().getServerRule(player, index);
                                    rule.reset();
                                });
                                updates.getAndIncrement();
                            });

                            if (sender != null)
                                sender.sendMessage(Component.text(updates.get() + " player(s) affected.", NamedTextColor.GREEN));
                        })
        );
        
        // Anything that stores a Boolean
        booleanServerRules.forEach((String name, Integer index) -> {
            subcommands.add(
                    new CommandAPICommand(name)
                        .withArguments(
                                new EntitySelectorArgument.ManyPlayers("players"),
                                new BooleanArgument("value")
                        )
                        .executes((sender, args) -> {
                            var players = (Collection<Player>) args.get("players");
                            var value = args.get("value");
                            updateServerRule(sender, players, index, value);
                        })
            );
        });

        // Anything that stores an Int
        integerServerRules.forEach((String name, Integer index) -> {
            subcommands.add(
                    new CommandAPICommand(name)
                        .withArguments(
                                new EntitySelectorArgument.ManyPlayers("players"),
                                new IntegerArgument("value")
                        )
                        .executes((sender, args) -> {
                            var players = (Collection<Player>) args.get("players");
                            var value = args.get("value");
                            updateServerRule(sender, players, index, value);                        
                        })
            );
        });

        // handItemOverride
        subcommands.add(
                new CommandAPICommand("handItemOverride")
                        .withArguments(
                                new EntitySelectorArgument.ManyPlayers("players"),
                                new ItemStackArgument("value")
                        )
                        .executes((sender, args) -> {
                            var players = (Collection<Player>) args.get("players");
                            var value = args.get("value");
                            updateServerRule(sender, players, ServerRuleIndices.HAND_ITEM_OVERRIDE, value);
                        })
        );
        
        // overrideGraphicsMode
        for (var type : GraphicsType.getEntries()) {
            subcommands.add(
                    new CommandAPICommand("overrideGraphicsMode")
                            .withArguments(
                                    new EntitySelectorArgument.ManyPlayers("players"),
                                    new LiteralArgument(type.name().toLowerCase(Locale.ROOT))
                            )
                            .executes((sender, args) -> {
                                var players = (Collection<Player>) args.get("players");
                                updateServerRule(sender, players, ServerRuleIndices.OVERRIDE_GRAPHICS_MODE, Optional.of(type));
                            })
            );
        }
        subcommands.add(
                new CommandAPICommand("overrideGraphicsMode")
                        .withArguments(
                                new EntitySelectorArgument.ManyPlayers("players"),
                                new LiteralArgument("disable")
                        )
                        .executes((sender, args) -> {
                            var players = (Collection<Player>) args.get("players");
                            updateServerRule(sender, players, ServerRuleIndices.OVERRIDE_GRAPHICS_MODE, Optional.empty());
                        })
        );
        
        // TODO: Implement ServerRule: customCreativeItems
        //       Currently no idea what would be the best way to do them.
        //       I could do add/remove commands, but then it would be too hard to 
        //       handle everyone. Probably will do it in the config and just make this a Boolean.
        
        // TODO: Implement ServerRule: qibBehaviors
        //       Might either do JSON files for each behaviour, since I see
        //       some deserialization in the Noxesium API
        
        

        new CommandAPICommand("noxesiumutils")
                .withPermission("noxesiumutils.commands")
                .withSubcommands(subcommands.toArray(new CommandAPICommand[0]))
                .register(this);
    }
    
    private static void updateServerRule(@Nullable CommandSender sender, Collection<Player> players, Integer index, Object value) {
        if (players == null) return;
        AtomicInteger updates = new AtomicInteger();
        players.forEach(player -> {
            var rule = NoxesiumUtils.getManager().getServerRule(player, index);
            rule.setValue(value);
            updates.getAndIncrement();
        });

        if (sender != null)
            sender.sendMessage(Component.text(updates.get() + " player(s) affected.", NamedTextColor.GREEN));
    }

    private static void resetServerRule(@Nullable CommandSender sender, Collection<Player> players, Integer index) {
        if (players == null) return;
        AtomicInteger updates = new AtomicInteger();
        players.forEach(player -> {
            var rule = NoxesiumUtils.getManager().getServerRule(player, index);
            rule.reset();
            updates.getAndIncrement();
        });

        if (sender != null)
            sender.sendMessage(Component.text(updates.get() + " player(s) affected.", NamedTextColor.GREEN));
    }

    public static void sendLoginServerRules(Player player) {
        // Send defaults
        if (NoxesiumUtils.getPlugin().getConfig().getBoolean("sendDefaultsOnJoin", false)) {
            // Send defaults after a little time, so the client actually registers the packet.
            var defaults = NoxesiumUtils.getPlugin().getConfig().getConfigurationSection("defaults");
            if (defaults == null) return;
            
            new BukkitRunnable() {
                @Override
                public void run() {
                    // Send present Boolean ServerRules  
                    booleanServerRules.forEach((String name, Integer index) -> {
                        if (!defaults.contains(name)) return;
                        var value = defaults.getBoolean(name, false);
                        updateServerRule(null, List.of(player), index, value);
                    });

                    // Send present Int ServerRules
                    integerServerRules.forEach((String name, Integer index) -> {
                        if (!defaults.contains(name)) return;
                        var value = defaults.getInt(name, 0);
                        updateServerRule(null, List.of(player), index, value);
                    });

                    // overrideGraphicsMode
                    if (defaults.contains("overrideGraphicsMode")) {
                        var overrideGraphicsModeStr = defaults.getString("overrideGraphicsMode");
                        var overrideGraphicsModeValue = Optional.of(GraphicsType.valueOf(overrideGraphicsModeStr));
                        updateServerRule(null, List.of(player), ServerRuleIndices.OVERRIDE_GRAPHICS_MODE, overrideGraphicsModeValue);
                    }
                    
                    // TODO:
                    //  handItemOverride
                    //  customCreativeItems
                    //  qibBehaviors
                }
            }.runTaskLater(NoxesiumUtils.getPlugin(), 5);
        }
    }
}
