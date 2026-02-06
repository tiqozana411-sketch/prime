package com.prime.speech

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.telecom.Call
import android.telecom.InCallService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream

/**
 * 通话音频拦截器
 * 拦截QQ/微信/电话的通话音频，实时识别语音指令
 */
class CallAudioInterceptor(
    private val context: Context,
    private val sttEngine: ISTTEngine,
    private val onCommandReceived: (VoiceCommand) -> Unit
) {
    
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recordingJob: Job? = null
    
    private val _callState = MutableStateFlow<CallState>(CallState.Idle)
    val callState: StateFlow<CallState> = _callState
    
    // 音频参数
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    // 音频缓冲区
    private val audioBuffer = mutableListOf<Short>()
    private val bufferDuration = 3000 // 3秒缓冲
    
    /**
     * 开始拦截通话音频
     */
    fun startIntercepting(callType: CallType) {
        if (isRecording) {
            Timber.w("⚠️ 已在拦截通话音频")
            return
        }
        
        _callState.value = CallState.Active(callType)
        
        try {
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            
            // 使用VOICE_COMMUNICATION音频源（通话音频）
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Timber.e("❌ AudioRecord初始化失败")
                return
            }
            
            audioRecord?.startRecording()
            isRecording = true
            
            Timber.i("✅ 开始拦截${callType.name}通话音频")
            
            // 启动录音协程
            recordingJob = CoroutineScope(Dispatchers.IO).launch {
                processAudioStream(bufferSize)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 启动音频拦截失败")
            _callState.value = CallState.Error(e.message ?: "未知错误")
        }
    }
    
    /**
     * 停止拦截
     */
    fun stopIntercepting() {
        isRecording = false
        recordingJob?.cancel()
        
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
        
        audioBuffer.clear()
        _callState.value = CallState.Idle
        
        Timber.i("🛑 停止拦截通话音频")
    }
    
    /**
     * 处理音频流
     */
    private suspend fun processAudioStream(bufferSize: Int) {
        val buffer = ShortArray(bufferSize)
        
        while (isRecording) {
            try {
                val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                
                if (readSize > 0) {
                    // 添加到缓冲区
                    audioBuffer.addAll(buffer.take(readSize))
                    
                    // 每3秒识别一次
                    if (audioBuffer.size >= sampleRate * bufferDuration / 1000) {
                        processAudioBuffer()
                    }
                }
                
            } catch (e: Exception) {
                Timber.e(e, "❌ 音频流处理错误")
                delay(100)
            }
        }
    }
    
    /**
     * 处理音频缓冲区
     */
    private suspend fun processAudioBuffer() {
        if (audioBuffer.isEmpty()) return
        
        try {
            // 保存音频到临时文件
            val tempFile = File(context.cacheDir, "call_audio_${System.currentTimeMillis()}.pcm")
            saveAudioToFile(audioBuffer.toShortArray(), tempFile)
            
            // 调用STT识别
            val result = sttEngine.recognizeFile(tempFile.absolutePath, "zh-CN")
            
            if (result?.success == true && result.text.isNotBlank()) {
                Timber.i("🎤 识别到通话语音: ${result.text}")
                
                // 解析为指令
                val command = VoiceCommandParser.parse(result.text)
                if (command != null) {
                    Timber.i("✅ 解析到指令: $command")
                    onCommandReceived(command)
                }
            }
            
            // 清理临时文件
            tempFile.delete()
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 音频缓冲区处理失败")
        } finally {
            audioBuffer.clear()
        }
    }
    
    /**
     * 保存音频到文件
     */
    private fun saveAudioToFile(audioData: ShortArray, file: File) {
        FileOutputStream(file).use { fos ->
            audioData.forEach { sample ->
                fos.write(sample.toInt() and 0xFF)
                fos.write((sample.toInt() shr 8) and 0xFF)
            }
        }
    }
}

/**
 * 通话状态
 */
sealed class CallState {
    object Idle : CallState()
    data class Active(val type: CallType) : CallState()
    data class Error(val message: String) : CallState()
}

/**
 * 通话类型
 */
enum class CallType {
    PHONE,      // 电话
    WECHAT,     // 微信
    QQ,         // QQ
    OTHER       // 其他
}