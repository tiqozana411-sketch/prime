package com.prime.ai.providers

/**
 * AI Provider接口
 * 支持多种AI后端（本地模型、云端API、自定义服务）
 */
interface AIProvider {
    
    /**
     * 初始化Provider
     */
    suspend fun initialize(config: AIConfig): Boolean
    
    /**
     * AI推理
     * @param prompt 提示词
     * @return AI响应（JSON格式的操作步骤）
     */
    suspend fun inference(prompt: String): String
    
    /**
     * 检查是否可用
     */
    fun isAvailable(): Boolean
    
    /**
     * 清理资源
     */
    fun cleanup()
    
    /**
     * Provider名称
     */
    fun getName(): String
}

/**
 * AI配置
 */
data class AIConfig(
    val provider: String = "rules",  // onnx/openai/custom/ollama/rules
    val apiKey: String = "",
    val baseUrl: String = "",
    val model: String = "",
    val timeout: Long = 30000,
    val maxRetries: Int = 3,
    val fallback: String = "rules",  // 降级策略
    val customHeaders: Map<String, String> = emptyMap(),
    val customBody: Map<String, Any> = emptyMap()
)
