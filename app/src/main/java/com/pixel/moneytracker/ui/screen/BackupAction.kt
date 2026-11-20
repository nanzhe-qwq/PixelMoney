package com.pixel.moneytracker.ui.screen

import android.content.ContentResolver
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.moneytracker.BuildConfig
import com.pixel.moneytracker.ui.component.PixelCard
import com.pixel.moneytracker.ui.theme.*
import com.pixel.moneytracker.ui.viewmodel.MainViewModel
import com.pixel.moneytracker.util.BackupJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份文件的硬上限。与 [MiniJson.MAX_CHARS] 同量级：一份正常账单备份远小于这个数，
 * 超过就说明选错了文件，早点拒绝比读到 OOM 更友好。
 */
private const val MAX_BACKUP_BYTES = 16 * 1024 * 1024

private fun ContentResolver.readBackup(uri: Uri): String {
    return (openInputStream(uri) ?: throw IllegalArgumentException("无法打开所选文件")).use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            require(output.size() + size <= MAX_BACKUP_BYTES) { "备份文件不能超过 16 MB" }
            output.write(buffer, 0, size)
        }
        output.toByteArray().toString(Charsets.UTF_8)
    }
}

/**
 * 本地备份 / 恢复。
 *
 * 恢复是**整包替换**，会覆盖当前所有账本与账单，所以：解码成功只到预览，
 * 用户按住确认后才写库；写库前也提示先导出一份当前数据。
 */
@Composable
fun BackupAction(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<BackupJson.Decoded?>(null) }
    var pendingText by remember { mutableStateOf<String?>(null) }

    val exporter = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                val json = viewModel.buildBackupJson(BuildConfig.VERSION_NAME)
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri) ?: error("无法写入所选位置")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(json) }
                }
                Toast.makeText(context, "备份已导出", Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Toast.makeText(context, "导出失败，请重新选择保存位置", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    val importer = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            busy = true
            try {
                val text = withContext(Dispatchers.IO) { context.contentResolver.readBackup(uri) }
                val decoded = withContext(Dispatchers.IO) { BackupJson.decode(text) }
                pendingText = text
                pending = decoded
            } catch (e: CancellationException) {
                throw e
            } catch (e: BackupJson.BackupException) {
                Toast.makeText(context, "不是有效的备份：${e.message}", Toast.LENGTH_LONG).show()
            } catch (e: IllegalArgumentException) {
                Toast.makeText(context, e.message ?: "无法读取该文件", Toast.LENGTH_LONG).show()
            } catch (_: Exception) {
                Toast.makeText(context, "读取备份失败，请确认文件完整", Toast.LENGTH_LONG).show()
            } finally {
                busy = false
            }
        }
    }

    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Text("本地备份", color = PixelGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "把账本、账单（含回收站）、预算和定时记账规则导出成一个 JSON 文件。换手机或重装后，用它原样恢复。",
            color = PixelTextGray,
            fontSize = 14.sp
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                enabled = !busy,
                onClick = {
                    exporter.launch("像素记账备份-${java.time.LocalDate.now()}.json")
                }
            ) { Text(if (busy) "处理中…" else "导出备份") }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    importer.launch(arrayOf("application/json", "text/*", "application/octet-stream"))
                }
            ) { Text("从备份恢复") }
        }
    }

    val decoded = pending
    if (decoded != null && pendingText != null) {
        val data = decoded.data
        val trash = data.records.count { it.deletedAt != null }
        AlertDialog(
            onDismissRequest = { pending = null; pendingText = null },
            title = { Text("覆盖当前数据？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        "导出时间：" + SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA)
                            .format(Date(decoded.exportedAt)),
                        fontSize = 13.sp
                    )
                    Text("账本 ${data.ledgers.size} · 账单 ${data.records.size - trash} · 回收站 $trash", fontSize = 13.sp)
                    Text("预算 ${data.budgets.size} · 定时记账 ${data.recurring.size}", fontSize = 13.sp)
                    if (decoded.droppedImportKeys > 0) {
                        Text("有 ${decoded.droppedImportKeys} 条导入记录对不上账单，将跳过。", fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "恢复会用这份备份替换当前全部数据，且无法撤销。建议先导出一份当前备份。",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = {
                    val text = pendingText ?: return@TextButton
                    pending = null
                    pendingText = null
                    scope.launch {
                        busy = true
                        try {
                            val result = viewModel.restoreBackup(text)
                            val extra = if (result.droppedImportKeys > 0) "，跳过 ${result.droppedImportKeys} 条导入记录" else ""
                            Toast.makeText(
                                context,
                                "已恢复 ${result.records} 笔账（回收站 ${result.trash} 笔）$extra",
                                Toast.LENGTH_LONG
                            ).show()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            Toast.makeText(context, "恢复失败，当前数据未被替换", Toast.LENGTH_LONG).show()
                        } finally {
                            busy = false
                        }
                    }
                }) { Text("覆盖并恢复", color = PixelRed) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null; pendingText = null }) { Text("取消") }
            }
        )
    }
}
