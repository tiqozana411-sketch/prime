package com.prime.memory

/**
 * 记忆数据类
 * 
 * 永久记忆系统的核心数据结构
 * 记忆永不删除，只会随时间模糊
 */
data class Memory(
    val id: String,
    val content: String,
    val keywords: List<String>,
    val emotion: Emotion,
    val importance: Float,              // 初始重要性 (0.0-1.0)
    val timestamp: Long,                 // 创建时间
    val context: Map<String, Any> = emptyMap(),
    
    // 遗忘曲线相关
    val clarity: Float = 1.0f,           // 清晰度 (0.1-1.0)
    val accessCount: Int = 0,            // 访问次数
    val lastAccess: Long = timestamp,    // 最后访问时间
    val consolidationLevel: Int = 0      // 巩固等级 (0-10)
)

/**
 * 情感枚举
 */
enum class Emotion(val valence: Float) {
    POSITIVE(1.0f),      // 积极
    NEGATIVE(-1.0f),     // 消极
    CURIOUS(0.5f),       // 好奇
    NEUTRAL(0.0f);       // 中性
    
    fun isPositive() = valence > 0.3f
    fun isNegative() = valence < -0.3f
}