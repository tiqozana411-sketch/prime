package com.prime

import android.app.Application
import com.topjohnwu.superuser.Shell
import timber.log.Timber

/**
 * PRIME应用主类
 */
class PrimeApplication : Application() {
    
    companion object {
        private var _instance: PrimeApplication? = null
        
        val instance: PrimeApplication
            get() = _instance ?: throw IllegalStateException(
                "PrimeApplication未初始化，请确保Application已启动"
            )
        
        fun isInitialized(): Boolean = _instance != null
    }
    
    override fun onCreate() {
        super.onCreate()
        _instance = this
        
        // 初始化日志
        Timber.plant(Timber.DebugTree())
        
        // 初始化Shell（ROOT）
        Shell.enableVerboseLogging = true
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_REDIRECT_STDERR)
                .setTimeout(10)
        )
        
        Timber.i("✅ PRIME启动完成")
    }
}
