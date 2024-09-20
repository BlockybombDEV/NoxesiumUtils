package me.superneon4ik.noxesiumutils;

import com.noxcrew.noxesium.api.protocol.rule.ServerRuleIndices;
import com.noxcrew.noxesium.paper.api.NoxesiumManager;
import com.noxcrew.noxesium.paper.api.rule.ServerRules;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.BooleanArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
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

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public final class NoxesiumUtils extends JavaPlugin {
    @Getter private static NoxesiumUtils plugin;
    @Getter private static final ModrinthUpdateChecker updateChecker = new ModrinthUpdateChecker("noxesiumutils");
    @Getter private static NoxesiumManager manager;
    @Getter private static ServerRules serverRules;

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        
        manager = new NoxesiumManager(this, LoggerFactory.getLogger("NoxesiumPaperManager"));
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

        final Map<String, Integer> booleanServerRules = new HashMap<>() {{
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

        final Map<String, Integer> integerServerRules = new HashMap<>() {{
            put("heldItemNameOffset", ServerRuleIndices.HELD_ITEM_NAME_OFFSET);
            put("riptideCoyoteTime", ServerRuleIndices.RIPTIDE_COYOTE_TIME);
        }};
        
        List<CommandAPICommand> subcommands = new LinkedList<>();
        booleanServerRules.forEach((String name, Integer index) -> {
            var command = new CommandAPICommand(name)
                    .withArguments(
                            new EntitySelectorArgument.ManyPlayers("players"),
                            new BooleanArgument("value")
                    )
                    .executes((sender, args) -> {
                        var players = (Collection<Player>) args.get("players");
                        var value = args.get("value");
                        updateServerRule(sender, players, index, value);
                    });
            
            subcommands.add(command);
        });

        integerServerRules.forEach((String name, Integer index) -> {
            var command = new CommandAPICommand(name)
                    .withArguments(
                            new EntitySelectorArgument.ManyPlayers("players"),
                            new IntegerArgument("value")
                    )
                    .executes((sender, args) -> {
                        var players = (Collection<Player>) args.get("players");
                        var value = args.get("value");
                        updateServerRule(sender, players, index, value);                        
                    });

            subcommands.add(command);
        });

        new CommandAPICommand("noxesiumutils")
                .withPermission("noxesiumutils.commands")
                .withSubcommands(subcommands.toArray(new CommandAPICommand[0]))
                .register(this);
    }
    
    private void updateServerRule(CommandSender sender, Collection<Player> players, Integer index, Object value) {
        if (players == null) return;
        AtomicInteger updates = new AtomicInteger();
        players.forEach(player -> {
            var rule = NoxesiumUtils.getManager().getServerRule(player, index);
            rule.setValue(value);
            updates.getAndIncrement();
        });

        sender.sendMessage(Component.text(updates.get() + " player(s) affected.", NamedTextColor.GREEN));
    }

    public void sendLoginServerRules(Player player) {
        // Send defaults
        if (NoxesiumUtils.getPlugin().getConfig().getBoolean("sendDefaultsOnJoin", false)) {
            // Send defaults after a little time, so the client actually registers the packet.
            new BukkitRunnable() {
                @Override
                public void run() {
                    // TODO: Re-implement this
                }
            }.runTaskLater(NoxesiumUtils.getPlugin(), 5);
        }
    }
}
