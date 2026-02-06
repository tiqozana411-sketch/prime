package com.prime.memory

/**
 * 情感检测器
 */
class EmotionDetector {
    
    fun detect(content: String): Emotion {
        val text = content.lowercase()
        
        if (text.matches(Regex(".*?(好|行|可以|要|继续|对|是的|太好|完美|厉害|赞).*"))) {
            return Emotion.POSITIVE
        }
        
        if (text.matches(Regex(".*?(不够|不行|不对|错|失望|啥|不懂).*"))) {
            return Emotion.NEGATIVE
        }
        
        if (text.matches(Regex(".*?(为什么|怎么|如何|是什么|能不能|会不会|是不是).*"))) {
            return Emotion.CURIOUS
        }
        
        return Emotion.NEUTRAL
    }
}