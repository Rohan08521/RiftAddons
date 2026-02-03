package com.riftaddons.pathfinder.config

import kotlin.math.pow

object Costs {
    private const val SPRINT_MOVEMENT_FACTOR = 0.13
    private const val WALKING_MOVEMENT_FACTOR = 0.1
    private const val SNEAKING_MOVEMENT_FACTOR = 0.03
    private const val JUMP_BOOST_LEVEL = -1

    val INF_COST = 1e6
    val N_BLOCK_FALL_COST: DoubleArray = generateNBlocksFallCost()
    val ONE_UP_LADDER_COST: Double = 1 / (0.12 * 9.8)
    val ONE_DOWN_LADDER_COST: Double = 1 / 0.15

    val ONE_BLOCK_WALK_COST = 1 / actionTime(getWalkingFriction(WALKING_MOVEMENT_FACTOR))
    val ONE_BLOCK_SPRINT_COST = 1 / actionTime(getWalkingFriction(SPRINT_MOVEMENT_FACTOR))
    val ONE_BLOCK_SNEAK_COST = 1 / actionTime(getWalkingFriction(SNEAKING_MOVEMENT_FACTOR))

    val ONE_BLOCK_WALK_IN_WATER_COST = 20 * actionTime(getWalkingInWaterFriction(WALKING_MOVEMENT_FACTOR))
    val ONE_BLOCK_WALK_OVER_SOUL_SAND_COST = ONE_BLOCK_WALK_COST * 2

    val WALK_OFF_ONE_BLOCK_COST = ONE_BLOCK_WALK_COST * 0.8
    val CENTER_AFTER_FALL_COST = ONE_BLOCK_WALK_COST * 0.2

    val SPRINT_MULTIPLIER = WALKING_MOVEMENT_FACTOR / SPRINT_MOVEMENT_FACTOR

    val JUMP_ONE_BLOCK_COST: Double

    init {
        var vel = 0.42 + (JUMP_BOOST_LEVEL + 1) * 0.1
        var height = 0.0
        var time = 1.0
        for (i in 1..20) {
            height += vel
            vel = (vel - 0.08) * 0.98
            if (vel < 0)
                break
            time++
        }
        JUMP_ONE_BLOCK_COST = time + fallDistanceToTicks(height - 1)
    }

    // Humanized costs - lower is better, prioritize natural movements
    var WALK_COST = 1.0  // Base cost - walking is natural
    var DIAGONAL_COST = 1.4  // Slightly more expensive than cardinal
    var JUMP_COST = 2.5  // Avoid jumping unless necessary
    var ASCEND_COST = 2.0  // Going up is harder
    var DESCEND_COST = 1.1  // Going down one block is easy
    var FALL_COST_BASE = 1.5  // Base cost for falling
    var FALL_COST_PER_BLOCK = 0.5  // Each block fallen adds this
    var PARKOUR_COST_MULTIPLIER = 10.0  // Discourage parkour heavily
    var CLIMB_COST = 3.0  // Climbing is slow

    var STAIR_COST = 1.0  // Stairs are same as normal blocks for walking
    var SLAB_COST = 1.0  // Slabs are same as normal blocks for walking
    var STAIR_STEP_UP_COST = 1.8  // Going up stairs (cheaper than jumping: 2.5)
    var SLAB_STEP_UP_COST = 1.5  // Going up slabs (cheaper than jumping: 2.5)
    var FENCE_COST = 5.0  // Jumping over fences is unnatural
    var WALL_COST = 5.0  // Jumping over walls is unnatural
    var CARPET_COST = 1.0  // Carpets are normal
    var SNOW_LAYER_COST = 1.05  // Slightly harder

    var WATER_COST = 8.0  // Water is very slow
    var LAVA_COST = INF_COST  // Never walk through lava
    var SOUL_SAND_COST = 3.0  // Soul sand is slow
    var HONEY_BLOCK_COST = 4.0  // Honey is sticky
    var SLIME_BLOCK_COST = 2.0  // Slime is bouncy
    var ICE_COST = 0.85  // Ice is fast
    var PACKED_ICE_COST = 0.8  // Packed ice is faster
    var BLUE_ICE_COST = 0.75  // Blue ice is fastest

