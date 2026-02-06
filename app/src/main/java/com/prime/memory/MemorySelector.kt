package com.prime.memory

/**
 * 记忆选择器
 * 
 * 判断哪些信息值得记住
 */
class MemorySelector {
    
    private val keywordWeights = mapOf(
        "PRIME" to 0.9f,
        "项目" to 0.8f,
        "核心" to 0.8f,
        "重要" to 0.7f,
        "功能" to 0.6f,
        "系统" to 0.6f,
        "为什么" to 0.5f,
        "怎么" to 0.5f
    )
    
    fun calculateImportance(content: String, context: Map<String, Any>): Float {
        var score = 0.3f
        
        // 关键词加分
        keywordWeights.forEach { (keyword, weight) ->
            if (content.contains(keyword, ignoreCase = true)) {
                score += weight * 0.3f
            }
        }
        
        // 长度加分
        score += when {
            content.length > 100 -> 0.15f
            content.length > 50 -> 0.1f
            content.length > 20 -> 0.05f
            else -> 0.0f
        }
        
        // 情感强度加分
        score += when {
            content.contains(Regex("太好|完美|厉害|不行|不对|错")) -> 0.2f
            content.contains(Regex("为什么|怎么|能不能")) -> 0.15f
            else -> 0.0f
        }
        
        return score.coerceIn(0f, 1f)
    }
}