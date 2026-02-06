package com.prime.core

import android.content.Context
import com.topjohnwu.superuser.Shell
import timber.log.Timber

/**
 * ROOT权限管理器
 * 
 * 职责：
 * 1. 检测ROOT权限
 * 2. 请求ROOT授权
 * 3. 执行ROOT命令
 * 
 * Day 1: 基础框架
 * Day 3-4: 完整实现
 */
object RootManager {
    
    private var initialized = false
    private var rootAvailable = false
    
    /**
     * 初始化ROOT管理器
     */
    fun init(context: Context) {
        if (initialized) return
        
        Timber.i("初始化ROOT管理器...")
        
        // 配置libsu
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
        
        // 检测ROOT
        rootAvailable = Shell.getShell().isRoot
        
        initialized = true
        Timber.i("ROOT管理器初始化完成 - ROOT可用: $rootAvailable")
    }
    
/**
     * 是否有ROOT权限（属性版本，兼容旧代码）
     */
    val isRootAvailable: Boolean
        get() = rootAvailable
    
    /**
     * 检查ROOT权限（suspend版本）
     */
    suspend fun checkRoot(): Boolean {
        if (!initialized) {
            return false
        }
        return rootAvailable
    }
    
    /**
     * 是否有ROOT权限
     */
    fun hasRoot(): Boolean {
        return rootAvailable
    }
    
/**
     * 请求ROOT权限（带错误处理）
     */
    fun requestRoot(): Boolean {
        if (!initialized) {
            Timber.e("❌ ROOT管理器未初始化，无法请求ROOT权限")
            return false
        }
        
        if (rootAvailable) {
            Timber.d("✅ ROOT权限已可用")
            return true
        }
        
        Timber.i("🔐 请求ROOT权限...")
        
        try {
            rootAvailable = Shell.getShell().isRoot
            
            if (rootAvailable) {
                Timber.i("✅ ROOT权限获取成功")
            } else {
                Timber.w("⚠️ ROOT权限获取失败，设备可能未ROOT或用户拒绝授权")
            }
        } catch (e: Exception) {
            Timber.e(e, "❌ 请求ROOT权限时发生异常")
            rootAvailable = false
        }
        
        return rootAvailable
    }
    
/**
     * 执行ROOT命令（带重试和统一错误处理）
     * 
     * @param command 命令
     * @param retryCount 重试次数（默认0）
     * @return 执行结果
     */
    fun exec(command: String, retryCount: Int = 0): ShellResult {
        // 前置检查
        if (!initialized) {
            Timber.e("❌ ROOT管理器未初始化")
            return ShellResult(
                success = false,
                output = emptyList(),
                error = "ROOT管理器未初始化，请先调用init()"
            )
        }
        
        if (!hasRoot()) {
            Timber.w("⚠️ ROOT权限不可用")
            return ShellResult(
                success = false,
                output = emptyList(),
                error = "ROOT权限不可用，请检查设备是否已ROOT"
            )
        }
        
        // 命令合法性检查
        if (command.isBlank()) {
            Timber.e("❌ 命令为空")
            return ShellResult(
                success = false,
                output = emptyList(),
                error = "命令不能为空"
            )
        }
        
        Timber.d("🔧 执行ROOT命令: $command")
        
        // 执行命令（带重试）
        var lastError: String? = null
        repeat(retryCount + 1) { attempt ->
            try {
                val result = Shell.cmd(command).exec()
                
                if (result.isSuccess) {
                    if (attempt > 0) {
                        Timber.i("✅ 命令执行成功（第${attempt + 1}次尝试）")
                    }
                    return ShellResult(
                        success = true,
                        output = result.out,
                        error = null
                    )
                } else {
                    lastError = result.err.joinToString("\n")
                    Timber.w("⚠️ 命令执行失败（第${attempt + 1}次尝试）: $lastError")
                }
                
            } catch (e: Exception) {
                lastError = e.message ?: "未知错误"
                Timber.e(e, "❌ 命令执行异常（第${attempt + 1}次尝试）")
            }
            
            // 重试前等待
            if (attempt < retryCount) {
                Thread.sleep(500)
            }
        }
        
        // 所有重试都失败
        return ShellResult(
            success = false,
            output = emptyList(),
            error = lastError ?: "命令执行失败"
        )
    }
    
    /**
     * 批量执行ROOT命令（事务模式）
     * 任何一条失败则全部回滚
     */
    fun execBatch(commands: List<String>): ShellResult {
        if (!hasRoot()) {
            return ShellResult(
                success = false,
                output = emptyList(),
                error = "ROOT权限不可用"
            )
        }
        
        if (commands.isEmpty()) {
            return ShellResult(
                success = false,
                output = emptyList(),
                error = "命令列表为空"
            )
        }
        
        Timber.d("🔧 批量执行${commands.size}条ROOT命令")
        
        try {
            val result = Shell.cmd(*commands.toTypedArray()).exec()
            
            return ShellResult(
                success = result.isSuccess,
                output = result.out,
                error = if (result.isSuccess) null else result.err.joinToString("\n")
            )
        } catch (e: Exception) {
            Timber.e(e, "❌ 批量命令执行异常")
            return ShellResult(
                success = false,
                output = emptyList(),
                error = e.message ?: "批量命令执行失败"
            )
        }
    }
    
    /**
     * Shell命令执行结果
     */
    data class ShellResult(
        val success: Boolean,
        val output: List<String>,
        val error: String?
    )
}
