package com.pixel.moneytracker.ui.screen

import android.content.ContentResolver
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.data.database.BillImport
import com.pixel.moneytracker.data.database.ImportPreview
import com.pixel.moneytracker.data.database.ImportTarget
import com.pixel.moneytracker.data.entity.RecordType
import com.pixel.moneytracker.ui.component.PixelCard
import com.pixel.moneytracker.ui.theme.*
import com.pixel.moneytracker.util.BillCsv
import com.pixel.moneytracker.util.Categories
import com.pixel.moneytracker.ui.viewmodel.MainViewModel
import com.pixel.moneytracker.util.LedgerNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

// 「新建账本…」在下拉里的哨兵值，不会与真实账本 id（自增正整数）冲突
private const val NEW_LEDGER_ID = -1L

private fun ContentResolver.readBill(uri: Uri): ByteArray {
    return (openInputStream(uri) ?: throw IllegalArgumentException("无法打开所选文件")).use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val size = input.read(buffer)
            if (size < 0) break
            require(output.size() + size <= BillCsv.MAX_BYTES) { "请选择不超过 4 MB 的账单文件" }
            output.write(buffer, 0, size)
        }
        output.toByteArray()
    }
}

private fun centsText(value: Long): String = BigDecimal.valueOf(value, 2).toPlainString()

