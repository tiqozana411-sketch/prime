package com.prime

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.prime.core.RootManager
import kotlinx.coroutines.launch
import timber.log.Timber
import android.widget.TextView

/**
 * PRIME主界面
 */
class MainActivity : AppCompatActivity() {
    
    private lateinit var tvStatus: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        tvStatus = findViewById(R.id.tvStatus)
        tvStatus.text = "检测ROOT权限中..."
        
        // 检测ROOT权限
        lifecycleScope.launch {
            val hasRoot = RootManager.checkRoot()
            
            tvStatus.text = if (hasRoot) {
                "✅ ROOT权限可用"
            } else {
                "❌ ROOT权限不可用"
            }
            
            Timber.i("ROOT状态: $hasRoot")
        }
    }
}