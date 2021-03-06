package com.massivecraft.factions.listeners;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.util.NumberConversions;

import com.massivecraft.factions.BoardColl;
import com.massivecraft.factions.ConfServer;
import com.massivecraft.factions.Const;
import com.massivecraft.factions.FFlag;
import com.massivecraft.factions.FPerm;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayerColl;
import com.massivecraft.factions.Rel;
import com.massivecraft.factions.TerritoryAccess;
import com.massivecraft.factions.integration.SpoutFeatures;
import com.massivecraft.factions.util.VisualizeUtil;
import com.massivecraft.mcore.ps.PS;


public class FactionsPlayerListener implements Listener
{
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerJoin(PlayerJoinEvent event)
	{
		// Make sure that all online players do have a fplayer.
		final FPlayer me = FPlayerColl.get().get(event.getPlayer());
		
		// Update the lastLoginTime for this fplayer
		me.setLastLoginTime(System.currentTimeMillis());

		// Store player's current Chunk and notify them where they are
		me.setCurrentChunk(PS.valueOf(event.getPlayer()));
		
		if ( ! SpoutFeatures.updateTerritoryDisplay(me))
		{
			me.sendFactionHereMessage();
		}
	}
	
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerQuit(PlayerQuitEvent event)
	{
		FPlayer me = FPlayerColl.get().get(event.getPlayer());

		// Make sure player's power is up to date when they log off.
		me.getPower();
		// and update their last login time to point to when the logged off, for auto-remove routine
		me.setLastLoginTime(System.currentTimeMillis());

		SpoutFeatures.playerDisconnect(me);
	}
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerMove(PlayerMoveEvent event)
	{
		if (event.isCancelled()) return;

		// quick check to make sure player is moving between chunks; good performance boost
		if
		(
			event.getFrom().getBlockX() >> 4 == event.getTo().getBlockX() >> 4
			&&
			event.getFrom().getBlockZ() >> 4 == event.getTo().getBlockZ() >> 4
			&&
			event.getFrom().getWorld() == event.getTo().getWorld()
		)
			return;

		Player player = event.getPlayer();
		FPlayer me = FPlayerColl.get().get(player);
		
		// Did we change coord?
		PS chunkFrom = me.getCurrentChunk();
		PS chunkTo = PS.valueOf(event.getTo()).getChunk(true);
		
		if (chunkFrom.equals(chunkTo)) return;
		
		// Yes we did change coord (:
		
		me.setCurrentChunk(chunkTo);
		TerritoryAccess access = BoardColl.get().getTerritoryAccessAt(chunkTo);

		// Did we change "host"(faction)?
		boolean changedFaction = (BoardColl.get().getFactionAt(chunkFrom) != access.getHostFaction());

		// let Spout handle most of this if it's available
		boolean handledBySpout = changedFaction && SpoutFeatures.updateTerritoryDisplay(me);
		
		if (me.isMapAutoUpdating())
		{
			me.sendMessage(BoardColl.get().getMap(me.getFaction(), chunkTo, player.getLocation().getYaw()));
		}
		else if (changedFaction && ! handledBySpout)
		{
			me.sendFactionHereMessage();
		}

		// show access info message if needed
		if ( ! handledBySpout && ! SpoutFeatures.updateAccessInfo(me) && ! access.isDefault())
		{
			if (access.subjectHasAccess(me))
				me.msg("<g>You have access to this area.");
			else if (access.subjectAccessIsRestricted(me))
				me.msg("<b>This area has restricted access.");
		}

		if (me.getAutoClaimFor() != null)
		{
			me.attemptClaim(me.getAutoClaimFor(), event.getTo(), true);
		}
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerInteract(PlayerInteractEvent event)
	{
		if (event.isCancelled()) return;
		// only need to check right-clicks and physical as of MC 1.4+; good performance boost
		if (event.getAction() != Action.RIGHT_CLICK_BLOCK && event.getAction() != Action.PHYSICAL) return;

		Block block = event.getClickedBlock();
		Player player = event.getPlayer();

		if (block == null) return;  // clicked in air, apparently

		if ( ! canPlayerUseBlock(player, block, false))
		{
			event.setCancelled(true);
			if (ConfServer.handleExploitInteractionSpam)
			{
				String name = player.getName();
				InteractAttemptSpam attempt = interactSpammers.get(name);
				if (attempt == null)
				{
					attempt = new InteractAttemptSpam();
					interactSpammers.put(name, attempt);
				}
				int count = attempt.increment();
				if (count >= 10)
				{
					FPlayer me = FPlayerColl.get().get(name);
					me.msg("<b>Ouch, that is starting to hurt. You should give it a rest.");
					player.damage(NumberConversions.floor((double)count / 10));
				}
			}
			return;
		}

		if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;  // only interested on right-clicks for below

		if ( ! playerCanUseItemHere(player, block.getLocation(), event.getMaterial(), false))
		{
			event.setCancelled(true);
			return;
		}
	}


	// for handling people who repeatedly spam attempts to open a door (or similar) in another faction's territory
	private Map<String, InteractAttemptSpam> interactSpammers = new HashMap<String, InteractAttemptSpam>();
	private static class InteractAttemptSpam
	{
		private int attempts = 0;
		private long lastAttempt = System.currentTimeMillis();

		// returns the current attempt count
		public int increment()
		{
			long Now = System.currentTimeMillis();
			if (Now > lastAttempt + 2000)
				attempts = 1;
			else
				attempts++;
			lastAttempt = Now;
			return attempts;
		}
	}


	// TODO: Refactor ! justCheck    -> to informIfNot
	// TODO: Possibly incorporate pain build... 
	public static boolean playerCanUseItemHere(Player player, Location loc, Material material, boolean justCheck)
	{
		String name = player.getName();
		if (ConfServer.playersWhoBypassAllProtection.contains(name)) return true;

		FPlayer me = FPlayerColl.get().get(name);
		if (me.isUsingAdminMode()) return true;
		if (Const.MATERIALS_EDIT_TOOLS.contains(material) && ! FPerm.BUILD.has(me, loc, ! justCheck)) return false;
		return true;
	}
	public static boolean canPlayerUseBlock(Player player, Block block, boolean justCheck)
	{
		String name = player.getName();
		if (ConfServer.playersWhoBypassAllProtection.contains(name)) return true;

		FPlayer me = FPlayerColl.get().get(name);
		if (me.isUsingAdminMode()) return true;
		Location loc = block.getLocation();
		Material material = block.getType();
		
		if (Const.MATERIALS_EDIT_ON_INTERACT.contains(material) && ! FPerm.BUILD.has(me, loc, ! justCheck)) return false;
		if (Const.MATERIALS_CONTAINER.contains(material) && ! FPerm.CONTAINER.has(me, loc, ! justCheck)) return false;
		if (Const.MATERIALS_DOOR.contains(material)      && ! FPerm.DOOR.has(me, loc, ! justCheck)) return false;
		if (material == Material.STONE_BUTTON          && ! FPerm.BUTTON.has(me, loc, ! justCheck)) return false;
		if (material == Material.LEVER                 && ! FPerm.LEVER.has(me, loc, ! justCheck)) return false;
		return true;
	}

	@EventHandler(priority = EventPriority.HIGH)
	public void onPlayerRespawn(PlayerRespawnEvent event)
	{
		FPlayer me = FPlayerColl.get().get(event.getPlayer());

		me.getPower();  // update power, so they won't have gained any while dead

		Location home = me.getFaction().getHome(); // TODO: WARNING FOR NPE HERE THE ORIO FOR RESPAWN SHOULD BE ASSIGNABLE FROM CONFIG.
		if
		(
			ConfServer.homesEnabled
			&&
			ConfServer.homesTeleportToOnDeath
			&&
			home != null
			&&
			(
				ConfServer.homesRespawnFromNoPowerLossWorlds
				||
				! ConfServer.worldsNoPowerLoss.contains(event.getPlayer().getWorld().getName())
			)
		)
		{
			event.setRespawnLocation(home);
		}
	}

	// For some reason onPlayerInteract() sometimes misses bucket events depending on distance (something like 2-3 blocks away isn't detected),
	// but these separate bucket events below always fire without fail
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerBucketEmpty(PlayerBucketEmptyEvent event)
	{
		if (event.isCancelled()) return;

		Block block = event.getBlockClicked();
		Player player = event.getPlayer();

		if ( ! playerCanUseItemHere(player, block.getLocation(), event.getBucket(), false))
		{
			event.setCancelled(true);
			return;
		}
	}
	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerBucketFill(PlayerBucketFillEvent event)
	{
		if (event.isCancelled()) return;

		Block block = event.getBlockClicked();
		Player player = event.getPlayer();

		if ( ! playerCanUseItemHere(player, block.getLocation(), event.getBucket(), false))
		{
			event.setCancelled(true);
			return;
		}
	}
	
	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void onPlayerCommandPreprocess(PlayerCommandPreprocessEvent event)
	{
		// Get the player
		Player player = event.getPlayer();
		FPlayer me = FPlayerColl.get().get(player);
		
		// With adminmode no commands are denied. 
		if (me.isUsingAdminMode()) return;
		
		// The full command is converted to lowercase and does include the slash in the front
		String fullCmd = event.getMessage().toLowerCase();
		
		if (me.hasFaction() && me.getFaction().getFlag(FFlag.PERMANENT) && isCommandInList(fullCmd, ConfServer.permanentFactionMemberDenyCommands))
		{
			me.msg("<b>You can't use the command \""+fullCmd+"\" because you are in a permanent faction.");
			event.setCancelled(true);
			return;
		}
		
		Rel rel = me.getRelationToLocation();
		if (BoardColl.get().getFactionAt(me.getCurrentChunk()).isNone()) return;
		
		if (rel == Rel.NEUTRAL && isCommandInList(fullCmd, ConfServer.territoryNeutralDenyCommands))
		{
			me.msg("<b>You can't use the command \""+fullCmd+"\" in neutral territory.");
			event.setCancelled(true);
			return;
		}

		if (rel == Rel.ENEMY && isCommandInList(fullCmd, ConfServer.territoryEnemyDenyCommands))
		{
			me.msg("<b>You can't use the command \""+fullCmd+"\" in enemy territory.");
			event.setCancelled(true);
			return;
		}

		return;
	}

	private static boolean isCommandInList(String fullCmd, Collection<String> strings)
	{
		String shortCmd = fullCmd.substring(1);
		Iterator<String> iter = strings.iterator();
		while (iter.hasNext())
		{
			String cmdCheck = iter.next();
			if (cmdCheck == null)
			{
				iter.remove();
				continue;
			}
			cmdCheck = cmdCheck.toLowerCase();
			if (fullCmd.startsWith(cmdCheck)) return true;
			if (shortCmd.startsWith(cmdCheck)) return true;
		}
		return false;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void onPlayerKick(PlayerKickEvent event)
	{
		if (event.isCancelled()) return;

		FPlayer badGuy = FPlayerColl.get().get(event.getPlayer());
		if (badGuy == null)
		{
			return;
		}

		SpoutFeatures.playerDisconnect(badGuy);

		// if player was banned (not just kicked), get rid of their stored info
		if (ConfServer.removePlayerDataWhenBanned && event.getReason().equals("Banned by admin."))
		{
			if (badGuy.getRole() == Rel.LEADER)
				badGuy.getFaction().promoteNewLeader();
			badGuy.leave(false);
			badGuy.detach();
		}
	}
	
	// -------------------------------------------- //
	// VisualizeUtil
	// -------------------------------------------- //
	
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerMoveClearVisualizations(PlayerMoveEvent event)
	{
		if (event.isCancelled()) return;
		
		Block blockFrom = event.getFrom().getBlock();
		Block blockTo = event.getTo().getBlock();
		if (blockFrom.equals(blockTo)) return;
		
		VisualizeUtil.clear(event.getPlayer());
	}
}
