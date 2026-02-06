package com.prime.swarm

import android.util.Log

/**
 * 任务分解器
 * 基于周易因果推理：分析任务 → 识别依赖 → 生成执行计划
 */
class TaskDecomposer {
    
    private val TAG = "TaskDecomposer"
    
    /**
     * 分解任务为子任务
     */
    fun decompose(description: String, context: Map<String, Any>): List<SubTask> {
        Log.d(TAG, "开始分解任务: $description")
        
        // 简单的规则引擎分解（后续可接入AI）
        val subTasks = mutableListOf<SubTask>()
        
        when {
            // 登录任务
            description.contains("登录", ignoreCase = true) -> {
                subTasks.add(SubTask(
                    id = "task_1",
                    type = TaskType.CLICK,
                    description = "点击用户名输入框",
                    complexity = 3,
                    priority = 8,
                    data = mapOf("target" to "用户名")
                ))
                subTasks.add(SubTask(
                    id = "task_2",
                    type = TaskType.INPUT,
                    description = "输入用户名",
                    complexity = 3,
                    priority = 8,
                    dependencies = listOf("task_1"),
                    data = mapOf(
                        "target" to "用户名",
                        "text" to (context["username"] ?: "")
                    )
                ))
                subTasks.add(SubTask(
                    id = "task_3",
                    type = TaskType.CLICK,
                    description = "点击密码输入框",
                    complexity = 3,
                    priority = 8,
                    dependencies = listOf("task_2"),
                    data = mapOf("target" to "密码")
                ))
                subTasks.add(SubTask(
                    id = "task_4",
                    type = TaskType.INPUT,
                    description = "输入密码",
                    complexity = 3,
                    priority = 8,
                    dependencies = listOf("task_3"),
                    data = mapOf(
                        "target" to "密码",
                        "text" to (context["password"] ?: "")
                    )
                ))
                subTasks.add(SubTask(
                    id = "task_5",
                    type = TaskType.CLICK,
                    description = "点击登录按钮",
                    complexity = 3,
                    priority = 10,
                    dependencies = listOf("task_4"),
                    data = mapOf("target" to "登录")
                ))
            }
            
            // 搜索任务
            description.contains("搜索", ignoreCase = true) -> {
                subTasks.add(SubTask(
                    id = "task_1",
                    type = TaskType.CLICK,
                    description = "点击搜索框",
                    complexity = 2,
                    priority = 7,
                    data = mapOf("target" to "搜索")
                ))
                subTasks.add(SubTask(
                    id = "task_2",
                    type = TaskType.INPUT,
                    description = "输入搜索内容",
                    complexity = 2,
                    priority = 7,
                    dependencies = listOf("task_1"),
                    data = mapOf(
                        "target" to "搜索",
                        "text" to (context["query"] ?: "")
                    )
                ))
                subTasks.add(SubTask(
                    id = "task_3",
                    type = TaskType.CLICK,
                    description = "点击搜索按钮",
                    complexity = 2,
                    priority = 7,
                    dependencies = listOf("task_2"),
                    data = mapOf("target" to "搜索按钮")
                ))
            }
            
            // 通用任务（单步操作）
            else -> {
                subTasks.add(SubTask(
                    id = "task_1",
                    type = inferTaskType(description),
                    description = description,
                    complexity = 5,
                    priority = 5,
                    data = context
                ))
            }
        }
        
        Log.d(TAG, "任务分解完成: ${subTasks.size}个子任务")
        return subTasks
    }
    
    /**
     * 推断任务类型
     */
    private fun inferTaskType(description: String): TaskType {
        return when {
            description.contains("点击", ignoreCase = true) -> TaskType.CLICK
            description.contains("输入", ignoreCase = true) -> TaskType.INPUT
            description.contains("滚动", ignoreCase = true) -> TaskType.SCROLL
            description.contains("等待", ignoreCase = true) -> TaskType.WAIT
            description.contains("验证", ignoreCase = true) -> TaskType.VERIFY
            description.contains("分析", ignoreCase = true) -> TaskType.ANALYZE
            else -> TaskType.COMPOSITE
        }
    }
}
