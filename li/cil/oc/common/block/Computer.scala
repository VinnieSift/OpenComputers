package li.cil.oc.common.block

import cpw.mods.fml.common.registry.GameRegistry
import li.cil.oc.common.GuiType
import li.cil.oc.common.tileentity
import li.cil.oc.{Config, OpenComputers}
import net.minecraft.client.renderer.texture.IconRegister
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.util.Icon
import net.minecraft.world.IBlockAccess
import net.minecraft.world.World
import net.minecraftforge.common.ForgeDirection

class Computer(val parent: Delegator) extends Delegate {
  GameRegistry.registerTileEntity(classOf[tileentity.Computer], "oc.computer")

  val unlocalizedName = "Computer"

  // ----------------------------------------------------------------------- //

  private object Icons {
    val on = Array.fill[Icon](6)(null)
    val off = Array.fill[Icon](6)(null)
  }

  override def getBlockTextureFromSide(world: IBlockAccess, x: Int, y: Int, z: Int, worldSide: ForgeDirection, localSide: ForgeDirection) = {
    getIcon(localSide, world.getBlockTileEntity(x, y, z) match {
      case computer: tileentity.Computer => computer.isOn
      case _ => false
    })
  }

  override def icon(side: ForgeDirection) = getIcon(side, isOn = false)

  private def getIcon(side: ForgeDirection, isOn: Boolean) =
    Some(if (isOn) Icons.on(side.ordinal) else Icons.off(side.ordinal))

  override def registerIcons(iconRegister: IconRegister) = {
    Icons.off(ForgeDirection.DOWN.ordinal) = iconRegister.registerIcon(Config.resourceDomain + ":computer_top")
    Icons.on(ForgeDirection.DOWN.ordinal) = Icons.off(ForgeDirection.DOWN.ordinal)
    Icons.off(ForgeDirection.UP.ordinal) = Icons.off(ForgeDirection.DOWN.ordinal)
    Icons.on(ForgeDirection.UP.ordinal) = Icons.off(ForgeDirection.UP.ordinal)

    Icons.off(ForgeDirection.NORTH.ordinal) = iconRegister.registerIcon(Config.resourceDomain + ":computer_back")
    Icons.on(ForgeDirection.NORTH.ordinal) = iconRegister.registerIcon(Config.resourceDomain + ":computer_back_on")

    Icons.off(ForgeDirection.SOUTH.ordinal) = iconRegister.registerIcon(Config.resourceDomain + ":computer_front")
    Icons.on(ForgeDirection.SOUTH.ordinal) = Icons.off(ForgeDirection.SOUTH.ordinal)

    Icons.off(ForgeDirection.WEST.ordinal) = iconRegister.registerIcon(Config.resourceDomain + ":computer_side")
    Icons.on(ForgeDirection.WEST.ordinal) = iconRegister.registerIcon(Config.resourceDomain + ":computer_side_on")
    Icons.off(ForgeDirection.EAST.ordinal) = Icons.off(ForgeDirection.WEST.ordinal)
    Icons.on(ForgeDirection.EAST.ordinal) = Icons.on(ForgeDirection.WEST.ordinal)
  }

  // ----------------------------------------------------------------------- //

  override def hasTileEntity = true

  override def createTileEntity(world: World, metadata: Int) = Some(new tileentity.Computer(world.isRemote))

  // ----------------------------------------------------------------------- //

  override def canConnectRedstone(world: IBlockAccess, x: Int, y: Int, z: Int, side: ForgeDirection) =
    world.getBlockTileEntity(x, y, z).asInstanceOf[tileentity.Computer].canConnectRedstone(side)

  override def isProvidingStrongPower(world: IBlockAccess, x: Int, y: Int, z: Int, side: ForgeDirection) =
    isProvidingWeakPower(world, x, y, z, side)

  override def isProvidingWeakPower(world: IBlockAccess, x: Int, y: Int, z: Int, side: ForgeDirection) =
    world.getBlockTileEntity(x, y, z).asInstanceOf[tileentity.Computer].output(side)

  override def breakBlock(world: World, x: Int, y: Int, z: Int, blockId: Int, metadata: Int) = {
    if (!world.isRemote) world.getBlockTileEntity(x, y, z) match {
      case computer: tileentity.Computer =>
        computer.turnOff()
        computer.dropContent(world, x, y, z)
      case _ => // Ignore.
    }
    super.breakBlock(world, x, y, z, blockId, metadata)
  }

  override def onBlockActivated(world: World, x: Int, y: Int, z: Int, player: EntityPlayer,
                                side: ForgeDirection, hitX: Float, hitY: Float, hitZ: Float) = {
    if (!player.isSneaking) {
      // Start the computer if it isn't already running and open the GUI.
      if (!world.isRemote)
        world.getBlockTileEntity(x, y, z).asInstanceOf[tileentity.Computer].turnOn()
      player.openGui(OpenComputers, GuiType.Computer.id, world, x, y, z)
      true
    }
    else false
  }
}