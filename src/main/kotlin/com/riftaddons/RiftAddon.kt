package com.riftaddons


import com.riftaddons.module.RiftModules
import org.cobalt.api.addon.Addon
import org.cobalt.api.module.Module

object RiftAddon : Addon() {

  override fun onLoad() {

    println("RiftAddons loaded!")
  }

  override fun onUnload() {
    println("RiftAddons unloaded!")
  }

  override fun getModules(): List<Module> {
    return listOf(RiftModules)
  }

}
