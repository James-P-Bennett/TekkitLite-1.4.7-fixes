package me.ryanhamshire.TekkitCustomizer;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class AdjacentBlockEvents implements Listener {

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block placedBlock = event.getBlockPlaced();
        Block againstBlock = placedBlock.getRelative(event.getBlockAgainst());

        int placedTypeId = placedBlock.getTypeId();
        byte placedData = placedBlock.getData();
        int againstTypeId = againstBlock.getTypeId();
        byte againstData = againstBlock.getData();

        for (Map.Entry<String, List<MaterialInfo>> entry : plugin.getConfigAdjacentBanned().entrySet()) {
            String groupName = entry.getKey();
            List<MaterialInfo> bannedMaterials = entry.getValue();

            for (MaterialInfo materialInfo : bannedMaterials) {
                if (materialInfo.matches(placedTypeId, placedData) && materialInfo.matches(againstTypeId, againstData)) {
                    // Cancel the event
                    event.setCancelled(true);
                    // Get the reason from the config and send it to the player
                    String reason = plugin.getConfig().getString("TekkitCustomizer.Bans.AdjacentBanned." + groupName);
                    if (reason != null && !reason.isEmpty()) {
                        event.getPlayer().sendMessage(reason);
                    }
                    return; // No need to check further if already matched
                }
            }
        }
    }
}
