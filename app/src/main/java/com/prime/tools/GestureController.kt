package com.prime.tools

import com.prime.core.RootManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import kotlin.math.cos
import kotlin.math.sin

/**
 * 手势控制器
 * 支持复杂手势：多点触控、曲线滑动等
 */
object GestureController {
    
    /**
     * 绘制手势路径
     */
    suspend fun drawPath(points: List<Point>, duration: Long = 500): Boolean = withContext(Dispatchers.IO) {
        if (points.size < 2) {
            Timber.e("路径点数不足")
            return@withContext false
        }
        
        try {
            // 构建swipe命令
            val commands = mutableListOf<String>()
            for (i in 0 until points.size - 1) {
                val start = points[i]
                val end = points[i + 1]
                val segmentDuration = duration / (points.size - 1)
                commands.add("input swipe ${start.x} ${start.y} ${end.x} ${end.y} $segmentDuration")
            }
            
            // 执行所有命令
            for (cmd in commands) {
                val result = RootManager.exec(cmd)
                if (!result.success) {
                    Timber.e("手势执行失败: ${result.error}")
                    return@withContext false
                }
            }
            
            Timber.d("✅ 手势绘制完成: ${points.size}个点")
            true
        } catch (e: Exception) {
            Timber.e(e, "手势异常")
            false
        }
    }
    
    /**
     * 圆形手势
     */
    suspend fun circle(
        centerX: Int,
        centerY: Int,
        radius: Int,
        clockwise: Boolean = true,
        duration: Long = 1000
    ): Boolean {
        val points = mutableListOf<Point>()
        val steps = 36 // 36个点组成圆
        
        for (i in 0..steps) {
            val angle = (i * 360.0 / steps) * Math.PI / 180.0
            val direction = if (clockwise) 1 else -1
            val x = centerX + (radius * cos(angle * direction)).toInt()
            val y = centerY + (radius * sin(angle * direction)).toInt()
            points.add(Point(x, y))
        }
        
        return drawPath(points, duration)
    }
    
    /**
     * 缩放手势（双指）
     */
    suspend fun pinch(
        centerX: Int,
        centerY: Int,
        startDistance: Int,
        endDistance: Int,
        duration: Long = 500
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            // 计算两个触摸点的起始和结束位置
            val startX1 = centerX - startDistance / 2
            val startX2 = centerX + startDistance / 2
            val endX1 = centerX - endDistance / 2
            val endX2 = centerX + endDistance / 2
            
            // 执行双指滑动（需要ROOT权限的高级命令）
            val cmd = """
                input swipe $startX1 $centerY $endX1 $centerY $duration &
                input swipe $startX2 $centerY $endX2 $centerY $duration
            """.trimIndent()
            
            val result = RootManager.exec(cmd)
            if (result.success) {
                Timber.d("✅ 缩放手势完成")
                true
            } else {
                Timber.e("❌ 缩放手势失败: ${result.error}")
                false
            }
        } catch (e: Exception) {
            Timber.e(e, "缩放手势异常")
            false
        }
    }
    
    /**
     * 拖拽
     */
    suspend fun drag(
        startX: Int, startY: Int,
        endX: Int, endY: Int,
        duration: Long = 1000
    ): Boolean {
        return UIAutomation.swipe(startX, startY, endX, endY, duration)
    }
}

data class Point(val x: Int, val y: Int)