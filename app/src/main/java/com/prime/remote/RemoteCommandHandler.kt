package com.prime.remote

import com.prime.tools.UIAutomation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import org.json.JSONObject

/**
 * 远程控制命令处理器
 */
class RemoteCommandHandler(
    private val uiAutomation: UIAutomation
) {
    
    /**
     * 处理远程命令
     */
    suspend fun handleCommand(command: String): RemoteCommandResult = 
        withContext(Dispatchers.IO) {
            try {
                val json = JSONObject(command)
                val action = json.getString("action")
                
                Timber.d("🎮 远程命令: $action")
                
                val success = when (action) {
                    "click" -> {
                        val x = json.getInt("x")
                        val y = json.getInt("y")
                        uiAutomation.click(x, y)
                    }
                    
                    "long_click" -> {
                        val x = json.getInt("x")
                        val y = json.getInt("y")
                        val duration = json.optInt("duration", 1000)
                        uiAutomation.longClick(x, y, duration)
                    }
                    
                    "swipe" -> {
                        val x1 = json.getInt("x1")
                        val y1 = json.getInt("y1")
                        val x2 = json.getInt("x2")
                        val y2 = json.getInt("y2")
                        val duration = json.optInt("duration", 300)
                        uiAutomation.swipe(x1, y1, x2, y2, duration)
                    }
                    
                    "input" -> {
                        val text = json.getString("text")
                        uiAutomation.inputText(text)
                    }
                    
                    "key" -> {
                        val keyCode = json.getInt("keyCode")
                        uiAutomation.pressKey(keyCode)
                    }
                    
                    "back" -> uiAutomation.pressBack()
                    "home" -> uiAutomation.pressHome()
                    "recent" -> uiAutomation.pressRecent()
                    
                    else -> {
                        Timber.w("⚠️ 未知命令: $action")
                        false
                    }
                }
                
                RemoteCommandResult(
                    success = success,
                    action = action
                )
                
            } catch (e: Exception) {
                Timber.e(e, "❌ 命令处理失败")
                RemoteCommandResult(
                    success = false,
                    action = "",
                    error = e.message
                )
            }
        }
}

data class RemoteCommandResult(
    val success: Boolean,
    val action: String,
    val error: String? = null
)