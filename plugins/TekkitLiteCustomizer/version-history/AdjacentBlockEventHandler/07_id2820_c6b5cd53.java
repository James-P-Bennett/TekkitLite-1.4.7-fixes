package me.ryanhamshire.TekkitCustomizer;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AdjacentBlockEventHandler implements Listener
{
    private Set<AdjacentBlockGroup> bannedGroups;
    private MaterialCollection adjacentBlockBanned = new MaterialCollection();

    private int parseTypeId(String typeString)
    {
        try
        {
            return Integer.parseInt(typeString);
        }
        catch (NumberFormatException e)
        {
            Material material = Material.matchMaterial(typeString.toUpperCase());
            return material != null ? material.getId() : 0;
        }
    }

    private byte parseData(String dataString)
    {
        try
        {
            return Byte.parseByte(dataString);
        }
        catch (NumberFormatException e)
        {
            return 0;
        }
    }

    private boolean isAdjacentBlockPlacement(Player player, Block placedBlock)
    {
        for (AdjacentBlockGroup group : bannedGroups)
        {
            if (group.contains(placedBlock.getTypeId(), placedBlock.getData()))
            {
                // Check if adjacent block is also in the banned group
                for (Block adjacentBlock : getAdjacentBlocks(placedBlock))
                {
                    if (group.contains(adjacentBlock.getTypeId(), adjacentBlock.getData()))
                    {
                        player.sendMessage("Cannot place " + placedBlock.getType().name() + " adjacent to " + adjacentBlock.getType().name() + "!");
                        return true;
                    }
                }
            }
        }
        return false;
    }

    // Now we do some mafs
    // Check all six faces around the center block
    private Set<Block> getAdjacentBlocks(Block center)
    {
        Set<Block> adjacentBlocks = new HashSet<>();
        for (int xOffset = -1; xOffset <= 1; xOffset++)
        {
            for (int yOffset = -1; yOffset <= 1; yOffset++)
            {
                for (int zOffset = -1; zOffset <= 1; zOffset++)
                {
                    if (xOffset != 0 || yOffset != 0 || zOffset != 0)
                    {
                        Block adjacentBlock = center.getRelative(xOffset, yOffset, zOffset);
                        adjacentBlocks.add(adjacentBlock);
                    }
                }
            }
        }

        return adjacentBlocks;
    }

    private static class BlockInfo
    {
        private final int typeId;
        private final byte data;
        private final String reason;

        public BlockInfo(int typeId, byte data, String reason)
        {
            this.typeId = typeId;
            this.data = data;
            this.reason = reason;
        }

        public int getTypeId()
        {
            return typeId;
        }

        public byte getData()
        {
            return data;
        }

        public String getReason()
        {
            return reason;
        }
    }

    private static class AdjacentBlockGroup
    {
        private final Set<BlockInfo> blockInfos;

        public AdjacentBlockGroup(Set<BlockInfo> blockInfos)
        {
            this.blockInfos = blockInfos;
        }

        public boolean contains(int typeId, byte data)
        {
            for (BlockInfo blockInfo : blockInfos)
            {
                if (blockInfo.getTypeId() == typeId && blockInfo.getData() == data)
                {
                    return true;
                }
            }
            return false;
        }
    }
}
