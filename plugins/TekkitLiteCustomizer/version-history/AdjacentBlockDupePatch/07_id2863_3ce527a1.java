package me.ryanhamshire.TekkitCustomizer;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class AdjacentBlockDupePatch implements Listener {

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent placeEvent) {
        Player player = placeEvent.getPlayer();
        Block placedBlock = placeEvent.getBlockPlaced();

        // Check if the placed block is one of the specified blocks
        if (isSpecificBlock(placedBlock, 763, (byte) 1) ||
                isSpecificBlock(placedBlock, 3131, (byte) 3)) {

            // Check if any adjacent block has the same type
            if (hasAdjacentBlockSameType(placedBlock)) {
                // Cancel the event
                placeEvent.setCancelled(true);

                // Send error message to the player
                player.sendMessage(ChatColor.RED + "You cannot place these two blocks near each other due to an exploit.");
            }
        }
    }

    private boolean isSpecificBlock(Block block, int itemId, byte data) {
        return block.getTypeId() == itemId && block.getData() == data;
    }

    private boolean hasAdjacentBlockSameType(Block block) {
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                        continue; // Skip the current block
                    }

                    Block adjacentBlock = block.getRelative(xOffset, yOffset, zOffset);

                    // Check if the adjacent block has the same type
                    if (adjacentBlock.getType() == block.getType() && adjacentBlock.getData() == block.getData()) {
                        return true; // Found adjacent block with the same type
                    }
                }
            }
        }
        return false; // No adjacent block with the same type found
    }
}
