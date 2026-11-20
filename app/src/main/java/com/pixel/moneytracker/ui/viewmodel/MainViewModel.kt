package com.pixel.moneytracker.ui.viewmodel

import android.app.Application
import androidx.room.withTransaction
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pixel.moneytracker.data.dao.CategorySummary
import com.pixel.moneytracker.data.dao.DailySummary
import com.pixel.moneytracker.data.database.AppDatabase
import com.pixel.moneytracker.data.database.BackupStore
import com.pixel.moneytracker.util.BackupJson
import com.pixel.moneytracker.util.TrashPolicy
import com.pixel.moneytracker.util.TrendWindow
import com.pixel.moneytracker.util.YearMonth
import com.pixel.moneytracker.data.entity.*
import com.pixel.moneytracker.reminder.BudgetAlert
import com.pixel.moneytracker.reminder.RecurringAlarm
import com.pixel.moneytracker.reminder.RecurringExecutor
import com.pixel.moneytracker.util.DateUtil
import com.pixel.moneytracker.util.Money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private companion object {
        /** 趋势图窗口：近 6 个月（含选中月）。 */
        const val TREND_MONTHS = 6
    }

    private val db = AppDatabase.getDatabase(application)
    private val recordDao = db.recordDao()
    private val budgetDao = db.budgetDao()
    private val recurringDao = db.recurringRecordDao()
    private val ledgerDao = db.ledgerDao()

    // ====== 账本状态 ======
    private val _currentLedgerId = MutableStateFlow(1L)
    val currentLedgerId: StateFlow<Long> = _currentLedgerId

    val allLedgers: StateFlow<List<Ledger>> = ledgerDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentLedger = MutableStateFlow<Ledger?>(null)
    val currentLedger: StateFlow<Ledger?> = _currentLedger

    init {
        // 异步检查并预填充常用账本（不阻塞主线程）
        viewModelScope.launch(Dispatchers.IO) {
            val existing = ledgerDao.getAll().first()
            if (existing.isEmpty()) {
                ledgerDao.insert(Ledger(name = "日常开销", emoji = "📝", isDefault = true))
                ledgerDao.insert(Ledger(name = "旅行基金", emoji = "✈️"))
                ledgerDao.insert(Ledger(name = "学习投资", emoji = "📚"))
                ledgerDao.insert(Ledger(name = "恋爱基金", emoji = "💕"))
                ledgerDao.insert(Ledger(name = "存钱目标", emoji = "🎯"))
                ledgerDao.insert(Ledger(name = "家庭账本", emoji = "🏠"))
            }
            // 加载默认账本
            val default = ledgerDao.getDefault()
            if (default != null) {
                _currentLedgerId.value = default.id
                _currentLedger.value = default
            }
            // 回收站：清掉超过保留期的记录（保留期内的一律留着，不做自动删除）
            runCatching { recordDao.purgeTrashOlderThan(TrashPolicy.cutoff(System.currentTimeMillis())) }
                .onFailure { android.util.Log.w("MainViewModel", "回收站清理失败", it) }
            // 周期记账：补记错过的到期点，并重排后台闹钟（不再依赖「打开 App」这一个触发点）
            refreshRecurring()
            // 预算判定：补一次，覆盖「上次记账时通知权限还没开」的情况
            evaluateBudget(_currentLedgerId.value, _selectedYear.value, _selectedMonth.value)
        }
    }

    // 当前选中的年月
    private val _selectedYear = MutableStateFlow(DateUtil.currentYear())
    val selectedYear: StateFlow<Int> = _selectedYear

    private val _selectedMonth = MutableStateFlow(DateUtil.currentMonth())
    val selectedMonth: StateFlow<Int> = _selectedMonth

    // 当前选中的日期（null = 显示全部）
    private val _selectedDay: MutableStateFlow<Int?> = MutableStateFlow(DateUtil.currentDay())
    val selectedDay: StateFlow<Int?> = _selectedDay

    fun setSelectedDay(day: Int?) {
        _selectedDay.value = day
    }

    // 月收入
    val monthIncome: StateFlow<Double> = _currentLedgerId.flatMapLatest { lid ->
        _selectedYear.flatMapLatest { y ->
            _selectedMonth.flatMapLatest { m -> recordDao.getMonthIncomeByLedger(lid, y, m) }
        }
    }.map { DateUtil.round2(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 月支出
    val monthExpense: StateFlow<Double> = _currentLedgerId.flatMapLatest { lid ->
        _selectedYear.flatMapLatest { y ->
            _selectedMonth.flatMapLatest { m -> recordDao.getMonthExpenseByLedger(lid, y, m) }
        }
    }.map { DateUtil.round2(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // 月记录列表
    val monthRecords: StateFlow<List<Record>> = _currentLedgerId.flatMapLatest { lid ->
        _selectedYear.flatMapLatest { y ->
            _selectedMonth.flatMapLatest { m -> recordDao.getRecordsByLedgerAndMonth(lid, y, m) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 支出分类汇总
    val expenseByCategory: StateFlow<List<CategorySummary>> = _currentLedgerId.flatMapLatest { lid ->
        _selectedYear.flatMapLatest { y ->
            _selectedMonth.flatMapLatest { m -> recordDao.getExpenseByCategoryLedger(lid, y, m) }
        }
    }.map { list -> list.map { it.copy(total = DateUtil.round2(it.total)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 每日支出（图表用）
    val dailyExpense: StateFlow<List<DailySummary>> = _currentLedgerId.flatMapLatest { lid ->
        _selectedYear.flatMapLatest { y ->
            _selectedMonth.flatMapLatest { m -> recordDao.getDailyExpenseByLedger(lid, y, m) }
        }
    }.map { list -> list.map { it.copy(total = DateUtil.round2(it.total)) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ====== 回收站 ======

    val trashRecords: StateFlow<List<Record>> = recordDao.getTrash()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val trashCount: StateFlow<Int> = recordDao.getTrashCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // ====== 近 6 个月趋势 ======

    /**
     * 以「选中月」为终点的连续 6 个月支出。跨年的槽位由 [TrendWindow] 算好，
     * 查询结果稀疏对齐后交给柱状图，缺的月份补 0 而不是错位。
     */
    val expenseTrend: StateFlow<List<Pair<YearMonth, Double>>> = combine(
        _selectedYear, _selectedMonth, _currentLedgerId
    ) { year, month, ledgerId -> Triple(year, month, ledgerId) }
        .flatMapLatest { (year, month, ledgerId) ->
            val slots = TrendWindow.lastMonths(year, month, TREND_MONTHS)
            recordDao.getMonthlyExpense(ledgerId, slots.first().key, slots.last().key)
                .map { rows ->
                    TrendWindow.align(slots, rows.associate { (it.year * 100 + it.month) to DateUtil.round2(it.total) })
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // 预算
    val budget: StateFlow<Budget?> = _selectedYear.flatMapLatest { y ->
        _selectedMonth.flatMapLatest { m -> budgetDao.getBudget(y, m) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // 自动记账规则列表
    val recurringRecords: StateFlow<List<RecurringRecord>> = recurringDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ====== 统计数据（我的页面用） ======
    val totalRecordCount: StateFlow<Int> = recordDao.getTotalRecordCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalRecordingDays: StateFlow<Int> = recordDao.getTotalRecordingDays()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val consecutiveDays: StateFlow<Int> = recordDao.getAllRecordDates()
        .map { dateNums -> calculateStreak(dateNums) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    /**
     * 计算连续记账天数：从今天往回数，有多少天连续有记录
     */
    private fun calculateStreak(dateNums: List<Int>): Int {
        if (dateNums.isEmpty()) return 0
        val sorted = dateNums.sortedDescending()
        val cal = java.util.Calendar.getInstance()
        // 今天是否在记录中
        val todayNum = cal.get(Calendar.YEAR) * 10000 +
                (cal.get(Calendar.MONTH) + 1) * 100 +
                cal.get(Calendar.DAY_OF_MONTH)
        // 如果今天没记录，从昨天开始数
        val startNum = if (sorted.first() == todayNum) todayNum else {
            cal.add(Calendar.DAY_OF_MONTH, -1)
            cal.get(Calendar.YEAR) * 10000 +
                    (cal.get(Calendar.MONTH) + 1) * 100 +
                    cal.get(Calendar.DAY_OF_MONTH)
        }
        if (!sorted.contains(startNum)) return 0
        var streak = 1
        cal.timeInMillis = java.util.Calendar.getInstance().timeInMillis
        // 重置 cal 到 startNum 对应的日期
        val startYear = startNum / 10000
        val startMonth = (startNum % 10000) / 100
        val startDay = startNum % 100
        cal.set(startYear, startMonth - 1, startDay)
        for (i in 1 until sorted.size) {
            cal.add(Calendar.DAY_OF_MONTH, -1)
            val prevNum = cal.get(Calendar.YEAR) * 10000 +
                    (cal.get(Calendar.MONTH) + 1) * 100 +
                    cal.get(Calendar.DAY_OF_MONTH)
            if (sorted.contains(prevNum)) {
                streak++
            } else {
                break
            }
        }
        return streak
    }

    // ====== 账本操作 ======

    fun switchLedger(ledger: Ledger) {
        _currentLedgerId.value = ledger.id
        _currentLedger.value = ledger
    }

    fun addLedger(name: String, emoji: String) {
        viewModelScope.launch {
            ledgerDao.insert(Ledger(name = name, emoji = emoji))
        }
    }

    fun renameLedger(id: Long, newName: String) {
        viewModelScope.launch {
            ledgerDao.rename(id, newName)
            // 如果改的是当前账本，刷新状态
            if (_currentLedgerId.value == id) {
                _currentLedger.value = ledgerDao.getById(id)
            }
        }
    }

    fun deleteLedger(ledger: Ledger) {
        viewModelScope.launch {
            // 如果删的是当前账本，切回默认
            if (_currentLedgerId.value == ledger.id) {
                val default = ledgerDao.getDefault()
                if (default != null) {
                    _currentLedgerId.value = default.id
                    _currentLedger.value = default
                }
            }
            ledgerDao.deleteById(ledger.id)
        }
    }

    suspend fun getLedgerRecordCount(ledgerId: Long): Int {
        return ledgerDao.getRecordCount(ledgerId)
    }

    // ====== 月份操作 ======

    fun setMonth(year: Int, month: Int) {
        _selectedYear.value = year
        _selectedMonth.value = month
    }

    fun prevMonth() {
        if (_selectedMonth.value == 1) {
            _selectedYear.value--
            _selectedMonth.value = 12
        } else {
            _selectedMonth.value--
        }
        _selectedDay.value = null
    }

    fun nextMonth() {
        if (_selectedMonth.value == 12) {
            _selectedYear.value++
            _selectedMonth.value = 1
        } else {
            _selectedMonth.value++
        }
        _selectedDay.value = null
    }

    // ====== 记录操作 ======

    fun addRecord(
        amount: Double,
        type: RecordType,
        category: String,
        emoji: String,
        note: String,
        year: Int,
        month: Int,
        day: Int
    ) {
        viewModelScope.launch {
            recordDao.insert(
                Record(
                    amount = amount,
                    type = type,
                    category = category,
                    emoji = emoji,
                    note = note,
                    year = year,
                    month = month,
                    day = day,
                    ledgerId = _currentLedgerId.value
                )
            )
            evaluateBudget(_currentLedgerId.value, year, month)
        }
    }

    fun updateRecord(record: Record) {
        viewModelScope.launch {
            recordDao.update(record)
            evaluateBudget(record.ledgerId, record.year, record.month)
        }
    }

    /**
     * 删除 = 挪进回收站（软删除）。返回 true 表示真的挪走了，
     * 界面据此决定要不要弹「已移到回收站 · 撤销」。
     */
    fun deleteRecord(record: Record, onMoved: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val moved = recordDao.moveToTrash(record.id, System.currentTimeMillis()) > 0
            if (moved) evaluateBudget(record.ledgerId, record.year, record.month)
            onMoved(moved)
        }
    }

    /** 从回收站恢复一笔。 */
    fun restoreRecord(id: Long, onDone: (Boolean) -> Unit = {}) {
        viewModelScope.launch {
            val restored = recordDao.restoreFromTrash(id) > 0
            if (restored) {
                recordDao.getAllRecordsRaw().firstOrNull { it.id == id }?.let {
                    evaluateBudget(it.ledgerId, it.year, it.month)
                }
            }
            onDone(restored)
        }
    }

    /** 彻底删除一笔（只在回收站里生效）。 */
    fun purgeRecord(id: Long, onDone: () -> Unit = {}) {
        viewModelScope.launch {
            recordDao.purge(id)
            onDone()
        }
    }

    fun emptyTrash(onDone: (Int) -> Unit = {}) {
        viewModelScope.launch {
            onDone(recordDao.purgeAllTrash())
        }
    }

    // ====== 备份 / 恢复 ======

    suspend fun buildBackupJson(appVersion: String): String = db.withTransaction {
        BackupJson.encode(BackupStore.read(db), System.currentTimeMillis(), appVersion)
    }

    /**
     * 整包恢复。会**覆盖**当前所有账本、账单（含回收站）、预算、周期规则和导入去重键，
     * 所以调用方必须先拿到用户的明确确认，并且已经导出一份当前备份。
     */
    suspend fun restoreBackup(text: String): BackupStore.RestoreResult {
        val decoded = BackupJson.decode(text)
        val result = BackupStore.write(db, decoded.data)
        // 恢复后当前账本可能已经不存在：切回默认账本，避免界面停在幽灵 id 上
        val default = ledgerDao.getDefault() ?: ledgerDao.getAll().first().firstOrNull()
        if (default != null) {
            _currentLedgerId.value = default.id
            _currentLedger.value = default
        } else {
            _currentLedgerId.value = 1L
            _currentLedger.value = null
        }
        // 预算提醒水位属于旧数据，清掉重新判定
        getApplication<Application>()
            .getSharedPreferences("budget_alert", android.content.Context.MODE_PRIVATE)
            .edit().clear().apply()
        rescheduleRecurring()
        evaluateBudget(_currentLedgerId.value, _selectedYear.value, _selectedMonth.value)
        return result.copy(droppedImportKeys = decoded.droppedImportKeys)
    }

    suspend fun exportRecordsCsv(): String = db.withTransaction {
        com.pixel.moneytracker.util.RecordCsv.encode(
            recordDao.getAllRecords().first(), ledgerDao.getAll().first().associate { it.id to it.name }
        )
    }

    // ====== 预算操作 ======

    /**
     * 保存当前选中月份的预算上限。
     *
     * 金额以「分」传入（界面已用 [Money.parseCents] 严格校验过），落库时再转回 Double，
     * 保证「输入 → 比较 → 展示」都走同一套整数分语义。
     */
    fun setBudget(amountCents: Long) {
        val cents = amountCents.coerceIn(1L, Money.MAX_CENTS)
        val year = _selectedYear.value
        val month = _selectedMonth.value
        viewModelScope.launch {
            val previous = budgetDao.getBudgetSync(year, month)
            budgetDao.insert(Budget(year = year, month = month, amount = Money.fromCents(cents)))
            // 上限真的变了才重置提醒水位：否则重复点「保存」会把这个月的提醒重新武装一遍
            if (previous == null || Money.toCents(previous.amount) != cents) {
                BudgetAlert.reset(getApplication(), year, month)
            }
            evaluateBudget(_currentLedgerId.value, year, month)
        }
    }

    /** 取消当前月份的预算（回到「未设置」）。 */
    fun clearBudget() {
        val year = _selectedYear.value
        val month = _selectedMonth.value
        viewModelScope.launch {
            budgetDao.deleteByMonth(year, month)
            BudgetAlert.reset(getApplication(), year, month)
        }
    }

    /**
     * 预算阈值检查：预算按「年 + 月」，支出按「当前账本 + 该月」，与首页进度条同口径。
     * 同一周期同一阈值只提醒一次的判定在 [BudgetAlert] 内部完成。
     */
    private suspend fun evaluateBudget(ledgerId: Long, year: Int, month: Int) {
        runCatching { BudgetAlert.evaluate(getApplication(), ledgerId, year, month) }
            .onFailure { android.util.Log.w("MainViewModel", "预算提醒判定失败", it) }
    }

    // ====== 自动记账 ======

    fun addRecurringRecord(
        amount: Double,
        type: RecordType,
        category: String,
        emoji: String,
        note: String,
        frequency: Frequency
    ) {
        viewModelScope.launch {
            recurringDao.insert(
                RecurringRecord(
                    amount = amount,
                    type = type,
                    category = category,
                    emoji = emoji,
                    note = note,
                    frequency = frequency,
                    // 规则跟着「创建时所在的账本」走，后台执行不再依赖界面状态
                    ledgerId = _currentLedgerId.value
                )
            )
            // 新规则沿用「立即执行一次」的旧行为，同时把后台闹钟排上
            refreshRecurring()
        }
    }

    fun deleteRecurringRecord(record: RecurringRecord) {
        viewModelScope.launch {
            recurringDao.delete(record)
            rescheduleRecurring()
        }
    }

    fun toggleRecurringEnabled(id: Long, enabled: Boolean) {
        viewModelScope.launch {
            recurringDao.setEnabled(id, enabled)
            rescheduleRecurring()
        }
    }

    /** 补记到期账单，然后重排下一次后台检查。 */
    private fun refreshRecurring() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { RecurringExecutor.run(app) }
                .onFailure { android.util.Log.w("MainViewModel", "周期记账执行失败", it) }
            runCatching { RecurringAlarm.reschedule(app) }
                .onFailure { android.util.Log.w("MainViewModel", "周期记账重排失败", it) }
        }
    }

    /** 只重排闹钟、不立即执行（启用/停用/删除规则后用）。 */
    private fun rescheduleRecurring() {
        val app = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { RecurringAlarm.reschedule(app) }
                .onFailure { android.util.Log.w("MainViewModel", "周期记账重排失败", it) }
        }
    }
}
