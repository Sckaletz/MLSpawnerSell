package eu.minelife.mLSpawnerSell;

import com.bgsoftware.wildstacker.api.WildStackerAPI;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.block.CreatureSpawner;

public final class MLSpawnerSell extends JavaPlugin implements CommandExecutor {

    private Economy economy;
    private FileConfiguration config;

    @Override
    public void onEnable() {
        // Save default config if it doesn't exist
        saveDefaultConfig();
        config = getConfig();

        // Setup Vault economy
        if (!setupEconomy()) {
            getLogger().severe("Vault not found or no economy plugin installed! Disabling plugin.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        // Register commands
        getCommand("sellspawner").setExecutor(this);
        getCommand("spvalue").setExecutor(this);

        getLogger().info("MLSpawnerSell has been enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("MLSpawnerSell has been disabled!");
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }

        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }

        economy = rsp.getProvider();
        return economy != null;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "This command can only be used by players.");
            return true;
        }

        Player player = (Player) sender;

        // Check if player has permission based on the command
        String commandName = command.getName().toLowerCase();
        if (commandName.equals("sellspawner") && !player.hasPermission("mlspawnersell.sell")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        } else if (commandName.equals("spvalue") && !player.hasPermission("mlspawnersell.value")) {
            player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
            return true;
        }

        // Get the item in the player's hand
        ItemStack itemInHand = player.getInventory().getItemInMainHand();

        // Check if the item is a spawner
        if (itemInHand.getType() != Material.SPAWNER) {
            player.sendMessage(ChatColor.RED + "You must be holding a spawner to use this command.");
            return true;
        }

        // Get spawner type
        EntityType spawnerType = getSpawnerType(itemInHand);
        if (spawnerType == null) {
            player.sendMessage(ChatColor.RED + "Could not determine spawner type.");
            return true;
        }

        // Get spawner count (for WildStacker)
        int spawnerCount = 1;
        if (getServer().getPluginManager().isPluginEnabled("WildStacker")) {
            try {
                // Try to get the stack amount from WildStacker
                // WildStacker typically stores stack information in item lore or NBT data

                // First approach: Check if the item has lore that might contain stack info
                if (itemInHand.hasItemMeta() && itemInHand.getItemMeta().hasLore()) {
                    for (String loreLine : itemInHand.getItemMeta().getLore()) {
                        // Common patterns in stacking plugins:
                        // "Amount: X", "Stack: X", "x5", etc.
                        String loreLC = ChatColor.stripColor(loreLine).toLowerCase();

                        // Pattern 1: "Amount: X" or "Stack: X"
                        if (loreLC.contains("amount:") || loreLC.contains("stack:")) {
                            String[] parts = loreLC.split(":");
                            if (parts.length > 1) {
                                String amountStr = parts[1].trim();
                                try {
                                    int amount = Integer.parseInt(amountStr.replaceAll("[^0-9]", ""));
                                    if (amount > 1) {
                                        spawnerCount = amount;
                                        break;
                                    }
                                } catch (NumberFormatException ignored) {
                                    // Not a number, continue checking
                                }
                            }
                        }

                        // Pattern 2: "x5" (common in many stacking plugins)
                        if (loreLC.contains("x")) {
                            String[] parts = loreLC.split("x");
                            if (parts.length > 1) {
                                try {
                                    String amountStr = parts[parts.length - 1].trim();
                                    int amount = Integer.parseInt(amountStr.replaceAll("[^0-9]", ""));
                                    if (amount > 1) {
                                        spawnerCount = amount;
                                        break;
                                    }
                                } catch (NumberFormatException ignored) {
                                    // Not a number, continue checking
                                }
                            }
                        }

                        // Pattern 3: Just a number (some plugins just add the number)
                        try {
                            String amountStr = loreLC.trim();
                            if (amountStr.matches("\\d+")) {
                                int amount = Integer.parseInt(amountStr);
                                if (amount > 1) {
                                    spawnerCount = amount;
                                    break;
                                }
                            }
                        } catch (NumberFormatException ignored) {
                            // Not a number, continue checking
                        }
                    }
                }

                // Second approach: Check if the item has display name that might contain stack info
                if (spawnerCount <= 1 && itemInHand.hasItemMeta() && itemInHand.getItemMeta().hasDisplayName()) {
                    String rawDisplayName = itemInHand.getItemMeta().getDisplayName();
                    String displayName = ChatColor.stripColor(rawDisplayName);

                    // Pattern 1: "Name x5"
                    if (displayName.contains(" x")) {
                        String[] parts = displayName.split(" x");
                        if (parts.length > 1) {
                            try {
                                int amount = Integer.parseInt(parts[parts.length - 1].trim());
                                if (amount > 1) {
                                    spawnerCount = amount;
                                }
                            } catch (NumberFormatException ignored) {
                                // Not a number, continue with other methods
                            }
                        }
                    }

                    // Pattern 2: "Name (5)"
                    if (displayName.contains("(") && displayName.contains(")")) {
                        int openIndex = displayName.lastIndexOf("(");
                        int closeIndex = displayName.lastIndexOf(")");
                        if (openIndex < closeIndex) {
                            try {
                                String amountStr = displayName.substring(openIndex + 1, closeIndex).trim();
                                int amount = Integer.parseInt(amountStr);
                                if (amount > 1) {
                                    spawnerCount = amount;
                                }
                            } catch (NumberFormatException ignored) {
                                // Not a number, continue with other methods
                            }
                        }
                    }

                    // Pattern 3: "2x Pig Spawners" (number at the beginning followed by 'x')
                    if (displayName.matches("^\\d+x.*")) {
                        try {
                            String amountStr = displayName.split("x")[0].trim();
                            int amount = Integer.parseInt(amountStr);
                            if (amount > 1) {
                                spawnerCount = amount;
                            }
                        } catch (NumberFormatException ignored) {
                            // Not a number, continue with other methods
                        }
                    }

                    // Pattern 4: "x3 Piggy Spawners" ('x' followed by a number)
                    if (displayName.matches("^x\\d+.*")) {
                        try {
                            String amountStr = displayName.substring(1).split(" ")[0].trim();
                            int amount = Integer.parseInt(amountStr);
                            if (amount > 1) {
                                spawnerCount = amount;
                            }
                        } catch (NumberFormatException ignored) {
                            // Not a number, continue with other methods
                        }
                    }
                }

                // Third approach: Try to use WildStacker API directly
                if (spawnerCount <= 1) {
                    try {
                        // Try to use reflection to access the API method
                        // This is a last resort approach
                        Object stackedItem = WildStackerAPI.class.getMethod("getStackedItem", ItemStack.class).invoke(null, itemInHand);
                        if (stackedItem != null) {
                            int amount = (int) stackedItem.getClass().getMethod("getStackAmount").invoke(stackedItem);
                            if (amount > 1) {
                                spawnerCount = amount;
                            }
                        }
                    } catch (Exception e) {
                    }
                }

                // Fourth approach: Fall back to item amount if all else fails
                if (spawnerCount <= 1) {
                    spawnerCount = Math.max(itemInHand.getAmount(), 1);
                } else {
                    // Multiply stack size by item amount to get total number of spawners
                    spawnerCount = spawnerCount * itemInHand.getAmount();
                }
            } catch (Exception e) {
                // If there's an error with any of the approaches, fall back to item amount
                spawnerCount = Math.max(itemInHand.getAmount(), 1);
            }
        } else {
            // If WildStacker is not enabled, just use the regular item amount
            spawnerCount = Math.max(itemInHand.getAmount(), 1);
        }

