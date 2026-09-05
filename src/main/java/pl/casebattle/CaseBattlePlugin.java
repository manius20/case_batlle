package pl.casebattle;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class CaseBattlePlugin extends JavaPlugin implements Listener {

    private final Map<UUID, BattleLobby> activeLobbies = new HashMap<>();
    private final Map<UUID, String> selectedCaseName = new HashMap<>();
    private final Map<UUID, BattleMode> selectedMode = new HashMap<>();
    private final Random random = new Random();

    public enum BattleMode {
        CLASSIC("Classic", "§aWygrywa gracz z największą sumą wartości przedmiotów.", Material.DIAMOND),
        UNDERDOG("Underdog", "§cWygrywa gracz z NAJMNIEJSZĄ sumą wartości przedmiotów.", Material.LEATHER_BOOTS),
        POINT_RUSH("Point Rush", "§eKażda wygrana runda daje 1 pkt. Wygrywa gracz z największą ilością punktów.", Material.GOLDEN_SWORD),
        TERMINAL("Terminal", "§bO wygranej decyduje WYŁĄCZNIE drop z ostatniej skrzynki.", Material.TNT);

        private final String displayName;
        private final String description;
        private final Material iconMaterial;

        BattleMode(String displayName, String description, Material iconMaterial) {
            this.displayName = displayName;
            this.description = description;
            this.iconMaterial = iconMaterial;
        }

        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
        public Material getIconMaterial() { return iconMaterial; }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getServer().getPluginManager().registerEvents(this, this);
        
        getCommand("bitwa").setExecutor((sender, command, label, args) -> {
            if (sender instanceof Player p) {
                if (args.length == 2 && args[0].equalsIgnoreCase("dolacz")) {
                    joinLobbyByOwner(p, args[1]);
                } else {
                    openMainMenu(p);
                }
            }
            return true;
        });
    }

    private ItemStack getPlayerHead(Player player, String displayName) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName(displayName);
            head.setItemMeta(meta);
        }
        return head;
    }

    // 1. GŁÓWNE MENU
    public void openMainMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§8Bitwy Skrzynek - Lista");

        ItemStack purpleGlass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = purpleGlass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            purpleGlass.setItemMeta(glassMeta);
        }

        for (int i = 0; i < 54; i++) {
            gui.setItem(i, purpleGlass);
        }

        ItemStack infoBook = new ItemStack(Material.BOOK);
        ItemMeta bookMeta = infoBook.getItemMeta();
        if (bookMeta != null) {
            bookMeta.setDisplayName("§e§lInformacje o Case Battle");
            bookMeta.setLore(Arrays.asList(
                "§7Czym jest Case Battle?",
                "§fOtwieranie skrzynek w czasie rzeczywistym",
                "§fz połączoną rywalizacją z innymi graczami!",
                "",
                "§7Waluta gry:",
                "§f• Płacisz §bZWYKŁYMI DIAMENTAMI §fz ekwipunku",
                "§f  (Bloki diamentów nie są akceptowane).",
                "",
                "§7Tryby gry:",
                "§f• §aClassic §7| §cUnderdog §7| §ePoint Rush §7| §bTerminal",
                "",
                "§7Opcje w menu:",
                "§f• §aStwórz bitwę §7- Wybierz skrzynkę, tryb i ilość.",
                "§f• §eOglądaj §7- Śledź bitwy innych graczy na żywo."
            ));
            infoBook.setItemMeta(bookMeta);
        }
        gui.setItem(0, infoBook);

        List<Integer> centerSlots = new ArrayList<>();
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 7; col++) {
                int slot = row * 9 + col;
                gui.setItem(slot, null);
                centerSlots.add(slot);
            }
        }

        int slotIndex = 0;
        for (BattleLobby lobby : activeLobbies.values()) {
            if (!lobby.started && !lobby.starting && lobby.players.size() < 6) {
                if (slotIndex >= centerSlots.size()) break;

                ItemStack lobbyItem = new ItemStack(Material.CHEST);
                ItemMeta meta = lobbyItem.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§aBitwa gracza: " + lobby.ownerName);
                    meta.setLore(Arrays.asList(
                        "§7Skrzynka: §e" + lobby.caseName,
                        "§7Tryb: §d" + lobby.mode.getDisplayName(),
                        "§7Ilość skrzynek: §b" + lobby.caseAmount,
                        "§7Gracze: §a" + lobby.players.size() + "/6",
                        "",
                        "§eKliknij, aby dołączyć do tej bitwy!"
                    ));
                    lobbyItem.setItemMeta(meta);
                }
                gui.setItem(centerSlots.get(slotIndex++), lobbyItem);
            }
        }

        ItemStack watchItem = new ItemStack(Material.ENDER_EYE);
        ItemMeta watchMeta = watchItem.getItemMeta();
        if (watchMeta != null) {
            watchMeta.setDisplayName("§e§lOglądaj trwające bitwy");
            watchMeta.setLore(Arrays.asList(
                "§7Kliknij, aby zobaczyć listę",
                "§7bitew, które są w trakcie losowania!"
            ));
            watchItem.setItemMeta(watchMeta);
        }
        gui.setItem(8, watchItem);

        int diamonds = countDiamonds(player);
        ItemStack emeraldBlock = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta emMeta = emeraldBlock.getItemMeta();
        if (emMeta != null) {
            emMeta.setDisplayName("§a§lTwoje Diamenty: §b" + diamonds);
            emMeta.setLore(Arrays.asList(
                "§7Łączna ilość diamentów w ekwipunku,",
                "§7które możesz przeznaczyć na bitwy."
            ));
            emeraldBlock.setItemMeta(emMeta);
        }
        gui.setItem(45, emeraldBlock);

        ItemStack createPaper = new ItemStack(Material.PAPER);
        ItemMeta paperMeta = createPaper.getItemMeta();
        if (paperMeta != null) {
            paperMeta.setDisplayName("§a§lStwórz bitwę");
            paperMeta.setLore(Arrays.asList(
                "§7Kliknij tutaj, aby wybrać skrzynkę",
                "§7i utworzyć nową bitwę!"
            ));
            createPaper.setItemMeta(paperMeta);
        }
        gui.setItem(53, createPaper);

        player.openInventory(gui);
    }

    public void openActiveBattlesMenu(Player player) {
        Inventory gui = Bukkit.createInventory(null, 54, "§8Trwające Bitwy (Widz)");

        ItemStack purpleGlass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = purpleGlass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            purpleGlass.setItemMeta(glassMeta);
        }

        for (int i = 0; i < 54; i++) {
            gui.setItem(i, purpleGlass);
        }

        int slot = 10;
        for (BattleLobby lobby : activeLobbies.values()) {
            if (lobby.started && lobby.activeGui != null) {
                if (slot >= 44) break;
                if (slot % 9 == 8 || slot % 9 == 0) slot += 2;

                ItemStack item = new ItemStack(Material.ENDER_EYE);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§eBitwa w trakcie: " + lobby.ownerName);
                    meta.setLore(Arrays.asList(
                        "§7Skrzynka: §e" + lobby.caseName,
                        "§7Tryb: §d" + lobby.mode.getDisplayName(),
                        "§7Ilość skrzynek: §b" + lobby.caseAmount,
                        "§7Uczestnicy: §a" + lobby.players.size() + " graczy",
                        "",
                        "§aKliknij, aby oglądać na żywo!"
                    ));
                    item.setItemMeta(meta);
                }
                gui.setItem(slot++, item);
            }
        }

        player.openInventory(gui);
    }

    public void openCaseSelectionMenu(Player player) {
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
                    "§eKliknij, aby wybrać tryb gry!"
                ));
                item.setItemMeta(meta);
            }
            gui.setItem(slot++, item);
        }
        player.openInventory(gui);
    }

    public void openModeSelectionMenu(Player player, String caseName) {
        selectedCaseName.put(player.getUniqueId(), caseName);
        Inventory gui = Bukkit.createInventory(null, 27, "§8Wybierz tryb bitwy");

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 27; i++) {
            gui.setItem(i, filler);
        }

        gui.setItem(10, createModeItem(BattleMode.CLASSIC.getIconMaterial(), "§a§lCLASSIC", BattleMode.CLASSIC));
        gui.setItem(12, createModeItem(BattleMode.UNDERDOG.getIconMaterial(), "§c§lUNDERDOG", BattleMode.UNDERDOG));
        gui.setItem(14, createModeItem(BattleMode.POINT_RUSH.getIconMaterial(), "§e§lPOINT RUSH", BattleMode.POINT_RUSH));
        gui.setItem(16, createModeItem(BattleMode.TERMINAL.getIconMaterial(), "§b§lTERMINAL", BattleMode.TERMINAL));

        player.openInventory(gui);
    }

    private ItemStack createModeItem(Material mat, String title, BattleMode mode) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(title);
            meta.setLore(Arrays.asList(
                mode.getDescription(),
                "",
                "§eKliknij, aby wybrać ten tryb!"
            ));
            item.setItemMeta(meta);
        }
        return item;
    }

    public void openAmountSelectionMenu(Player player) {
        String caseName = selectedCaseName.get(player.getUniqueId());
        BattleMode mode = selectedMode.get(player.getUniqueId());
        if (caseName == null || mode == null) return;

        Inventory gui = Bukkit.createInventory(null, 54, "§8Wybierz ilość skrzynek (1-35)");

        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        if (fillerMeta != null) {
            fillerMeta.setDisplayName(" ");
            filler.setItemMeta(fillerMeta);
        }
        for (int i = 0; i < 54; i++) {
            gui.setItem(i, filler);
        }

        int singleCost = getCaseCost(caseName);
        
        int currentAmount = 1;
        for (int row = 1; row <= 4; row++) {
            for (int col = 1; col <= 8; col++) {
                if (currentAmount > 35) break;
                
                int slot = row * 9 + (col - 1);
                ItemStack item = new ItemStack(Material.CHEST, currentAmount);
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    meta.setDisplayName("§a§l" + currentAmount + " Skrzynek");
                    meta.setLore(Arrays.asList(
                        "§7Tryb: §d" + mode.getDisplayName(),
                        "§7Koszt całkowity: §b" + (singleCost * currentAmount) + " Diamentów",
                        "",
                        "§eKliknij, aby utworzyć bitwę!"
                    ));
                    item.setItemMeta(meta);
                }
                gui.setItem(slot, item);
                currentAmount++;
            }
        }

        player.openInventory(gui);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String title = event.getView().getTitle();

        if (title.equals("§8Bitwy Skrzynek - Lista")) {
            event.setCancelled(true);
            int slot = event.getRawSlot();

            if (slot == 8) { openActiveBattlesMenu(player); return; }
            if (slot == 53) { openCaseSelectionMenu(player); return; }

            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.CHEST && clicked.hasItemMeta()) {
                String displayName = clicked.getItemMeta().getDisplayName();
                if (displayName.startsWith("§aBitwa gracza: ")) {
                    String ownerName = displayName.replace("§aBitwa gracza: ", "");
                    joinLobbyByOwner(player, ownerName);
                }
            }

        } else if (title.equals("§8Trwające Bitwy (Widz)")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked != null && clicked.getType() == Material.ENDER_EYE && clicked.hasItemMeta()) {
                String displayName = clicked.getItemMeta().getDisplayName();
                if (displayName.startsWith("§eBitwa w trakcie: ")) {
                    String ownerName = displayName.replace("§eBitwa w trakcie: ", "");
                    for (BattleLobby lobby : activeLobbies.values()) {
                        if (lobby.ownerName.equalsIgnoreCase(ownerName) && lobby.started && lobby.activeGui != null) {
                            player.openInventory(lobby.activeGui);
                            player.sendMessage("§aOglądasz bitwę gracza " + ownerName + "!");
                            break;
                        }
                    }
                }
            }

        } else if (title.equals("§8Wybierz skrzynkę do bitwy")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.CHEST) return;

            String caseName = clicked.getItemMeta().getDisplayName();
            openModeSelectionMenu(player, caseName);

        } else if (title.equals("§8Wybierz tryb bitwy")) {
            event.setCancelled(true);
            int slot = event.getRawSlot();

            if (slot == 10) selectedMode.put(player.getUniqueId(), BattleMode.CLASSIC);
            else if (slot == 12) selectedMode.put(player.getUniqueId(), BattleMode.UNDERDOG);
            else if (slot == 14) selectedMode.put(player.getUniqueId(), BattleMode.POINT_RUSH);
            else if (slot == 16) selectedMode.put(player.getUniqueId(), BattleMode.TERMINAL);
            else return;

            openAmountSelectionMenu(player);

        } else if (title.equals("§8Wybierz ilość skrzynek (1-35)")) {
            event.setCancelled(true);
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType() != Material.CHEST) return;

            int amount = clicked.getAmount();
            String caseName = selectedCaseName.get(player.getUniqueId());
            BattleMode mode = selectedMode.get(player.getUniqueId());
            if (caseName == null || mode == null) return;

            int totalCost = getCaseCost(caseName) * amount;

            if (!hasEnoughDiamonds(player, totalCost)) {
                player.sendMessage("§cNie masz wystarczająco diamentów! Wymagane: §b" + totalCost);
                player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                return;
            }

            removeDiamonds(player, totalCost);
            player.sendMessage("§aPobrano §b" + totalCost + " §adiamentów za " + amount + " skrzynek.");
            createNewLobby(player, caseName, mode, amount);

        } else if (title.startsWith("§8Poczekalnia Bitwy:")) {
            event.setCancelled(true);
            if (event.getRawSlot() == 22) {
                BattleLobby lobby = getLobbyByPlayer(player);
                if (lobby != null && !lobby.started && !lobby.starting) {
                    startCountdown(lobby);
                }
            }
        } else if (title.startsWith("§8Losowanie Skrzynek...") || title.startsWith("§8Dogrywka (Tie-Break)...")) {
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

    private int countDiamonds(Player player) {
        int count = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == Material.DIAMOND) {
                count += stack.getAmount();
            }
        }
        return count;
    }

    private boolean hasEnoughDiamonds(Player player, int amount) {
        return countDiamonds(player) >= amount;
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

    private void createNewLobby(Player player, String caseName, BattleMode mode, int caseAmount) {
        BattleLobby lobby = new BattleLobby(player.getName(), caseName, mode, caseAmount);
        lobby.players.add(player);
        activeLobbies.put(player.getUniqueId(), lobby);
        openLobbyGUI(lobby);

        TextComponent msg = new TextComponent("§a★ Gracz §e" + player.getName() + " §astworzył bitwę! ");
        TextComponent button = new TextComponent("§b§l[DOŁĄCZ DO BITWY]");
        button.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text("§7Kliknij, aby dołączyć do bitwy!")));
        button.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/bitwa dolacz " + player.getName()));
        msg.addExtra(button);

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.spigot().sendMessage(msg);
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.2f);
        }
    }

    private void joinLobbyByOwner(Player player, String ownerName) {
        BattleLobby targetLobby = null;
        for (BattleLobby lobby : activeLobbies.values()) {
            if (lobby.ownerName.equalsIgnoreCase(ownerName) && !lobby.started && !lobby.starting && lobby.players.size() < 6) {
                targetLobby = lobby;
                break;
            }
        }

        if (targetLobby == null) {
            player.sendMessage("§cTa bitwa nie jest już dostępna lub jest pełna.");
            return;
        }

        if (targetLobby.players.contains(player)) {
            player.sendMessage("§cJesteś już w tej bitwie!");
            return;
        }

        int totalCost = getCaseCost(targetLobby.caseName) * targetLobby.caseAmount;
        if (!hasEnoughDiamonds(player, totalCost)) {
            player.sendMessage("§cNie masz wystarczająco diamentów, aby dołączyć! Wymagane: §b" + totalCost);
            player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            return;
        }

        removeDiamonds(player, totalCost);
        player.sendMessage("§aPobrano §b" + totalCost + " §adiamentów i dołączono do bitwy!");
        targetLobby.players.add(player);

        openLobbyGUI(targetLobby);

        if (targetLobby.players.size() == 6 && !targetLobby.starting) {
            startCountdown(targetLobby);
        }
    }

    private BattleLobby getLobbyByPlayer(Player player) {
        for (BattleLobby lobby : activeLobbies.values()) {
            if (lobby.players.contains(player)) {
                return lobby;
            }
        }
        return null;
    }

    private void openLobbyGUI(BattleLobby lobby) {
        Inventory gui = Bukkit.createInventory(null, 27, "§8Poczekalnia Bitwy: " + lobby.caseName);

        for (int i = 0; i < lobby.players.size(); i++) {
            Player p = lobby.players.get(i);
            ItemStack head = getPlayerHead(p, "§aGracz: " + p.getName());
            gui.setItem(10 + i, head);
        }

        ItemStack start = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta meta = start.getItemMeta();
        if (meta != null) {
            if (lobby.starting) {
                meta.setDisplayName("§e§lSTARTOWANIE GIERKI...");
            } else {
                meta.setDisplayName("§a§lSTART (" + lobby.caseAmount + " Skrzynek, " + lobby.mode.getDisplayName() + ") §7[" + lobby.players.size() + "/6]");
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
        lobby.activeGui = battleGui;

        for (Player p : lobby.players) {
            p.openInventory(battleGui);
        }

        int playerCount = lobby.players.size();
        int[] playerScores = new int[playerCount];
        int[] terminalValues = new int[playerCount];
        List<List<ItemStack>> wonItems = new ArrayList<>();
        for (int i = 0; i < playerCount; i++) {
            wonItems.add(new ArrayList<>());
        }

        runRound(lobby, battleGui, 1, lobby.caseAmount, playerScores, terminalValues, wonItems);
    }

    private void runRound(BattleLobby lobby, Inventory battleGui, int currentRound, int totalRounds, int[] playerScores, int[] terminalValues, List<List<ItemStack>> wonItems) {
        int playerCount = lobby.players.size();
        ItemStack[][] columns = new ItemStack[playerCount][4];

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

        ItemStack modeItem = new ItemStack(lobby.mode.getIconMaterial());
        ItemMeta modeMeta = modeItem.getItemMeta();
        if (modeMeta != null) {
            modeMeta.setDisplayName("§d§lTryb: " + lobby.mode.getDisplayName());
            modeMeta.setLore(Arrays.asList(
                lobby.mode.getDescription()
            ));
            modeItem.setItemMeta(modeMeta);
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

                battleGui.setItem(8, modeItem);

                List<Integer> leaders = getLeadersIndices(lobby.mode, playerScores, terminalValues);

                for (int pIndex = 0; pIndex < playerCount; pIndex++) {
                    Player p = lobby.players.get(pIndex);

                    int baseColumn = 1 + pIndex;

                    if (leaders.contains(pIndex) && leaders.size() == 1) {
                        battleGui.setItem(baseColumn, emeraldBlock);
                    }

                    ItemStack head = getPlayerHead(p, "§b" + p.getName() + " §7(Wynik: §a" + playerScores[pIndex] + "§7)");
                    battleGui.setItem(9 + baseColumn, head);
                }

                for (int pIndex = 0; pIndex < playerCount; pIndex++) {
                    for (int row = 3; row > 0; row--) {
                        columns[pIndex][row] = columns[pIndex][row - 1];
                    }
                    columns[pIndex][0] = getRandomItemFromConfig(lobby.caseName);

                    int baseColumn = 1 + pIndex;
                    for (int row = 0; row < 4; row++) {
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

                    int[] roundItemValues = new int[playerCount];
                    int maxRoundValue = -1;

                    for (int pIndex = 0; pIndex < playerCount; pIndex++) {
                        ItemStack won = columns[pIndex][2]; 
                        if (won != null) {
                            wonItems.get(pIndex).add(won);
                            int val = getItemValue(won);
                            roundItemValues[pIndex] = val;

                            if (lobby.mode == BattleMode.CLASSIC || lobby.mode == BattleMode.UNDERDOG) {
                                playerScores[pIndex] += val;
                            } else if (lobby.mode == BattleMode.TERMINAL) {
                                if (currentRound == totalRounds) {
                                    terminalValues[pIndex] = val;
                                    playerScores[pIndex] = val;
                                }
                            }

                            if (val > maxRoundValue) {
                                maxRoundValue = val;
                            }
                        }
                    }

                    if (lobby.mode == BattleMode.POINT_RUSH) {
                        for (int pIndex = 0; pIndex < playerCount; pIndex++) {
                            if (roundItemValues[pIndex] == maxRoundValue && maxRoundValue > 0) {
                                playerScores[pIndex]++;
                            }
                        }
                    }

                    List<Integer> newLeaders = getLeadersIndices(lobby.mode, playerScores, terminalValues);
                    for (int pIndex = 0; pIndex < playerCount; pIndex++) {
                        int baseColumn = 1 + pIndex;
                        if (newLeaders.contains(pIndex) && newLeaders.size() == 1) {
                            battleGui.setItem(baseColumn, emeraldBlock);
                        } else {
                            battleGui.setItem(baseColumn, purpleGlass);
                        }

                        Player p = lobby.players.get(pIndex);
                        ItemStack head = getPlayerHead(p, "§b" + p.getName() + " §7(Wynik: §a" + playerScores[pIndex] + "§7)");
                        battleGui.setItem(9 + baseColumn, head);
                    }

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            if (currentRound < totalRounds) {
                                runRound(lobby, battleGui, currentRound + 1, totalRounds, playerScores, terminalValues, wonItems);
                            } else {
                                checkBattleWinnerOrTie(lobby, playerScores, terminalValues, wonItems);
                            }
                        }
                    }.runTaskLater(CaseBattlePlugin.this, 60L);
                }
            }
        }.runTaskTimer(this, 0L, 4L);
    }

    private List<Integer> getLeadersIndices(BattleMode mode, int[] scores, int[] terminalValues) {
        List<Integer> leaders = new ArrayList<>();
        int[] targetArray = (mode == BattleMode.TERMINAL) ? terminalValues : scores;

        if (targetArray.length == 0) return leaders;

        int bestValue = targetArray[0];
        for (int val : targetArray) {
            if (mode == BattleMode.UNDERDOG) {
                if (val < bestValue) bestValue = val;
            } else {
                if (val > bestValue) bestValue = val;
            }
        }

        for (int i = 0; i < targetArray.length; i++) {
            if (targetArray[i] == bestValue) {
                leaders.add(i);
            }
        }
        return leaders;
    }

    private void checkBattleWinnerOrTie(BattleLobby lobby, int[] playerScores, int[] terminalValues, List<List<ItemStack>> wonItems) {
        List<Integer> tiedWinners = getLeadersIndices(lobby.mode, playerScores, terminalValues);

        if (tiedWinners.size() == 1) {
            finishBattle(lobby, tiedWinners.get(0), wonItems, false);
        } else {
            runTieBreaker(lobby, tiedWinners, wonItems);
        }
    }

    private void runTieBreaker(BattleLobby lobby, List<Integer> tiedIndices, List<List<ItemStack>> wonItems) {
        Inventory battleGui = lobby.activeGui;
        if (battleGui == null) return;

        for (Player p : lobby.players) {
            p.sendMessage("§e★ Mamy REMIS! Rozpoczyna się losowanie dogrywki (Tie-Break)...");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.0f);
        }

        ItemStack emeraldBlock = new ItemStack(Material.EMERALD_BLOCK);
        ItemMeta emMeta = emeraldBlock.getItemMeta();
        if (emMeta != null) {
            emMeta.setDisplayName("§a§lLOSOWANIE ZWYCIĘZCY");
            emeraldBlock.setItemMeta(emMeta);
        }

        ItemStack purpleGlass = new ItemStack(Material.PURPLE_STAINED_GLASS_PANE);
        ItemMeta glassMeta = purpleGlass.getItemMeta();
        if (glassMeta != null) {
            glassMeta.setDisplayName(" ");
            purpleGlass.setItemMeta(glassMeta);
        }

        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = 30;

            @Override
            public void run() {
                ticks++;

                for (int i = 0; i < lobby.players.size(); i++) {
                    battleGui.setItem(1 + i, purpleGlass);
                }

                int highlightedPlayerIndex = tiedIndices.get(random.nextInt(tiedIndices.size()));
                battleGui.setItem(1 + highlightedPlayerIndex, emeraldBlock);

                for (Player p : lobby.players) {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.8f, 1.5f);
                }

                if (ticks >= maxTicks) {
                    this.cancel();

                    int finalWinnerIndex = tiedIndices.get(random.nextInt(tiedIndices.size()));

                    for (int i = 0; i < lobby.players.size(); i++) {
                        battleGui.setItem(1 + i, purpleGlass);
                    }
                    battleGui.setItem(1 + finalWinnerIndex, emeraldBlock);

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            finishBattle(lobby, finalWinnerIndex, wonItems, true);
                        }
                    }.runTaskLater(CaseBattlePlugin.this, 20L);
                }
            }
        }.runTaskTimer(this, 0L, 2L);
    }

    private int getItemValue(ItemStack item) {
        if (item == null) return 0;

        int valuePerItem = switch (item.getType()) {
            case DIAMOND -> 100;
            case IRON_INGOT -> 10;
            case GOLD_INGOT -> 25;
            case EMERALD -> 50;
            case NETHERITE_INGOT -> 250;
            default -> 1;
        };

        return valuePerItem * item.getAmount();
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

                        if (meta != null) {
                            if (itemMap.containsKey("display-name")) {
                                meta.setDisplayName(((String) itemMap.get("display-name")).replace("&", "§"));
                            }

                            if (itemMap.containsKey("enchantments")) {
                                Map<?, ?> enchants = (Map<?, ?>) itemMap.get("enchantments");
                                for (Map.Entry<?, ?> entry : enchants.entrySet()) {
                                    String enchName = entry.getKey().toString().toLowerCase();
                                    int level = ((Number) entry.getValue()).intValue();
                                    
                                    Enchantment enchantment = Enchantment.getByKey(NamespacedKey.minecraft(enchName));
                                    if (enchantment != null) {
                                        meta.addEnchant(enchantment, level, true);
                                    }
                                }
                            }

                            if (meta instanceof PotionMeta potionMeta && itemMap.containsKey("potion_type")) {
                                try {
                                    PotionType type = PotionType.valueOf((String) itemMap.get("potion_type"));
                                    potionMeta.setBasePotionType(type);
                                } catch (Exception ignored) {}
                            }

                            stack.setItemMeta(meta);
                        }
                        return stack;
                    }
                }
            }
        }
        return new ItemStack(Material.DIRT);
    }

    private void finishBattle(BattleLobby lobby, int winnerIndex, List<List<ItemStack>> wonItems, boolean tieBroken) {
        Player winner = lobby.players.get(winnerIndex);

        for (Player p : lobby.players) {
            if (tieBroken) {
                p.sendMessage("§a★ Bitwę po dogrywce (Tie-Break) wygrał gracz: §e" + winner.getName() + "§a!");
            } else {
                p.sendMessage("§a★ Bitwę w trybie §d" + lobby.mode.getDisplayName() + " §awygrał gracz: §e" + winner.getName() + "§a!");
            }
            p.sendMessage("§aZgarnia wszystkie przedmioty ze wszystkich " + lobby.caseAmount + " skrzynek!");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        for (List<ItemStack> list : wonItems) {
            for (ItemStack item : list) {
                winner.getInventory().addItem(item);
            }
        }

        activeLobbies.entrySet().removeIf(entry -> entry.getValue().equals(lobby));
    }

    private static class BattleLobby {
        String ownerName;
        String caseName;
        BattleMode mode;
        int caseAmount;
        List<Player> players = new ArrayList<>();
        boolean started = false;
        boolean starting = false;
        Inventory activeGui = null;

        BattleLobby(String ownerName, String caseName, BattleMode mode, int caseAmount) {
            this.ownerName = ownerName;
            this.caseName = caseName;
            this.mode = mode;
            this.caseAmount = caseAmount;
        }
    }
}
