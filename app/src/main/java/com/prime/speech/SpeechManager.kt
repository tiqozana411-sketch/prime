package com.prime.speech

import android.content.Context
import kotlinx.coroutines.flow.Flow
import timber.log.Timber

/**
 * 语音管理器
 * 统一管理TTS和STT
 */
class SpeechManager(private val context: Context) {
    
    private var ttsEngine: ITTSEngine? = null
    private var sttEngine: ISTTEngine? = null
    
    private var usePiperTTS = false // 是否使用Piper（默认用Android TTS）
    
    /**
     * 初始化
     */
    suspend fun initialize(usePiper: Boolean = false): Boolean {
        usePiperTTS = usePiper
        
        // 初始化TTS
        ttsEngine = if (usePiper) {
            PiperTTS(context).also {
                if (!it.initialize()) {
                    Timber.w("⚠️ Piper TTS初始化失败，回退到Android TTS")
                    AndroidTTS(context).also { fallback ->
                        fallback.initialize()
                    }
                } else it
            }
        } else {
            AndroidTTS(context).also { it.initialize() }
        }
        
        // 初始化STT
        sttEngine = WhisperSTT(context).also {
            if (!it.initialize()) {
                Timber.w("⚠️ Whisper STT初始化失败")
            }
        }
        
        return ttsEngine != null
    }
    
    /**
     * 朗读文本
     */
    suspend fun speak(
        text: String,
        language: String = "zh-CN",
        speed: Float = 1.0f,
        pitch: Float = 1.0f
    ): Boolean {
        return ttsEngine?.speak(text, language, speed, pitch) ?: false
    }
    
    /**
     * 停止朗读
     */
    suspend fun stopSpeaking() {
        ttsEngine?.stop()
    }
    
    /**
     * 开始语音识别
     */
    fun startListening(language: String = "zh-CN"): Flow<STTResult>? {
        return sttEngine?.startRecognition(language)
    }
    
    /**
     * 停止语音识别
     */
    suspend fun stopListening() {
        sttEngine?.stopRecognition()
    }
    
    /**
     * 识别音频文件
     */
    suspend fun recognizeFile(audioPath: String, language: String = "zh-CN"): STTResult? {
        return sttEngine?.recognizeFile(audioPath, language)
    }
    
    /**
     * 是否正在朗读
     */
    fun isSpeaking(): Boolean = ttsEngine?.isSpeaking() ?: false
    
    /**
     * 是否正在录音
     */
    fun isListening(): Boolean = sttEngine?.isRecording() ?: false
    
    /**
     * 释放资源
     */
    suspend fun release() {
        ttsEngine?.release()
        sttEngine?.release()
    }
}