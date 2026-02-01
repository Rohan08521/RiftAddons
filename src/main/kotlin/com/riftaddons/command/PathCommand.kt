package com.riftaddons.command

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.riftaddons.pathfinder.path.Astar
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import org.cobalt.api.util.ChatUtils
import java.awt.Color

object PathCommand {
    private val mc = Minecraft.getInstance()
    private var currentPath: List<BlockPos>? = null
    private var pathColor = Color.CYAN

    fun register(): LiteralArgumentBuilder<FabricClientCommandSource> {
        return ClientCommandManager.literal("path")
            .then(
                ClientCommandManager.argument("x", IntegerArgumentType.integer())
                    .then(
                        ClientCommandManager.argument("y", IntegerArgumentType.integer())
                            .then(
                                ClientCommandManager.argument("z", IntegerArgumentType.integer())
                                    .executes { context ->
                                        val x = IntegerArgumentType.getInteger(context, "x")
                                        val y = IntegerArgumentType.getInteger(context, "y")
                                        val z = IntegerArgumentType.getInteger(context, "z")
                                        calculatePath(x, y, z)
                                        1
                                    }
                            )
                    )
            )
            .then(
                ClientCommandManager.literal("clear")
                    .executes {
                        clearPath()
                        1
                    }
            )
    }

    private fun calculatePath(x: Int, y: Int, z: Int) {
        val player = mc.player
        if (player == null) {
            ChatUtils.sendMessage("§cPlayer is null!")
            return
        }

        val startPos = player.blockPosition()
        val goalPos = BlockPos(x, y, z)

        ChatUtils.sendMessage("§aCalculating path from §e${startPos.x}, ${startPos.y}, ${startPos.z} §ato §e$x, $y, $z§a...")

        Thread {
            try {
                val pathfinder = Astar(startPos, goalPos)
                val path = pathfinder.findPath(maxIterations = 50000)

                mc.execute {
                    if (path != null && path.isNotEmpty()) {
                        currentPath = path
                        ChatUtils.sendMessage("§aPath found! §e${path.size} §anodes")
                        ChatUtils.sendMessage("§7Explored: §e${pathfinder.getClosedSetSize()} §7nodes")
                    } else {
                        currentPath = null
                        ChatUtils.sendMessage("§cNo path found!")
                    }
                }
            } catch (e: Exception) {
                mc.execute {
                    ChatUtils.sendMessage("§cError calculating path: ${e.message}")
                    e.printStackTrace()
                }
            }
        }.start()
    }

    private fun clearPath() {
        currentPath = null
        ChatUtils.sendMessage("§aPath cleared!")
    }

    fun getCurrentPath(): List<BlockPos>? = currentPath

    fun getPathColor(): Color = pathColor
}