        // Calculate price
        String spawnerTypeName = spawnerType.name();

        // Try to find the spawner type in the config (case-insensitive)
        String configKey = null;

        for (String key : config.getKeys(false)) {
            if (key.equalsIgnoreCase(spawnerTypeName)) {
                configKey = key;
                break;
            }
        }

        if (configKey == null) {
            player.sendMessage(ChatColor.RED + "This spawner type cannot be sold.");
            return true;
        }

        double pricePerSpawner = config.getDoubleList(configKey).get(0);
        double totalPrice = pricePerSpawner * spawnerCount;

        // Handle different commands
        if (commandName.equals("sellspawner")) {
            // Add money to player
            economy.depositPlayer(player, totalPrice);

            // Remove spawner from player's hand
            player.getInventory().setItemInMainHand(null);

            // Send success message
            player.sendMessage(ChatColor.GREEN + "You sold " + spawnerCount + " " + 
                    formatSpawnerName(spawnerTypeName) + " spawner" + (spawnerCount > 1 ? "s" : "") + 
                    " for " + economy.format(totalPrice) + ".");
        } else if (commandName.equals("spvalue")) {
            // Just display the value without selling
            player.sendMessage(ChatColor.GREEN + "Value of " + spawnerCount + " " + 
                    formatSpawnerName(spawnerTypeName) + " spawner" + (spawnerCount > 1 ? "s" : "") + 
                    ": " + economy.format(totalPrice));
        }

        return true;
    }

    private EntityType getSpawnerType(ItemStack spawner) {
        // First try to get the type from lore (for WildStacker spawners)
        if (spawner.hasItemMeta() && spawner.getItemMeta().hasLore()) {
            java.util.List<String> lore = spawner.getItemMeta().getLore();
            if (lore.size() >= 2) {
                // Check the second line of lore which might contain the spawner type
                String secondLine = ChatColor.stripColor(lore.get(1)).trim();
                // Try to parse the second line as an entity type
                try {
                    EntityType entityType = EntityType.valueOf(secondLine.toUpperCase());
                    return entityType;
                } catch (IllegalArgumentException e) {
                    // Continue to the next method if this fails
                }
            }
        }

        // Fall back to the standard method of getting the spawner type
        if (spawner.getItemMeta() instanceof BlockStateMeta) {
            BlockStateMeta meta = (BlockStateMeta) spawner.getItemMeta();
            if (meta.getBlockState() instanceof CreatureSpawner) {
                CreatureSpawner creatureSpawner = (CreatureSpawner) meta.getBlockState();
                return creatureSpawner.getSpawnedType();
            }
        }
        return null;
    }

    private String formatSpawnerName(String name) {
        String[] parts = name.split("_");
        StringBuilder result = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                result.append(part.substring(0, 1).toUpperCase())
                      .append(part.substring(1).toLowerCase())
                      .append(" ");
            }
        }

        return result.toString().trim();
    }
}
