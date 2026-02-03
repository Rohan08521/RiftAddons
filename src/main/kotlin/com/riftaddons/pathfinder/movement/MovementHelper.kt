package com.riftaddons.pathfinder.movement

import com.riftaddons.pathfinder.cache.ChunkCache
import com.riftaddons.pathfinder.config.Costs
import com.riftaddons.pathfinder.path.MovementType
import com.riftaddons.pathfinder.path.NodeData
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.*

object MovementHelper {

    // Cache for walkability checks
    private val walkabilityCache = mutableMapOf<BlockPos, Boolean>()
    private val penaltyCache = mutableMapOf<BlockPos, Double>()
    private val neighborsCache = mutableMapOf<BlockPos, List<NodeData>>()

    fun clearCache() {
        walkabilityCache.clear()
        penaltyCache.clear()
        neighborsCache.clear()
    }

    fun getNeighbors(pos: BlockPos): List<NodeData> {
        if (neighborsCache.containsKey(pos)) {
            return neighborsCache[pos]!!
        }

        val neighbors = mutableListOf<NodeData>()

        // Priority 1: Simple cardinal movements (most natural)
        neighbors.addAll(getCardinalMoves(pos))

        // Priority 2: Diagonal movements (still natural)
        neighbors.addAll(getDiagonalMoves(pos))

        // Priority 3: Simple descend (walking down is easy)
        neighbors.addAll(getDescendMoves(pos))

        // Priority 4: Jump up (common but less preferred)
        neighbors.addAll(getJumpMoves(pos))

        // Priority 5: Stairs and slabs (if present, very natural)
        neighbors.addAll(getStairSlabMoves(pos))

        // Only check complex movements if we have few options
        if (neighbors.size < 4) {
            neighbors.addAll(getClimbMoves(pos))
        }

        // Only check parkour as last resort (very unnatural)
        if (neighbors.size < 2) {
            neighbors.addAll(getParkourMoves(pos))
        }

        neighborsCache[pos] = neighbors
        return neighbors
    }

