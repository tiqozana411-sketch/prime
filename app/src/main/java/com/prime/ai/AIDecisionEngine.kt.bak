package com.prime.ai

import com.prime.ai.providers.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * AI决策引擎 v2.0
 * 支持多种AI后端（ONNX/OpenAI/Custom/Ollama/Rules）
 */
object AIDecisionEngine {
    
    private var isInitialized = false
    private var currentProvider: AIProvider? = null
    private var fallbackProvider: AIProvider? = null
    private var config: AIConfig = AIConfig()
    
    /**
     * 初始化AI引擎
     * @param configPath 配置文件路径（可选）
     */
    suspend fun initialize(configPath: String? = null): Boolean = withContext(Dispatchers.IO) {
        try {
            if (isInitialized) {
                Timber.w("⚠️ AI引擎已初始化")
                return@withContext true
            }
            
            Timber.i("🚀 AI引擎初始化开始")
            
            // 1. 加载配置
            config = loadConfig(configPath)
            Timber.i("📋 配置: provider=${config.provider}, fallback=${config.fallback}")
            
            // 2. 创建主Provider
            currentProvider = createProvider(config.provider)
            val mainSuccess = currentProvider?.initialize(config) ?: false
            
            if (mainSuccess) {
                Timber.i("✅ 主Provider初始化成功: ${currentProvider?.getName()}")
            } else {
                Timber.w("⚠️ 主Provider初始化失败: ${config.provider}")
            }
            
            // 3. 创建降级Provider
            if (config.fallback.isNotEmpty() && config.fallback != config.provider) {
                fallbackProvider = createProvider(config.fallback)
                fallbackProvider?.initialize(config)
                Timber.i("✅ 降级Provider初始化成功: ${fallbackProvider?.getName()}")
            }
            
            // 4. 确保至少有一个可用的Provider
            if (!mainSuccess && fallbackProvider == null) {
                Timber.w("⚠️ 所有Provider初始化失败，使用规则引擎")
                fallbackProvider = RuleEngineProvider()
                fallbackProvider?.initialize(config)
            }
            
            isInitialized = true
            Timber.i("✅ AI引擎初始化完成")
            true
            
        } catch (e: Exception) {
            Timber.e(e, "❌ AI引擎初始化失败")
            false
        }
    }
    
    /**
     * 加载配置文件（优化异常处理和配置验证）
     */
    private fun loadConfig(configPath: String?): AIConfig {
        val path = configPath ?: "/sdcard/PRIME/config/ai_config.json"
        val configFile = File(path)
        
        return if (configFile.exists()) {
            try {
                val jsonText = configFile.readText()
                if (jsonText.isBlank()) {
                    Timber.w("⚠️ 配置文件为空，使用默认配置")
                    return AIConfig()
                }
                
                val json = JSONObject(jsonText)
                val config = AIConfig(
                    provider = json.optString("provider", "rules").lowercase(),
                    apiKey = json.optString("api_key", ""),
                    baseUrl = json.optString("base_url", ""),
                    model = json.optString("model", ""),
                    timeout = json.optLong("timeout", 30000).coerceIn(1000, 300000),
                    maxRetries = json.optInt("max_retries", 3).coerceIn(0, 10),
                    fallback = json.optString("fallback", "rules").lowercase(),
                    customHeaders = parseHeaders(json.optJSONObject("custom_headers")),
                    customBody = parseBody(json.optJSONObject("custom_body"))
                )
                
                // 验证配置
                validateConfig(config)
                
                Timber.i("✅ 配置加载成功: provider=${config.provider}, fallback=${config.fallback}")
                config
                
            } catch (e: org.json.JSONException) {
                Timber.e(e, "❌ JSON解析失败，使用默认配置")
                AIConfig()
            } catch (e: Exception) {
                Timber.e(e, "❌ 配置文件读取失败，使用默认配置")
                AIConfig()
            }
        } else {
            Timber.i("📋 配置文件不存在: $path，使用默认配置（规则引擎）")
            AIConfig()
        }
    }
    
