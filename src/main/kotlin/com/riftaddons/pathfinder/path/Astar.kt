package com.riftaddons.pathfinder.path

import com.riftaddons.pathfinder.movement.MovementHelper
import net.minecraft.core.BlockPos
import java.util.*
import kotlin.math.abs
import kotlin.math.sqrt

class Astar(
    private val start: BlockPos,
    private val goal: BlockPos
) {
    private val openSet = PriorityQueue<Node>(compareBy { it.fCost })
    private val closedSet = mutableSetOf<BlockPos>()
    private val allNodes = mutableMapOf<BlockPos, Node>()

    fun findPath(maxIterations: Int = 10000): List<BlockPos>? {
        val startNode = Node(start, 0.0, heuristic(start, goal))
        openSet.add(startNode)
        allNodes[start] = startNode

        var iterations = 0

        while (openSet.isNotEmpty() && iterations < maxIterations) {
            iterations++

            val current = openSet.poll() ?: break

            if (current.pos == goal) {
                return reconstructPath(current)
            }

            closedSet.add(current.pos)

            val neighbors = MovementHelper.getNeighbors(current.pos)

            for (neighborData in neighbors) {
                val neighborPos = neighborData.pos

                if (closedSet.contains(neighborPos)) continue

                val tentativeGCost = current.gCost + neighborData.cost

                val neighborNode = allNodes.getOrPut(neighborPos) {
                    Node(neighborPos, Double.MAX_VALUE, heuristic(neighborPos, goal))
                }

                if (tentativeGCost < neighborNode.gCost) {
                    neighborNode.parent = current
                    neighborNode.gCost = tentativeGCost
                    neighborNode.hCost = heuristic(neighborPos, goal)

                    if (!openSet.contains(neighborNode)) {
                        openSet.add(neighborNode)
                    }
                }
            }
        }

        return null // No path found
    }

    private fun heuristic(from: BlockPos, to: BlockPos): Double {
        val dx = abs(from.x - to.x).toDouble()
        val dy = abs(from.y - to.y).toDouble()
        val dz = abs(from.z - to.z).toDouble()

        // Octile distance (allows diagonal movement)
        val dMax = maxOf(dx, dz)
        val dMin = minOf(dx, dz)
        return (dMax - dMin) + sqrt(2.0) * dMin + dy
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
}

