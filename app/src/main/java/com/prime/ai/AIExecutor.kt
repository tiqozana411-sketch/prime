package com.prime.ai

import com.prime.core.AdaptiveParamsManager
import com.prime.core.SmartRetryStrategy
import com.prime.core.TaskResult
import com.prime.tools.UIAutomation
import com.prime.vision.VisionEngine
import timber.log.Timber

/**
 * AI执行器 v2.0
 * 执行AI决策引擎生成的步骤（带智能重试）
 */
class AIExecutor(
    private val uiAutomation: UIAutomation,
    private val visionEngine: VisionEngine
) {
    
    private val adaptiveParams = AdaptiveParamsManager()
    private val retryStrategy = SmartRetryStrategy(adaptiveParams)
    
    suspend fun execute(steps: List<AIStep>): Boolean {
        return try {
            steps.forEach { step ->
                when (step) {
                    is AIStep.Click -> executeClick(step)
                    is AIStep.Input -> executeInput(step)
                    is AIStep.Swipe -> executeSwipe(step)
                    is AIStep.Wait -> executeWait(step)
                    is AIStep.Back -> executeBack()
                    is AIStep.Home -> executeHome()
                }
            }
            true
        } catch (e: Exception) {
            Timber.e(e, "执行失败")
            false
        }
    }
    
    private suspend fun executeClick(step: AIStep.Click) {
        Timber.d("点击: ${step.target} (${step.x}, ${step.y})")
        
        // 使用智能点击（带重试和坐标微调）
        val success = uiAutomation.clickSmart(step.x, step.y)
        
        if (!success) {
            throw Exception("点击失败: ${step.target}")
        }
    }
    
    private suspend fun executeInput(step: AIStep.Input) {
        Timber.d("输入: ${step.text}")
        
        // 使用智能重试
        retryStrategy.executeWithRetry("input") {
            val success = uiAutomation.input(step.text)
            if (!success) throw Exception("输入失败")
            success
        } ?: throw Exception("输入失败: ${step.text}")
    }
    
    private suspend fun executeSwipe(step: AIStep.Swipe) {
        Timber.d("滑动: ${step.direction}")
        
        // 使用智能重试
        retryStrategy.executeWithRetry("swipe") {
            val success = uiAutomation.swipe(step.direction)
            if (!success) throw Exception("滑动失败")
            success
        } ?: throw Exception("滑动失败: ${step.direction}")
    }
    
    private suspend fun executeWait(step: AIStep.Wait) {
        Timber.d("等待: ${step.target}")
        
        // 使用自适应超时
        val timeout = adaptiveParams.getTimeout("wait").coerceAtLeast(step.timeout.toLong())
        val result = visionEngine.waitForElement(step.target, timeout)
        
        if (result == null) {
            throw Exception("等待超时: ${step.target}")
        }
    }
    
    private suspend fun executeBack() {
        Timber.d("返回")
        uiAutomation.back()
    }
    
    private suspend fun executeHome() {
        Timber.d("主页")
        uiAutomation.home()
    }
    
    // ========== 蜂群系统扩展方法 ==========
    
    /**
     * 执行点击操作（蜂群Worker使用）
     */
    suspend fun executeClick(target: String): TaskResult {
        return try {
            Timber.d("蜂群点击: $target")
            
            // 1. 使用视觉引擎查找目标
            val element = visionEngine.findElement(target)
            if (element == null) {
                return TaskResult(false, "未找到目标: $target")
            }
            
            // 2. 执行点击
            val success = uiAutomation.clickSmart(element.x, element.y)
            
            if (success) {
                TaskResult(true, "点击成功: $target")
            } else {
                TaskResult(false, "点击失败: $target")
            }
        } catch (e: Exception) {
            Timber.e(e, "点击异常: $target")
            TaskResult(false, "点击异常: ${e.message}")
        }
    }
    
    /**
     * 执行输入操作（蜂群Worker使用）
     */
    suspend fun executeInput(target: String, text: String): TaskResult {
        return try {
            Timber.d("蜂群输入: $target = $text")
            
            // 1. 先点击目标输入框
            val clickResult = executeClick(target)
            if (!clickResult.success) {
                return clickResult
            }
            
            // 2. 等待输入框激活
            kotlinx.coroutines.delay(500)
            
            // 3. 执行输入
            val success = uiAutomation.input(text)
            
            if (success) {
                TaskResult(true, "输入成功: $text")
            } else {
                TaskResult(false, "输入失败")
            }
        } catch (e: Exception) {
            Timber.e(e, "输入异常: $target")
            TaskResult(false, "输入异常: ${e.message}")
        }
    }
    
    /**
     * 执行滚动操作（蜂群Worker使用）
     */
    suspend fun executeScroll(direction: String, distance: Int): TaskResult {
        return try {
            Timber.d("蜂群滚动: $direction $distance")
            
            val success = uiAutomation.swipe(direction)
            
            if (success) {
                TaskResult(true, "滚动成功")
            } else {
                TaskResult(false, "滚动失败")
            }
        } catch (e: Exception) {
            Timber.e(e, "滚动异常")
            TaskResult(false, "滚动异常: ${e.message}")
        }
    }
    
    /**
     * 验证条件（蜂群Worker使用）
     */
    suspend fun verifyCondition(condition: String): Boolean {
        return try {
            Timber.d("蜂群验证: $condition")
            
            // 使用视觉引擎查找条件
            val element = visionEngine.findElement(condition)
            element != null
        } catch (e: Exception) {
            Timber.e(e, "验证异常: $condition")
            false
        }
    }
    
    /**
     * 分析屏幕（蜂群Worker使用）
     */
    suspend fun analyzeScreen(): String {
        return try {
            Timber.d("蜂群分析屏幕")
            
            // 截图并OCR
            val screenshot = visionEngine.captureScreen()
            val ocrResult = visionEngine.performOCR(screenshot)
            
            ocrResult.text
        } catch (e: Exception) {
            Timber.e(e, "分析异常")
            ""
        }
    }
}