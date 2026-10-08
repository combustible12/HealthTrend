package com.combustible12.healthtrend

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File

/**
 * Shared capture entry point for all image attachments.
 * The caller owns the resulting image immediately and persists it before leaving the editor.
 * A cancelled capture never changes the attachment list.
 */
@Composable
fun rememberAttachmentCamera(
    onCaptured: (Uri) -> Unit,
    onError: (String) -> Unit
): () -> Unit {
    val context = LocalContext.current
    var pendingPath by rememberSaveable { mutableStateOf<String?>(null) }
    val currentCaptured by rememberUpdatedState(onCaptured)
    val currentError by rememberUpdatedState(onError)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val path = pendingPath
        pendingPath = null
        if (path != null) {
            val file = File(path)
            if (success && file.isFile && file.length() > 0L) {
                try {
                    currentCaptured(FileProvider.getUriForFile(context, context.packageName + ".files", file))
                } catch (e: Exception) {
                    currentError("照片读取失败：${e.message}")
                }
            } else {
                file.delete()
                if (success) currentError("拍照失败：没有生成有效图片")
            }
        }
    }
    return {
        try {
            val dir = File(context.cacheDir, "capture").apply { mkdirs() }
            val file = File.createTempFile("attachment-", ".jpg", dir)
            val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
            pendingPath = file.absolutePath
            launcher.launch(uri)
        } catch (e: Exception) {
            pendingPath?.let { File(it).delete() }
            pendingPath = null
            currentError("无法启动相机：${e.message}")
        }
    }
}
