package com.riftaddons

import com.riftaddons.module.BerberisMacro
import com.riftaddons.pathfinder.PathfinderInit
import org.cobalt.api.addon.Addon
import org.cobalt.api.event.EventBus
import org.cobalt.api.module.Module

object RiftAddon : Addon() {

  override fun onLoad() {
    EventBus.register(BerberisMacro)
    PathfinderInit.init()
    println("RiftAddons loaded!")
  }

  override fun onUnload() {
    println("RiftAddons unloaded!")
  }

  override fun getModules(): List<Module> {
    return listOf(BerberisMacro)
  }
}