    private fun getProximityPenalty(pos: BlockPos): Double {
        penaltyCache[pos]?.let { return it }

        var penalty = 0.0
        val checkDirs = listOf(
            BlockPos(1, 0, 0),
            BlockPos(-1, 0, 0),
            BlockPos(0, 0, 1),
            BlockPos(0, 0, -1),
            BlockPos(1, 0, 1),
            BlockPos(1, 0, -1),
            BlockPos(-1, 0, 1),
            BlockPos(-1, 0, -1)
        )

        for (dir in checkDirs) {
            val neighbor = pos.offset(dir)
            // If the neighbor block is not passable (i.e., a wall) or is a fence/wall, add penalty
            if (!isPassable(neighbor)) {
                penalty += 0.5 // Add specific cost per blocked neighbor
            }
             // Also check for head level obstruction (2 blocks high wall)
            if (!isPassable(neighbor.above())) {
                penalty += 0.3
            }
        }

        penaltyCache[pos] = penalty
        return penalty
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
            if (!ChunkCache.isLoaded(newPos)) continue
            if (canWalkOn(newPos) && hasHeadroom(newPos)) {
                val penalty = getProximityPenalty(newPos)
                moves.add(NodeData(newPos, Costs.WALK_COST + penalty, MovementType.WALK))
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
            if (!ChunkCache.isLoaded(newPos)) continue
            if (canWalkOn(newPos) && hasHeadroom(newPos)) {
                val penalty = getProximityPenalty(newPos)
                moves.add(NodeData(newPos, Costs.DIAGONAL_COST + penalty, MovementType.DIAGONAL))
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
            if (!ChunkCache.isLoaded(upPos)) continue
            if (canWalkOn(upPos) && hasHeadroom(upPos.above())) {
                val penalty = getProximityPenalty(upPos)
                moves.add(NodeData(upPos, Costs.JUMP_COST + penalty, MovementType.JUMP))
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
            // Only check falling up to 3 blocks (4 is max safe fall)
            for (drop in 1..3) {
                val downPos = pos.offset(dir).below(drop)

                // Early exit if not loaded
                if (!ChunkCache.isLoaded(downPos)) break

                if (canWalkOn(downPos) && hasHeadroom(downPos)) {
                    val cost = if (drop == 1) Costs.DESCEND_COST else Costs.calculateFallCost(drop)
                    val penalty = getProximityPenalty(downPos)
                    moves.add(NodeData(downPos, cost + penalty, if (drop == 1) MovementType.DESCEND else MovementType.FALL))
                    break
                }

                // If we hit a solid block, stop checking further down
                if (!isPassable(downPos.above())) break
            }
        }

        return moves
    }

    private fun getParkourMoves(pos: BlockPos): List<NodeData> {
        val moves = mutableListOf<NodeData>()
        // Only 2-block parkour, 3+ block gaps are too risky/unnatural
        val directions = listOf(
            BlockPos(2, 0, 0),
            BlockPos(-2, 0, 0),
            BlockPos(0, 0, 2),
            BlockPos(0, 0, -2)
        )

        for (dir in directions) {
            val parkourPos = pos.offset(dir)
            if (canWalkOn(parkourPos) && hasHeadroom(parkourPos.above(2))) {
                val distance = 2.0
                val penalty = getProximityPenalty(parkourPos)
                moves.add(NodeData(parkourPos, Costs.calculateParkourCost(distance) + penalty, MovementType.PARKOUR))
            }
        }

        return moves
    }

    private fun getClimbMoves(pos: BlockPos): List<NodeData> {
        val moves = mutableListOf<NodeData>()
        val blockState = ChunkCache.getBlockState(pos) ?: return moves

        if (isClimbable(blockState.block)) {
            val upPos = pos.above()
            if (isPassable(upPos)) {
                val penalty = getProximityPenalty(upPos)
                moves.add(NodeData(upPos, Costs.CLIMB_COST + penalty, MovementType.ASCEND))
            }

            val downPos = pos.below()
            if (isPassable(downPos)) {
                val penalty = getProximityPenalty(downPos)
                moves.add(NodeData(downPos, Costs.CLIMB_COST + penalty, MovementType.DESCEND))
            }
        }

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
                    val penalty = getProximityPenalty(upPos)
                    moves.add(NodeData(upPos, Costs.CLIMB_COST + penalty, MovementType.ASCEND))
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

            if (isStairOrSlab(adjacentState.block)) {
                if (hasHeadroom(adjacentPos)) {
                    val cost = if (adjacentState.block is StairBlock) Costs.STAIR_COST else Costs.SLAB_COST
                    val penalty = getProximityPenalty(adjacentPos)
                    moves.add(NodeData(adjacentPos, cost + penalty, MovementType.WALK))
                }
            }

            val upPos = adjacentPos.above()
            val upState = ChunkCache.getBlockState(upPos) ?: continue

            if (isStairOrSlab(upState.block) && hasHeadroom(upPos.above())) {
                val cost = if (upState.block is StairBlock) Costs.STAIR_STEP_UP_COST else Costs.SLAB_STEP_UP_COST
                val penalty = getProximityPenalty(upPos)
                moves.add(NodeData(upPos, cost + penalty, MovementType.ASCEND))
            }
        }

        return moves
    }

    fun canWalkOn(pos: BlockPos): Boolean {
        // Check cache first
        walkabilityCache[pos]?.let { return it }

        val blockBelow = ChunkCache.getBlockState(pos.below()) ?: return false.also { walkabilityCache[pos] = false }
        val blockAt = ChunkCache.getBlockState(pos) ?: return false.also { walkabilityCache[pos] = false }

        val isWalkableBelow = !blockBelow.isAir &&
                (blockBelow.isSolidRender() ||
                        isStairOrSlab(blockBelow.block) ||
                        isFenceOrWall(blockBelow.block) ||
                        blockBelow.block is CarpetBlock ||
                        blockBelow.block is SnowLayerBlock)

        val isCurrentPassable = blockAt.isAir ||
                isPassable(pos) ||
                isClimbable(blockAt.block)

        val result = isWalkableBelow && isCurrentPassable
        walkabilityCache[pos] = result
        return result
    }

    fun hasHeadroom(pos: BlockPos): Boolean {
        val block1 = ChunkCache.getBlockState(pos) ?: return false
        val block2 = ChunkCache.getBlockState(pos.above()) ?: return false

        return (block1.isAir || isPassable(pos)) &&
                (block2.isAir || isPassable(pos.above()))
    }

    fun isPassable(pos: BlockPos): Boolean {
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
}
