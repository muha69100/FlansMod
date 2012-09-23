package co.uk.flansmods.common;

import co.uk.flansmods.client.*;
import net.minecraft.src.*;
import cpw.mods.fml.common.network.*;

public class GuiHandler implements IGuiHandler 
{
	@Override
	public Object getServerGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		switch(ID) 
		{
			case 0: return new ContainerPlaneCrafting(player.inventory, world, x, y, z, false);
			case 1: return new ContainerPlaneCrafting(player.inventory, world, x, y, z, true);
			case 2: return new ContainerVehicleCrafting(player.inventory, world, x, y, z);
		}
		return null;
	}

	@Override
	public Object getClientGuiElement(int ID, EntityPlayer player, World world, int x, int y, int z) {
		switch(ID) 
		{
			case 0: return new GuiPlaneCrafting(player.inventory, world, x, y, z, false);
			case 1: return new GuiPlaneCrafting(player.inventory, world, x, y, z, true);
			case 2: return new GuiVehicleCrafting(player.inventory, world, x, y, z);
		}
		return null;
	}
}