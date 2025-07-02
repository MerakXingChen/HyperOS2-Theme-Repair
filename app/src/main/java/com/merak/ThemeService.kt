package com.merak

import android.os.RemoteException
import java.io.*

class ThemeService : IThemeService.Stub() {
    
    @Throws(RemoteException::class)
    override fun destroy() {
        System.exit(0)
    }

    @Throws(RemoteException::class)
    override fun exit() {
        destroy()
    }

    @Throws(RemoteException::class)
    override fun exec(command: String): String {
        val stringBuilder = StringBuilder()
        try {
            val process = Runtime.getRuntime().exec(command)
            val inputStreamReader = InputStreamReader(process.inputStream)
            val bufferedReader = BufferedReader(inputStreamReader)
            var line: String?
            while (bufferedReader.readLine().also { line = it } != null) {
                stringBuilder.append(line).append("\n")
            }
            bufferedReader.close()
            inputStreamReader.close()
            process.waitFor()
        } catch (e: Exception) {
            return "执行命令失败: ${e.message}"
        }
        return stringBuilder.toString()
    }

    @Throws(RemoteException::class)
    override fun copyFile(sourcePath: String, targetPath: String): Boolean {
        return try {
            val sourceFile = File(sourcePath)
            val targetFile = File(targetPath)
            
            if (!sourceFile.exists()) {
                return false
            }
            
            // 创建目标目录
            targetFile.parentFile?.mkdirs()
            
            // 复制文件
            sourceFile.copyTo(targetFile, overwrite = true)
            true
        } catch (e: Exception) {
            false
        }
    }
}
