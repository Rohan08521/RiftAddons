package com.riftaddons.pathfinder.path

import net.minecraft.core.BlockPos

data class NodeData(
    val pos: BlockPos,
    val cost: Double,
    val movementType: MovementType
)

enum class MovementType {
    WALK,
    JUMP,
    FALL,
    PARKOUR,
    DIAGONAL,
    ASCEND,
    DESCEND
}
