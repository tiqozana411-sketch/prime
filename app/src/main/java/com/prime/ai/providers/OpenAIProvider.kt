package com.prime.ai.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenAI Provider
 * 兼容OpenAI格式的API（OpenAI/Claude/Gemini/DeepSeek等）
 */
class OpenAIProvider : AIProvider {
    
    private var config: AIConfig? = null
    
    override suspend fun initialize(config: AIConfig): Boolean {
        this.config = config
        
        if (config.apiKey.isEmpty()) {
            Timber.w("⚠️ OpenAI API未配置apiKey")
            return false
        }
        
        val baseUrl = config.baseUrl.ifEmpty { "https://api.openai.com/v1" }
        Timber.i("✅ OpenAI API初始化成功: $baseUrl")
        return true
    }
    
    override suspend fun inference(prompt: String): String = withContext(Dispatchers.IO) {
        val cfg = config ?: throw IllegalStateException("未初始化")
        
        try {
            val baseUrl = cfg.baseUrl.ifEmpty { "https://api.openai.com/v1" }
            val url = "$baseUrl/chat/completions"
            
            Timber.d("🌐 调用OpenAI API: $url")
            
            // 构建请求体
            val requestBody = JSONObject().apply {
                put("model", cfg.model.ifEmpty { "gpt-3.5-turbo" })
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.7)
            }.toString()
            
            // 发送请求
            val connection = URL(url).openConnection() as HttpURLConnection
            
            try {
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer ${cfg.apiKey}")
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
                val content = json.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                
                Timber.d("✅ OpenAI API响应成功")
                content
                
            } finally {
                connection.disconnect()
            }
            
        } catch (e: Exception) {
            Timber.e(e, "❌ OpenAI API调用失败")
            throw e
        }
    }
    
    override fun isAvailable(): Boolean {
        return config?.apiKey?.isNotEmpty() == true
    }
    
    override fun cleanup() {
        config = null
    }
    
    override fun getName(): String = "OpenAI"
}
