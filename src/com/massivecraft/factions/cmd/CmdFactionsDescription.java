package com.massivecraft.factions.cmd;

import com.massivecraft.factions.ConfServer;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.FPlayerColl;
import com.massivecraft.factions.Perm;
import com.massivecraft.factions.Rel;
import com.massivecraft.factions.cmd.req.ReqRoleIsAtLeast;
import com.massivecraft.mcore.cmd.req.ReqHasPerm;
import com.massivecraft.mcore.util.Txt;

public class CmdFactionsDescription extends FCommand
{
	public CmdFactionsDescription()
	{
		this.addAliases("desc");
		
		this.addRequiredArg("desc");
		this.setErrorOnToManyArgs(false);
		
		this.addRequirements(ReqHasPerm.get(Perm.DESCRIPTION.node));
		this.addRequirements(ReqRoleIsAtLeast.get(Rel.OFFICER));
	}
	
	@Override
	public void perform()
	{
		// if economy is enabled, they're not on the bypass list, and this command has a cost set, make 'em pay
		if ( ! payForCommand(ConfServer.econCostDesc, "to change faction description", "for changing faction description")) return;

		// TODO: This must be an invalid replace-approach. The call order is wrong somehow? 
		myFaction.setDescription(Txt.implode(args, " ").replaceAll("(&([a-f0-9]))", "& $2"));  // since "&" color tags seem to work even through plain old FPlayer.sendMessage() for some reason, we need to break those up

		if ( ! ConfServer.broadcastDescriptionChanges)
		{
			fme.msg("You have changed the description for <h>%s<i> to:", myFaction.describeTo(fme));
			fme.sendMessage(myFaction.getDescription());
			return;
		}

		// Broadcast the description to everyone
		for (FPlayer fplayer : FPlayerColl.get().getAllOnline())
		{
			fplayer.msg("<h>%s<i> changed their description to:", myFaction.describeTo(fplayer));
			fplayer.sendMessage(myFaction.getDescription());  // players can inject "&" or "`" or "<i>" or whatever in their description, thus exploitable (masquerade as server messages or whatever); by the way, &k is particularly interesting looking
		}
	}
	
}
