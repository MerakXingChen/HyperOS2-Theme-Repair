package com.merak

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.merak.databinding.ActivityMainBinding
import rikka.shizuku.Shizuku
import java.io.File

class MainActivity : AppCompatActivity() {

    private var _binding: ActivityMainBinding? = null
    
    private val binding: ActivityMainBinding
        get() = checkNotNull(_binding) { "Activity has been destroyed" }
    
    private var selectedThemeFile: String? = null
    private val correctPassword = "656100875"
    private val targetThemePath = "/sdcard/Android/data/com.android.thememanager/files/temp.mtz"
    
    companion object {
        private const val PERMISSION_CODE = 1001
    }

    // 文件选择器
    private val filePickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                handleSelectedFile(uri)
            }
        }
    }

    // Shizuku 权限请求监听
    private val onRequestPermissionResultListener = 
        Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
            val granted = grantResult == PackageManager.PERMISSION_GRANTED
            if (granted) {
                Toast.makeText(this, "Shizuku 权限授予成功", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Shizuku 权限授予失败", Toast.LENGTH_SHORT).show()
            }
            updateUIState()
        }

    // Shizuku 服务状态监听
    private val onBinderReceivedListener = Shizuku.OnBinderReceivedListener {
        runOnUiThread {
            Toast.makeText(this, "Shizuku 服务启动", Toast.LENGTH_SHORT).show()
            updateUIState()
        }
    }

    private val onBinderDeadListener = Shizuku.OnBinderDeadListener {
        runOnUiThread {
            Toast.makeText(this, "Shizuku 服务终止", Toast.LENGTH_SHORT).show()
            updateUIState()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        _binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupClickListeners()
        setupShizuku()
        updateUIState()
    }

    private fun setupClickListeners() {
        // 选择主题文件按钮
        binding.btnSelectTheme.setOnClickListener {
            openFilePicker()
        }

        // 申请 Shizuku 权限按钮
        binding.btnRequestShizuku.setOnClickListener {
            requestShizukuPermission()
        }

        // 移动主题文件按钮
        binding.btnMoveTheme.setOnClickListener {
            moveThemeFile()
        }
        
        // 安装主题按钮
        binding.btnInstall.setOnClickListener {
            installTheme()
        }
        
        // 密码输入监听
        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateInstallButtonState()
            }
        })
    }

    private fun setupShizuku() {
        // 添加权限请求监听
        Shizuku.addRequestPermissionResultListener(onRequestPermissionResultListener)
        
        // 添加服务状态监听
        Shizuku.addBinderReceivedListenerSticky(onBinderReceivedListener)
        Shizuku.addBinderDeadListener(onBinderDeadListener)
    }

    private fun checkShizukuPermission(): Boolean {
        return if (Shizuku.pingBinder()) {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } else {
            false
        }
    }

    private fun requestShizukuPermission() {
        if (checkShizukuPermission()) {
            Toast.makeText(this, "已拥有 Shizuku 权限", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Shizuku.pingBinder()) {
            Toast.makeText(this, "Shizuku 服务未运行，请先启动 Shizuku", Toast.LENGTH_LONG).show()
            return
        }

        if (Shizuku.isPreV11()) {
            Toast.makeText(this, "当前 Shizuku 版本不支持动态申请权限", Toast.LENGTH_SHORT).show()
            return
        }

        Shizuku.requestPermission(PERMISSION_CODE)
    }

    private fun updateUIState() {
        val hasShizukuPermission = checkShizukuPermission()
        val shizukuRunning = Shizuku.pingBinder()
        
        // 更新 Shizuku 状态显示
        when {
            !shizukuRunning -> {
                binding.tvShizukuStatus.text = "❌ Shizuku 服务未运行"
                binding.tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                binding.btnRequestShizuku.visibility = android.view.View.GONE
            }
            !hasShizukuPermission -> {
                binding.tvShizukuStatus.text = "⚠️ Shizuku 权限未授予"
                binding.tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark))
                binding.btnRequestShizuku.visibility = android.view.View.VISIBLE
            }
            else -> {
                binding.tvShizukuStatus.text = "✅ Shizuku 服务已就绪"
                binding.tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                binding.btnRequestShizuku.visibility = android.view.View.GONE
            }
        }

        // 更新移动按钮状态
        updateMoveButtonState()
        // 更新安装按钮状态
        updateInstallButtonState()
    }

    private fun updateMoveButtonState() {
        val hasFile = selectedThemeFile != null
        val canMove = hasFile && checkShizukuPermission()
        binding.btnMoveTheme.isEnabled = canMove
    }

    private fun updateInstallButtonState() {
        val hasPassword = binding.etPassword.text?.isNotEmpty() == true
        val themeExists = File(targetThemePath).exists()
        binding.btnInstall.isEnabled = hasPassword && themeExists
    }

    private fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf("*/*"))
        }
        filePickerLauncher.launch(intent)
    }
    
    private fun handleSelectedFile(uri: Uri) {
        try {
            val fileName = getFileName(uri)
            
            if (fileName != null && fileName.endsWith(".mtz", ignoreCase = true)) {
                val realPath = getRealPathFromUri(uri)
                if (realPath != null) {
                    selectedThemeFile = realPath
                    binding.tvSelectedFile.text = "已选择: $fileName"
                    updateMoveButtonState()
                } else {
                    showToast("无法获取文件路径")
                }
            } else {
                showToast("请选择 .mtz 格式的主题文件")
            }
        } catch (e: Exception) {
            showToast("文件选择失败: ${e.message}")
        }
    }
    
    private fun getFileName(uri: Uri): String? {
        return contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            cursor.moveToFirst()
            cursor.getString(nameIndex)
        }
    }
    
    private fun getRealPathFromUri(uri: Uri): String? {
        return try {
            val inputStream = contentResolver.openInputStream(uri)
            val fileName = getFileName(uri) ?: "temp.mtz"
            val tempFile = File(cacheDir, fileName)
            
            inputStream?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            
            tempFile.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    private fun moveThemeFile() {
        val sourceFile = selectedThemeFile
        if (sourceFile.isNullOrEmpty()) {
            showToast("请先选择主题文件")
            return
        }

        if (!checkShizukuPermission()) {
            showToast("Shizuku 权限未授予")
            return
        }

        try {
            binding.tvMoveStatus.text = "移动中..."
            binding.btnMoveTheme.isEnabled = false

            // 使用 Shizuku 的 Shell 命令复制文件
            Thread {
                try {
                    // 创建目标目录
                    val mkdirCommand = "mkdir -p /sdcard/Android/data/com.android.thememanager/files/"
                    val mkdirResult = Shizuku.newProcess(arrayOf("sh", "-c", mkdirCommand), null, null)
                        .waitFor()

                    // 复制文件
                    val copyCommand = "cp \"$sourceFile\" \"$targetThemePath\""
                    val copyResult = Shizuku.newProcess(arrayOf("sh", "-c", copyCommand), null, null)
                        .waitFor()

                    runOnUiThread {
                        if (copyResult == 0) {
                            binding.tvMoveStatus.text = "✅ 移动成功"
                            binding.tvMoveStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
                            showToast("主题文件移动成功")
                            updateInstallButtonState()
                        } else {
                            binding.tvMoveStatus.text = "❌ 移动失败"
                            binding.tvMoveStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                            showToast("主题文件移动失败")
                        }
                        binding.btnMoveTheme.isEnabled = true
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        binding.tvMoveStatus.text = "❌ 移动失败: ${e.message}"
                        binding.tvMoveStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
                        showToast("移动文件时发生错误: ${e.message}")
                        binding.btnMoveTheme.isEnabled = true
                    }
                }
            }.start()

        } catch (e: Exception) {
            binding.tvMoveStatus.text = "❌ 移动失败: ${e.message}"
            binding.tvMoveStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            showToast("移动文件失败: ${e.message}")
            binding.btnMoveTheme.isEnabled = true
        }
    }
    
    private fun installTheme() {
        val password = binding.etPassword.text.toString().trim()
        
        // 验证密码
        if (password != correctPassword) {
            showToast("密码错误，无法安装主题")
            return
        }
        
        // 检查主题文件是否存在
        if (!File(targetThemePath).exists()) {
            showToast("主题文件不存在，请先移动主题文件")
            return
        }
        
        try {
            // 创建 Intent 调用小米主题管理器
            val intent = Intent().apply {
                setClassName(
                    "com.android.thememanager",
                    "com.android.thememanager.ApplyThemeForScreenshot"
                )
                putExtra("theme_file_path", targetThemePath)
                putExtra("api_called_from", "ThemeEditor")
                putExtra("ver2_step", "ver2_step_apply")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            
            startActivity(intent)
            showToast("正在启动主题安装...")
            
        } catch (e: Exception) {
            showToast("启动主题管理器失败: ${e.message}")
        }
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
    
    override fun onDestroy() {
        super.onDestroy()
        
        // 移除监听器
        Shizuku.removeRequestPermissionResultListener(onRequestPermissionResultListener)
        Shizuku.removeBinderReceivedListener(onBinderReceivedListener)
        Shizuku.removeBinderDeadListener(onBinderDeadListener)
        
        _binding = null
    }
}
