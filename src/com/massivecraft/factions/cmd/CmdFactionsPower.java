package com.massivecraft.factions.cmd;

import com.massivecraft.factions.ConfServer;
import com.massivecraft.factions.FPlayer;
import com.massivecraft.factions.Perm;
import com.massivecraft.factions.cmd.arg.ARFPlayer;
import com.massivecraft.mcore.cmd.req.ReqHasPerm;

public class CmdFactionsPower extends FCommand
{
	
	public CmdFactionsPower()
	{
		this.addAliases("power", "pow");
		
		this.addOptionalArg("player", "you");
		
		this.addRequirements(ReqHasPerm.get(Perm.POWER.node));
	}
	
	@Override
	public void perform()
	{
		FPlayer target = this.arg(0, ARFPlayer.getStartAny(), fme);
		if (target == null) return;
		
		if (target != fme && ! Perm.POWER_ANY.has(sender, true)) return;

		// if economy is enabled, they're not on the bypass list, and this command has a cost set, make 'em pay
		if ( ! payForCommand(ConfServer.econCostPower, "to show player power info", "for showing player power info")) return;

		double powerBoost = target.getPowerBoost();
		String boost = (powerBoost == 0.0) ? "" : (powerBoost > 0.0 ? " (bonus: " : " (penalty: ") + powerBoost + ")";
		msg("%s<a> - Power / Maxpower: <i>%d / %d %s", target.describeTo(fme, true), target.getPowerRounded(), target.getPowerMaxRounded(), boost);
	}
	
}
