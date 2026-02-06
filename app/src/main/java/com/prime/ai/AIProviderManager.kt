package com.prime.ai

import com.prime.ai.providers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File

/**
 * AI Provider管理器
 * 负责加载配置、选择Provider、智能降级
 */
object AIProviderManager {
    
    private var currentProvider: AIProvider? = null
    private var fallbackProvider: AIProvider? = null
    private var config: AIConfig? = null
    
    /**
     * 初始化（从配置文件加载）
     */
    suspend fun initialize(configPath: String = "/sdcard/PRIME/config/ai_config.json"): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // 加载配置
                val cfg = loadConfig(configPath)
                config = cfg
                
                Timber.i("📋 AI配置: provider=${cfg.provider}, model=${cfg.model}")
                
                // 创建主Provider
                currentProvider = createProvider(cfg.provider)
                val success = currentProvider?.initialize(cfg) ?: false
                
                if (!success) {
                    Timber.w("⚠️ 主Provider初始化失败，尝试降级")
                }
                
                // 创建降级Provider
                if (cfg.fallback.isNotEmpty() && cfg.fallback != cfg.provider) {
                    fallbackProvider = createProvider(cfg.fallback)
                    fallbackProvider?.initialize(cfg)
                    Timber.i("✅ 降级Provider已准备: ${cfg.fallback}")
                }
                
                // 如果主Provider失败，直接切换到降级
                if (!success && fallbackProvider != null) {
                    Timber.i("🔄 切换到降级Provider")
                    currentProvider = fallbackProvider
                    fallbackProvider = null
                }
                
