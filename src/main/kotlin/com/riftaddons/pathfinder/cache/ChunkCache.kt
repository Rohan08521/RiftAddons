package com.riftaddons.pathfinder.cache

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import java.util.concurrent.ConcurrentHashMap

object ChunkCache {
    private val mc = Minecraft.getInstance()
    private val cache = ConcurrentHashMap<Long, CachedChunk>()
    private const val MAX_CACHE_SIZE = 256

    data class CachedChunk(
        val chunkX: Int,
        val chunkZ: Int,
        val timestamp: Long = System.currentTimeMillis()
    )

    fun getBlockState(pos: BlockPos): BlockState? {
        val level = mc.level ?: return null
        val chunkX = pos.x shr 4
        val chunkZ = pos.z shr 4
        val key = getChunkKey(chunkX, chunkZ)

        cache[key] = CachedChunk(chunkX, chunkZ)

        if (cache.size > MAX_CACHE_SIZE) {
            cleanOldEntries()
        }

        return try {
            val chunk = level.getChunk(chunkX, chunkZ)
            chunk?.getBlockState(pos)
        } catch (_: Exception) {
            null
        }
    }

    fun isLoaded(pos: BlockPos): Boolean {
        val level = mc.level ?: return false
        val chunkX = pos.x shr 4
        val chunkZ = pos.z shr 4
        return level.hasChunk(chunkX, chunkZ)
    }

    private fun getChunkKey(chunkX: Int, chunkZ: Int): Long {
        return (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)
    }

    private fun cleanOldEntries() {
        val sortedEntries = cache.entries.sortedBy { it.value.timestamp }
        val toRemove = sortedEntries.take(cache.size - MAX_CACHE_SIZE / 2)
        toRemove.forEach { cache.remove(it.key) }
    }

    fun clear() {
        cache.clear()
    }
}
