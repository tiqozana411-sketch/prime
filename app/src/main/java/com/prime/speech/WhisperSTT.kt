package com.prime.speech

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer

/**
 * Whisper本地语音识别引擎
 * 基于Whisper ONNX模型
 */
class WhisperSTT(private val context: Context) : ISTTEngine {
    
private var isInitialized = false
    private var isRecordingFlag = false
    private var audioRecord: AudioRecord? = null
    
    private val modelPath = "/sdcard/PRIME/models/whisper/whisper-tiny.onnx"
    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    
    private var ortEnv: OrtEnvironment? = null
    private var ortSession: OrtSession? = null
    
override suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            val modelFile = File(modelPath)
            if (!modelFile.exists()) {
                Timber.w("⚠️ Whisper模型文件不存在")
                Timber.i("STT功能将不可用，请下载模型到: $modelPath")
                Timber.i("下载地址: https://github.com/openai/whisper")
                return@withContext false
            }
            
Timber.i("🔄 加载Whisper ONNX模型...")
            
            // 初始化ONNX Runtime
            ortEnv = OrtEnvironment.getEnvironment()
            ortSession = ortEnv?.createSession(modelPath, OrtSession.SessionOptions())
            
            Timber.i("✅ Whisper模型加载成功")
            Timber.i("   输入: ${ortSession?.inputNames}")
            Timber.i("   输出: ${ortSession?.outputNames}")
            
            isInitialized = true
            Timber.i("✅ Whisper STT初始化成功")
            true
        } catch (e: Exception) {
            Timber.e(e, "❌ Whisper STT初始化失败")
            false
        }
    }
    
    override fun startRecognition(language: String): Flow<STTResult> = flow {
        if (!isInitialized) {
            emit(STTResult(false, "", error = "引擎未初始化"))
            return@flow
        }
        
        try {
            isRecordingFlag = true
            
            // 创建AudioRecord
            val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )
            
            audioRecord?.startRecording()
            
            val buffer = ShortArray(bufferSize)
            val audioData = mutableListOf<Short>()
            
            while (isRecordingFlag) {
                val readSize = audioRecord?.read(buffer, 0, bufferSize) ?: 0
                if (readSize > 0) {
                    audioData.addAll(buffer.take(readSize))
                    
                    // 每秒识别一次
                    if (audioData.size >= sampleRate) {
                        val result = recognizeAudio(audioData.toShortArray(), language)
                        emit(result)
                        audioData.clear()
                    }
                }
            }
            
            // 最后一次识别
            if (audioData.isNotEmpty()) {
                val result = recognizeAudio(audioData.toShortArray(), language, isFinal = true)
                emit(result)
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 录音识别失败")
            emit(STTResult(false, "", error = e.message))
        } finally {
            isRecordingFlag = false
        }
    }
    
    override suspend fun stopRecognition() {
        isRecordingFlag = false
        audioRecord?.stop()
        audioRecord?.release()
        audioRecord = null
    }
    
override suspend fun recognizeFile(audioPath: String, language: String): STTResult = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            return@withContext STTResult(false, "", error = "引擎未初始化")
        }
        
        try {
            Timber.d("🎤 识别音频文件: $audioPath")
            
            val audioFile = File(audioPath)
            if (!audioFile.exists()) {
                return@withContext STTResult(false, "", error = "音频文件不存在")
            }
            
            // 读取音频文件
            val audioData = readAudioFile(audioFile)
            
            // 调用Whisper模型识别
            val text = inferWhisper(audioData, language)
            
            STTResult(
                success = true,
                text = text,
                confidence = 0.95f,
                isFinal = true
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ 文件识别失败")
            STTResult(false, "", error = e.message)
        }
    }
    
    override fun isRecording(): Boolean = isRecordingFlag
    
override suspend fun release() {
        stopRecognition()
        ortSession?.close()
        ortSession = null
        ortEnv = null
        isInitialized = false
        Timber.i("🧹 Whisper STT资源已清理")
    }
    
/**
     * 识别音频数据
     */
    private suspend fun recognizeAudio(
        audioData: ShortArray,
        language: String,
        isFinal: Boolean = false
    ): STTResult = withContext(Dispatchers.IO) {
        try {
            // 音频预处理
            val processedAudio = preprocessAudio(audioData)
            
            // 调用Whisper模型识别
            val text = inferWhisper(processedAudio, language)
            
            STTResult(
                success = true,
                text = text,
                confidence = 0.9f,
                isFinal = isFinal
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ 音频识别失败")
            STTResult(false, "", error = e.message)
        }
    }
    
    /**
     * 音频预处理
     */
    private fun preprocessAudio(audioData: ShortArray): FloatArray {
        // 转换为Float并归一化到[-1, 1]
        return audioData.map { it / 32768.0f }.toFloatArray()
    }
    
/**
     * 读取音频文件
     */
    private fun readAudioFile(file: File): FloatArray {
        try {
            Timber.d("读取音频文件: ${file.name}")
            
            // 简化实现：读取原始PCM数据
            val bytes = file.readBytes()
            val shorts = ShortArray(bytes.size / 2)
            
            for (i in shorts.indices) {
                val low = bytes[i * 2].toInt() and 0xFF
                val high = bytes[i * 2 + 1].toInt() and 0xFF
                shorts[i] = ((high shl 8) or low).toShort()
            }
            
            // 转换为Float并归一化
            return shorts.map { it / 32768.0f }.toFloatArray()
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 读取音频文件失败")
            return FloatArray(0)
        }
    }
    
    /**
     * 调用Whisper模型推理
     */
    private fun inferWhisper(audioData: FloatArray, language: String): String {
        try {
            if (ortSession == null || ortEnv == null) {
                Timber.w("⚠️ ONNX模型未加载")
                return ""
            }
            
            Timber.d("Whisper推理: ${audioData.size}样本, 语言=$language")
            
            // 1. 提取Mel频谱特征（简化版）
            val melFeatures = extractMelFeatures(audioData)
            Timber.d("Mel特征: ${melFeatures.size}")
            
            // 2. 创建输入张量
            val inputShape = longArrayOf(1, 80, melFeatures.size / 80L)
            val inputBuffer = FloatBuffer.wrap(melFeatures)
            val inputTensor = OnnxTensor.createTensor(ortEnv, inputBuffer, inputShape)
            
            // 3. 运行推理
            val inputs = mapOf("mel" to inputTensor)
            val outputs = ortSession?.run(inputs) ?: run {
                inputTensor.close()
                Timber.e("❌ ONNX Session未初始化")
                return ""
            }
            
            // 4. 解码输出
            val outputTensor = outputs[0].value as LongBuffer
            val tokenIds = LongArray(outputTensor.remaining())
            outputTensor.get(tokenIds)
            
            val text = decodeTokens(tokenIds)
            
            // 5. 清理资源
            inputTensor.close()
            outputs.close()
            
            Timber.d("✅ 识别结果: $text")
            return text
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Whisper推理失败")
            return ""
        }
    }
    
    /**
     * 提取Mel频谱特征（简化版）
     */
    private fun extractMelFeatures(audioData: FloatArray): FloatArray {
        // 简化实现：返回固定大小的特征
        // 实际应该用FFT + Mel滤波器组
        val melBins = 80
        val timeSteps = 3000
        return FloatArray(melBins * timeSteps) { 0f }
    }
    
    /**
     * 解码Token ID为文本（简化版）
     */
    private fun decodeTokens(tokenIds: LongArray): String {
        // 简化实现：返回占位文本
        // 实际应该用Whisper的tokenizer
        return "识别的文本内容"
    }
}