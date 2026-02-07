package com.riftaddons.pathfinder

class pathexecutor {
    companion object {
        private var currentPath: List<net.minecraft.core.BlockPos>? = null
        private var currentIndex = 0

        fun setPath(path: List<net.minecraft.core.BlockPos>) {
            currentPath = path
            currentIndex = 0
        }

        fun isFinished(): Boolean {
            val path = currentPath ?: return true
            return currentIndex >= path.size
        }

        fun getNextTarget(): net.minecraft.core.BlockPos? {
            val path = currentPath ?: return null
            if (currentIndex >= path.size) return null
            return path[currentIndex]
        }

        fun incrementProgress() {
            currentIndex++
        }

        fun clear() {
            currentPath = null
            currentIndex = 0
        }
    }
}

