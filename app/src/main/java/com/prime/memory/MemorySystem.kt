package com.prime.memory

import android.content.Context
import java.util.UUID

/**
 * PRIME永久记忆系统
 * 
 * 核心特性：
 * 1. 永不删除记忆
 * 2. 遗忘曲线模拟
 * 3. 情感关联
 */
class MemorySystem(context: Context) {
    
    private val selector = MemorySelector()
    private val emotionDetector = EmotionDetector()
    private val storage = MemoryStorage(context)
    
    /**
     * 记录记忆
     */
    fun remember(content: String, context: Map<String, Any> = emptyMap()): Memory? {
        val importance = selector.calculateImportance(content, context)
        if (importance < 0.3f) return null
        
        val emotion = emotionDetector.detect(content)
        val keywords = extractKeywords(content)
        
        val memory = Memory(
            id = UUID.randomUUID().toString(),
            content = content,
            keywords = keywords,
            emotion = emotion,
            importance = importance,
            timestamp = System.currentTimeMillis(),
            context = context
        )
        
        storage.save(memory)
        return memory
    }
    
    /**
     * 回忆记忆（考虑遗忘曲线）
     */
    fun recall(query: String, limit: Int = 5): List<Memory> {
        // 1. 搜索匹配的记忆
        val keywordMatches = storage.search(query)
        val emotion = emotionDetector.detect(query)
        val emotionMatches = storage.searchByEmotion(emotion)
        
        // 2. 合并去重
        val allMatches = (keywordMatches + emotionMatches).distinctBy { it.id }
        
        // 3. 更新清晰度
        val withClarity = allMatches.map { memory ->
            memory.copy(clarity = ForgettingCurve.calculateClarity(memory))
        }
        
        // 4. 过滤太模糊的（清晰度<0.3）
        val clearMemories = withClarity.filter { it.clarity > 0.3f }
        
        // 5. 按清晰度*重要性排序
        return clearMemories
            .sortedByDescending { it.clarity * it.importance }
            .take(limit)
    }
    
    /**
     * 巩固记忆（访问后增强）
     */
    fun consolidate(memory: Memory): Memory {
        val currentClarity = ForgettingCurve.calculateClarity(memory)
        
        val newLevel = if (ForgettingCurve.shouldConsolidate(memory, currentClarity)) {
            (memory.consolidationLevel + 1).coerceAtMost(10)
        } else {
            memory.consolidationLevel
        }
        
        val consolidated = memory.copy(
            clarity = currentClarity,
            accessCount = memory.accessCount + 1,
            lastAccess = System.currentTimeMillis(),
            consolidationLevel = newLevel
        )
        
        storage.save(consolidated)
        return consolidated
    }
    
    private fun extractKeywords(content: String): List<String> {
        val stopWords = setOf("的", "了", "是", "在", "我", "你")
        return content.split(Regex("[\\s，。！？]"))
            .filter { it.length >= 2 && it !in stopWords }
            .distinct()
            .take(5)
    }
}