                Timber.i("✅ AI Provider初始化完成: ${currentProvider?.getName()}")
                true
                
            } catch (e: Exception) {
                Timber.e(e, "❌ AI Provider初始化失败")
                
                // 最终降级：使用规则引擎
                currentProvider = RuleEngineProvider()
                currentProvider?.initialize(AIConfig(provider = "rules"))
                Timber.i("✅ 使用规则引擎作为最终降级")
                
                true  // 总是返回true，因为规则引擎总是可用
            }
        }
    }
    
    /**
     * AI推理（带自动降级）
     */
    suspend fun inference(prompt: String): String {
        return try {
            // 尝试主Provider
            val provider = currentProvider
            if (provider?.isAvailable() == true) {
                Timber.d("🤖 使用${provider.getName()}推理")
                provider.inference(prompt)
            } else {
                throw Exception("主Provider不可用")
            }
        } catch (e: Exception) {
            Timber.w(e, "⚠️ 主Provider失败，尝试降级")
            
            // 尝试降级Provider
            val fallback = fallbackProvider
            if (fallback?.isAvailable() == true) {
                Timber.i("🔄 切换到降级Provider: ${fallback.getName()}")
                val result = fallback.inference(prompt)
                
                // 降级成功，替换主Provider
                currentProvider?.cleanup()
                currentProvider = fallbackProvider
                fallbackProvider = null
                
                result
            } else {
                throw Exception("所有Provider都失败")
            }
        }
    }
    
    /**
     * 清理资源
     */
    fun cleanup() {
        currentProvider?.cleanup()
        fallbackProvider?.cleanup()
        currentProvider = null
        fallbackProvider = null
        config = null
        Timber.i("🧹 AI Provider资源已清理")
    }
    
    /**
     * 获取当前Provider名称
     */
    fun getCurrentProviderName(): String {
        return currentProvider?.getName() ?: "None"
    }
    
    /**
     * 加载配置文件
     */
    private fun loadConfig(path: String): AIConfig {
        val file = File(path)
        
        if (!file.exists()) {
            Timber.w("⚠️ 配置文件不存在: $path，使用默认配置")
            return createDefaultConfig()
        }
        
        return try {
            val json = JSONObject(file.readText())
            
            AIConfig(
                provider = json.optString("provider", "rules"),
                apiKey = json.optString("api_key", ""),
                baseUrl = json.optString("base_url", ""),
                model = json.optString("model", ""),
                timeout = json.optLong("timeout", 30000),
                maxRetries = json.optInt("max_retries", 3),
                fallback = json.optString("fallback", "rules"),
                customHeaders = parseHeaders(json.optJSONObject("custom_headers")),
                customBody = parseBody(json.optJSONObject("custom_body"))
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ 配置文件解析失败，使用默认配置")
            createDefaultConfig()
        }
    }
    
    /**
     * 创建默认配置
     */
    private fun createDefaultConfig(): AIConfig {
        return AIConfig(
            provider = "rules",
            fallback = "rules"
        )
    }
    
    /**
     * 解析自定义Headers
     */
    private fun parseHeaders(json: JSONObject?): Map<String, String> {
        if (json == null) return emptyMap()
        
        val headers = mutableMapOf<String, String>()
        json.keys().forEach { key ->
            headers[key] = json.getString(key)
        }
        return headers
    }
    
    /**
     * 解析自定义Body
     */
    private fun parseBody(json: JSONObject?): Map<String, Any> {
        if (json == null) return emptyMap()
        
        val body = mutableMapOf<String, Any>()
        json.keys().forEach { key ->
            body[key] = json.get(key)
        }
        return body
    }
    
    /**
     * 创建Provider实例
     */
    private fun createProvider(type: String): AIProvider {
        return when (type.lowercase()) {
            "onnx" -> ONNXProvider()
            "openai" -> OpenAIProvider()
            "custom" -> CustomAPIProvider()
            "ollama" -> OllamaProvider()
            "rules" -> RuleEngineProvider()
            else -> {
                Timber.w("⚠️ 未知Provider类型: $type，使用规则引擎")
                RuleEngineProvider()
            }
        }
    }
    
    /**
     * 保存配置示例（供用户参考）
     */
    fun saveConfigExample(path: String = "/sdcard/PRIME/config/ai_config_example.json") {
        val example = """
{
  "provider": "custom",
  "api_key": "your-api-key-here",
  "base_url": "https://your-api.com/v1/chat",
  "model": "your-model-name",
  "timeout": 30000,
  "max_retries": 3,
  "fallback": "rules",
  
  "custom_headers": {
    "X-Custom-Header": "value",
    "Authorization": "Bearer your-token"
  },
  
  "custom_body": {
    "model": "{{model}}",
    "prompt": "{{prompt}}",
    "temperature": 0.7,
    "max_tokens": 2000
  }
}

// 预设配置示例：

// 1. OpenAI
{
  "provider": "openai",
  "api_key": "sk-xxx",
  "base_url": "https://api.openai.com/v1",
  "model": "gpt-4",
  "fallback": "rules"
}

// 2. Claude (通过OpenAI格式)
{
  "provider": "openai",
  "api_key": "sk-ant-xxx",
  "base_url": "https://api.anthropic.com/v1",
  "model": "claude-3-opus-20240229",
  "fallback": "rules"
}

// 3. 本地Ollama
{
  "provider": "ollama",
  "base_url": "http://localhost:11434",
  "model": "llama2",
  "fallback": "rules"
}

// 4. 自定义API
{
  "provider": "custom",
  "api_key": "your-key",
  "base_url": "https://your-api.com/inference",
  "model": "your-model",
  "custom_headers": {
    "X-API-Key": "your-key"
  },
  "custom_body": {
    "input": "{{prompt}}",
    "model_name": "{{model}}"
  },
  "fallback": "rules"
}

// 5. 本地ONNX模型
{
  "provider": "onnx",
  "base_url": "/sdcard/PRIME/models/qwen2.5-7b.onnx",
  "fallback": "rules"
}

// 6. 纯规则引擎（无AI）
{
  "provider": "rules"
}
        """.trimIndent()
        
        try {
            File(path).apply {
                parentFile?.mkdirs()
                writeText(example)
            }
            Timber.i("✅ 配置示例已保存: $path")
        } catch (e: Exception) {
            Timber.e(e, "❌ 保存配置示例失败")
        }
    }
}