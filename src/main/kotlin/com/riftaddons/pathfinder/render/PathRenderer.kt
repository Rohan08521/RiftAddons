package com.riftaddons.pathfinder.render

import com.riftaddons.command.PathCommand
import net.minecraft.world.phys.Vec3
import org.cobalt.api.event.annotation.SubscribeEvent
import org.cobalt.api.event.impl.render.WorldRenderEvent
import org.cobalt.api.util.render.Render3D
import java.awt.Color

object PathRenderer {

    @SubscribeEvent
    fun onWorldRender(event: WorldRenderEvent.Last) {
        val path = PathCommand.getCurrentPath() ?: return
        val color = PathCommand.getPathColor()

        if (path.size < 2) return

        // Draw lines between path nodes
        for (i in 0 until path.size - 1) {
            val current = path[i]
            val next = path[i + 1]

            val start = Vec3(
                current.x + 0.5,
                current.y + 0.25,
                current.z + 0.5
            )

            val end = Vec3(
                next.x + 0.5,
                next.y + 0.25,
                next.z + 0.5
            )

            Render3D.drawLine(
                context = event.context,
                start = start,
                end = end,
                color = color,
                esp = true,
                thickness = 1f
            )
        }
    }
}
