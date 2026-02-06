package com.prime.ai.providers

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.net.HttpURLConnection
import java.net.URL

/**
 * 自定义API Provider
 * 支持任意HTTP API（完全自定义请求格式）
 */
class CustomAPIProvider : AIProvider {
    
    private var config: AIConfig? = null
    
    override suspend fun initialize(config: AIConfig): Boolean {
        this.config = config
        
        if (config.baseUrl.isEmpty()) {
            Timber.w("⚠️ Custom API未配置baseUrl")
            return false
        }
        
        Timber.i("✅ Custom API初始化成功: ${config.baseUrl}")
        return true
    }
    
    override suspend fun inference(prompt: String): String = withContext(Dispatchers.IO) {
        val cfg = config ?: throw IllegalStateException("未初始化")
        
        try {
            Timber.d("🌐 调用Custom API: ${cfg.baseUrl}")
            
            // 构建请求体（支持自定义格式）
            val requestBody = buildRequestBody(prompt, cfg)
            
            // 发送HTTP请求
            val response = sendHttpRequest(cfg.baseUrl, requestBody, cfg)
            
            // 解析响应（支持自定义格式）
            val result = parseResponse(response, cfg)
            
            Timber.d("✅ Custom API响应成功")
            result
            
        } catch (e: Exception) {
            Timber.e(e, "❌ Custom API调用失败")
            throw e
        }
    }
    
    override fun isAvailable(): Boolean {
        return config?.baseUrl?.isNotEmpty() == true
    }
    
    override fun cleanup() {
        config = null
    }
    
    override fun getName(): String = "CustomAPI"
    
    /**
     * 构建请求体（支持自定义格式）
     */
    private fun buildRequestBody(prompt: String, config: AIConfig): String {
        return if (config.customBody.isNotEmpty()) {
            // 使用自定义Body模板
            val body = JSONObject(config.customBody)
            
            // 替换占位符
            val bodyStr = body.toString()
                .replace("{{prompt}}", prompt)
                .replace("{{model}}", config.model)
            
            bodyStr
        } else {
            // 默认格式（兼容OpenAI）
            JSONObject().apply {
                put("model", config.model)
                put("messages", org.json.JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", prompt)
                    })
                })
                put("temperature", 0.7)
            }.toString()
        }
    }
    
    /**
     * 发送HTTP请求
     */
    private fun sendHttpRequest(url: String, body: String, config: AIConfig): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.connectTimeout = config.timeout.toInt()
            connection.readTimeout = config.timeout.toInt()
            
            // 添加API Key
            if (config.apiKey.isNotEmpty()) {
                connection.setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            
            // 添加自定义Headers
            config.customHeaders.forEach { (key, value) ->
                connection.setRequestProperty(key, value)
            }
            
            connection.doOutput = true
            
            // 发送请求体
            connection.outputStream.use { it.write(body.toByteArray()) }
            
            // 读取响应
            val responseCode = connection.responseCode
            if (responseCode != 200) {
                val error = connection.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                throw Exception("HTTP $responseCode: $error")
            }
            
            return connection.inputStream.bufferedReader().readText()
            
        } finally {
            connection.disconnect()
        }
    }
    
    /**
     * 解析响应（支持自定义格式）
     */
    private fun parseResponse(response: String, config: AIConfig): String {
        return try {
            val json = JSONObject(response)
            
            // 尝试多种常见格式
            when {
                // OpenAI格式
                json.has("choices") -> {
                    json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                }
                
                // Claude格式
                json.has("content") -> {
                    val content = json.get("content")
                    if (content is org.json.JSONArray) {
                        content.getJSONObject(0).getString("text")
                    } else {
                        content.toString()
                    }
                }
                
                // 直接返回text字段
                json.has("text") -> json.getString("text")
                
                // 直接返回result字段
                json.has("result") -> json.getString("result")
                
                // 直接返回response字段
                json.has("response") -> json.getString("response")
                
                // 返回整个JSON
                else -> response
            }
        } catch (e: Exception) {
            Timber.w("⚠️ 响应解析失败，返回原始内容")
            response
        }
    }
}
