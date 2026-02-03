package com.riftaddons.pathfinder.path

import com.riftaddons.pathfinder.movement.MovementHelper
import net.minecraft.core.BlockPos
import java.util.*
import kotlin.math.abs

class Astar(
    private val start: BlockPos,
    private val goal: BlockPos,
    private val maxDistance: Int = 500 // Don't pathfind too far
) {
    private val openSet = PriorityQueue<Node>(compareBy { it.fCost })
    private val closedSet = mutableSetOf<BlockPos>()
    private val allNodes = mutableMapOf<BlockPos, Node>()

    // Performance tracking
    private var nodesExplored = 0
    private var timeoutReached = false

    fun findPath(maxIterations: Int = 150000, timeoutMs: Long = 5000): List<BlockPos>? {
        val startTime = System.currentTimeMillis()

        // Quick distance check
        val directDistance = start.distManhattan(goal)
        if (directDistance > maxDistance) {
            return null
        }

        val startNode = Node(start, 0.0, heuristic(start, goal))
        openSet.add(startNode)
        allNodes[start] = startNode

        while (openSet.isNotEmpty() && nodesExplored < maxIterations) {
            // Check timeout
            if (System.currentTimeMillis() - startTime > timeoutMs) {
                timeoutReached = true
                break
            }

            val current = openSet.poll() ?: break
            nodesExplored++

            // Found goal
            if (current.pos == goal) {
                return smoothPath(reconstructPath(current))
            }

            closedSet.add(current.pos)

            // Get neighbors (optimized in MovementHelper)
            val neighbors = MovementHelper.getNeighbors(current.pos)

            for (neighborData in neighbors) {
                val neighborPos = neighborData.pos

                if (closedSet.contains(neighborPos)) continue

                // Skip if too far from goal
                if (neighborPos.distManhattan(goal) > maxDistance) continue

                val tentativeGCost = current.gCost + neighborData.cost

                val neighborNode = allNodes.getOrPut(neighborPos) {
                    Node(neighborPos, Double.MAX_VALUE, heuristic(neighborPos, goal))
                }

                if (tentativeGCost < neighborNode.gCost) {
                    openSet.remove(neighborNode)

                    neighborNode.parent = current
                    neighborNode.gCost = tentativeGCost
                    neighborNode.hCost = heuristic(neighborPos, goal)

                    openSet.add(neighborNode)
                }
            }
        }

        return null
    }

    // Better heuristic: Octile distance with proper weighting
    private fun heuristic(from: BlockPos, to: BlockPos): Double {
        val dx = abs(from.x - to.x).toDouble()
        val dy = abs(from.y - to.y).toDouble()
        val dz = abs(from.z - to.z).toDouble()

        // Octile distance for X/Z plane
        val dMax = maxOf(dx, dz)
        val dMin = minOf(dx, dz)
        val horizontalDist = (dMax - dMin) + 1.414 * dMin

        // Add vertical cost (more expensive to go up/down)
        val verticalDist = dy * 1.5

        // Greedy weighting (1.5) for much faster pathfinding
        return (horizontalDist + verticalDist) * 1.5
    }

    // Smooth the path to reduce unnecessary turns and make it more human-like
    private fun smoothPath(path: List<BlockPos>): List<BlockPos> {
        if (path.size <= 2) return path

        val smoothed = mutableListOf<BlockPos>()
        smoothed.add(path.first())

        var current = 0
        while (current < path.size - 1) {
            var farthest = current + 1

            // Try to find the farthest point we can directly move to
            for (i in current + 2 until path.size) {
                if (canDirectlyReach(path[current], path[i])) {
                    farthest = i
                } else {
                    break
                }
            }

            smoothed.add(path[farthest])
            current = farthest
        }

        return smoothed
    }

    // Check if we can directly reach from one position to another (straight line) without collisions
    private fun canDirectlyReach(from: BlockPos, to: BlockPos): Boolean {
        // Only smooth flat movements to avoid falling/clipping into floors/ceilings
        if (from.y != to.y) return false

        val dx = to.x - from.x
        val dz = to.z - from.z

        // Must be cardinal or diagonal
        if (abs(dx) != abs(dz) && dx != 0 && dz != 0) return false

        // Check every block along the path
        val steps = maxOf(abs(dx), abs(dz))
        val stepX = kotlin.math.sign(dx.toDouble()).toInt()
        val stepZ = kotlin.math.sign(dz.toDouble()).toInt()

        var current = from
        for (i in 1..steps) {
            val previous = current
            current = current.offset(stepX, 0, stepZ)

            // Check walkability (floor and headroom)
            if (!MovementHelper.canWalkOn(current) || !MovementHelper.hasHeadroom(current)) {
                return false
            }

            // For diagonal moves, prevent cutting through hard corners (both sides blocked)
            if (dx != 0 && dz != 0) {
                val side1 = previous.offset(stepX, 0, 0)
                val side2 = previous.offset(0, 0, stepZ)

                // If both adjacent corner blocks are not passable, we can't squeeze through
                // (Using hasHeadroom logic which checks for passability)
                // We just need passability of the block at head level (and feet level)
                // Simplest check: if both sides are solid walls, we blocked.
                if (!MovementHelper.isPassable(side1) && !MovementHelper.isPassable(side2)) {
                    return false
                }

                // Also similar check for head level?
                if (!MovementHelper.isPassable(side1.above()) && !MovementHelper.isPassable(side2.above())) {
                    return false
                }
            }
        }

        return true
    }

    private fun reconstructPath(endNode: Node): List<BlockPos> {
        val path = mutableListOf<BlockPos>()
        var current: Node? = endNode

        while (current != null) {
            path.add(0, current.pos)
            current = current.parent
        }

        return path
    }

    fun getOpenSetSize() = openSet.size
    fun getClosedSetSize() = closedSet.size
    fun getNodesExplored() = nodesExplored
    fun isTimeoutReached() = timeoutReached
}

