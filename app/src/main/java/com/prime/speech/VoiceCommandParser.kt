package com.prime.speech

import timber.log.Timber

/**
 * 语音指令解析器
 * 将语音识别结果解析为可执行的指令
 */
object VoiceCommandParser {
    
    /**
     * 解析语音指令
     */
    fun parse(text: String): VoiceCommand? {
        val normalizedText = text.trim().lowercase()
        
        return when {
            // 打开应用
            normalizedText.matches(Regex("打开.*")) -> {
                val appName = normalizedText.removePrefix("打开").trim()
                VoiceCommand.OpenApp(appName)
            }
            
            // 截图
            normalizedText.contains("截图") || normalizedText.contains("截屏") -> {
                VoiceCommand.Screenshot
            }
            
            // 返回
            normalizedText.contains("返回") -> {
                VoiceCommand.PressBack
            }
            
            // 回到主页
            normalizedText.contains("主页") || normalizedText.contains("桌面") -> {
                VoiceCommand.PressHome
            }
            
            // 搜索
            normalizedText.matches(Regex("搜索.*")) -> {
                val keyword = normalizedText.removePrefix("搜索").trim()
                VoiceCommand.Search(keyword)
            }
            
            // 发送消息
            normalizedText.matches(Regex("发送.*给.*")) -> {
                val parts = normalizedText.removePrefix("发送").split("给")
                if (parts.size == 2) {
                    val message = parts[0].trim()
                    val contact = parts[1].trim()
                    VoiceCommand.SendMessage(contact, message)
                } else null
            }
            
            // 音量控制
            normalizedText.contains("音量") -> {
                when {
                    normalizedText.contains("增大") || normalizedText.contains("调大") -> 
                        VoiceCommand.VolumeUp
                    normalizedText.contains("减小") || normalizedText.contains("调小") -> 
                        VoiceCommand.VolumeDown
                    normalizedText.contains("静音") -> 
                        VoiceCommand.Mute
                    else -> null
                }
            }
            
            // 滑动
            normalizedText.contains("向上滑") || normalizedText.contains("上滑") -> 
                VoiceCommand.ScrollUp
            normalizedText.contains("向下滑") || normalizedText.contains("下滑") -> 
                VoiceCommand.ScrollDown
            
            // 未识别
            else -> {
                Timber.w("⚠️ 未识别的语音指令: $text")
                null
            }
        }
    }
}

/**
 * 语音指令类型
 */
sealed class VoiceCommand {
    data class OpenApp(val appName: String) : VoiceCommand()
    object Screenshot : VoiceCommand()
    object PressBack : VoiceCommand()
    object PressHome : VoiceCommand()
    data class Search(val keyword: String) : VoiceCommand()
    data class SendMessage(val contact: String, val message: String) : VoiceCommand()
    object VolumeUp : VoiceCommand()
    object VolumeDown : VoiceCommand()
    object Mute : VoiceCommand()
    object ScrollUp : VoiceCommand()
    object ScrollDown : VoiceCommand()
}