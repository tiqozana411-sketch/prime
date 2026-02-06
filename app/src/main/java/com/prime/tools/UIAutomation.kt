package com.prime.tools

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import com.prime.core.AdaptiveParamsManager
import com.prime.core.SmartRetryStrategy
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume

/**
 * UI自动化服务
 * 基于AccessibilityService实现点击、滑动、输入
 */
class UIAutomationService : AccessibilityService() {
    
    companion object {
        private var instance: UIAutomationService? = null
        
        val isRunning: Boolean
            get() = instance != null
        
        fun getInstance(): UIAutomationService? = instance
    }
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Timber.i("✅ UI自动化服务已启动")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 处理无障碍事件
    }
    
    override fun onInterrupt() {
        Timber.w("UI自动化服务中断")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Timber.i("UI自动化服务已停止")
    }
    
    /**
     * 执行手势
     */
    suspend fun performGesture(gesture: GestureDescription): Boolean = 
        suspendCancellableCoroutine { continuation ->
            val callback = object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    continuation.resume(true)
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    continuation.resume(false)
                }
            }
            
            dispatchGesture(gesture, callback, null)
        }
}

/**
 * UI自动化控制器 v2.0
 * 增加智能重试和自适应优化
 */
object UIAutomation {
    
    private val adaptiveParams = AdaptiveParamsManager()
    private val retryStrategy = SmartRetryStrategy(adaptiveParams)
    
    /**
     * 智能点击（带重试和坐标微调）
     */
    suspend fun clickSmart(x: Int, y: Int): Boolean {
        return retryStrategy.executeWithRetry("click") { attempt ->
            val service = UIAutomationService.getInstance()
            if (service == null) {
                Timber.e("UI自动化服务未运行")
                throw Exception("服务未运行")
            }
            
            // 第1次：精确点击
            if (attempt == 1) {
                val result = performClick(service, x, y)
                if (result) return@executeWithRetry true
            }
            
            // 第2-3次：微调坐标
            val offsets = listOf(
                Pair(0, -10), Pair(0, 10),
                Pair(-10, 0), Pair(10, 0)
            )
            
            for ((dx, dy) in offsets) {
                val result = performClick(service, x + dx, y + dy)
                if (result) return@executeWithRetry true
            }
            
            throw Exception("点击失败")
        } ?: false
    }
    
    private suspend fun performClick(
        service: UIAutomationService,
        x: Int,
        y: Int,
        duration: Long = 100
    ): Boolean {
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        return service.performGesture(gesture)
    }
    
    /**
     * 点击坐标（原有方法保持兼容）
     */
    suspend fun click(x: Int, y: Int, duration: Long = 100): Boolean {
        val service = UIAutomationService.getInstance()
        if (service == null) {
            Timber.e("UI自动化服务未运行")
            return false
        }
        
        val path = Path().apply {
            moveTo(x.toFloat(), y.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        val success = service.performGesture(gesture)
        Timber.d("点击($x, $y): ${if (success) "成功" else "失败"}")
        
        return success
    }
    
    /**
     * 滑动
     */
    suspend fun swipe(
        startX: Int, 
        startY: Int, 
        endX: Int, 
        endY: Int, 
        duration: Long = 300
    ): Boolean {
        val service = UIAutomationService.getInstance()
        if (service == null) {
            Timber.e("UI自动化服务未运行")
            return false
        }
        
        val path = Path().apply {
            moveTo(startX.toFloat(), startY.toFloat())
            lineTo(endX.toFloat(), endY.toFloat())
        }
        
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        
        val success = service.performGesture(gesture)
        Timber.d("滑动($startX,$startY)->($endX,$endY): ${if (success) "成功" else "失败"}")
        
        return success
    }
    
/**
     * 长按（别名，兼容 RemoteCommandHandler）
     */
    suspend fun longClick(x: Int, y: Int, duration: Int = 1000): Boolean {
        return longPress(x, y, duration.toLong())
    }
    
    /**
     * 长按
     */
    suspend fun longPress(x: Int, y: Int, duration: Long = 1000): Boolean {
        return click(x, y, duration)
    }
    
    /**
     * 输入文本（需要配合ROOT的input命令）
     */
    suspend fun inputText(text: String): Boolean {
        val result = com.prime.core.RootManager.exec("input text \"$text\"")
        Timber.d("输入文本: ${if (result.success) "成功" else "失败"}")
        return result.success
    }
    
    /**
     * 按键
     */
    suspend fun pressKey(keyCode: Int): Boolean {
        val result = com.prime.core.RootManager.exec("input keyevent $keyCode")
        Timber.d("按键$keyCode: ${if (result.success) "成功" else "失败"}")
        return result.success
    }
    
    /**
     * 返回键
     */
    suspend fun pressBack() = pressKey(4)
    
    /**
     * Home键
     */
    suspend fun pressHome() = pressKey(3)
    
    /**
     * 最近任务键
     */
    suspend fun pressRecent() = pressKey(187)
    
    /**
     * 输入（AIExecutor兼容方法）
     */
    suspend fun input(text: String) = inputText(text)
    
    /**
     * 滑动方向（AIExecutor兼容方法）
     */
    suspend fun swipe(direction: String): Boolean {
        val displayMetrics = android.content.res.Resources.getSystem().displayMetrics
        val width = displayMetrics.widthPixels
        val height = displayMetrics.heightPixels
        
        return when (direction.lowercase()) {
            "up" -> swipe(width / 2, height * 3 / 4, width / 2, height / 4)
            "down" -> swipe(width / 2, height / 4, width / 2, height * 3 / 4)
            "left" -> swipe(width * 3 / 4, height / 2, width / 4, height / 2)
            "right" -> swipe(width / 4, height / 2, width * 3 / 4, height / 2)
            else -> {
                Timber.e("未知滑动方向: $direction")
                false
            }
        }
    }
    
    /**
     * 返回（AIExecutor兼容方法）
     */
    suspend fun back() = pressBack()
    
    /**
     * 主页（AIExecutor兼容方法）
     */
    suspend fun home() = pressHome()
}