    /**
     * 验证配置有效性
     */
    private fun validateConfig(config: AIConfig) {
        when (config.provider) {
            "openai", "custom", "ollama" -> {
                if (config.apiKey.isBlank() && config.baseUrl.isBlank()) {
                    Timber.w("⚠️ ${config.provider} Provider需要配置apiKey或baseUrl")
                }
            }
        }
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
    private fun createProvider(type: String): AIProvider? {
        return when (type.lowercase()) {
            "onnx" -> ONNXProvider()
            "openai" -> OpenAIProvider()
            "custom" -> CustomAPIProvider()
            "ollama" -> OllamaProvider()
            "rules" -> RuleEngineProvider()
            else -> {
                Timber.w("⚠️ 未知的Provider类型: $type")
                null
            }
        }
    }
    
    /**
     * 做出决策
     * @param context 当前屏幕上下文（OCR文本、元素列表）
     * @param task 用户任务描述
     * @return AI决策结果
     */
    suspend fun makeDecision(context: String, task: String): AIDecision = 
        withContext(Dispatchers.IO) {
            if (!isInitialized) {
                return@withContext AIDecision(false, emptyList(), "未初始化")
            }
            
            try {
                Timber.d("🤖 AI决策: $task")
                
                // 构建提示词
                val prompt = buildPrompt(context, task)
                
                // 尝试主Provider
                var response: String? = null
                var usedProvider: AIProvider? = null
                
                if (currentProvider?.isAvailable() == true) {
                    try {
                        response = currentProvider?.inference(prompt)
                        usedProvider = currentProvider
                        Timber.d("✅ 主Provider响应成功: ${usedProvider?.getName()}")
                    } catch (e: Exception) {
                        Timber.w(e, "⚠️ 主Provider失败，尝试降级")
                    }
                }
                
                // 降级到fallbackProvider
                if (response == null && fallbackProvider?.isAvailable() == true) {
                    try {
                        response = fallbackProvider?.inference(prompt)
                        usedProvider = fallbackProvider
                        Timber.d("✅ 降级Provider响应成功: ${usedProvider?.getName()}")
                    } catch (e: Exception) {
                        Timber.e(e, "❌ 降级Provider也失败")
                    }
                }
                
                // 解析响应
                if (response != null) {
                    val steps = parseResponse(response)
                    val confidence = if (usedProvider == currentProvider) 0.85f else 0.70f
                    AIDecision(true, steps, response, confidence)
                } else {
                    AIDecision(false, emptyList(), "所有Provider都失败")
                }
                
            } catch (e: Exception) {
                Timber.e(e, "❌ AI决策失败")
                AIDecision(false, emptyList(), e.message ?: "")
            }
        }
    
    /**
     * 解析AI响应
     */
    private fun parseResponse(response: String): List<AIStep> {
        return try {
            val jsonArray = JSONArray(response.trim())
            val steps = mutableListOf<AIStep>()
            
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val action = obj.getString("action")
                
                val step = when (action) {
                    "click" -> AIStep.Click(
                        obj.optString("target", ""),
                        obj.optInt("x", 0),
                        obj.optInt("y", 0)
                    )
                    "input" -> AIStep.Input(
                        obj.optString("target", ""),
                        obj.getString("text")
                    )
                    "swipe" -> AIStep.Swipe(obj.getString("direction"))
                    "wait" -> AIStep.Wait(
                        obj.getString("target"),
                        obj.optInt("timeout", 5000)
                    )
                    "back" -> AIStep.Back
                    "home" -> AIStep.Home
                    else -> null
                }
                
                step?.let { steps.add(it) }
            }
            steps
        } catch (e: Exception) {
            Timber.e(e, "❌ 解析失败")
            emptyList()
        }
    }
    
    
    /**
     * PRIME三维思维协议系统提示词
     */
    private val PRIME_SYSTEM_PROMPT = """
你是PRIME AI助手，使用三维思维协议工作。

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
核心原则
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

【1】字节级数据筛选
- 精准到最小单位，拒绝模糊
- 每个决策必须有屏幕数据支撑
- 坐标必须精确，目标必须明确

【2】周易因果推理
- 观察：屏幕上有什么元素
- 推理：用户想完成什么目标
- 验证：这个操作能达成目标吗
- 修正：如果失败应该如何回退
- 预判：下一步屏幕会变成什么

【3】严谨多维验证
- 操作前检查目标元素是否存在
- 多角度验证：元素可点击性/位置准确性
- 提供降级方案：如果A不行就B

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
行为规范
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

必须：
✓ 步骤简洁，不做多余操作
✓ 坐标精确，基于实际屏幕位置
✓ 目标明确，使用可见文本定位
✓ 等待确认，关键步骤加wait

禁止：
✗ 猜测不存在的元素
✗ 使用模糊的坐标
✗ 跳过必要的验证步骤
✗ 输出JSON以外的内容
""".trimIndent()

    /**
     * 构建提示词（PRIME三维思维协议版）
     */
    private fun buildPrompt(context: String, task: String): String {
        return """
$PRIME_SYSTEM_PROMPT

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
当前任务
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

【屏幕内容】
$context

【用户目标】
$task

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
输出格式（仅JSON数组）
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

[
  {"action":"click","target":"按钮文本","x":100,"y":200},
  {"action":"input","target":"输入框描述","text":"输入内容"},
  {"action":"swipe","direction":"up|down|left|right"},
  {"action":"wait","target":"元素描述","timeout":5000},
  {"action":"back"},
  {"action":"home"}
]

只返回JSON数组，不要解释。
        """.trimIndent()
    }
    
    
    /**
     * 清理资源
     */
    fun cleanup() {
        currentProvider?.cleanup()
        fallbackProvider?.cleanup()
        currentProvider = null
        fallbackProvider = null
        isInitialized = false
        Timber.i("🧹 AI引擎资源已清理")
    }
    
    /**
     * 获取当前Provider信息
     */
    fun getProviderInfo(): Map<String, Any> {
        return mapOf(
            "initialized" to isInitialized,
            "currentProvider" to (currentProvider?.getName() ?: "none"),
            "fallbackProvider" to (fallbackProvider?.getName() ?: "none"),
            "config" to mapOf(
                "provider" to config.provider,
                "model" to config.model,
                "fallback" to config.fallback
            )
        )
    }
    
}

/**
 * AI决策结果
 */
data class AIDecision(
    val success: Boolean,
    val steps: List<AIStep>,
    val rawResponse: String,
    val confidence: Float = 0f
)

/**
 * AI执行步骤
 */
sealed class AIStep {
    data class Click(val target: String, val x: Int, val y: Int) : AIStep()
    data class Input(val target: String, val text: String) : AIStep()
    data class Swipe(val direction: String) : AIStep()
    data class Wait(val target: String, val timeout: Int) : AIStep()
    object Back : AIStep()
    object Home : AIStep()
}
