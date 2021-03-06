package com.massivecraft.factions.cmd;

import com.massivecraft.factions.ConfServer;
import com.massivecraft.factions.Faction;
import com.massivecraft.factions.FactionColl;
import com.massivecraft.factions.Perm;
import com.massivecraft.factions.Rel;
import com.massivecraft.factions.cmd.req.ReqRoleIsAtLeast;
import com.massivecraft.mcore.cmd.arg.ARBoolean;
import com.massivecraft.mcore.cmd.req.ReqHasPerm;

public class CmdFactionsOpen extends FCommand
{
	public CmdFactionsOpen()
	{
		this.addAliases("open");
		
		this.addOptionalArg("yes/no", "toggle");
		
		this.addRequirements(ReqHasPerm.get(Perm.OPEN.node));
		this.addRequirements(ReqRoleIsAtLeast.get(Rel.OFFICER));
	}
	
	@Override
	public void perform()
	{
		Boolean target = this.arg(0, ARBoolean.get(), !myFaction.isOpen());
		if (target == null) return;

		// if economy is enabled, they're not on the bypass list, and this command has a cost set, make 'em pay
		if ( ! payForCommand(ConfServer.econCostOpen, "to open or close the faction", "for opening or closing the faction")) return;
		
		myFaction.setOpen(target);
		
		String descTarget = myFaction.isOpen() ? "open" : "closed";
		
		// Inform
		myFaction.msg("%s<i> changed the faction to <h>%s<i>.", fme.describeTo(myFaction, true), descTarget);
		for (Faction faction : FactionColl.get().getAll())
		{
			if (faction == myFaction)
			{
				continue;
			}
			faction.msg("<i>The faction %s<i> is now %s", myFaction.getTag(faction), descTarget);
		}
	}
	
}
