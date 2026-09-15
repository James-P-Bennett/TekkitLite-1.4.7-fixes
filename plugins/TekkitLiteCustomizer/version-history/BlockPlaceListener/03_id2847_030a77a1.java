package me.ryanhamshire.TekkitCustomizer;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class BlockPlaceListener implements Listener {

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(new BlockPlaceListener(), this);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block placedBlock = event.getBlockPlaced();

        // Check if the placed block is one of the specified blocks
        if (isSpecificBlock(placedBlock, 763, (byte) 1) ||
                isSpecificBlock(placedBlock, 3131, (byte) 3)) {

            // Check if the placed block is adjacent to another specified block
            if (isAdjacentToSpecificBlock(placedBlock, 763, (byte) 1) ||
                    isAdjacentToSpecificBlock(placedBlock, 3131, (byte) 3)) {

                // Cancel the event
                event.setCancelled(true);

                // Send error message to the player
                player.sendMessage(ChatColor.RED + "You cannot place these two blocks near each other due to an exploit.");
            }
        }
    }

    private boolean isSpecificBlock(Block block, int itemId, byte data) {
        return block.getTypeId() == itemId && block.getData() == data;
    }

    private boolean isAdjacentToSpecificBlock(Block block, int itemId, byte data) {
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                        continue; // Skip the current block
                    }

                    Block adjacentBlock = block.getRelative(xOffset, yOffset, zOffset);
                    if (isSpecificBlock(adjacentBlock, itemId, data)) {
                        return true; // Found adjacent block
                    }
                }
            }
        }
        return false; // No adjacent block found
    }
}
