package com.prime.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Piper本地TTS引擎
 * 基于Piper ONNX模型
 */
class PiperTTS(private val context: Context) : ITTSEngine {
    
private var isInitialized = false
    private var isSpeakingFlag = false
    private var audioTrack: AudioTrack? = null
    
    private val modelPath = "/sdcard/PRIME/models/piper/zh_CN-huayan-medium.onnx"
    private val configPath = "/sdcard/PRIME/models/piper/zh_CN-huayan-medium.onnx.json"
    
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    
    override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // 检查模型文件
            val modelFile = File(modelPath)
            val configFile = File(configPath)
            
if (!modelFile.exists() || !configFile.exists()) {
                Timber.w("⚠️ Piper模型文件不存在")
                Timber.i("TTS功能将不可用，请下载模型到: $modelPath")
                Timber.i("下载地址: https://github.com/rhasspy/piper/releases")
                return@withContext false
            }
            
Timber.i("🔄 加载Piper ONNX模型...")
            
            // 初始化ONNX Runtime
            ortEnv = OrtEnvironment.getEnvironment()
            ortSession = ortEnv?.createSession(modelPath, OrtSession.SessionOptions())
            
            Timber.i("✅ Piper模型加载成功")
            Timber.i("   输入: ${ortSession?.inputNames}")
            Timber.i("   输出: ${ortSession?.outputNames}")
            
            isInitialized = true
            Timber.i("✅ Piper TTS初始化成功")
            true
        } catch (e: Exception) {
            Timber.e(e, "❌ Piper TTS初始化失败")
            false
        }
    }
    
    override suspend fun speak(
        text: String,
        language: String,
        speed: Float,
        pitch: Float
    ): Boolean = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Timber.w("⚠️ TTS引擎未初始化")
            return@withContext false
        }
        
try {
            isSpeakingFlag = true
            Timber.d("🔊 开始朗读: $text")
            
            // 文本预处理
            val processedText = preprocessText(text)
            
            // 调用Piper模型生成音频
            val audioData = generateAudio(processedText, speed, pitch)
            
            if (audioData.isEmpty()) {
                Timber.w("⚠️ 音频生成失败，使用静音占位")
                isSpeakingFlag = false
                return@withContext false
            }
            
            // 播放音频
            playAudio(audioData)
            
            isSpeakingFlag = false
            Timber.i("✅ TTS朗读完成")
            true
        } catch (e: Exception) {
            isSpeakingFlag = false
            Timber.e(e, "❌ TTS朗读失败")
            false
        }
    }
    
    override suspend fun stop() {
        audioTrack?.stop()
        audioTrack?.release()
        audioTrack = null
        isSpeakingFlag = false
    }
    
    override suspend fun pause() {
        audioTrack?.pause()
    }
    
    override suspend fun resume() {
        audioTrack?.play()
    }
    
    override fun isSpeaking(): Boolean = isSpeakingFlag
    
override suspend fun release() {
        stop()
        ortSession?.close()
        ortSession = null
        ortEnv = null
        isInitialized = false
        Timber.i("🧹 Piper TTS资源已清理")
    }
    
/**
     * 生成音频数据（使用ONNX Runtime推理）
     */
    private fun generateAudio(text: String, speed: Float, pitch: Float): ByteArray {
        try {
            if (ortSession == null || ortEnv == null) {
                Timber.w("⚠️ ONNX模型未加载")
                return ByteArray(0)
            }
            
            Timber.d("生成音频: $text (speed=$speed, pitch=$pitch)")
            
            // 1. 文本转音素ID（简化版）
            val phonemeIds = textToPhonemes(text)
            Timber.d("音素数量: ${phonemeIds.size}")
            
            // 2. 创建输入张量
            val inputShape = longArrayOf(1, phonemeIds.size.toLong())
            val inputBuffer = LongBuffer.wrap(phonemeIds.map { it.toLong() }.toLongArray())
            val inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, inputShape)
            
            // 3. 运行推理
            val inputs = mapOf("input" to inputTensor)
            val outputs = ortSession?.run(inputs) ?: run {
                inputTensor.close()
                Timber.e("❌ ONNX Session未初始化")
                return ByteArray(0)
            }
            
            // 4. 提取音频数据
            val outputTensor = outputs[0].value as FloatBuffer
            val audioFloats = FloatArray(outputTensor.remaining())
            outputTensor.get(audioFloats)
            
            // 5. 转换为PCM 16bit
            val audioBytes = floatToPCM16(audioFloats)
            
            // 6. 清理资源
            inputTensor.close()
            outputs.close()
            
            Timber.d("✅ 音频生成完成: ${audioBytes.size} bytes")
            return audioBytes
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 音频生成失败")
            return ByteArray(0)
        }
    }
    
    /**
     * 文本转音素（简化版）
     */
    private fun textToPhonemes(text: String): List<Int> {
        // 简化实现：每个字符映射到ID
        // 实际应该用专业的中文分词+音素转换
        return text.map { it.code % 256 }
    }
    
    /**
     * Float音频转PCM 16bit
     */
    private fun floatToPCM16(floats: FloatArray): ByteArray {
        val bytes = ByteArray(floats.size * 2)
        for (i in floats.indices) {
            val sample = (floats[i] * 32767f).toInt().coerceIn(-32768, 32767)
            bytes[i * 2] = (sample and 0xFF).toByte()
            bytes[i * 2 + 1] = ((sample shr 8) and 0xFF).toByte()
        }
        return bytes
    }
    
    /**
     * 文本预处理
     */
    private fun preprocessText(text: String): String {
        return text
            .replace(Regex("\\s+"), " ")
            .trim()
    }
    
    /**
     * 播放音频
     */
    private fun playAudio(audioData: ByteArray) {
        val sampleRate = 22050
        val channelConfig = AudioFormat.CHANNEL_OUT_MONO
        val audioFormat = AudioFormat.ENCODING_PCM_16BIT
        
        val bufferSize = AudioTrack.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        
        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelConfig)
                    .setEncoding(audioFormat)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .build()
        
        audioTrack?.play()
        audioTrack?.write(audioData, 0, audioData.size)
    }
}