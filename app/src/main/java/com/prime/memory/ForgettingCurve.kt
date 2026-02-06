package com.prime.memory

import kotlin.math.exp
import kotlin.math.ln

/**
 * 遗忘曲线计算器
 * 
 * 基于Ebbinghaus遗忘曲线理论
 * 记忆会随时间衰减，但不会完全消失
 */
object ForgettingCurve {
    
    /**
     * 计算记忆清晰度
     * 
     * 公式: R = e^(-t/S)
     * R = 记忆保持率（清晰度）
     * t = 时间（天）
     * S = 记忆强度
     */
    fun calculateClarity(memory: Memory): Float {
        val now = System.currentTimeMillis()
        val daysPassed = (now - memory.lastAccess) / (24 * 60 * 60 * 1000f)
        
        // 计算记忆强度
        val strength = calculateStrength(memory)
        
        // 遗忘曲线
        val clarity = exp(-daysPassed / strength).toFloat()
        
        // 最低保持10%清晰度（永不完全遗忘）
        return clarity.coerceIn(0.1f, 1.0f)
    }
    
    /**
     * 计算记忆强度
     * 
     * 强度由三部分组成：
     * 1. 初始重要性（基础）
     * 2. 巩固等级（学习效果）
     * 3. 访问次数（重复强化）
     */
    private fun calculateStrength(memory: Memory): Float {
        val importanceComponent = memory.importance * 10f
        val consolidationComponent = memory.consolidationLevel * 5f
        val accessComponent = ln(memory.accessCount + 1f)
        
        return importanceComponent + consolidationComponent + accessComponent
    }
    
    /**
     * 判断是否需要巩固
     * 
     * 当清晰度恢复到0.8以上，且访问次数>3时
     * 可以提升巩固等级
     */
    fun shouldConsolidate(memory: Memory, currentClarity: Float): Boolean {
        return currentClarity > 0.8f && 
               memory.accessCount > 3 && 
               memory.consolidationLevel < 10
    }
}