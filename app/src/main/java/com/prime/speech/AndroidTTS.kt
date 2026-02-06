package com.prime.speech

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.*
import kotlin.coroutines.resume

/**
 * Android原生TTS引擎
 * 作为备用方案
 */
class AndroidTTS(private val context: Context) : ITTSEngine {
    
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var isSpeakingFlag = false
    
    override suspend fun initialize(): Boolean = suspendCancellableCoroutine { continuation ->
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.CHINESE
                isInitialized = true
                Timber.i("✅ Android TTS初始化成功")
                continuation.resume(true)
            } else {
                Timber.e("❌ Android TTS初始化失败")
                continuation.resume(false)
            }
        }
    }
    
    override suspend fun speak(
        text: String,
        language: String,
        speed: Float,
        pitch: Float
    ): Boolean = withContext(Dispatchers.Main) {
        if (!isInitialized || tts == null) {
            Timber.w("⚠️ TTS引擎未初始化")
            return@withContext false
        }
        
        try {
            // 设置语言
            val locale = when (language) {
                "zh-CN" -> Locale.CHINESE
                "en-US" -> Locale.US
                else -> Locale.CHINESE
            }
            tts?.language = locale
            
            // 设置语速和音调
            tts?.setSpeechRate(speed)
            tts?.setPitch(pitch)
            
            // 朗读
            isSpeakingFlag = true
            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
            
            if (result == TextToSpeech.SUCCESS) {
                Timber.i("✅ TTS朗读: $text")
                true
            } else {
                isSpeakingFlag = false
                Timber.e("❌ TTS朗读失败")
                false
            }
        } catch (e: Exception) {
            isSpeakingFlag = false
            Timber.e(e, "❌ TTS朗读异常")
            false
        }
    }
    
    override suspend fun stop() {
        tts?.stop()
        isSpeakingFlag = false
    }
    
    override suspend fun pause() {
        // Android TTS不支持暂停
        stop()
    }
    
    override suspend fun resume() {
        // Android TTS不支持恢复
    }
    
    override fun isSpeaking(): Boolean = isSpeakingFlag
    
    override suspend fun release() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
    }
}