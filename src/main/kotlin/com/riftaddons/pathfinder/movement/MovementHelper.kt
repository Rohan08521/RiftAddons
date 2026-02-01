package com.riftaddons.pathfinder.movement

import com.riftaddons.pathfinder.cache.ChunkCache
import com.riftaddons.pathfinder.path.MovementType
import com.riftaddons.pathfinder.path.NodeData
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.*

object MovementHelper {

    fun getNeighbors(pos: BlockPos): List<NodeData> {
        val neighbors = mutableListOf<NodeData>()

        // Cardinal directions (WALK)
        neighbors.addAll(getCardinalMoves(pos))

        // Diagonal movements
        neighbors.addAll(getDiagonalMoves(pos))

        // Jumps and ascend
        neighbors.addAll(getJumpMoves(pos))

        // Descend and falls
        neighbors.addAll(getDescendMoves(pos))

        // Parkour jumps
        neighbors.addAll(getParkourMoves(pos))

        // Ladder/vine climbing
        neighbors.addAll(getClimbMoves(pos))

        // Stairs and slabs
        neighbors.addAll(getStairSlabMoves(pos))

        return neighbors.filter { isValidMove(it) }
    }

    private fun getCardinalMoves(pos: BlockPos): List<NodeData> {
        val moves = mutableListOf<NodeData>()
        val directions = listOf(
            BlockPos(1, 0, 0),
            BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1),
            BlockPos(0, 0, -1)
        )

        for (dir in directions) {
            val newPos = pos.offset(dir)
            if (canWalkOn(newPos) && hasHeadroom(newPos)) {
                moves.add(NodeData(newPos, 1.0, MovementType.WALK))
            }
        }