    var LADDER_UP_COST = 3.0  // Ladders are slow to climb
    var LADDER_DOWN_COST = 2.5  // Faster going down
    var VINE_UP_COST = 3.5  // Vines are slower
    var VINE_DOWN_COST = 3.0
    var SCAFFOLDING_COST = 2.0  // Scaffolding is easier

    var NEAR_LAVA_PENALTY = 50.0  // Avoid near lava
    var NEAR_VOID_PENALTY = 100.0  // Strongly avoid void
    var TIGHT_SPACE_PENALTY = 10.0  // Avoid tight spaces

    private fun getWalkingFriction(landMovementFactor: Double): Double {
        return landMovementFactor * ((0.16277136) / (0.91 * 0.91 * 0.91))
    }

    private fun getWalkingInWaterFriction(landMovementFactor: Double): Double {
        return 0.02 + (landMovementFactor - 0.02) * (1.0 / 3.0)
    }

    private fun actionTime(friction: Double): Double {
        return friction * 10
    }


    fun fallDistanceToTicks(distance: Double): Double {
        if (distance == 0.0) return 0.0
        var tmpDistance = distance
        var tickCount = 0
        while (true) {
            val fallDistance = downwardMotionAtTick(tickCount)
            if (tmpDistance <= fallDistance) {
                return tickCount + tmpDistance / fallDistance
            }
            tmpDistance -= fallDistance
            tickCount++
        }
    }

    private fun downwardMotionAtTick(tick: Int): Double {
        return (0.98.pow(tick.toDouble()) - 1) * -3.92
    }

    private fun generateNBlocksFallCost(): DoubleArray {
        val timeCost = DoubleArray(257)
        var currentDistance = 0.0
        var targetDistance = 1
        var tickCount = 0

        while (true) {
            val velocityAtTick = downwardMotionAtTick(tickCount)

            if (currentDistance + velocityAtTick >= targetDistance) {
                timeCost[targetDistance] = tickCount + (targetDistance - currentDistance) / velocityAtTick
                targetDistance++
                if (targetDistance > 256) break
                continue
            }

            currentDistance += velocityAtTick
            tickCount++
        }
        return timeCost
    }

    fun calculateFallCost(dropDistance: Int): Double {
        return if (dropDistance in 0..256) {
            N_BLOCK_FALL_COST[dropDistance] + CENTER_AFTER_FALL_COST
        } else {
            fallDistanceToTicks(dropDistance.toDouble()) + CENTER_AFTER_FALL_COST
        }
    }

    fun calculateParkourCost(distance: Double): Double {
        return JUMP_ONE_BLOCK_COST * distance * PARKOUR_COST_MULTIPLIER
    }

    fun resetToDefaults() {
        WALK_COST = 1.0
        DIAGONAL_COST = 1.4
        JUMP_COST = 2.5
        ASCEND_COST = 2.0
        DESCEND_COST = 1.1
        FALL_COST_BASE = 1.5
        FALL_COST_PER_BLOCK = 0.5
        PARKOUR_COST_MULTIPLIER = 10.0
        CLIMB_COST = 3.0

        STAIR_COST = 1.0
        SLAB_COST = 1.0
        STAIR_STEP_UP_COST = 1.8
        SLAB_STEP_UP_COST = 1.5
        FENCE_COST = 5.0
        WALL_COST = 5.0
        CARPET_COST = 1.0
        SNOW_LAYER_COST = 1.05

        WATER_COST = 8.0
        LAVA_COST = INF_COST
        SOUL_SAND_COST = 3.0
        HONEY_BLOCK_COST = 4.0
        SLIME_BLOCK_COST = 2.0
        ICE_COST = 0.85
        PACKED_ICE_COST = 0.8
        BLUE_ICE_COST = 0.75

        LADDER_UP_COST = 3.0
        LADDER_DOWN_COST = 2.5
        VINE_UP_COST = 3.5
        VINE_DOWN_COST = 3.0
        SCAFFOLDING_COST = 2.0

        NEAR_LAVA_PENALTY = 50.0
        NEAR_VOID_PENALTY = 100.0
        TIGHT_SPACE_PENALTY = 10.0
    }
}
