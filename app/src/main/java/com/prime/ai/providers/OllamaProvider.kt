package com.prime.ai.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * Ollama Provider
 * 支持本地Ollama服务（http://localhost:11434）
 */
class OllamaProvider : AIProvider {
    
    private var config: AIConfig? = null
    
    override suspend fun initialize(config: AIConfig): Boolean {
        this.config = config
        
        val baseUrl = config.baseUrl.ifEmpty { "http://localhost:11434" }
        Timber.i("✅ Ollama初始化成功: $baseUrl")
        return true
    }
    
    override suspend fun inference(prompt: String): String = withContext(Dispatchers.IO) {
        val cfg = config ?: throw IllegalStateException("未初始化")
        
        try {
            val baseUrl = cfg.baseUrl.ifEmpty { "http://localhost:11434" }
            val url = "$baseUrl/api/generate"
            
            Timber.d("🌐 调用Ollama: $url")
            
            // 构建请求体
            val requestBody = JSONObject().apply {
                put("model", cfg.model.ifEmpty { "llama2" })
                put("prompt", prompt)
                put("stream", false)
            }.toString()
            
            // 发送请求
            val connection = URL(url).openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = cfg.timeout.toInt()
                connection.readTimeout = cfg.timeout.toInt()
                connection.doOutput = true
                
                // 发送请求体
                connection.outputStream.use { it.write(requestBody.toByteArray()) }
                
                // 读取响应
                val responseCode = connection.responseCode
                if (responseCode != 200) {
                    val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                    throw Exception("HTTP $responseCode: $error")
                }
                
                val response = connection.inputStream.bufferedReader().readText()
                
                // 解析响应
                val json = JSONObject(response)
                val content = json.getString("response")
                
                Timber.d("✅ Ollama响应成功")
                content
                
            } finally {
                connection.disconnect()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Ollama调用失败")
            throw e
        }
    }
    
    override fun isAvailable(): Boolean {
        return true  // Ollama不需要API Key
    }
    
    override fun cleanup() {
        config = null
    }
    
    override fun getName(): String = "Ollama"
}
