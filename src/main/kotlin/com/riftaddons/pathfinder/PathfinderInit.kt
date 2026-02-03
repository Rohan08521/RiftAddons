package com.riftaddons.pathfinder

import com.riftaddons.command.PathCommand
import com.riftaddons.pathfinder.render.PathRenderer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import org.cobalt.api.event.EventBus
import org.cobalt.api.util.ChatUtils

object PathfinderInit {

    fun init() {
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(PathCommand.register())
        }

        EventBus.register(PathRenderer)

        ChatUtils.sendMessage("§aPathfinder system initialized!")
    }
}
