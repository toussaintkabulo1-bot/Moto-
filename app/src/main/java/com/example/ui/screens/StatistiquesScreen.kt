package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.Ride
import com.example.models.RideStatus
import com.example.models.UiState
import com.example.models.User
import com.example.models.UserRole
import com.example.models.Withdrawal
import com.example.ui.components.formatFcAmount
import java.util.Calendar
import java.util.Locale

@Composable
fun StatistiquesScreen(
    currentUser: User,
    ridesState: UiState<List<Ride>>,
    withdrawalsState: UiState<List<Withdrawal>>,
    isWithdrawing: Boolean,
    isOnline: Boolean = true,
    onWithdraw: (operateur: String, numero: String, montant: Long, maxAvailable: Long) -> Unit
) {
    val isMotard = currentUser.role == UserRole.MOTARD.name
    val rides = (ridesState as? UiState.Success)?.data.orEmpty()
    val withdrawals = (withdrawalsState as? UiState.Success)?.data.orEmpty()
    val completedRidesList = rides.filter { it.status == RideStatus.TERMINEE.name }

    val totalRidesCount = maxOf(currentUser.completedRides, completedRidesList.size.toLong())
    val totalMoney = if (isMotard) {
        maxOf(currentUser.totalGains, completedRidesList.sumOf { it.prix_actuel })
    } else {
        maxOf(currentUser.totalDepenses, completedRidesList.sumOf { it.prix_actuel })
    }
    val totalWithdrawnFromDocs = withdrawals.sumOf { it.montant }
    val effectiveWithdrawn = maxOf(currentUser.totalRetraits, totalWithdrawnFromDocs)
    val availableBalance = (totalMoney - effectiveWithdrawn).coerceAtLeast(0L)

    val distinctMotards = completedRidesList.map { it.id_motard }.distinct().size
        .coerceAtLeast(if (totalRidesCount > 0) 1 else 0)

    val dayCounts = remember(rides) {
        val counts = IntArray(7)
        val cal = Calendar.getInstance()
        rides.forEach { ride ->
            val date = (ride.createdAt ?: ride.updatedAt)?.toDate()
            if (date != null) {
                cal.time = date
                val idx = when (cal.get(Calendar.DAY_OF_WEEK)) {
                    Calendar.MONDAY -> 0
                    Calendar.TUESDAY -> 1
                    Calendar.WEDNESDAY -> 2
                    Calendar.THURSDAY -> 3
                    Calendar.FRIDAY -> 4
                    Calendar.SATURDAY -> 5
                    else -> 6
                }
                counts[idx]++
            }
        }
        counts
    }

    var showWithdrawDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Statistiques",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                            append("${currentUser.nom} · compte ")
                        }
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append(currentUser.roleEnum().labelFr)
                        }
                    },
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatKpiCard(
                        label = if (isMotard) "Courses (semaine)" else "Commandes",
                        value = "$totalRidesCount",
                        modifier = Modifier.weight(1f)
                    )
                    StatKpiCard(
                        label = if (isMotard) "Gains" else "Dépensé",
                        value = formatFcAmount(totalMoney),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isMotard) {
                        val hasRating = currentUser.note > 0.0
                        StatKpiCard(
                            label = "Note",
                            value = if (hasRating) String.format(Locale.US, "%.1f", currentUser.note) else "Nouveau",
                            showStar = hasRating,
                            modifier = Modifier.weight(1f)
                        )
                        val acceptanceLabel = if (rides.isEmpty()) {
                            "—"
                        } else {
                            val pct = ((rides.count { it.status != RideStatus.EN_NEGOCIATION.name } * 100) / rides.size)
                                .coerceIn(0, 100)
                            "$pct %"
                        }
                        StatKpiCard(
                            label = "Acceptation",
                            value = acceptanceLabel,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        StatKpiCard(
                            label = "Motards différents",
                            value = "$distinctMotards",
                            modifier = Modifier.weight(1f)
                        )
                        StatKpiCard(
                            label = "Cette semaine",
                            value = "${rides.size}",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        item {
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Commandes par jour",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val maxCount = (dayCounts.maxOrNull() ?: 1).coerceAtLeast(1)
                    val labels = listOf("Lun", "Mar", "Mer", "Jeu", "Ven", "Sam", "Dim")

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(140.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        labels.forEachIndexed { index, dayLabel ->
                            val count = dayCounts[index]
                            val barHeight = if (count == 0) {
                                3.dp
                            } else {
                                (16f + (count.toFloat() / maxCount) * 86f).dp
                            }
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Bottom
                            ) {
                                if (count > 0) {
                                    Text(
                                        text = "$count",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(barHeight)
                                        .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = dayLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        item {
            if (isMotard) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    text = "Solde disponible",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatFcAmount(availableBalance),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Button(
                                onClick = { showWithdrawDialog = true },
                                enabled = isOnline && availableBalance > 0L && !isWithdrawing,
                                shape = RoundedCornerShape(50),
                                modifier = Modifier
                                    .height(48.dp)
                                    .testTag("withdraw_button")
                            ) {
                                if (isWithdrawing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                } else {
                                    Text(
                                        text = if (isOnline) "Retirer" else "Hors ligne",
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (!isOnline) {
                            Text(
                                text = "Connexion Internet requise pour effectuer un nouveau retrait Mobile Money. Votre historique reste lisible hors ligne.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Text(
                            text = "Retraits",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (withdrawalsState is UiState.Loading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else if (withdrawals.isEmpty()) {
                            Text(
                                text = "Aucun retrait effectué pour l'instant.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            withdrawals.forEach { w ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "${w.operateur} · ${w.numero.takeLast(4)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "-${formatFcAmount(w.montant)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Historique",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (rides.isEmpty()) {
                            Text(
                                text = "Aucune commande pour l'instant.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        } else {
                            rides.take(5).forEach { ride ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ride.nom_motard,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            text = ride.statusEnum().labelFr,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = formatFcAmount(ride.prix_actuel),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showWithdrawDialog) {
        val operators = remember { listOf("M-Pesa", "Orange Money", "Airtel Money") }
        var selectedOperator by remember { mutableStateOf(operators.first()) }
        var numeroInput by remember { mutableStateOf(currentUser.phone.ifBlank { "+243" }) }
        var amountInput by remember { mutableStateOf(availableBalance.toString()) }
        var validationError by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = { if (!isWithdrawing) showWithdrawDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Retrait des gains") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Solde disponible : ${formatFcAmount(availableBalance)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        operators.forEach { op ->
                            FilterChip(
                                selected = selectedOperator == op,
                                onClick = { selectedOperator = op },
                                label = { Text(op, maxLines = 1) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = numeroInput,
                        onValueChange = { numeroInput = it },
                        label = { Text("Numéro Mobile Money") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_phone_input")
                    )
                    OutlinedTextField(
                        value = amountInput,
                        onValueChange = { amountInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Montant à retirer (FC)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("withdraw_amount_input")
                    )
                    if (validationError != null) {
                        Text(
                            text = validationError!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val amt = amountInput.toLongOrNull() ?: 0L
                        val cleanPhone = numeroInput.trim()
                        when {
                            cleanPhone.length < 6 ->
                                validationError = "Entrez un numéro valide."
                            amt !in 1L..availableBalance ->
                                validationError = "Saisissez un montant jusqu'à ${formatFcAmount(availableBalance)}."
                            else -> {
                                validationError = null
                                showWithdrawDialog = false
                                onWithdraw(selectedOperator, cleanPhone, amt, availableBalance)
                            }
                        }
                    },
                    enabled = !isWithdrawing,
                    modifier = Modifier.testTag("confirm_withdraw_button")
                ) {
                    Text("Confirmer le retrait")
                }
            },
            dismissButton = {
                TextButton(onClick = { showWithdrawDialog = false }, enabled = !isWithdrawing) {
                    Text("Annuler")
                }
            }
        )
    }
}

@Composable
private fun StatKpiCard(
    label: String,
    value: String,
    showStar: Boolean = false,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(18.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 22.sp),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                if (showStar) {
                    Icon(
                        imageVector = Icons.Filled.Star,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
