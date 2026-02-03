package com.riftaddons.pathfinder.cache

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import java.util.concurrent.ConcurrentHashMap

object ChunkCache {
    private val mc = Minecraft.getInstance()
    private val chunkMap = ConcurrentHashMap<Long, LevelChunk>()

    fun getBlockState(pos: BlockPos): BlockState? {
        val chunkX = pos.x shr 4
        val chunkZ = pos.z shr 4
        val key = getChunkKey(chunkX, chunkZ)

        var chunk = chunkMap[key]
        if (chunk == null) {
            val level = mc.level ?: return null
            try {
                chunk = level.getChunk(chunkX, chunkZ)
                if (chunk != null) {
                    chunkMap[key] = chunk
                }
            } catch (_: Exception) {
                return null
            }
        }

        return chunk?.getBlockState(pos)
    }

    fun isLoaded(pos: BlockPos): Boolean {
        // Use cache first if available
        val chunkX = pos.x shr 4
        val chunkZ = pos.z shr 4
        if (chunkMap.containsKey(getChunkKey(chunkX, chunkZ))) return true

        val level = mc.level ?: return false
        return level.hasChunk(chunkX, chunkZ)
    }

    private fun getChunkKey(chunkX: Int, chunkZ: Int): Long {
        return (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)
    }

    fun clear() {
        chunkMap.clear()
    }
}
