package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.models.User
import com.example.models.UserRole
import com.example.ui.components.InitialsAvatar
import com.example.ui.components.SosAlertDialog

@Composable
fun ProfilScreen(
    currentUser: User,
    onOpenStats: () -> Unit,
    onSignOut: () -> Unit
) {
    var showSosDialog by remember { mutableStateOf(false) }
    val isMotard = currentUser.role == UserRole.MOTARD.name

    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(16.dp))

        InitialsAvatar(
            name = currentUser.nom,
            isOnline = false,
            size = 90.dp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = currentUser.nom.ifBlank { "Utilisateur Wewa" },
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append(currentUser.roleEnum().labelFr)
                }
                withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) {
                    append(" · ")
                    append(currentUser.phone.ifBlank { "Téléphone non renseigné" })
                }
            },
            style = MaterialTheme.typography.bodyLarge
        )

        if (isMotard && (currentUser.motoModele.isNotBlank() || currentUser.plaque.isNotBlank())) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "${currentUser.motoModele} · ${currentUser.plaque}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(modifier = Modifier.height(34.dp))

        Button(
            onClick = { showSosDialog = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            ),
            shape = RoundedCornerShape(50),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .testTag("sos_emergency_button")
        ) {
            Text(
                text = "SOS",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        TextButton(
            onClick = onOpenStats,
            modifier = Modifier
                .height(48.dp)
                .testTag("open_stats_button")
        ) {
            Text(
                text = "Mes statistiques",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.weight(1f, fill = true))
        Spacer(modifier = Modifier.height(28.dp))

        TextButton(
            onClick = onSignOut,
            modifier = Modifier
                .height(48.dp)
                .testTag("sign_out_button")
        ) {
            Text(
                text = "Se déconnecter",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
    }

    if (showSosDialog) {
        SosAlertDialog(
            plaque = currentUser.plaque,
            onDismiss = { showSosDialog = false }
        )
    }
}
