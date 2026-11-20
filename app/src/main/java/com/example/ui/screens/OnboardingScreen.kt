package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.models.UserRole
import com.example.ui.components.WewaFilledField

@Composable
fun OnboardingScreen(
    defaultName: String,
    defaultPhone: String,
    isSaving: Boolean,
    onSignOut: () -> Unit = {},
    onSubmitProfile: (
        nom: String,
        phone: String,
        role: UserRole,
        motoModele: String,
        plaque: String
    ) -> Unit
) {
    var nom by remember { mutableStateOf(defaultName.ifBlank { "" }) }
    var phone by remember { mutableStateOf(defaultPhone.ifBlank { "" }) }
    var selectedRole by remember { mutableStateOf(UserRole.CLIENT) }
    var motoModele by remember { mutableStateOf("") }
    var plaque by remember { mutableStateOf("") }
    var validationError by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 540.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            IconButton(
                onClick = onSignOut,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("onboarding_back_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Retour",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = "Créer un compte",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )

            RoleSegmentedSwitch(
                selectedRole = selectedRole,
                onRoleSelected = { selectedRole = it }
            )

            WewaFilledField(
                value = nom,
                onValueChange = { nom = it },
                label = "Nom complet",
                placeholder = "Votre nom et prénom",
                modifier = Modifier.testTag("onboarding_name_input")
            )

            WewaFilledField(
                value = phone,
                onValueChange = { phone = it },
                label = "Téléphone",
                placeholder = "+243...",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.testTag("onboarding_phone_input")
            )

            AnimatedVisibility(visible = selectedRole == UserRole.MOTARD) {
                Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                    WewaFilledField(
                        value = motoModele,
                        onValueChange = { motoModele = it },
                        label = "Modèle de moto",
                        placeholder = "Modèle de votre moto",
                        modifier = Modifier.testTag("onboarding_moto_input")
                    )

                    WewaFilledField(
                        value = plaque,
                        onValueChange = { plaque = it },
                        label = "Plaque",
                        placeholder = "Numéro d'immatriculation",
                        modifier = Modifier.testTag("onboarding_plaque_input")
                    )
                }
            }

            if (validationError != null) {
                Text(
                    text = validationError!!,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Button(
                onClick = {
                    when {
                        nom.trim().isEmpty() ->
                            validationError = "Renseignez votre nom complet."
                        phone.trim().length < 6 ->
                            validationError = "Renseignez votre numéro de téléphone."
                        selectedRole == UserRole.MOTARD && motoModele.trim().isEmpty() ->
                            validationError = "Renseignez le modèle de votre moto."
                        selectedRole == UserRole.MOTARD && plaque.trim().isEmpty() ->
                            validationError = "Renseignez votre numéro de plaque."
                        else -> {
                            validationError = null
                            onSubmitProfile(
                                nom.trim(),
                                phone.trim(),
                                selectedRole,
                                motoModele.trim(),
                                plaque.trim()
                            )
                        }
                    }
                },
                enabled = !isSaving,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("onboarding_submit_button")
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(
                        text = "S'inscrire",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
