package me.ryanhamshire.TekkitCustomizer;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import java.util.List;
import java.util.Map;

public class AdjacentBlockEvents implements Listener {

    private final TekkitCustomizer plugin;

    public AdjacentBlockEvents(TekkitCustomizer plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block placedBlock = event.getBlockPlaced();

        // Check if the placed block is in the configured adjacent banned list
        for (Map.Entry<String, List<MaterialInfo>> entry : plugin.config_adjacentBanned.entrySet()) {
            String groupName = entry.getKey();
            List<MaterialInfo> materialInfoList = entry.getValue();

            for (MaterialInfo materialInfo : materialInfoList) {
                Material bannedMaterial = Material.getMaterial(block.getTypeId());

                // Check adjacent blocks
                if (hasAdjacentBannedBlock(placedBlock, bannedMaterial)) {
                    // Prevent block placement
                    event.setCancelled(true);

                    // Send a message to the player with the reason
                    player.sendMessage("You are not allowed to place " + bannedMaterial.toString() +
                            " adjacent to " + groupName + ". Reason: " + materialInfo.getReason());

                    return; // No need to check further once a match is found
                }
            }
        }
    }

    private boolean hasAdjacentBannedBlock(Block placedBlock, Material bannedMaterial) {
        // Check if any adjacent blocks are of the banned type
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue; // Skip the placed block itself
                    }

                    Block adjacentBlock = placedBlock.getRelative(x, y, z);
                    if (adjacentBlock.getType() == bannedMaterial) {
                        return true; // Found an adjacent banned block
                    }
                }
            }
        }
        return false; // No adjacent banned blocks found
    }
}
