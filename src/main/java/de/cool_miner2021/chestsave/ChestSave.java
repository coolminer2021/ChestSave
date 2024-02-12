package de.cool_miner2021.chestsave;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.UUID;

public class ChestSave extends JavaPlugin implements Listener {

    private FileConfiguration playerData;

    @Override
    public void onEnable() {
        getServer().getPluginManager().registerEvents(this, this);
        createPlayerDataFile();
        loadPlayerData();
        getLogger().info("ChestSave von cool_miner2021 erfolgreich gestartet!");
    }

    private void createPlayerDataFile() {
        File file = new File(getDataFolder(), "playerdata.yml");
        if (!file.exists()) {
            try {
                getDataFolder().mkdirs();
                file.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void loadPlayerData() {
        File file = new File(getDataFolder(), "playerdata.yml");
        if (!file.exists()) {
            saveResource("playerdata.yml", false);
        }
        playerData = YamlConfiguration.loadConfiguration(file);
    }

    private void savePlayerData() {
        try {
            playerData.save(new File(getDataFolder(), "playerdata.yml"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getState() instanceof Chest) {
                Chest chest = (Chest) clickedBlock.getState();

                if (isPrivate(chest)) {
                    // Die Truhe ist bereits privat, nur der Besitzer kann sie öffnen.
                    if (!isOwner(chest, event.getPlayer()) && !event.getPlayer().hasPermission("chestsave.all") && !hasChestPermission(event.getPlayer(),chest)) { //
                        event.getPlayer().sendMessage(ChatColor.RED + "Du hast keine Berechtigung, diese Truhe zu öffnen!");
                        event.setCancelled(true);
                        return;
                    }
                } else {
                    // Die Truhe ist noch nicht privat, und der Spieler ist der Besitzer.
                    ItemStack itemInHand = event.getItem();
                    if (itemInHand != null && itemInHand.getType().toString().endsWith("_SIGN")) {
                        makePrivate(chest, event.getPlayer());
                        event.getPlayer().sendMessage(ChatColor.GREEN + "Die Truhe wurde privat gemacht.");
                        event.setCancelled(true);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        Block brokenBlock = event.getBlock();
        if (brokenBlock != null && brokenBlock.getState() instanceof Chest) {
            Chest chest = (Chest) brokenBlock.getState();

            // Überprüfen, ob die Truhe privat ist und der Spieler der Besitzer oder hat "chestsave.all" Berechtigung.
            if (isPrivate(chest) && !isOwner(chest, event.getPlayer()) && !event.getPlayer().hasPermission("chestsave.all")) {
                event.getPlayer().sendMessage(ChatColor.RED + "Du hast keine Berechtigung, diese Truhe abzubauen!");
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placedBlock = event.getBlockPlaced();
        if (placedBlock != null && placedBlock.getState() instanceof Chest) {
            Chest placedChest = (Chest) placedBlock.getState();

            // Überprüfen, ob die platzierte Truhe neben einer privaten Truhe gehört jemand anderem steht.
            if (isAdjacentToPrivateChest(placedChest, event.getPlayer())) {
                event.getPlayer().sendMessage(ChatColor.RED + "Du kannst keine private Truhe direkt neben einer anderen platzieren, die jemand anderem gehört!");
                event.setCancelled(true);
            }
        }
    }

    public String getUUID(String playerName) throws Exception {
        String url = "https://api.mojang.com/users/profiles/minecraft/" + playerName;
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();

        BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;

        while ((line = reader.readLine()) != null) {
            response.append(line);
        }

        reader.close();

        if (response.length() == 0) {
            throw new RuntimeException("UUID not found for player: " + playerName);
        }

        // JSON-Verarbeitung hier (z. B. mit einer JSON-Bibliothek wie Gson)
        // Du kannst die JSON-Bibliothek deiner Wahl verwenden, um das Ergebnis zu analysieren
        String answer = response.toString();
        // In diesem Beispiel wird einfach der gesamte JSON-Text zurückgegeben
        JsonParser parser = new JsonParser();
        JsonObject json = parser.parse(answer).getAsJsonObject();

        // Extrahieren der ID
        answer = json.get("id").getAsString();
        return toUUID(answer).toString();
    }

    private boolean hasChestPermission(Player player, Chest chest) {
        String chestOwner = getChestOwner(chest);
        try {
            chestOwner =getUUID(chestOwner);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        ConfigurationSection playerSection = playerData.getConfigurationSection(chestOwner);
        if (playerSection != null && playerSection.contains("chests")) {
            List<String> playerChests = playerSection.getStringList("chests");
            return playerChests.contains(player.getName());
        }

        return false;
    }

    public static UUID toUUID(String uuidString) {
        String formattedUUID = String.format(
                "%s-%s-%s-%s-%s",
                uuidString.substring(0, 8),
                uuidString.substring(8, 12),
                uuidString.substring(12, 16),
                uuidString.substring(16, 20),
                uuidString.substring(20)
        );
        return UUID.fromString(formattedUUID);
    }

    private String getChestOwner(Chest chest) {
        if (isPrivate(chest)) {
            return chest.getCustomName().substring("§lPrivate [".length(), chest.getCustomName().length() - 1);
        }

        return "";
    }

    @EventHandler
    public void onBlockExplode(EntityExplodeEvent event) {
        for (Block explodedBlock : event.blockList()) {
            if (explodedBlock.getState() instanceof Chest) {
                Chest explodedChest = (Chest) explodedBlock.getState();
                if (isPrivate(explodedChest)) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    private boolean isPrivate(Chest chest) {
        return chest.getCustomName() != null && chest.getCustomName().startsWith(ChatColor.BOLD + "Private");
    }

    private boolean isOwner(Chest chest, Player player) {
        if (isPrivate(chest)) {
            String playerName = chest.getCustomName().substring("§lPrivate [".length(), chest.getCustomName().length() - 1);
            return player.getName().equals(playerName);
        }
        return false;
    }

    private void makePrivate(Chest chest, Player player) {
        String playerName = player.getName();
        chest.setCustomName(ChatColor.BOLD + "Private [" + playerName + "]");
        chest.update(true);
    }

    private boolean isAdjacentToPrivateChest(Chest chest, Player player) {
        // Überprüfen, ob die platzierte Truhe direkt neben einer privaten Truhe steht.
        for (Block block : getAdjacentBlocks(chest.getLocation().getBlock())) {
            if (block.getState() instanceof Chest && isPrivate((Chest) block.getState()) && !isOwner((Chest) block.getState(), player)) {
                return true;
            }
        }
        return false;
    }

    private Block[] getAdjacentBlocks(Block block) {
        // Gibt eine Liste der benachbarten Blöcke zurück.
        return new Block[]{
                block.getRelative(1, 0, 0),
                block.getRelative(-1, 0, 0),
                block.getRelative(0, 0, 1),
                block.getRelative(0, 0, -1)
        };
    }
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player) {
            Player player = (Player) sender;

            if (command.getName().equalsIgnoreCase("chest")) {
                if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
                    listChestAccess(player);
                    return true;
                } else if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
                    addChestAccess(player, args[1]);
                    return true;
                } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
                    removeChestAccess(player, args[1]);
                    return true;
                }
            }
        }
        return false;
    }

    private void listChestAccess(Player player) {
        ConfigurationSection playerSection = playerData.getConfigurationSection(player.getUniqueId().toString());

        if (playerSection != null && playerSection.contains("chests")) {
            List<String> playerChests = playerSection.getStringList("chests");
            if (!playerChests.isEmpty()) {
                player.sendMessage(ChatColor.GREEN + "Spieler mit Zugriff auf deine Truhen:");
                for (String username : playerChests) {
                    player.sendMessage(ChatColor.YELLOW + "- " + username);
                }
            } else {
                player.sendMessage(ChatColor.YELLOW + "Du hast keinen Spielern Zugriff auf deine Truhen gewährt.");
            }
        } else {
            player.sendMessage(ChatColor.YELLOW + "Du hast keinen Spielern Zugriff auf deine Truhen gewährt.");
        }
    }


    private void addChestAccess(Player player, String username) {
        ConfigurationSection playerSection = playerData.getConfigurationSection(player.getUniqueId().toString());

        if (playerSection == null) {
            playerSection = playerData.createSection(player.getUniqueId().toString());
        }

        List<String> playerChests = playerSection.getStringList("chests");

        if (!playerChests.contains(username)) {
            playerChests.add(username);
            playerSection.set("chests", playerChests);
            savePlayerData();

            player.sendMessage(ChatColor.GREEN + "Spieler hinzugefügt: " + username);
        } else {
            player.sendMessage(ChatColor.RED + "Der Spieler " + username + " hat bereits Berechtigung für deine Truhen.");
        }
    }


    private void removeChestAccess(Player player, String username) {
        ConfigurationSection playerSection = playerData.getConfigurationSection(player.getUniqueId().toString());

        if (playerSection != null && playerSection.contains("chests")) {
            List<String> playerChests = playerSection.getStringList("chests");

            if (playerChests.contains(username)) {
                playerChests.remove(username);
                playerSection.set("chests", playerChests);
                savePlayerData();

                player.sendMessage(ChatColor.RED + "Spieler entfernt: " + username);
            } else {
                player.sendMessage(ChatColor.RED + "Der Spieler " + username + " hat keine Berechtigung für deine Truhen.");
            }
        } else {
            player.sendMessage(ChatColor.RED + "Du hast keine Spieler hinzugefügt.");
        }
    }

}