        return moves
    }

    private fun getDiagonalMoves(pos: BlockPos): List<NodeData> {
        val moves = mutableListOf<NodeData>()
        val diagonals = listOf(
            BlockPos(1, 0, 1),
            BlockPos(1, 0, -1),
            BlockPos(-1, 0, 1),
            BlockPos(-1, 0, -1)
        )

        for (diag in diagonals) {
            val newPos = pos.offset(diag)
            if (canWalkOn(newPos) && hasHeadroom(newPos)) {
                moves.add(NodeData(newPos, 1.414, MovementType.DIAGONAL))
            }
        }

        return moves
    }

    private fun getJumpMoves(pos: BlockPos): List<NodeData> {
        val moves = mutableListOf<NodeData>()
        val directions = listOf(
            BlockPos(1, 0, 0),
            BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1),
            BlockPos(0, 0, -1)
        )

        for (dir in directions) {
            val upPos = pos.offset(dir).above()
            if (canWalkOn(upPos) && hasHeadroom(upPos.above())) {
                moves.add(NodeData(upPos, 2.0, MovementType.JUMP))
            }
        }

        return moves
    }

    private fun getDescendMoves(pos: BlockPos): List<NodeData> {
        val moves = mutableListOf<NodeData>()
        val directions = listOf(
            BlockPos(1, 0, 0),
            BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1),
            BlockPos(0, 0, -1)
        )

        for (dir in directions) {
            for (drop in 1..4) {
                val downPos = pos.offset(dir).below(drop)
                if (canWalkOn(downPos) && hasHeadroom(downPos)) {
                    val cost = 1.0 + (drop * 0.5)
                    moves.add(NodeData(downPos, cost, if (drop == 1) MovementType.DESCEND else MovementType.FALL))
                    break
                }
                if (!isPassable(downPos.above())) break
            }
        }

        return moves
    }

    private fun getParkourMoves(pos: BlockPos): List<NodeData> {
        val moves = mutableListOf<NodeData>()
        val directions = listOf(
            BlockPos(2, 0, 0),
            BlockPos(-2, 0, 0),
            BlockPos(0, 0, 2),
            BlockPos(0, 0, -2),
            BlockPos(3, 0, 0),
            BlockPos(-3, 0, 0),
            BlockPos(0, 0, 3),
            BlockPos(0, 0, -3)
        )

        for (dir in directions) {
            val parkourPos = pos.offset(dir)
            if (canWalkOn(parkourPos) && hasHeadroom(parkourPos.above(2))) {
                val distance = kotlin.math.sqrt((dir.x * dir.x + dir.z * dir.z).toDouble())
                moves.add(NodeData(parkourPos, distance * 2.0, MovementType.PARKOUR))
            }
        }

        return moves
    }

    private fun getClimbMoves(pos: BlockPos): List<NodeData> {
        val moves = mutableListOf<NodeData>()
        val blockState = ChunkCache.getBlockState(pos) ?: return moves

        // Check if current position has a climbable block
        if (isClimbable(blockState.block)) {
            // Can climb up
            val upPos = pos.above()
            if (isPassable(upPos)) {
                moves.add(NodeData(upPos, 1.5, MovementType.ASCEND))
            }

            // Can climb down
            val downPos = pos.below()
            if (isPassable(downPos)) {
                moves.add(NodeData(downPos, 1.5, MovementType.DESCEND))
            }
        }

        // Check adjacent blocks for ladders/vines
        val directions = listOf(
            BlockPos(1, 0, 0),
            BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1),
            BlockPos(0, 0, -1)
        )

        for (dir in directions) {
            val adjacentPos = pos.offset(dir)
            val adjacentState = ChunkCache.getBlockState(adjacentPos) ?: continue

            if (isClimbable(adjacentState.block)) {
                val upPos = adjacentPos.above()
                if (isPassable(upPos) && hasHeadroom(upPos)) {
                    moves.add(NodeData(upPos, 1.5, MovementType.ASCEND))
                }
            }
        }

        return moves
    }

    private fun getStairSlabMoves(pos: BlockPos): List<NodeData> {
        val moves = mutableListOf<NodeData>()
        val directions = listOf(
            BlockPos(1, 0, 0),
            BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1),
            BlockPos(0, 0, -1)
        )

        for (dir in directions) {
            val adjacentPos = pos.offset(dir)
            val adjacentState = ChunkCache.getBlockState(adjacentPos) ?: continue

            // Check for stairs or slabs at the same level
            if (isStairOrSlab(adjacentState.block)) {
                if (hasHeadroom(adjacentPos)) {
                    moves.add(NodeData(adjacentPos, 1.0, MovementType.WALK))
                }
            }

            // Check for stairs/slabs one block up (stepping up)
            val upPos = adjacentPos.above()
            val upState = ChunkCache.getBlockState(upPos) ?: continue

            if (isStairOrSlab(upState.block) && hasHeadroom(upPos.above())) {
                moves.add(NodeData(upPos, 1.3, MovementType.ASCEND))
            }
        }

        return moves
    }

    private fun canWalkOn(pos: BlockPos): Boolean {
        val blockBelow = ChunkCache.getBlockState(pos.below()) ?: return false
        val blockAt = ChunkCache.getBlockState(pos) ?: return false

        // Check if the block below is walkable
        val isWalkableBelow = !blockBelow.isAir &&
                              (blockBelow.isSolidRender() ||
                               isStairOrSlab(blockBelow.block) ||
                               isFenceOrWall(blockBelow.block) ||
                               blockBelow.block is CarpetBlock ||
                               blockBelow.block is SnowLayerBlock)

        // Check if current position is passable
        val isCurrentPassable = blockAt.isAir ||
                               isPassable(pos) ||
                               isClimbable(blockAt.block)

        return isWalkableBelow && isCurrentPassable
    }

    private fun hasHeadroom(pos: BlockPos): Boolean {
        val block1 = ChunkCache.getBlockState(pos) ?: return false
        val block2 = ChunkCache.getBlockState(pos.above()) ?: return false

        return (block1.isAir || isPassable(pos)) &&
               (block2.isAir || isPassable(pos.above()))
    }

    private fun isPassable(pos: BlockPos): Boolean {
        val blockState = ChunkCache.getBlockState(pos) ?: return false
        val block = blockState.block

        return blockState.isAir ||
               block is FlowerBlock ||
               block is TallGrassBlock ||
               block is VineBlock ||
               block is LadderBlock ||
               block is SignBlock ||
               block is BannerBlock ||
               block is TorchBlock ||
               block is RedStoneWireBlock ||
               block is RepeaterBlock ||
               block is ComparatorBlock ||
               block is ButtonBlock ||
               block is LeverBlock ||
               block is PressurePlateBlock ||
               block is CarpetBlock ||
               block is SnowLayerBlock ||
               block == Blocks.WATER ||
               block == Blocks.LAVA
    }

    private fun isClimbable(block: Block): Boolean {
        return block is LadderBlock ||
               block is VineBlock ||
               block is ScaffoldingBlock ||
               block is TwistingVinesBlock ||
               block is WeepingVinesBlock
    }

    private fun isStairOrSlab(block: Block): Boolean {
        return block is StairBlock ||
               block is SlabBlock
    }

    private fun isFenceOrWall(block: Block): Boolean {
        return block is FenceBlock ||
               block is WallBlock ||
               block is FenceGateBlock
    }

    private fun isValidMove(nodeData: NodeData): Boolean {
        return ChunkCache.isLoaded(nodeData.pos) &&
               canWalkOn(nodeData.pos) &&
               hasHeadroom(nodeData.pos)
    }
}


