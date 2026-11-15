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
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.*
import com.ridenow.driver.R
import com.ridenow.driver.ui.components.*
import com.ridenow.driver.ui.data.*
import com.ridenow.driver.ui.theme.RideTheme

/** Bank account: linked accounts, details of the selected one, Edit, Remove and Add. Used only for payouts. */
@Composable
fun BankAccountManageScreen(store: DriverStore, onBack: () -> Unit, onHelp: () -> Unit, onEdit: (BankAccountInfo) -> Unit, onAdd: () -> Unit, onRemoved: () -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    var selected by rememberSaveable { mutableStateOf(0) }
    var confirm by remember { mutableStateOf(false) }
    val accounts = store.banks
    val acc = accounts.getOrNull(selected.coerceAtMost((accounts.size - 1).coerceAtLeast(0)))
    SubScreen(str(R.string.bank_acc_title), onBack, modifier, trailing = { CircleButton(RideIcons.Help, str(R.string.acct_help), onHelp, filled = false) }) {
        InfoBanner(RideIcons.Shield, str(R.string.bank_note_title), str(R.string.bank_note_text), Modifier.padding(top = 4.dp), Tone.Brand)
        SectionTitle(str(R.string.bank_linked))
        if (acc == null) {
            GroupCard { EmptyState(RideIcons.Bank, str(R.string.bank_none_title), str(R.string.bank_none_text)) }
            Spacer(Modifier.height(16.dp)); RideButton(str(R.string.bank_add), onAdd)
            return@SubScreen
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            accounts.forEachIndexed { i, a ->
                val on = a === acc
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(c.field).border(if (on) 1.5.dp else 1.dp, if (on) c.fill else c.border, RoundedCornerShape(18.dp)).clickable(role = Role.Button) { selected = i }.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(56.dp).clip(CircleShape).background(c.good.copy(alpha = 0.22f)), contentAlignment = Alignment.Center) { RideIcon(RideIcons.Bank, c.good, size = 26.dp) }
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a.bank, color = c.fg, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f, fill = false))
                            if (a.verified) StatusChip(str(R.string.doc_verified), Tone.Good, Modifier.padding(start = 8.dp), dot = true)
                        }
                        Text(a.holder, color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 2.dp))
                        Text(a.number, color = c.muted, fontSize = 13.sp)
                        Text(a.branch, color = c.muted, fontSize = 13.sp)
                    }
                    RideIcon(RideIcons.Chevron, c.fg, size = 16.dp)
                }
            }
        }
        SectionTitle(str(R.string.bank_details))
        GroupCard {
            DetailRow(RideIcons.Person, str(R.string.bank_holder), acc.holder); RowDivider()
            DetailRow(RideIcons.Bank, str(R.string.bank_name), acc.bank); RowDivider()
            DetailRow(RideIcons.Card, str(R.string.bank_number), acc.number); RowDivider()
            DetailRow(RideIcons.Pin, str(R.string.bank_branch), acc.branch); RowDivider()
            DetailRow(RideIcons.Info, str(R.string.bank_type), acc.type)
        }
        Spacer(Modifier.height(12.dp))
        SoftButton(str(R.string.bank_edit), { onEdit(acc) }, icon = RideIcons.Pencil)
        Spacer(Modifier.height(10.dp))
        SoftButton(str(R.string.bank_remove), { confirm = true }, icon = RideIcons.Trash, danger = true)
        SectionTitle(str(R.string.bank_add_another))
        RideButton(str(R.string.bank_add), onAdd, modifier = Modifier.padding(bottom = 16.dp))
    }
    if (confirm && acc != null) AlertDialog(
        onDismissRequest = { confirm = false }, containerColor = c.bg,
        title = { Text(str(R.string.bank_remove_title), color = c.fg) },
        text = { Text(str(R.string.bank_remove_text), color = c.muted) },
        confirmButton = { TextButton(onClick = { confirm = false; store.banks.remove(acc); selected = 0; onRemoved() }) { Text(str(R.string.bank_remove_confirm), color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text(str(R.string.dialog_cancel), color = c.fg) } },
    )
}

/** Add a bank account, or edit [existing]. */
@Composable
fun BankFormScreen(existing: BankAccountInfo?, onBack: () -> Unit, onSave: (holder: String, bank: String, branch: String, number: String, type: String) -> Unit, modifier: Modifier = Modifier) {
    val c = RideTheme.colors
    val savings = str(R.string.bank_savings); val current = str(R.string.bank_current)
    var holder by rememberSaveable { mutableStateOf(existing?.holder ?: "") }
    var bank by rememberSaveable { mutableStateOf(existing?.bank ?: "") }
    var branch by rememberSaveable { mutableStateOf(existing?.branch ?: "") }
    var number by rememberSaveable { mutableStateOf(existing?.number ?: "") }
    var confirmNo by rememberSaveable { mutableStateOf(existing?.number ?: "") }
    var type by rememberSaveable { mutableStateOf(existing?.type ?: savings) }
    var tried by rememberSaveable { mutableStateOf(false) }
    val numberOk = number.length in 6..20
    val ok = holder.trim().length >= 2 && bank.trim().length >= 2 && branch.isNotBlank() && numberOk && confirmNo == number
    SubScreen(str(if (existing == null) R.string.bank_add else R.string.bank_edit), onBack, modifier, bottom = {
        RideButton(str(R.string.bank_save_btn), { tried = true; if (ok) onSave(holder.trim(), bank.trim(), branch.trim(), number, type) })
    }) {
        InfoBanner(RideIcons.Shield, str(R.string.bank_note_title), str(R.string.bank_note_text), Modifier.padding(top = 4.dp, bottom = 14.dp), Tone.Brand)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            RideTextField(holder, { holder = it }, str(R.string.bank_holder), RideIcons.Person, isError = tried && holder.trim().length < 2)
            RideTextField(bank, { bank = it }, str(R.string.bank_name), RideIcons.Bank, isError = tried && bank.trim().length < 2)
            RideTextField(branch, { branch = it }, str(R.string.bank_branch), RideIcons.Pin, isError = tried && branch.isBlank())
            RideTextField(number, { number = it.filter { ch -> ch.isDigit() }.take(20) }, str(R.string.bank_number), RideIcons.Card, isError = tried && !numberOk, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            RideTextField(confirmNo, { confirmNo = it.filter { ch -> ch.isDigit() }.take(20) }, str(R.string.bank_confirm_no), RideIcons.Lock, isError = tried && confirmNo != number, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Text(str(R.string.bank_type), color = c.muted, fontSize = 13.sp, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 16.dp)) {
            RideChip(savings, type == savings, { type = savings }, Modifier.weight(1f)); RideChip(current, type == current, { type = current }, Modifier.weight(1f))
        }
    }
}
