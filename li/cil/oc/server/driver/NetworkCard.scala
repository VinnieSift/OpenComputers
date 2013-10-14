package li.cil.oc.server.driver

import li.cil.oc.api.driver
import li.cil.oc.api.driver.Slot
import li.cil.oc.server.component
import li.cil.oc.{Config, Items}
import net.minecraft.item.ItemStack

object NetworkCard extends driver.Item {
  override def api = Option(getClass.getResourceAsStream(Config.driverPath + "network.lua"))

  def worksWith(item: ItemStack) = WorksWith(Items.lan)(item)

  def slot(item: ItemStack) = Slot.Card

  override def node(item: ItemStack) = Some(new component.NetworkCard())
}
