package me.ryanhamshire.TekkitCustomizer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BlockPlaceListener implements Listener {

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block placedBlock = event.getBlockPlaced();

        // Check if the placed block is one of the specified blocks
        if (isSpecificBlock(placedBlock, Material.LOG, (byte) 1) ||
                isSpecificBlock(placedBlock, Material.STAINED_GLASS, (byte) 3)) {

            // Check if the placed block is adjacent to another specified block
            if (isAdjacentToSpecificBlock(placedBlock, Material.LOG, (byte) 1) ||
                    isAdjacentToSpecificBlock(placedBlock, Material.STAINED_GLASS, (byte) 3)) {

                // Cancel the event
                event.setCancelled(true);

                // Send error message to the player
                player.sendMessage(ChatColor.RED + "You cannot place these two blocks near each other due to an exploit.");
            }
        }
    }

    private boolean isSpecificBlock(Block block, Material material, byte data) {
        return block.getType() == material && block.getData() == data;
    }

    private boolean isAdjacentToSpecificBlock(Block block, Material material, byte data) {
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                        continue; // Skip the current block
                    }

                    Block adjacentBlock = block.getRelative(xOffset, yOffset, zOffset);
                    if (isSpecificBlock(adjacentBlock, material, data)) {
                        return true; // Found adjacent block
                    }
                }
            }
        }
        return false; // No adjacent block found
    }

    public static void main(String[] args) {
        // Register the listener in your plugin's onEnable() method
        JavaPlugin plugin = ...; // Replace with your actual plugin instance
        Bukkit.getPluginManager().registerEvents(new BlockPlaceListener(), plugin);
    }
}
