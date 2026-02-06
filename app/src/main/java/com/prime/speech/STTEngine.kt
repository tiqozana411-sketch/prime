package com.prime.speech

import kotlinx.coroutines.flow.Flow

/**
 * STT语音识别引擎接口
 */
interface ISTTEngine {
    
    /**
     * 初始化引擎
     */
    suspend fun initialize(): Boolean
    
    /**
     * 开始录音识别
     * @param language 语言代码
     * @return 识别结果流
     */
    fun startRecognition(language: String = "zh-CN"): Flow<STTResult>
    
    /**
     * 停止录音
     */
    suspend fun stopRecognition()
    
    /**
     * 识别音频文件
     * @param audioPath 音频文件路径
     */
    suspend fun recognizeFile(audioPath: String, language: String = "zh-CN"): STTResult
    
    /**
     * 是否正在录音
     */
    fun isRecording(): Boolean
    
    /**
     * 释放资源
     */
    suspend fun release()
}

/**
 * STT识别结果
 */
data class STTResult(
    val success: Boolean,
    val text: String,
    val confidence: Float = 0f,
    val isFinal: Boolean = false,
    val duration: Long = 0,
    val error: String? = null
)