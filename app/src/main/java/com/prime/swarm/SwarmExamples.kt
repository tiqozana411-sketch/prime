package com.prime.swarm

/**
 * 蜂群系统使用示例
 */

// ============================================
// 示例1：简单登录任务
// ============================================
suspend fun example1_SimpleLogin() {
    val controller = PrimeController.getInstance(context)
    controller.initialize()
    
    val result = controller.executeTaskWithSwarm(
        task = "登录微信",
        context = mapOf(
            "username" to "user123",
            "password" to "pass456"
        )
    )
    
    if (result.success) {
        println("✅ 登录成功")
    } else {
        println("❌ 登录失败: ${result.message}")
    }
}

// ============================================
// 示例2：复杂搜索任务
// ============================================
suspend fun example2_ComplexSearch() {
    val controller = PrimeController.getInstance(context)
    
    val result = controller.executeTaskWithSwarm(
        task = "搜索手机",
        context = mapOf(
            "query" to "iPhone 15 Pro",
            "filters" to listOf("价格", "评分")
        )
    )
    
    val swarmResult = result.data as? SwarmExecutionResult
    println("完成任务: ${swarmResult?.completedTasks}/${swarmResult?.totalTasks}")
    println("耗时: ${swarmResult?.executionTime}ms")
}

// ============================================
// 示例3：监控性能指标
// ============================================
fun example3_MonitorPerformance() {
    val controller = PrimeController.getInstance(context)
    val stats = controller.getStats()
    
    val metrics = stats["swarmMetrics"] as? PerformanceMetrics
    
    println("=== 蜂群性能指标 ===")
    println("效率:")
    println("  总任务: ${metrics?.totalTasks}")
    println("  平均耗时: ${metrics?.avgExecutionTime}ms")
    println("  并发度: ${metrics?.concurrency}")
    
    println("\n准确率:")
    println("  成功率: ${"%.2f".format(metrics?.successRate)}%")
    println("  失败率: ${"%.2f".format(metrics?.failureRate)}%")
    
    println("\n设备性能:")
    println("  CPU: ${"%.1f".format(metrics?.cpuUsage)}%")
    println("  内存: ${"%.1f".format(metrics?.memoryUsage)}%")
    println("  温度: ${"%.1f".format(metrics?.temperature)}°C")
}

// ============================================
// 示例4：对比单Agent和蜂群模式
// ============================================
suspend fun example4_ComparePerformance() {
    val controller = PrimeController.getInstance(context)
    
    // 单Agent模式
    val start1 = System.currentTimeMillis()
    val result1 = controller.executeTask("登录微信")
    val time1 = System.currentTimeMillis() - start1
    
    // 蜂群模式
    val start2 = System.currentTimeMillis()
    val result2 = controller.executeTaskWithSwarm("登录微信")
    val time2 = System.currentTimeMillis() - start2
    
    println("=== 性能对比 ===")
    println("单Agent: ${time1}ms 成功=${result1.success}")
    println("蜂群模式: ${time2}ms 成功=${result2.success}")
    println("提升: ${((time1 - time2) * 100.0 / time1).toInt()}%")
}

// ============================================
// 示例5：自定义任务分解
// ============================================
suspend fun example5_CustomTaskDecomposition() {
    val controller = PrimeController.getInstance(context)
    
    // 复杂的多步骤任务
    val result = controller.executeTaskWithSwarm(
        task = "发送朋友圈",
        context = mapOf(
            "text" to "今天天气真好！",
            "images" to listOf("/sdcard/photo1.jpg", "/sdcard/photo2.jpg"),
            "location" to "北京"
        )
    )
    
    // 任务会被自动分解为：
    // 1. 点击朋友圈入口
    // 2. 点击发布按钮
    // 3. 输入文本
    // 4. 选择图片1 (并发)
    // 5. 选择图片2 (并发)
    // 6. 选择位置
    // 7. 点击发送
    
    println("任务结果: ${result.message}")
}

// ============================================
// 示例6：错误处理
// ============================================
suspend fun example6_ErrorHandling() {
    val controller = PrimeController.getInstance(context)
    
    val result = controller.executeTaskWithSwarm("登录微信")
    
    if (!result.success) {
        val swarmResult = result.data as? SwarmExecutionResult
        
        println("❌ 任务失败")
        println("错误: ${swarmResult?.error}")
        println("完成: ${swarmResult?.completedTasks}/${swarmResult?.totalTasks}")
        println("失败任务:")
        
        swarmResult?.results?.filter { !it.success }?.forEach { taskResult ->
            println("  - ${taskResult.taskId}: ${taskResult.error}")
        }
    }
}

// ============================================
// 示例7：性能自适应
// ============================================
suspend fun example7_PerformanceAdaptive() {
    val controller = PrimeController.getInstance(context)
    
    // 蜂群系统会自动根据设备性能调整Worker数量
    // 高性能设备: 5个Worker
    // 中等设备: 3个Worker
    // 低端设备: 2个Worker
    // 极低端设备: 1个Worker
    
    val result = controller.executeTaskWithSwarm("登录微信")
    val metrics = controller.getStats()["swarmMetrics"] as? PerformanceMetrics
    
    println("当前并发度: ${metrics?.concurrency}")
    println("CPU使用率: ${metrics?.cpuUsage}%")
    println("内存使用率: ${metrics?.memoryUsage}%")
}