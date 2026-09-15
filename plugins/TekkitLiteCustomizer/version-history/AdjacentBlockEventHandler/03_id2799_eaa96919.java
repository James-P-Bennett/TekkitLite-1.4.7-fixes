package me.ryanhamshire.TekkitCustomizer;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class AdjacentBlockEventHandler implements Listener
{
    @EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
    public void onAdjacentBlockPlace(BlockPlaceEvent placeEvent)
    {
        Player player = placeEvent.getPlayer();
        Block placedBlock = placeEvent.getBlock();

        // Check for adjacent block placement
        if (isAdjacentBlockPlacement(player, placedBlock)) {
            placeEvent.setCancelled(true);
            player.sendMessage("Cannot place two blocks adjacent to each other.");
        }
    }

    // Helper method to check for adjacent block placement
    private boolean isAdjacentBlockPlacement(Player player, Block placedBlock)
    {
        // Your logic to check for adjacent block placement goes here.
        // You can use methods like placedBlock.getRelative() to get neighboring blocks and check their type.
        // For simplicity, let's assume you want to cancel if there's a block to the east of the placed block.
        Block adjacentBlock = placedBlock.getRelative(1, 0, 0);
        return adjacentBlock.getType() != Material.AIR;
    }
}
