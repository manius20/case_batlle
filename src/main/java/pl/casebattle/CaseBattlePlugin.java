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
    private final Map<UUID, String> selectedCaseName = new HashMap<>();
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
        Inventory gui = Bukkit.createInventory(null, 54, "§8Wybierz skrzynkę do bitwy");
        ConfigurationSection cs = getConfig().getConfigurationSection("cases");
        if (cs == null) return;

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, filler);
        }

        int slot = 10;
        for (String key : cs.getKeys(false)) {
            if (slot >= 44) break;
            if (slot % 9 == 8 || slot % 9 == 0) slot += 2;

            String name = cs.getString(key + ".display-name", key);
            int cost = cs.getInt(key + ".cost", 0);

            ItemStack item = new ItemStack(Material.CHEST);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(name.replace("&", "§"));
                meta.setLore(Arrays.asList(
                    "§7Koszt za 1 skrzynkę: §b" + cost + " Diamentów",
                    "",
                    "§eKliknij, aby wybrać ilość skrzynek!"
                ));
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
        player.openInventory(gui);
    }

    public void openAmountSelectionMenu(Player player, String caseName) {
        selectedCaseName.put(player.getUniqueId(), caseName);
        Inventory gui = Bukkit.createInventory(null, 27, "§8Wybierz ilość skrzynek (1-15)");

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        int singleCost = getCaseCost(caseName);

        // Tworzenie 15 przycisków wyboru ilości skrzynek
        int[] slots = {0, 1, 2, 3, 4, 5, 6, 7, 8, 10, 11, 12, 13, 14, 15};
        for (int i = 1; i <= 15; i++) {
            ItemStack item = new ItemStack(Material.CHEST, i);
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setDisplayName("§a§l" + i + " Skrzynek");
                meta.setLore(Arrays.asList(
                    "§7Koszt całkowity: §b" + (singleCost * i) + " Diamentów",
                    "",
                    "§eKliknij, aby rozpocząć bitwę o " + i + " skrzynek!"
                ));
                item.setItemMeta(meta);
            }
            gui.setItem(slots[i - 1], item);
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
            openAmountSelectionMenu(player, caseName);

        } else if (title.equals("§8Wybierz ilość skrzynek (1-15)")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.CHEST) return;

            int amount = clicked.getAmount();
            String caseName = selectedCaseName.get(player.getUniqueId());
            if (caseName == null) return;

            int totalCost = getCaseCost(caseName) * amount;

            if (!hasEnoughDiamonds(player, totalCost)) {
                player.sendMessage("§cNie masz wystarczająco diamentów! Wymagane: §b" + totalCost);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            removeDiamonds(player, totalCost);
            player.sendMessage("§aPobrano §b" + totalCost + " §adiamentów za " + amount + " skrzynek.");
            createOrJoinLobby(player, caseName, amount);

        } else if (title.startsWith("§8Poczekalnia Bitwy:")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 22) {
                BattleLobby lobby = activeLobbies.get(player.getUniqueId());
                if (lobby != null && !lobby.started && !lobby.starting) {
                    startCountdown(lobby);
                }
            }
        } else if (title.startsWith("§8Losowanie Skrzynek...")) {
            event.setCancelled(true);
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

    private void createOrJoinLobby(Player player, String caseName, int caseAmount) {
        BattleLobby targetLobby = null;
        for (BattleLobby lobby : activeLobbies.values()) {
            if (lobby.caseName.equals(caseName) && lobby.caseAmount == caseAmount && !lobby.started && !lobby.starting && lobby.players.size() < 4) {
                targetLobby = lobby;
                break;
            }
        }

        if (targetLobby == null) {
            targetLobby = new BattleLobby(caseName, caseAmount);
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
            if (lobby.starting) {
                meta.setDisplayName("§e§lSTARTOWANIE GIERKI...");
            } else {
                meta.setDisplayName("§a§lSTART (" + lobby.caseAmount + " Skrzynek) §7[" + lobby.players.size() + "/4]");
            }
            start.setItemMeta(meta);
        }
        gui.setItem(22, start);

        for (Player p : lobby.players) {
            p.openInventory(gui);
        }
    }

    private void startCountdown(BattleLobby lobby) {
        int minPlayers = getConfig().getInt("settings.min-players", 2);
        if (lobby.players.size() < minPlayers) {
            lobby.players.forEach(p -> p.sendMessage("§cZa mało graczy, aby rozpocząć bitwę! Wymagane: " + minPlayers));
            return;
        }

        lobby.starting = true;

        new BukkitRunnable() {
            int countdown = 5;

            @Override
            public void run() {
                if (countdown > 0) {
                    for (Player p : lobby.players) {
                        p.sendMessage("§eGra wystartuje za §c" + countdown + " §esekund...");
                        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
                    }
                    openLobbyGUI(lobby);
                    countdown--;
                } else {
                    this.cancel();
                    startBattleSequence(lobby);
                }
            }
        }.runTaskTimer(this, 0L, 20L);
    }

    private void startBattleSequence(BattleLobby lobby) {
        lobby.started = true;
        Inventory battleGui = Bukkit.createInventory(null, 54, "§8Losowanie Skrzynek...");

        for (Player p : lobby.players) {
            p.openInventory(battleGui);
        }

        int playerCount = lobby.players.size();
        int[] playerScores = new int[playerCount];
        List<List<ItemStack>> wonItems = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            wonItems.add(new ArrayList<>());
        }

        runRound(lobby, battleGui, 1, lobby.caseAmount, playerScores, wonItems);
    }

    private void runRound(BattleLobby lobby, Inventory battleGui, int currentRound, int totalRounds, int[] playerScores, List<List<ItemStack>> wonItems) {
        int playerCount = lobby.players.size();
        ItemStack[][] columns = new ItemStack[playerCount][3];

        ItemStack purpleGlass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = purpleGlass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            purpleGlass.setItemMeta(glassMeta);
        }

        ItemStack sign = new ItemStack(Material.OAK_HANGING_SIGN);
        ItemMeta signMeta = sign.getItemMeta();
        if (signMeta != null) {
            signMeta.setDisplayName("§e§lRUNDA " + currentRound + "/" + totalRounds);
            sign.setItemMeta(signMeta);
        }

        ItemStack emeraldBlock = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta emMeta = emeraldBlock.getItemMeta();
        if (emMeta != null) {
            emMeta.setDisplayName("§a§lLIDER BITWY");
            emeraldBlock.setItemMeta(emMeta);
        }

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 20;

            @Override
            public void run() {
                ticks++;

                for (int i = 0; i < 54; i++) {
                    battleGui.setItem(i, purpleGlass);
                }

                battleGui.setItem(27, sign);
                battleGui.setItem(35, sign);

                int leadingPlayerIndex = getLeaderIndex(playerScores);

                for (int pIndex = 0; pIndex < playerCount; pIndex++) {
                    Player p = lobby.players.get(pIndex);

                    if (pIndex == leadingPlayerIndex && playerScores[leadingPlayerIndex] > 0) {
                        battleGui.setItem(2 + pIndex, emeraldBlock);
                    }

                    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                    ItemMeta headMeta = head.getItemMeta();
                    if (headMeta != null) {
                        headMeta.setDisplayName("§b" + p.getName() + " §7(Suma: §a" + playerScores[pIndex] + "§7)");
                        head.setItemMeta(headMeta);
                    }
                    battleGui.setItem(11 + pIndex, head);
                }

                for (int pIndex = 0; pIndex < playerCount; pIndex++) {
                    for (int row = 2; row > 0; row--) {
                        columns[pIndex][row] = columns[pIndex][row - 1];
                    }
                    columns[pIndex][0] = getRandomItemFromConfig(lobby.caseName);

                    int baseColumn = 2 + pIndex;
                    for (int row = 0; row < 3; row++) {
                        int slot = ((row + 2) * 9) + baseColumn;
                        if (columns[pIndex][row] != null) {
                            battleGui.setItem(slot, columns[pIndex][row]);
                        }
                    }

                    Player p = lobby.players.get(pIndex);
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.2f);
                }

                if (ticks >= maxTicks) {
                    this.cancel();

                    for (int pIndex = 0; pIndex < playerCount; pIndex++) {
                        ItemStack won = columns[pIndex][1];
                        if (won != null) {
                            wonItems.get(pIndex).add(won);
                            playerScores[pIndex] += getItemValue(won);
                        }
                    }

                    int newLeader = getLeaderIndex(playerScores);
                    for (int pIndex = 0; pIndex < playerCount; pIndex++) {
                        if (pIndex == newLeader && playerScores[newLeader] > 0) {
                            battleGui.setItem(2 + pIndex, emeraldBlock);
                        } else {
                            battleGui.setItem(2 + pIndex, purpleGlass);
                        }

                        Player p = lobby.players.get(pIndex);
                        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                        ItemMeta headMeta = head.getItemMeta();
                        if (headMeta != null) {
                            headMeta.setDisplayName("§b" + p.getName() + " §7(Suma: §a" + playerScores[pIndex] + "§7)");
                            head.setItemMeta(headMeta);
                        }
                        battleGui.setItem(11 + pIndex, head);
                    }

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (currentRound < totalRounds) {
                                runRound(lobby, battleGui, currentRound + 1, totalRounds, playerScores, wonItems);
                            } else {
                                finishBattle(lobby, playerScores, wonItems);
                            }
                        }
                    }.runTaskLater(CaseBattlePlugin.this, 60L);
                }
            }
        }.runTaskTimer(this, 0L, 4L);
    }

    private int getLeaderIndex(int[] scores) {
        int maxIndex = 0;
        for (int i = 1; i < scores.length; i++) {
            if (scores[i] > scores[maxIndex]) {
                maxIndex = i;
            }
        }
        return maxIndex;
    }

    private int getItemValue(ItemStack item) {
        if (item == null) return 1;
        return item.getAmount();
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

    private void finishBattle(BattleLobby lobby, int[] playerScores, List<List<ItemStack>> wonItems) {
        int winnerIndex = getLeaderIndex(playerScores);
        Player winner = lobby.players.get(winnerIndex);

        for (Player p : lobby.players) {
            p.sendMessage("§a★ Bitwę wygrał gracz: §e" + winner.getName() + " §a z punktacją §b" + playerScores[winnerIndex] + "§a!");
            p.sendMessage("§aZgarnia wszystkie przedmioty ze wszystkich " + lobby.caseAmount + " skrzynek!");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        for (List<ItemStack> list : wonItems) {
            for (ItemStack item : list) {
                winner.getInventory().addItem(item);
            }
        }

        activeLobbies.remove(winner.getUniqueId());
    }

    private static class BattleLobby {
        String caseName;
        int caseAmount;
        List<Player> players = new ArrayList<>();
        boolean started = false;
        boolean starting = false;

        BattleLobby(String caseName, int caseAmount) {
            this.caseName = caseName;
            this.caseAmount = caseAmount;
        }
    }
}
