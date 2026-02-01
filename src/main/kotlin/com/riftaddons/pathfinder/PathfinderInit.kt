package com.riftaddons.pathfinder

import com.riftaddons.command.PathCommand
import com.riftaddons.pathfinder.render.PathRenderer
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback
import org.cobalt.api.event.EventBus
import org.cobalt.api.util.ChatUtils

object PathfinderInit {

    fun init() {
        // Register path command
        ClientCommandRegistrationCallback.EVENT.register { dispatcher, _ ->
            dispatcher.register(PathCommand.register())
        }

        // Register path renderer with EventBus
        EventBus.register(PathRenderer)

        ChatUtils.sendMessage("§aPathfinder system initialized!")
    }
}
