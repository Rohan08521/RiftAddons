package com.riftaddons.pathfinder.path

import net.minecraft.core.BlockPos

data class Node(
    val pos: BlockPos,
    var gCost: Double = 0.0,
    var hCost: Double = 0.0,
    var parent: Node? = null
) {
    val fCost: Double
        get() = gCost + hCost

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Node) return false
        return pos == other.pos
    }

    override fun hashCode(): Int {
        return pos.hashCode()
    }
}
