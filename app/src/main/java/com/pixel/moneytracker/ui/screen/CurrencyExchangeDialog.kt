package com.pixel.moneytracker.ui.screen

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pixel.moneytracker.util.CurrencyExchange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun CurrencyExchangeDialog(onDismiss: () -> Unit, onApply: (String, String) -> Unit) {
    val context = LocalContext.current
    val prefs = remember(context) { context.getSharedPreferences("currency_rates", Context.MODE_PRIVATE) }
    val scope = rememberCoroutineScope()
    var code by rememberSaveable {
        mutableStateOf(prefs.getString("currency", null)?.takeIf { it in CurrencyExchange.currencies } ?: "USD")
    }
    var amount by rememberSaveable { mutableStateOf("") }
    var rate by rememberSaveable(code) {
        mutableStateOf(CurrencyExchange.displayRate(prefs.getString("rate_$code", "").orEmpty()))
    }
    var quoteDate by rememberSaveable(code) { mutableStateOf(prefs.getString("ecb_date_$code", "").orEmpty()) }
    var expanded by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember(code) { mutableStateOf(false) }
    val converted = CurrencyExchange.convert(amount, rate)

    AlertDialog(onDismissRequest = onDismiss, title = { Text("汇率换算") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box {
                OutlinedButton(onClick = { expanded = true }, enabled = !loading) {
                    Text("${CurrencyExchange.currencies[code]}（$code） ▾")
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false },
                    modifier = Modifier.heightIn(max = 280.dp)) {
                    CurrencyExchange.currencies.forEach { (iso, name) ->
                        DropdownMenuItem(text = { Text("$name（$iso）") }, onClick = {
                            code = iso
                            expanded = false
                        })
                    }
                }
            }
            OutlinedTextField(value = amount, onValueChange = { if (it.length <= 24) amount = it },
                label = { Text("外币金额") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            OutlinedTextField(value = rate, onValueChange = {
                if (it.length <= 24) {
                    rate = it
                    quoteDate = ""
                    error = false
                }
            }, label = { Text("1 $code 折合人民币（元）") }, modifier = Modifier.fillMaxWidth(),
                enabled = !loading, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            TextButton(onClick = {
                scope.launch {
                    loading = true
                    error = false
                    try {
                        val quote = withContext(Dispatchers.IO) { CurrencyExchange.fetchQuote(code) }
                        rate = CurrencyExchange.displayRate(quote.rate)
                        quoteDate = quote.date.toString()
                        prefs.edit().putString("rate_$code", rate).putString("ecb_date_$code", quoteDate).apply()
                    } catch (ex: CancellationException) {
                        throw ex
                    } catch (_: Exception) {
                        error = true
                    } finally {
                        loading = false
                    }
                }
            }, enabled = !loading, modifier = Modifier.align(Alignment.Start)) {
                Text(if (loading) "正在获取…" else "获取参考汇率")
            }
            if (quoteDate.isNotEmpty()) Text("欧洲央行参考汇率 · $quoteDate", style = MaterialTheme.typography.bodySmall)
            if (error) Text("参考汇率获取失败，可输入银行或商户的实际汇率。", color = MaterialTheme.colorScheme.error)
            if (converted != null) {
                Text("折合人民币 ¥${converted.toPlainString()}", style = MaterialTheme.typography.titleMedium)
                Text("原币金额和汇率将填入备注。", style = MaterialTheme.typography.bodySmall)
            } else if (amount.isNotBlank() && rate.isNotBlank()) {
                Text("请检查金额和汇率，折合金额至少为 0.01 元。", color = MaterialTheme.colorScheme.error)
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            if (converted != null) {
                prefs.edit().putString("currency", code).putString("rate_$code", rate)
                    .putString("ecb_date_$code", quoteDate).apply()
                val provenance = if (quoteDate.isEmpty()) "手动汇率" else "欧洲央行 $quoteDate"
                onApply(converted.toPlainString(), "$amount $code × $rate 元/$code（$provenance）")
            }
        }, enabled = converted != null && !loading) { Text("填入账单") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}
