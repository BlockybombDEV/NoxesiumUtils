package me.superneon4ik.noxesiumutils;

import com.noxcrew.noxesium.api.protocol.rule.ServerRuleIndices;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkitConfig;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.*;
import lombok.Getter;
import me.superneon4ik.noxesiumutils.listeners.NoxesiumBukkitListener;
import me.superneon4ik.noxesiumutils.listeners.LegacyNoxesiumMessageListener;
import me.superneon4ik.noxesiumutils.listeners.NoxesiumMessageListener;
import me.superneon4ik.noxesiumutils.modules.ModrinthUpdateChecker;
import me.superneon4ik.noxesiumutils.modules.NoxesiumServerRuleBuilder;
import me.superneon4ik.noxesiumutils.network.clientbound.ClientboundChangeServerRulesPacket;
import me.superneon4ik.noxesiumutils.objects.PlayerClientSettings;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public final class NoxesiumUtils extends JavaPlugin {
    public static final int SERVER_PROTOCOL_VERSION = 3;

    // legacy
    public static final String NOXESIUM_LEGACY_CLIENT_INFORMATION_CHANNEL = "noxesium:client_information";
    public static final String NOXESIUM_LEGACY_CLIENT_SETTINGS_CHANNEL = "noxesium:client_settings";
    public static final String NOXESIUM_LEGACY_SERVER_RULE_CHANNEL = "noxesium:server_rules";

    // v1
    public static final String NOXESIUM_V1_CLIENT_INFORMATION_CHANNEL = "noxesium-v1:client_info";
    public static final String NOXESIUM_V1_CLIENT_SETTINGS_CHANNEL = "noxesium-v1:client_settings";
    public static final String NOXESIUM_V1_SERVER_INFORMATION_CHANNEL = "noxesium-v1:server_info";
    public static final String NOXESIUM_V1_CHANGE_SERVER_RULES_CHANNEL = "noxesium-v1:change_server_rules";
    public static final String NOXESIUM_V1_RESET_SERVER_RULES_CHANNEL = "noxesium-v1:reset_server_rules";
    public static final String NOXESIUM_V1_RESET_CHANNEL = "noxesium-v1:reset";

    @Getter private static NoxesiumUtils plugin;
    @Getter private static final ModrinthUpdateChecker updateChecker = new ModrinthUpdateChecker("noxesiumutils");
    @Getter private static final NoxesiumManager manager = new NoxesiumManager();

    @Override
    public void onEnable() {
        plugin = this;
        saveDefaultConfig();
        registerCommands();

        // Register outgoing plugin messaging channels
        getServer().getMessenger().registerOutgoingPluginChannel(this, NOXESIUM_LEGACY_SERVER_RULE_CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, NOXESIUM_V1_CHANGE_SERVER_RULES_CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, NOXESIUM_V1_RESET_SERVER_RULES_CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, NOXESIUM_V1_SERVER_INFORMATION_CHANNEL);
        getServer().getMessenger().registerOutgoingPluginChannel(this, NOXESIUM_V1_RESET_CHANNEL);

        // Register incoming plugin messaging channels
        getServer().getMessenger().registerIncomingPluginChannel(this, NOXESIUM_LEGACY_CLIENT_INFORMATION_CHANNEL, new LegacyNoxesiumMessageListener());
        getServer().getMessenger().registerIncomingPluginChannel(this, NOXESIUM_LEGACY_CLIENT_SETTINGS_CHANNEL, new LegacyNoxesiumMessageListener());
        getServer().getMessenger().registerIncomingPluginChannel(this, NOXESIUM_V1_CLIENT_INFORMATION_CHANNEL, new NoxesiumMessageListener());
        getServer().getMessenger().registerIncomingPluginChannel(this, NOXESIUM_V1_CLIENT_SETTINGS_CHANNEL, new NoxesiumMessageListener());

        // Register Bukkit listener
        getServer().getPluginManager().registerEvents(new NoxesiumBukkitListener(), this);

        // Check for updates
        if (getConfig().getBoolean("checkForUpdates")) updateChecker.beginChecking(5 * 60 * 20);
    }

    @Override
    public void onLoad() {
        CommandAPI.onLoad(new CommandAPIBukkitConfig(this));
    }

    @SuppressWarnings({"unsafe", "unchecked"})
    private void registerCommands() {
        CommandAPI.registerCommand(NoxesiumUtilsCommand.class);
        new CommandAPICommand("noxesiumutils")
                .withPermission("noxesiumutils.commands")
                .withSubcommands(
                        new CommandAPICommand("globalCanPlaceOn")
                                .withArguments(new EntitySelectorArgument.ManyPlayers("players"), new ListArgumentBuilder<Material>("values")
                                        .withList(List.of(Material.values()))
                                        .withMapper(material -> "minecraft:" + material.name().toLowerCase())
                                        .buildGreedy()
                                )
                                .executes((sender, args) -> {
                                    var players = (Collection<Player>) args.get(0);
                                    var materialValues = (List<Material>) args.get(1);
                                    assert materialValues != null && players != null;
                                    var stringValues = materialValues.stream().map(v -> "minecraft:" + v.name().toLowerCase()).toList();

                                    AtomicInteger updates = new AtomicInteger();
                                    players.stream().filter(x -> NoxesiumUtils.getManager().isUsingNoxesium(x, 2)).forEach(player -> {
                                        var rule = NoxesiumUtils.getManager().<List<String>>getServerRule(player, ServerRuleIndices.GLOBAL_CAN_PLACE_ON);
                                        if (rule == null) return;
                                        rule.setValue(stringValues);
                                        if (new ClientboundChangeServerRulesPacket<>(List.of(rule)).send(player)) {
                                            updates.getAndIncrement();
                                        }
                                    });
                                    sender.sendMessage(ChatColor.GREEN + String.valueOf(updates.get()) + " player(s) affected.");
                                }),
                        new CommandAPICommand("globalCanDestroy")
                                .withArguments(new EntitySelectorArgument.ManyPlayers("players"), new ListArgumentBuilder<Material>("values")
                                        .withList(List.of(Material.values()))
                                        .withMapper(material -> "minecraft:" + material.name().toLowerCase())
                                        .buildGreedy()
                                )
                                .executes((sender, args) -> {
                                    var players = (Collection<Player>) args.get(0);
                                    var materialValues = (List<Material>) args.get(1);
                                    assert materialValues != null && players != null;
                                    var stringValues = materialValues.stream().map(v -> "minecraft:" + v.name().toLowerCase()).toList();

                                    AtomicInteger updates = new AtomicInteger();
                                    players.stream().filter(x -> NoxesiumUtils.getManager().isUsingNoxesium(x, 2)).forEach(player -> {
                                        var rule = NoxesiumUtils.getManager().<List<String>>getServerRule(player, ServerRuleIndices.GLOBAL_CAN_DESTROY);
                                        if (rule == null) return;
                                        rule.setValue(stringValues);
                                        if (new ClientboundChangeServerRulesPacket<>(List.of(rule)).send(player)) {
                                            updates.getAndIncrement();
                                        }
                                    });
                                    sender.sendMessage(ChatColor.GREEN + String.valueOf(updates.get()) + " player(s) affected.");
                                })
                )
                .register();
    }

//    /**
//     * Execute a Consumer for all Noxesium players online.
//     * @param minProtocol Minimum noxesium protocol version.
//     * @param playerConsumer Consumer (Player, Protocol Version). Runs for each Noxesium player.
//     * @return Number of Noxesium players affected.
//     */
//    public static int forNoxesiumPlayers(int minProtocol, BiConsumer<Player, Integer> playerConsumer) {
//        int amount = 0;
//        for (Player player : Bukkit.getOnlinePlayers()) {
//            if (noxesiumPlayers.containsKey(player.getUniqueId())) {
//                Integer protocolVersion = noxesiumPlayers.get(player.getUniqueId());
//                if (protocolVersion >= minProtocol) {
//                    playerConsumer.accept(player, protocolVersion);
//                    amount++;
//                }
//            }
//        }
//        return amount;
//    }
//
//    /**
//     * Execute a Consumer for Noxesium players from the Collection.
//     * @param players Collection of players.
//     * @param minProtocol Minimum noxesium protocol version.
//     * @param playerConsumer Consumer (Player, Protocol Version). Runs for each Noxesium player.
//     * @return Number of Noxesium players affected.
//     */
//    public static int forNoxesiumPlayers(Collection<Player> players, int minProtocol, BiConsumer<Player, Integer> playerConsumer) {
//        int amount = 0;
//        for (Player player : players) {
//            if (noxesiumPlayers.containsKey(player.getUniqueId())) {
//                Integer protocolVersion = noxesiumPlayers.get(player.getUniqueId());
//                if (protocolVersion >= minProtocol) {
//                    playerConsumer.accept(player, protocolVersion);
//                    amount++;
//                }
//            }
//        }
//        return amount;
//    }
//
//    /**
//     * Send a server rules packet to a player.
//     * @param player Receiver.
//     * @param packet Bytes.
//     */
//    public static void sendServerRulesPacket(@NotNull Player player, byte[] packet) {
//        var protocolVersion = getPlayerProtocolVersion(player.getUniqueId());
//        if (protocolVersion >= 3) {
//            player.sendPluginMessage(getPlugin(), NOXESIUM_V1_SERVER_RULE_CHANNEL, packet);
//        }
//        else if (protocolVersion >= 1){
//            player.sendPluginMessage(getPlugin(), NOXESIUM_LEGACY_SERVER_RULE_CHANNEL, packet);
//        }
//    }
//
//    /**
//     * Returns player's Noxesium protocol version.
//     * @param uuid UUID of the player.
//     * @return Protocol Version or 0 if not installed.
//     */
//    public static int getPlayerProtocolVersion(UUID uuid) {
//        return noxesiumPlayers.getOrDefault(uuid, 0);
//    }
//
//    /**
//     * Returns player's client settings.
//     * @param uuid UUID of the player.
//     * @return Client settings or NULL if not installed.
//     */
//    public static @Nullable PlayerClientSettings getPlayerClientSettings(UUID uuid) {
//         return noxesiumClientSettings.getOrDefault(uuid, null);
//    }
}