// Imported rows only carry a guessed category; tapping it lets the user correct that guess
// before anything is written, without leaving the preview.
@Composable
private fun CategoryChip(type: RecordType, current: String, enabled: Boolean, onPick: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val options = Categories.getByType(type)
    val emoji = options.firstOrNull { it.name == current }?.emoji ?: "📝"
    Box {
        TextButton(enabled = enabled, onClick = { expanded = true },
            modifier = Modifier.heightIn(min = 36.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
            Text("$emoji $current", fontSize = 12.sp, color = PixelGold)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { category ->
                DropdownMenuItem(text = { Text("${category.emoji} ${category.name}") }, onClick = {
                    expanded = false
                    onPick(category.name)
                })
            }
        }
    }
}

/**
 * 导入目标账本选择器：只对外部账单（微信 / 支付宝）显示。
 * 选到「新建账本…」时把名称一并校验（非空、≤20 字、同名复用已有账本）。
 */
@Composable
private fun TargetLedgerPicker(
    preview: ImportPreview,
    choice: Long,
    newName: String,
    error: String?,
    enabled: Boolean,
    onChoose: (Long) -> Unit,
    onNameChange: (String) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = preview.ledgers.firstOrNull { it.id == choice }
    val label = when {
        choice == NEW_LEDGER_ID -> "新建账本"
        selected != null -> "${selected.emoji} ${selected.name}"
        else -> preview.defaultLedger
    }
    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("目标账本", fontSize = 13.sp, color = PixelTextGray)
            Spacer(Modifier.width(6.dp))
            Box {
                TextButton(
                    enabled = enabled,
                    onClick = { expanded = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Text("$label ▾", fontSize = 13.sp, color = PixelGold, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    preview.ledgers.forEach { ledger ->
                        DropdownMenuItem(
                            text = { Text("${ledger.emoji} ${ledger.name}") },
                            onClick = {
                                expanded = false
                                onClearError()
                                onChoose(ledger.id)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("＋ 新建账本…", color = PixelBlue) },
                        onClick = {
                            expanded = false
                            onClearError()
                            onChoose(NEW_LEDGER_ID)
                        }
                    )
                }
            }
        }
        if (choice == NEW_LEDGER_ID) {
            OutlinedTextField(
                value = newName,
                onValueChange = {
                    onNameChange(it)
                    onClearError()
                },
                enabled = enabled,
                singleLine = true,
                label = { Text("新账本名称", color = PixelTextGray) },
                placeholder = { Text("例如：装修基金", color = PixelTextDark) },
                isError = error != null,
                supportingText = {
                    Text(
                        error ?: "与已有账本同名时直接复用",
                        color = if (error != null) PixelRed else PixelTextDark,
                        fontSize = 11.sp
                    )
                },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PixelGold,
                    unfocusedBorderColor = PixelSurface,
                    focusedTextColor = PixelTextWhite,
                    unfocusedTextColor = PixelTextWhite,
                    cursorColor = PixelGold,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .heightIn(min = 56.dp)
            )
        }
    }
}

/**
 * 账单导入与导出放在同一张卡片里：两者都是「把账目搬进/搬出这个 App」，
 * 分开成两块会让人误以为是两套功能。
 */
@Composable
fun BillsTransfer(viewModel: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val importer = remember(context) { BillImport(AppDatabase.getDatabase(context)) }
    var preview by remember { mutableStateOf<ImportPreview?>(null) }
    var busy by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var saveError by remember { mutableStateOf("") }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch {
            exporting = true
            try {
                withContext(Dispatchers.IO) {
                    val csv = viewModel.exportRecordsCsv()
                    val output = context.contentResolver.openOutputStream(uri) ?: error("无法打开文件")
                    output.bufferedWriter(Charsets.UTF_8).use { it.write(csv) }
                }
                Toast.makeText(context, "账单已导出", Toast.LENGTH_LONG).show()
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) {
                Toast.makeText(context, "导出失败，请重新选择保存位置", Toast.LENGTH_LONG).show()
            } finally { exporting = false }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null && !busy) {
            busy = true
            message = ""
            saveError = ""
            scope.launch {
                try {
                    preview = withContext(Dispatchers.IO) {
                        importer.preview(BillCsv.parse(context.contentResolver.readBill(uri)))
                    }
                } catch (e: CancellationException) { throw e }
                catch (e: IllegalArgumentException) { message = e.message ?: "文件格式无效" }
                catch (_: Exception) { message = "文件读取失败，请选择已解压的原始账单文件" }
                finally { busy = false }
            }
        }
    }
    PixelCard(modifier = Modifier.fillMaxWidth()) {
        Text("账单导入 / 导出", color = PixelGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "导入：选择像素记账、微信或支付宝的 CSV / XLSX 明细，核对后写入账本。\n" +
                "导出：把全部账目存成 CSV，可用表格软件查看。",
            color = PixelTextGray, fontSize = 14.sp
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy && !exporting, onClick = { picker.launch(arrayOf("*/*")) }) {
                Text(if (busy) "处理中…" else "导入账单", fontSize = 14.sp)
            }
            OutlinedButton(
                enabled = !busy && !exporting,
                onClick = { exporter.launch("像素记账-${java.time.LocalDate.now()}.csv") }
            ) {
                Text(if (exporting) "正在导出…" else "导出 CSV", fontSize = 14.sp)
            }
        }
        if (message.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(message, color = PixelTextWhite, fontSize = 14.sp)
        }
    }
    val data = preview ?: return
    val external = data.file.source != "像素记账"
    var excluded by remember(data) { mutableStateOf(emptySet<String>()) }
    var categoryEdits by remember(data) { mutableStateOf(emptyMap<String, String>()) }
    // 目标账本：默认选中「日常开销」（外部账单原来的落点），可改选其它账本或新建
    var ledgerChoice by remember(data) {
        mutableStateOf(
            data.ledgers.firstOrNull { it.name == data.defaultLedger }?.id
                ?: data.ledgers.firstOrNull()?.id
                ?: NEW_LEDGER_ID
        )
    }
    var newLedgerName by remember(data) { mutableStateOf("") }
    var ledgerError by remember(data) { mutableStateOf<String?>(null) }

    val selected = remember(data, excluded, categoryEdits) {
        data.pending.filterNot { it.key in excluded }.map { bill ->
            categoryEdits[bill.key]?.takeIf { it != bill.category }?.let { bill.copy(category = it) } ?: bill
        }
    }
    val income = remember(selected) { selected.filter { it.type == RecordType.INCOME }.sumOf { it.cents } }
    val expense = remember(selected) { selected.filter { it.type == RecordType.EXPENSE }.sumOf { it.cents } }

    // 解析本次导入的目标账本；返回 null 表示名称校验没过（错误已写进 ledgerError）
    val resolveTarget: () -> ImportTarget? = {
        if (ledgerChoice == NEW_LEDGER_ID) {
            val invalid = LedgerNames.validate(newLedgerName)
            if (invalid != null) {
                ledgerError = invalid
                null
            } else {
                val name = LedgerNames.normalize(newLedgerName)
                val existing = data.ledgers.firstOrNull { it.name == name }
                ledgerError = null
                if (existing != null) ImportTarget.Existing(existing.id) else ImportTarget.Named(name)
            }
        } else {
            ledgerError = null
            ImportTarget.Existing(ledgerChoice)
        }
    }

    val dismiss = { if (!busy) preview = null }
    AlertDialog(onDismissRequest = dismiss, title = { Text("核对导入明细") }, text = {
        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 470.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            item {
                Column {
                    Text("${data.file.source} · 已选 ${selected.size} / ${data.pending.size} 笔", fontWeight = FontWeight.Bold)
                    Text("收入 ¥${centsText(income)}　支出 ¥${centsText(expense)}")
                    if (external) {
                        TargetLedgerPicker(
                            preview = data,
                            choice = ledgerChoice,
                            newName = newLedgerName,
                            error = ledgerError,
                            enabled = !busy,
                            onChoose = { ledgerChoice = it },
                            onNameChange = { newLedgerName = it },
                            onClearError = { ledgerError = null }
                        )
                    } else {
                        Text("按文件中的账本名称导入，缺少的账本将自动创建。")
                    }
                    if (data.duplicates > 0) Text("重复 ${data.duplicates} 笔，已跳过")
                    if (categoryEdits.isNotEmpty()) Text("已修正分类 ${categoryEdits.size} 笔")
                    data.file.skipped.forEach { (reason, count) -> Text("$reason：$count 行") }
                    if (external && data.pending.isNotEmpty()) {
                        Text("手动或弹窗记过的交易可取消勾选。", style = MaterialTheme.typography.bodySmall)
                        TextButton(enabled = !busy, onClick = {
                            excluded = if (selected.isEmpty()) emptySet() else data.pending.mapTo(hashSetOf()) { it.key }
                        }) { Text(if (selected.isEmpty()) "全选" else "取消全选") }
                    }
                    if (saveError.isNotEmpty()) Text(saveError, color = MaterialTheme.colorScheme.error)
                }
            }
            items(data.pending, key = { it.key }) { bill ->
                val checked = bill.key !in excluded
                Row(modifier = if (!external) Modifier else Modifier.fillMaxWidth().toggleable(
                    value = checked, enabled = !busy, role = Role.Checkbox,
                    onValueChange = { include -> excluded = if (include) excluded - bill.key else excluded + bill.key }
                ).heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (external) {
                        Checkbox(checked = checked, onCheckedChange = null, enabled = !busy)
                        Spacer(Modifier.width(8.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        val date = if (external) bill.time.toString().replace('T', ' ') else bill.time.toLocalDate().toString()
                        Text("$date　${if (bill.type == RecordType.INCOME) "+" else "−"}¥${centsText(bill.cents)}", fontWeight = FontWeight.Medium)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (external) CategoryChip(bill.type, categoryEdits[bill.key] ?: bill.category, !busy) { name ->
                                categoryEdits = if (name == bill.category) categoryEdits - bill.key
                                else categoryEdits + (bill.key to name)
                            }
                            Text(listOfNotNull(if (external) null else bill.ledger, if (external) null else bill.category,
                                bill.note.takeIf { it.isNotEmpty() }).joinToString(" · "),
                                maxLines = 2, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }, confirmButton = {
        if (data.pending.isNotEmpty()) TextButton(enabled = !busy && selected.isNotEmpty(), onClick = {
            if (!busy) {
                val target = if (external) resolveTarget() else ImportTarget.Default
                if (target != null) {
                    busy = true
                    saveError = ""
                    scope.launch {
                        try {
                            val saved = withContext(Dispatchers.IO) {
                                importer.save(data.copy(pending = selected), target)
                            }
                            message = "已导入 $saved 笔"
                            preview = null
                        } catch (e: CancellationException) { throw e }
                        catch (e: IllegalArgumentException) { saveError = e.message ?: "请重新预览账单" }
                        catch (_: Exception) { saveError = "导入失败，本次变更已撤销。可重试。" }
                        finally { busy = false }
                    }
                }
            }
        }) { Text(if (busy) "正在导入…" else "导入 ${selected.size} 笔") }
    }, dismissButton = { TextButton(onClick = dismiss, enabled = !busy) { Text(if (data.pending.isEmpty()) "关闭" else "取消") } })
}
