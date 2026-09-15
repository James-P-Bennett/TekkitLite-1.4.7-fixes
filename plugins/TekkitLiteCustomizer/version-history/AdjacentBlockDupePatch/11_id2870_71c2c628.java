package me.ryanhamshire.TekkitCustomizer;

import org.bukkit.ChatColor;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.HashMap;
import java.util.Map;

public class AdjacentBlockDupePatch implements Listener {

    private static final Map<String, String> itemIdentifiers = new HashMap<>();

    static {
        // Assign values to each item
        itemIdentifiers.put("763:1", "x");
        itemIdentifiers.put("3131:3", "y");
        // Add more items as needed
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onBlockPlace(BlockPlaceEvent placeEvent) {
        Player player = placeEvent.getPlayer();
        Block placedBlock = placeEvent.getBlockPlaced();

        // Get the identifier for the placed block
        String placedIdentifier = getIdentifier(placedBlock.getTypeId(), placedBlock.getData());

        // Check if the placed block has an identifier
        if (placedIdentifier != null) {

            // Check if the placed block is adjacent to another block with a different identifier
            if (isAdjacentToDifferentIdentifier(placedBlock, placedIdentifier)) {

                // Cancel the event
                placeEvent.setCancelled(true);

                // Send error message to the player
                player.sendMessage(ChatColor.RED + "You cannot place these two blocks near each other due to an exploit.");
            }
        }
    }

    private String getIdentifier(int itemId, byte data) {
        // Build a unique identifier string for the item
        String identifier = itemId + ":" + data;
        // Check if the item has an assigned identifier
        return itemIdentifiers.get(identifier);
    }

    private boolean isAdjacentToDifferentIdentifier(Block block, String placedIdentifier) {
        for (int xOffset = -1; xOffset <= 1; xOffset++) {
            for (int yOffset = -1; yOffset <= 1; yOffset++) {
                for (int zOffset = -1; zOffset <= 1; zOffset++) {
                    if (xOffset == 0 && yOffset == 0 && zOffset == 0) {
                        continue; // Skip the current block
                    }

                    Block adjacentBlock = block.getRelative(xOffset, yOffset, zOffset);

                    // Get the identifier for the adjacent block
                    String adjacentIdentifier = getIdentifier(adjacentBlock.getTypeId(), adjacentBlock.getData());

                    // Check if the adjacent block has a different identifier
                    if (adjacentIdentifier != null && !adjacentIdentifier.equals(placedIdentifier)) {
                        return true; // Found adjacent block with different identifier
                    }
                }
            }
        }
        return false; // No adjacent block with different identifier found
    }
}

