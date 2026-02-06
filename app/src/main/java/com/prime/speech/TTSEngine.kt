package com.prime.speech

import kotlinx.coroutines.flow.Flow

/**
 * TTS引擎接口
 */
interface ITTSEngine {
    
    /**
     * 初始化引擎
     */
    suspend fun initialize(): Boolean
    
    /**
     * 文字转语音
     * @param text 要朗读的文本
     * @param language 语言代码（zh-CN/en-US）
     * @param speed 语速（0.5-2.0）
     * @param pitch 音调（0.5-2.0）
     */
    suspend fun speak(
        text: String,
        language: String = "zh-CN",
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ): Boolean
    
    /**
     * 停止朗读
     */
    suspend fun stop()
    
    /**
     * 暂停朗读
     */
    suspend fun pause()
    
    /**
     * 继续朗读
     */
    suspend fun resume()
    
    /**
     * 是否正在朗读
     */
    fun isSpeaking(): Boolean
    
    /**
     * 释放资源
     */
    suspend fun release()
}

/**
 * TTS结果
 */
data class TTSResult(
    val success: Boolean,
    val text: String,
    val duration: Long = 0,
    val error: String? = null
)