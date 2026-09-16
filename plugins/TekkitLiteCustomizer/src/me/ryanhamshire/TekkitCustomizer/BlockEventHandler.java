/*
    TekkitCustomizer Server Plugin for Minecraft
    Copyright (C) 2012 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.TekkitCustomizer;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockEventHandler implements Listener
{
	// IC2 Nuke block (IC2.cfg blockNuke). Placing one in a claim with explosions enabled warns the placer.
	private static final int NUKE_ID = 237;

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOWEST)
	public void onBlockPlace(BlockPlaceEvent placeEvent)
	{
		Player player = placeEvent.getPlayer();
		Block block = placeEvent.getBlock();

		MaterialInfo bannedInfo = TekkitCustomizer.instance.isBanned(ActionType.Ownership, player, block.getTypeId(), block.getData(), block.getLocation());
		if(bannedInfo == null)
		{
			bannedInfo = TekkitCustomizer.instance.isBanned(ActionType.Placement, player, block.getTypeId(), block.getData(), block.getLocation());
		}
		if(bannedInfo != null)
		{
			placeEvent.setCancelled(true);
			player.sendMessage("Sorry, that block is banned.  Reason: " + bannedInfo.reason);
		}
	}

	// Runs last and only on placements nothing cancelled, so it never fires when the place is denied.
	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void warnNukeInExplosiveClaim(BlockPlaceEvent placeEvent)
	{
		Block block = placeEvent.getBlock();
		if(block.getTypeId() != NUKE_ID)
		{
			return;
		}
		if(ClaimQuery.explosionsAllowedAt(block.getLocation()))
		{
			placeEvent.getPlayer().sendMessage("§cWarning: explosions are enabled in this claim, so this Nuke will damage it if it goes off. Use /claimexplosions to turn them off.");
		}
	}
}
