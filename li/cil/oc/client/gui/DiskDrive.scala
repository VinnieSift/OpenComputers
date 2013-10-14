package li.cil.oc.client.gui

import li.cil.oc.common.container
import li.cil.oc.common.tileentity
import net.minecraft.entity.player.InventoryPlayer
import net.minecraft.util.StatCollector

class DiskDrive(playerInventory: InventoryPlayer, val drive: tileentity.DiskDrive) extends DynamicGuiContainer(new container.DiskDrive(playerInventory, drive)) {
  override def drawGuiContainerForegroundLayer(mouseX: Int, mouseY: Int) = {
    fontRenderer.drawString(
      StatCollector.translateToLocal("oc.container.disk_drive"),
      8, 6, 0x404040)
  }
}
