package com.prime.ai.providers

import timber.log.Timber

/**
 * 规则引擎 Provider
 * 基于关键词匹配的简单规则（降级方案）
 */
class RuleEngineProvider : AIProvider {
    
    override suspend fun initialize(config: AIConfig): Boolean {
        Timber.i("✅ 规则引擎初始化成功")
        return true
    }
    
    override suspend fun inference(prompt: String): String {
        Timber.d("🔧 使用规则引擎推理")
        
        // 提取任务描述（从prompt中提取用户任务）
        val task = extractTask(prompt)
        
        // 简单的规则匹配
        return when {
            task.contains("搜索") -> {
                val keyword = task.replace("搜索", "").trim()
                """
[
  {"action":"click","target":"搜索框","x":540,"y":200},
  {"action":"input","text":"$keyword"},
  {"action":"wait","target":"搜索结果","timeout":3000}
]
                """.trimIndent()
            }
            
            task.contains("打开") -> {
                val app = task.replace("打开", "").trim()
                """
[
  {"action":"click","target":"$app","x":540,"y":800},
  {"action":"wait","target":"应用界面","timeout":2000}
]
                """.trimIndent()
            }
            
            task.contains("返回") -> """
[{"action":"back"}]
            """.trimIndent()
            
            task.contains("主页") || task.contains("桌面") -> """
[{"action":"home"}]
            """.trimIndent()
            
            task.contains("向上滑动") || task.contains("上滑") -> """
[{"action":"swipe","direction":"up"}]
            """.trimIndent()
            
            task.contains("向下滑动") || task.contains("下滑") -> """
[{"action":"swipe","direction":"down"}]
            """.trimIndent()
            
            task.contains("点击") -> {
                val target = task.replace("点击", "").trim()
                """
[{"action":"click","target":"$target","x":540,"y":800}]
                """.trimIndent()
            }
            
            else -> """
[{"action":"wait","target":"任意元素","timeout":1000}]
            """.trimIndent()
        }
    }
    
    override fun isAvailable(): Boolean = true
    
    override fun cleanup() {
        // 规则引擎无需清理
    }
    
    override fun getName(): String = "RuleEngine"
    
    /**
     * 从prompt中提取用户任务
     */
    private fun extractTask(prompt: String): String {
        // 查找"用户任务："后面的内容
        val taskMarker = "用户任务："
        val startIndex = prompt.indexOf(taskMarker)
        
        if (startIndex == -1) {
            return prompt  // 如果没找到标记，返回整个prompt
        }
        
        val taskStart = startIndex + taskMarker.length
        val taskEnd = prompt.indexOf("\n", taskStart).let {
            if (it == -1) prompt.length else it
        }
        
        return prompt.substring(taskStart, taskEnd).trim()
    }
}
