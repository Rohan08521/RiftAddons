package com.riftaddons.command

import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.riftaddons.pathfinder.path.Astar
import com.riftaddons.pathfinder.config.Costs
import com.riftaddons.pathfinder.movement.MovementHelper
import com.riftaddons.pathfinder.cache.ChunkCache
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
                // Clear cache before starting new pathfinding
                MovementHelper.clearCache()
                ChunkCache.clear()
                val startTime = System.currentTimeMillis()

                val pathfinder = Astar(startPos, goalPos, maxDistance = 1000)
                val path = pathfinder.findPath(maxIterations = 100000, timeoutMs = 10000)

                val endTime = System.currentTimeMillis()
                val timeTaken = endTime - startTime

                mc.execute {
                    if (path != null && path.isNotEmpty()) {
                        currentPath = path
                        ChatUtils.sendMessage("§aPath found! §e${path.size} §anodes §7(smoothed)")
                        ChatUtils.sendMessage("§7Explored: §e${pathfinder.getNodesExplored()} §7nodes")
                        ChatUtils.sendMessage("§7Time: §e${timeTaken}ms §7(§e${String.format("%.2f", timeTaken / 1000.0)}s§7)")
                        if (pathfinder.isTimeoutReached()) {
                            ChatUtils.sendMessage("§eWarning: Pathfinding timed out, path may not be optimal")
                        }
                    } else {
                        currentPath = null
                        if (pathfinder.isTimeoutReached()) {
                            ChatUtils.sendMessage("§cNo path found (timed out after ${timeTaken}ms)")
                        } else {
                            ChatUtils.sendMessage("§cNo path found!")
                        }
                        ChatUtils.sendMessage("§7Explored: §e${pathfinder.getNodesExplored()} §7nodes")
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
