package com.prime.swarm

import com.prime.core.TaskResult
import com.prime.vision.VisionEngine
import android.util.Log

/**
 * Scout Agent - 侦察蜂
 * 职责：探索环境、收集信息
 * 
 * 基于字节级数据筛选：精准识别屏幕元素
 */
class ScoutAgent(
    id: String,
    private val visionEngine: VisionEngine
) : SwarmAgent(id, AgentType.SCOUT) {
    
    private val TAG = "ScoutAgent"
    
    override suspend fun execute(task: SubTask): TaskResult {
        return TaskResult(false, "Scout不直接执行任务")
    }
    
    override fun canHandle(task: SubTask): Boolean = false
    
    override fun getCapability(): AgentCapability {
        return AgentCapability(
            complexity = 10,
            speed = 10,
            accuracy = 10,
            reliability = 10
        )
    }
    
    /**
     * 探索屏幕环境
     */
    suspend fun exploreScreen(): ScreenEnvironment {
        Log.d(TAG, "[$id] 开始探索屏幕环境")
        
        try {
            // 1. 截图
            val screenshot = visionEngine.captureScreen()
            
            // 2. OCR识别
            val ocrResult = visionEngine.performOCR(screenshot)
            
            // 3. 提取可交互元素
            val elements = extractInteractiveElements(ocrResult.text)
            
            // 4. 分析屏幕布局
            val layout = analyzeLayout(elements)
            
            Log.d(TAG, "[$id] 探索完成: 发现${elements.size}个可交互元素")
            
            return ScreenEnvironment(
                elements = elements,
                layout = layout,
                ocrText = ocrResult.text,
                confidence = 0.8f // ocrResult.confidence 字段不存在，使用默认值
            )
            
        } catch (e: Exception) {
            Log.e(TAG, "[$id] 探索失败", e)
            return ScreenEnvironment(
                elements = emptyList(),
                layout = ScreenLayout.UNKNOWN,
                ocrText = "",
                confidence = 0.0f
            )
        }
    }
    
    /**
     * 提取可交互元素
     */
    private fun extractInteractiveElements(text: String): List<InteractiveElement> {
        val elements = mutableListOf<InteractiveElement>()
        
        // 常见的可交互元素关键词
        val keywords = listOf(
            "按钮", "button", "btn",
            "输入", "input", "edit",
            "搜索", "search",
            "登录", "login",
            "注册", "register",
            "确定", "ok", "确认", "confirm",
            "取消", "cancel",
            "提交", "submit",
            "发送", "send"
        )
        
        for (keyword in keywords) {
            if (text.contains(keyword, ignoreCase = true)) {
                elements.add(InteractiveElement(
                    type = inferElementType(keyword),
                    text = keyword,
                    confidence = 0.8f
                ))
            }
        }
        
        return elements
    }
    
    /**
     * 推断元素类型
     */
    private fun inferElementType(keyword: String): ElementType {
        return when {
            keyword.contains("按钮", ignoreCase = true) || 
            keyword.contains("button", ignoreCase = true) -> ElementType.BUTTON
            
            keyword.contains("输入", ignoreCase = true) || 
            keyword.contains("input", ignoreCase = true) -> ElementType.INPUT
            
            keyword.contains("搜索", ignoreCase = true) || 
            keyword.contains("search", ignoreCase = true) -> ElementType.SEARCH
            
            else -> ElementType.UNKNOWN
        }
    }
    
    /**
     * 分析屏幕布局
     */
    private fun analyzeLayout(elements: List<InteractiveElement>): ScreenLayout {
        return when {
            elements.any { it.text.contains("登录", ignoreCase = true) } -> ScreenLayout.LOGIN
            elements.any { it.text.contains("搜索", ignoreCase = true) } -> ScreenLayout.SEARCH
            elements.any { it.text.contains("列表", ignoreCase = true) } -> ScreenLayout.LIST
            elements.size > 5 -> ScreenLayout.COMPLEX
            else -> ScreenLayout.SIMPLE
        }
    }
}

/**
 * 屏幕环境
 */
data class ScreenEnvironment(
    val elements: List<InteractiveElement>,
    val layout: ScreenLayout,
    val ocrText: String,
    val confidence: Float
)

/**
 * 可交互元素
 */
data class InteractiveElement(
    val type: ElementType,
    val text: String,
    val confidence: Float
)

/**
 * 元素类型
 */
enum class ElementType {
    BUTTON,   // 按钮
    INPUT,    // 输入框
    SEARCH,   // 搜索框
    UNKNOWN   // 未知
}

/**
 * 屏幕布局
 */
enum class ScreenLayout {
    LOGIN,    // 登录页面
    SEARCH,   // 搜索页面
    LIST,     // 列表页面
    COMPLEX,  // 复杂页面
    SIMPLE,   // 简单页面
    UNKNOWN   // 未知
}