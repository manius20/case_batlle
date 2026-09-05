package pl.casebattle;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class CaseBattlePlugin extends JavaPlugin implements Listener {

    private final Map<UUID, BattleLobby> activeLobbies = new HashMap<>();
    private final Random random = new Random();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("bitwa").setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player p) {
                openMainMenu(p);
            }
            return true;
        });
    }

    public void openMainMenu(Player player) {
        // Zwiększono rozmiar GUI na 54 sloty (duża skrzynia)
        Inventory gui = Bukkit.createInventory(null, 54, "§8Wybierz skrzynkę do bitwy");
        ConfigurationSection cs = getConfig().getConfigurationSection("cases");
        if (cs == null) return;

        int slot = 10;
        for (String key : cs.getKeys(false)) {
            if (slot >= 44) break; // Zabezpieczenie przed przepełnieniem
            
            // Omijamy odstępy przy krawędziach dla ładniejszego wyglądu
            if (slot % 9 == 8) slot += 2;

            String name = cs.getString(key + ".display-name", key);
            int cost = cs.getInt(key + ".cost", 0);

            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name.replace("&", "§"));
                meta.setLore(Arrays.asList(
                    "§7Koszt: §b" + cost + " Diamentów",
                    "",
                    "§eKliknij, aby utworzyć lub dołączyć!"
                ));
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.equals("§8Wybierz skrzynkę do bitwy")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.CHEST) return;

            String caseName = clicked.getItemMeta().getDisplayName();
            int cost = getCaseCost(caseName);

            // Sprawdzanie czy gracz ma wystarczająco diamentów
            if (!hasEnoughDiamonds(player, cost)) {
                player.sendMessage("§cNie masz wystarczająco diamentów! Wymagane: §b" + cost);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            // Pobieranie diamentów i wejście do gry
            removeDiamonds(player, cost);
            player.sendMessage("§aPobrano §b" + cost + " §adiamentów za wejście do bitwy.");
            createOrJoinLobby(player, caseName);

        } else if (title.startsWith("§8Poczekalnia Bitwy:")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 22) {
                BattleLobby lobby = activeLobbies.get(player.getUniqueId());
                if (lobby != null && !lobby.started) {
                    startBattle(lobby);
                }
            }
        }
    }

    private int getCaseCost(String caseDisplayName) {
        ConfigurationSection cs = getConfig().getConfigurationSection("cases");
        if (cs == null) return 0;

        for (String key : cs.getKeys(false)) {
            String name = cs.getString(key + ".display-name", "").replace("&", "§");
            if (name.equals(caseDisplayName)) {
                return cs.getInt(key + ".cost", 0);
            }
        }
        return 0;
    }

    private boolean hasEnoughDiamonds(Player player, int amount) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.DIAMOND) {
                count += stack.getAmount();
            }
        }
        return count >= amount;
    }

    private void removeDiamonds(Player player, int amount) {
        int leftToRemove = amount;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.DIAMOND) {
                int stackAmount = stack.getAmount();
                if (stackAmount <= leftToRemove) {
                    leftToRemove -= stackAmount;
                    stack.setAmount(0);
                } else {
                    stack.setAmount(stackAmount - leftToRemove);
                    leftToRemove = 0;
                }
            }
            if (leftToRemove <= 0) break;
        }
    }

    private void createOrJoinLobby(Player player, String caseName) {
        BattleLobby targetLobby = null;
        for (BattleLobby lobby : activeLobbies.values()) {
            if (lobby.caseName.equals(caseName) && !lobby.started && lobby.players.size() < 4) {
                targetLobby = lobby;
                break;
            }
        }

        if (targetLobby == null) {
            targetLobby = new BattleLobby(caseName);
            activeLobbies.put(player.getUniqueId(), targetLobby);
        }

        if (!targetLobby.players.contains(player)) {
            targetLobby.players.add(player);
        }

        openLobbyGUI(targetLobby);
    }

    private void openLobbyGUI(BattleLobby lobby) {
        Inventory gui = Bukkit.createInventory(null, 27, "§8Poczekalnia Bitwy: " + lobby.caseName);

        for (int i = 0; i < lobby.players.size(); i++) {
            Player p = lobby.players.get(i);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = head.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§aGracz: " + p.getName());
                head.setItemMeta(meta);
            }
            gui.setItem(10 + i, head);
        }

        ItemStack start = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = start.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§a§lSTART / DOŁĄCZ (" + lobby.players.size() + "/4)");
            start.setItemMeta(meta);
        }
        gui.setItem(22, start);

        for (Player p : lobby.players) {
            p.openInventory(gui);
        }
    }

    private void startBattle(BattleLobby lobby) {
        if (lobby.players.size() < getConfig().getInt("settings.min-players", 2)) {
            lobby.players.forEach(p -> p.sendMessage("§cZa mało graczy, aby rozpocząć bitwę!"));
            return;
        }

        lobby.started = true;
        Inventory battleGui = Bukkit.createInventory(null, 36, "§8Otwieranie Skrzynek...");

        for (Player p : lobby.players) {
            p.openInventory(battleGui);
        }

        new BukkitRunnable() {
            int ticks = 0;

            @Override
            public void run() {
                ticks++;
                if (ticks > 20) {
                    this.cancel();
                    determineWinner(lobby, battleGui);
                    return;
                }

                for (int i = 0; i < lobby.players.size(); i++) {
                    ItemStack rolled = getRandomItemFromConfig(lobby.caseName);
                    battleGui.setItem(10 + i, rolled);
                    lobby.players.get(i).playSound(lobby.players.get(i).getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                }
            }
        }.runTaskTimer(this, 0L, 3L);
    }

    private ItemStack getRandomItemFromConfig(String caseDisplayName) {
        ConfigurationSection cs = getConfig().getConfigurationSection("cases");
        if (cs == null) return new ItemStack(Material.DIRT);

        for (String key : cs.getKeys(false)) {
            String name = cs.getString(key + ".display-name", "").replace("&", "§");
            if (name.equals(caseDisplayName)) {
                List<Map<?, ?>> items = cs.getMapList(key + ".items");
                double rand = random.nextDouble() * 100;
                double cumulative = 0.0;

                for (Map<?, ?> itemMap : items) {
                    cumulative += ((Number) itemMap.get("chance")).doubleValue();
                    if (rand <= cumulative) {
                        Material mat = Material.valueOf((String) itemMap.get("material"));
                        int amount = ((Number) itemMap.get("amount")).intValue();
                        ItemStack stack = new ItemStack(mat, amount);
                        ItemMeta meta = stack.getItemMeta();
                        if (meta != null && itemMap.containsKey("display-name")) {
                            meta.setDisplayName(((String) itemMap.get("display-name")).replace("&", "§"));
                            stack.setItemMeta(meta);
                        }
                        return stack;
                    }
                }
            }
        }
        return new ItemStack(Material.DIRT);
    }

    private void determineWinner(BattleLobby lobby, Inventory gui) {
        Player winner = lobby.players.get(random.nextInt(lobby.players.size()));

        for (Player p : lobby.players) {
            p.sendMessage("§aBitwę wygrał gracz: §e" + winner.getName() + " §ai zabiera wszystkie wylosowane itemy!");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        for (int i = 0; i < lobby.players.size(); i++) {
            ItemStack drop = gui.getItem(10 + i);
            if (drop != null) {
                winner.getInventory().addItem(drop);
            }
        }

        activeLobbies.remove(winner.getUniqueId());
    }

    private static class BattleLobby {
        String caseName;
        List<Player> players = new ArrayList<>();
        boolean started = false;

        BattleLobby(String caseName) {
            this.caseName = caseName;
        }
    }
}
