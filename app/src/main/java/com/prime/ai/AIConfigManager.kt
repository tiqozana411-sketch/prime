package com.prime.ai

import com.prime.ai.providers.AIConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * AI配置管理器
 * 负责读取和保存AI配置
 */
object AIConfigManager {
    
    private const val CONFIG_PATH = "/sdcard/PRIME/config/ai_config.json"
    
    /**
     * 加载配置
     */
    suspend fun loadConfig(): AIConfig = withContext(Dispatchers.IO) {
        try {
            val file = File(CONFIG_PATH)
            
            if (!file.exists()) {
                Timber.w("⚠️ 配置文件不存在，使用默认配置")
                return@withContext getDefaultConfig()
            }
            
            val json = JSONObject(file.readText())
            
            // 解析自定义Headers
            val customHeaders = mutableMapOf<String, String>()
            if (json.has("customHeaders")) {
                val headersJson = json.getJSONObject("customHeaders")
                headersJson.keys().forEach { key ->
                    customHeaders[key] = headersJson.getString(key)
                }
            }
            
            // 解析自定义Body
            val customBody = mutableMapOf<String, Any>()
            if (json.has("customBody")) {
                val bodyJson = json.getJSONObject("customBody")
                bodyJson.keys().forEach { key ->
                    customBody[key] = bodyJson.get(key)
                }
            }
            
            val config = AIConfig(
                provider = json.optString("provider", "rules"),
                apiKey = json.optString("apiKey", ""),
                baseUrl = json.optString("baseUrl", ""),
                model = json.optString("model", ""),
                timeout = json.optLong("timeout", 30000),
                maxRetries = json.optInt("maxRetries", 3),
                fallback = json.optString("fallback", "rules"),
                customHeaders = customHeaders,
                customBody = customBody
            )
            
            Timber.i("✅ 配置加载成功: provider=${config.provider}")
            config
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 配置加载失败，使用默认配置")
            getDefaultConfig()
        }
    }
    
    /**
     * 保存配置
     */
    suspend fun saveConfig(config: AIConfig): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(CONFIG_PATH)
            file.parentFile?.mkdirs()
            
            val json = JSONObject().apply {
                put("provider", config.provider)
                put("apiKey", config.apiKey)
                put("baseUrl", config.baseUrl)
                put("model", config.model)
                put("timeout", config.timeout)
                put("maxRetries", config.maxRetries)
                put("fallback", config.fallback)
                
                // 保存自定义Headers
                if (config.customHeaders.isNotEmpty()) {
                    put("customHeaders", JSONObject(config.customHeaders))
                }
                
                // 保存自定义Body
                if (config.customBody.isNotEmpty()) {
                    put("customBody", JSONObject(config.customBody))
                }
            }
            
            file.writeText(json.toString(2))
            
            Timber.i("✅ 配置保存成功")
            true
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 配置保存失败")
            false
        }
    }
    
    /**
     * 获取默认配置
     */
    private fun getDefaultConfig(): AIConfig {
        return AIConfig(
            provider = "rules",
            fallback = "rules"
        )
    }
    
    /**
     * 创建示例配置文件
     */
    suspend fun createExampleConfig(): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File("/sdcard/PRIME/config/ai_config_example.json")
            file.parentFile?.mkdirs()
            
            val example = """
{
  "provider": "custom",
  "apiKey": "your-api-key-here",
  "baseUrl": "https://your-api.com/v1",
  "model": "your-model-name",
  "timeout": 30000,
  "maxRetries": 3,
  "fallback": "rules",
  "customHeaders": {
    "X-Custom-Header": "value"
  },
  "customBody": {
    "model": "{{model}}",
    "prompt": "{{prompt}}",
    "temperature": 0.7
  }
}

// 支持的provider类型：
// - "onnx": 本地ONNX模型（需要下载模型文件）
// - "openai": OpenAI兼容API（OpenAI/Claude/Gemini/DeepSeek等）
// - "custom": 自定义HTTP API（完全自定义请求格式）
// - "ollama": 本地Ollama服务
// - "rules": 规则引擎（无需AI模型）

// OpenAI示例：
// {
//   "provider": "openai",
//   "apiKey": "sk-xxx",
//   "baseUrl": "https://api.openai.com/v1",
//   "model": "gpt-3.5-turbo",
//   "fallback": "rules"
// }

// Ollama示例：
// {
//   "provider": "ollama",
//   "baseUrl": "http://localhost:11434",
//   "model": "llama2",
//   "fallback": "rules"
// }

// 自定义API示例：
// {
//   "provider": "custom",
//   "apiKey": "your-key",
//   "baseUrl": "https://your-api.com/generate",
//   "model": "your-model",
//   "customHeaders": {
//     "Authorization": "Bearer your-token",
//     "X-Custom-Header": "value"
//   },
//   "customBody": {
//     "model": "{{model}}",
//     "input": "{{prompt}}",
//     "max_tokens": 1000
//   },
//   "fallback": "rules"
// }
            """.trimIndent()
            
            file.writeText(example)
            
            Timber.i("✅ 示例配置文件创建成功: ${file.absolutePath}")
            true
            
        } catch (e: Exception) {
            Timber.e(e, "❌ 示例配置文件创建失败")
            false
        }
    }
}