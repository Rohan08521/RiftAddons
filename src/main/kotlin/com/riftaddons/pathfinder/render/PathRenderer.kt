package com.riftaddons.pathfinder.render

import com.riftaddons.command.PathCommand
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.phys.Vec3
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.render.WorldRenderEvent
import org.cobalt.api.util.render.Render3D
import java.awt.Color

object PathRenderer {

    @SubscribeEvent
    fun onWorldRender(event: WorldRenderEvent.Last) {
        val path = PathCommand.getCurrentPath() ?: return

        if (path.size < 2) return

        val level = Minecraft.getInstance().level ?: return
        val keyNodes = identifyKeyNodes(path)

        for (i in 0 until keyNodes.size - 1) {
            val current = keyNodes[i]
            val next = keyNodes[i + 1]

            val y1 = getGroundY(level, current)
            val y2 = getGroundY(level, next)

            val start = Vec3(
                current.x + 0.5,
                y1 + 0.05,
                current.z + 0.5
            )

            val end = Vec3(
                next.x + 0.5,
                y2 + 0.05,
                next.z + 0.5
            )

            Render3D.drawLine(
                context = event.context,
                start = start,
                end = end,
                color = Color.ORANGE,
                esp = false,
                thickness = 2f
            )
        }

        for (node in keyNodes) {
            val groundPos = node.below()
            val state = level.getBlockState(groundPos)
            val shape = state.getShape(level, groundPos)

            val box = if (!shape.isEmpty) {
                val bounds = shape.bounds()
                net.minecraft.world.phys.AABB(
                    groundPos.x.toDouble() + bounds.minX, groundPos.y.toDouble() + bounds.minY, groundPos.z.toDouble() + bounds.minZ,
                    groundPos.x.toDouble() + bounds.maxX, groundPos.y.toDouble() + bounds.maxY, groundPos.z.toDouble() + bounds.maxZ
                )
            } else {
                net.minecraft.world.phys.AABB(
                    node.x.toDouble(), node.y.toDouble() - 1.0, node.z.toDouble(),
                    node.x.toDouble() + 1.0, node.y.toDouble(), node.z.toDouble() + 1.0
                )
            }
            Render3D.drawBox(event.context, box, Color.ORANGE, esp = false)
        }
    }

    private fun identifyKeyNodes(path: List<BlockPos>): List<BlockPos> {
        if (path.size <= 2) return path

        val keyNodes = mutableListOf<BlockPos>()
        keyNodes.add(path.first())

        for (i in 1 until path.size - 1) {
            val prev = path[i - 1]
            val current = path[i]
            val next = path[i + 1]

            if (!isStraightLine(prev, current, next)) {
                keyNodes.add(current)
            }
        }

        keyNodes.add(path.last())
        return keyNodes
    }

    private fun isStraightLine(p1: BlockPos, p2: BlockPos, p3: BlockPos): Boolean {
        val dx1 = p2.x - p1.x
        val dy1 = p2.y - p1.y
        val dz1 = p2.z - p1.z

        val dx2 = p3.x - p2.x
        val dy2 = p3.y - p2.y
        val dz2 = p3.z - p2.z

        return dx1 == dx2 && dy1 == dy2 && dz1 == dz2
    }

    private fun getGroundY(level: net.minecraft.world.level.Level, pos: BlockPos): Double {
        val groundPos = pos.below()
        val state = level.getBlockState(groundPos)
        val shape = state.getShape(level, groundPos)
        if (shape.isEmpty) return pos.y.toDouble()
        return groundPos.y.toDouble() + shape.bounds().maxY
    }
}
