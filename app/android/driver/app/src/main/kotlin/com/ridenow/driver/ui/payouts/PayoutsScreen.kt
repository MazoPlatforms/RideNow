package com.ridenow.driver.ui.payouts

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.auth.PhoneAuthApi
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.*
import com.ridenow.driver.ui.earnings.EarningsApi
import com.ridenow.driver.ui.theme.RideTheme
import com.ridenow.driver.ui.util.Money
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

private fun iconFor(k: TxnKind) = when (k) {
    TxnKind.Trip -> RideIcons.Car; TxnKind.Cash -> RideIcons.Wallet; TxnKind.Bonus -> RideIcons.Gift
    TxnKind.Payout -> RideIcons.Bank; TxnKind.Cancellation -> RideIcons.Block; TxnKind.Adjustment -> RideIcons.Receipt
}
private fun toneFor(k: TxnKind) = when (k) {
    TxnKind.Trip -> Tone.Neutral; TxnKind.Cash -> Tone.Good; TxnKind.Bonus -> Tone.Danger; TxnKind.Payout -> Tone.Neutral; TxnKind.Cancellation -> Tone.Danger; TxnKind.Adjustment -> Tone.Info
}

/** Payouts and transactions: balance with Withdraw, week/month/total figures, filter pills and transactions grouped by day. */
@Composable
fun PayoutsScreen(store: DriverStore, onBack: () -> Unit, onHelp: () -> Unit, onAddBank: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf(0) }
    var withdraw by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<Txn?>(null) }
    var failed by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }
    var retry by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    suspend fun refresh() {
        loading = true; failed = false
        try {
            val periods = EarningsApi.summary(context)
            store.apiWeekTotal = (periods[1].total / 100L).toInt()
            store.apiMonthTotal = (periods[2].total / 100L).toInt()
            store.apiAllTotal = (periods[3].total / 100L).toInt()
            val finance = DriverPayoutApi.payouts(context)
            store.balance = (finance.getJSONObject("balance").getString("net_minor").toLong() / 100L).toInt()
            val bank = DriverPayoutApi.method(context)
            store.banks.clear()
            if (bank != null) store.banks.add(BankAccountInfo(1, bank.holder, bank.bank, bank.number, bank.branch, bank.type, verified = false))
            store.txns.clear()
            val result = PhoneAuthApi.authorized(context, "GET", "/v1/earnings/transactions?limit=100")
            val entries = result.getJSONArray("items")
            for (index in 0 until entries.length()) {
                val item = entries.getJSONObject(index)
                val kindName = item.optString("kind")
                val kind = when {
                    kindName.startsWith("FARE") -> TxnKind.Trip
                    kindName == "CASH_COLLECTION" -> TxnKind.Cash
                    kindName == "PAYOUT" -> TxnKind.Payout
                    kindName.contains("CANCEL") -> TxnKind.Cancellation
                    kindName.contains("BONUS") -> TxnKind.Bonus
                    else -> TxnKind.Adjustment
                }
                val debit = item.optString("debit", "0").toLongOrNull() ?: 0L
                val credit = item.optString("credit", "0").toLongOrNull() ?: 0L
                val minor = credit - debit
                val instant = Instant.parse(item.getString("created_at"))
                val local = instant.atZone(ZoneId.systemDefault())
                val age = ChronoUnit.DAYS.between(local.toLocalDate(), LocalDate.now()).toInt().coerceAtLeast(0)
                val amount = (minor / 100L).toInt()
                val ref = item.optString("ride_id").takeIf { it.isNotBlank() }?.take(8)
                val title = when (kind) {
                    TxnKind.Trip -> context.getString(R.string.pay_earnings)
                    TxnKind.Cash -> "Cash collected"
                    TxnKind.Payout -> context.getString(R.string.pay_payouts)
                    TxnKind.Cancellation -> context.getString(R.string.ride_cancelled)
                    TxnKind.Bonus -> "Bonus"
                    TxnKind.Adjustment -> context.getString(R.string.pay_adjustments)
                }
                store.txns.add(Txn(item.getString("id"), kind, title,
                    ref?.let { "Ride · #$it" } ?: kindName.replace('_', ' '), age, local.hour * 60 + local.minute, amount))
            }
            val pending = finance.getJSONArray("items")
            for (index in 0 until pending.length()) {
                val item = pending.getJSONObject(index)
                if (item.optString("status") !in setOf("PENDING", "APPROVED", "REJECTED")) continue
                val instant = Instant.parse(item.getString("created_at"))
                val local = instant.atZone(ZoneId.systemDefault())
                val age = ChronoUnit.DAYS.between(local.toLocalDate(), LocalDate.now()).toInt().coerceAtLeast(0)
                val minor = item.getString("amount_minor").toLong()
                store.txns.add(Txn(item.getString("id"), TxnKind.Payout,
                    context.getString(R.string.pay_payouts), item.getString("status"), age,
                    local.hour * 60 + local.minute, -(minor / 100L).toInt()))
            }
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { failed = true }
        finally { loading = false }
    }
    LaunchedEffect(retry) { refresh() }
    val list = store.txns.filter {
        filter == 0 || (filter == 1 && it.kind in listOf(TxnKind.Trip, TxnKind.Cash, TxnKind.Bonus, TxnKind.Cancellation)) ||
            (filter == 2 && it.kind == TxnKind.Payout) || (filter == 3 && it.kind == TxnKind.Adjustment)
    }.sortedWith(compareBy<Txn> { it.ageDays }.thenByDescending { it.minute })
    val groups = list.groupBy { it.ageDays }
    val needBank = str(R.string.pay_need_bank)

    SubScreen(str(R.string.pay_title), onBack, modifier, trailing = { CircleButton(RideIcons.Help, str(R.string.acct_help), onHelp, filled = false) }) {
        GroupCard(Modifier.padding(top = 4.dp)) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(c.line), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Wallet, c.fg, size = 24.dp) }
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(str(R.string.pay_available), color = c.muted, fontSize = 13.sp)
                    Text(Money.fmt(store.balance), color = c.fg, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(str(R.string.pay_paid_to_bank), color = c.muted, fontSize = 11.sp, lineHeight = 15.sp)
                }
                Box(
                    Modifier.clip(RoundedCornerShape(20.dp)).background(c.fill).clickable(role = Role.Button) { if (store.banks.isEmpty()) { toast(context, needBank); onAddBank() } else withdraw = true }.heightIn(min = 44.dp).padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(str(R.string.pay_withdraw), color = c.onFill, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Row(Modifier.padding(top = 12.dp).height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Figure(str(R.string.pay_week), store.weekTotal, if (store.sample) "+12%" else null, Modifier.weight(1f))
            Figure(str(R.string.pay_month), store.monthTotal, if (store.sample) "+8%" else null, Modifier.weight(1f))
            Figure(str(R.string.pay_total), store.allTotal, null, Modifier.weight(1f))
        }
        FilterPills(listOf(str(R.string.filter_all), str(R.string.pay_earnings), str(R.string.pay_payouts), str(R.string.pay_adjustments)), filter, { filter = it }, Modifier.padding(top = 16.dp))
        if (failed) {
            Text(str(R.string.earn_live_error), color = c.muted, modifier = Modifier.padding(top = 12.dp))
            RideButton(str(R.string.sys_try_again), { retry++ }, style = RideButtonStyle.Text)
        } else if (loading && list.isEmpty()) CircularProgressIndicator(color = c.accent, modifier = Modifier.padding(top = 20.dp))
        else if (list.isEmpty()) EmptyState(RideIcons.Receipt, str(R.string.pay_empty_title), str(R.string.pay_empty_text))
        groups.forEach { (age, items) ->
            Row(Modifier.padding(top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (age == 0) str(R.string.pay_today) else Calendars.dayLabel(store.sample, age), color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).semantics { heading() })
                Text(str(R.string.pay_count, items.size), color = c.muted, fontSize = 12.sp)
            }
            GroupCard {
                items.forEachIndexed { i, t ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().clickable(role = Role.Button) { detail = t }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconTile(iconFor(t.kind), toneFor(t.kind), size = 44.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(t.title, color = c.fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
                            Text(t.sub, color = c.muted, fontSize = 12.sp, maxLines = 2)
                            Text(Calendars.timeLabel(t.minute), color = c.muted, fontSize = 12.sp)
                        }
                        Text(Money.signed(t.amount), color = if (t.amount < 0) c.danger else c.good, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        RideIcon(RideIcons.Chevron, c.muted, size = 16.dp, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
    if (withdraw) WithdrawSheet(store, { withdraw = false }, onWithdraw = { amountMinor ->
        DriverPayoutApi.request(context, amountMinor)
        refresh()
    }) { toast(context, it) }
    detail?.let { t ->
        RideSheet(t.title, { detail = null }) { close ->
            GroupCard {
                DetailRow(RideIcons.Receipt, str(R.string.pay_d_amount), Money.signed(t.amount)); RowDivider()
                DetailRow(RideIcons.Doc, str(R.string.pay_d_details), t.sub); RowDivider()
                DetailRow(RideIcons.Calendar, str(R.string.pay_d_date), Calendars.dayLabel(store.sample, t.ageDays)); RowDivider()
                DetailRow(RideIcons.Clock, str(R.string.pay_d_time), Calendars.timeLabel(t.minute)); RowDivider()
                DetailRow(RideIcons.Tag, str(R.string.pay_d_ref), "#" + t.id.uppercase())
            }
            Spacer(Modifier.height(14.dp)); RideButton(str(R.string.dialog_ok), { close() })
        }
    }
}

@Composable
private fun Figure(label: String, amount: Int, delta: String?, modifier: Modifier) {
    val c = RideTheme.colors
    Column(modifier.clip(RoundedCornerShape(16.dp)).background(c.field).border(1.dp, c.border, RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(label, color = c.muted, fontSize = 12.sp, maxLines = 2, lineHeight = 15.sp)
        // Currency on its own line so large totals such as 482,150 always fit.
        Text(Money.CURRENCY, color = c.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
        Text(Money.fmt(amount).removePrefix(Money.CURRENCY).trim(), color = c.fg, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
        if (delta != null) Text("\u2197 $delta", color = c.good, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
        else Spacer(Modifier.height(17.dp))
    }
}

@Composable
private fun WithdrawSheet(store: DriverStore, onDismiss: () -> Unit, onWithdraw: suspend (Long) -> Unit, notify: (String) -> Unit) {
    val c = RideTheme.colors
    var amount by rememberSaveable { mutableStateOf("") }
    var tried by rememberSaveable { mutableStateOf(false) }
    val bank = store.banks.first()
    val value = amount.toIntOrNull() ?: 0
    val bad = tried && (value <= 0 || value > store.balance)
    val done = str(R.string.pay_withdraw_done)
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    RideSheet(str(R.string.pay_withdraw), onDismiss, str(R.string.pay_withdraw_sub, Money.fmt(store.balance))) { close ->
        RideTextField(amount, { amount = it.filter { ch -> ch.isDigit() }.take(8) }, str(R.string.pay_amount) + " (" + Money.CURRENCY + ")", RideIcons.Wallet, isError = bad, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (bad) Text(str(R.string.pay_amount_error), color = c.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RideChip(str(R.string.pay_all), false, { amount = store.balance.toString() }, Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            RideIcon(RideIcons.Bank, c.muted, size = 18.dp)
            Text(bank.bank + " \u2022 ****" + bank.number.takeLast(4), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(14.dp))
        error.let { if (it) Text(str(R.string.earn_live_error), color = c.danger, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
        RideButton(str(R.string.pay_withdraw), {
            tried = true
            if (value in 1..store.balance) {
                busy = true; error = false
                scope.launch {
                    try { onWithdraw(value.toLong() * 100L); notify(done); close() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { error = true }
                    finally { busy = false }
                }
            }
        }, enabled = !busy, loading = busy)
    }
}
