package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.credentials.CredentialManager
import com.example.models.UserRole
import com.example.ui.components.WewaFilledField
import com.example.util.isValidE164Phone
import com.example.util.normalizePhoneE164
import com.example.util.onGoogleSignInClicked

@Immutable
data class PendingRegistration(
    val nom: String,
    val phone: String,
    val role: UserRole,
    val motoModele: String,
    val plaque: String
)

private enum class AuthStep {
    LANDING,
    CREATE_ACCOUNT,
    LOGIN
}

@Composable
fun AuthScreen(
    initialPhoneHint: String = "",
    isOnline: Boolean = true,
    onAuthSuccess: (phoneHint: String, pendingRegistration: PendingRegistration?) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }

    var step by rememberSaveable { mutableStateOf(AuthStep.LANDING) }

    var phoneInput by rememberSaveable { mutableStateOf(initialPhoneHint) }
    var fullName by rememberSaveable { mutableStateOf("") }
    var selectedRole by rememberSaveable { mutableStateOf(UserRole.CLIENT) }
    var motoModele by rememberSaveable { mutableStateOf("") }
    var plaque by rememberSaveable { mutableStateOf("") }

    var isLoading by remember { mutableStateOf(false) }
    var helperHint by remember { mutableStateOf<String?>(null) }

    if (step != AuthStep.LANDING) {
        BackHandler(enabled = !isLoading) {
            helperHint = null
            step = AuthStep.LANDING
        }
    }

    fun launchGoogleAuth(registration: PendingRegistration?, cleanPhone: String) {
        if (!isOnline) {
            helperHint = "Connectez-vous à Internet pour finaliser votre accès."
            return
        }
        isLoading = true
        helperHint = null
        onGoogleSignInClicked(
            context = context,
            credentialManager = credentialManager,
            onAuthSuccess = {
                isLoading = false
                onAuthSuccess(cleanPhone, registration)
            },
            onAuthFailed = {
                isLoading = false
            },
            scope = scope,
            onAuthCancelled = {
                isLoading = false
            }
        )
    }

    val primaryGlow = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val bgColor = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .background(
                Brush.radialGradient(
                    colors = listOf(primaryGlow, Color.Transparent),
                    center = Offset(920f, 120f),
                    radius = 950f
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentAlignment = Alignment.TopCenter
    ) {
        when (step) {
            AuthStep.LANDING -> {
                LandingStepContent(
                    isOnline = isOnline,
                    onCreateAccountClick = {
                        helperHint = null
                        step = AuthStep.CREATE_ACCOUNT
                    },
                    onExistingAccountClick = {
                        helperHint = null
                        step = AuthStep.LOGIN
                    }
                )
            }

            AuthStep.CREATE_ACCOUNT -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 540.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (!isLoading) {
                                helperHint = null
                                step = AuthStep.LANDING
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("auth_back_button")
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

                    if (!isOnline) {
                        OfflineAuthNotice()
                    }

                    RoleSegmentedSwitch(
                        selectedRole = selectedRole,
                        onRoleSelected = { selectedRole = it }
                    )

                    WewaFilledField(
                        value = fullName,
                        onValueChange = {
                            fullName = it
                            helperHint = null
                        },
                        label = "Nom complet",
                        placeholder = "Votre nom et prénom",
                        modifier = Modifier.testTag("register_name_input")
                    )

                    WewaFilledField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it
                            helperHint = null
                        },
                        label = "Numéro de téléphone (ex. +243...)",
                        placeholder = "+243...",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.testTag("register_phone_input")
                    )

                    AnimatedVisibility(visible = selectedRole == UserRole.MOTARD) {
                        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            WewaFilledField(
                                value = motoModele,
                                onValueChange = {
                                    motoModele = it
                                    helperHint = null
                                },
                                label = "Modèle de moto",
                                placeholder = "Modèle de votre moto",
                                modifier = Modifier.testTag("register_moto_input")
                            )

                            WewaFilledField(
                                value = plaque,
                                onValueChange = {
                                    plaque = it
                                    helperHint = null
                                },
                                label = "Plaque",
                                placeholder = "Numéro d'immatriculation",
                                modifier = Modifier.testTag("register_plaque_input")
                            )
                        }
                    }

                    Text(
                        text = "Votre numéro de téléphone est enregistré sur votre profil pour les courses et retraits Mobile Money. La validation du compte s'effectue via Google.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (helperHint != null) {
                        NeutralAuthHint(text = helperHint!!)
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Button(
                        onClick = {
                            val cleanName = fullName.trim()
                            val normalizedPhone = normalizePhoneE164(phoneInput)
                            val cleanMoto = motoModele.trim()
                            val cleanPlaque = plaque.trim()
                            when {
                                cleanName.isEmpty() ->
                                    helperHint = "Renseignez votre nom complet."
                                !isValidE164Phone(normalizedPhone) ->
                                    helperHint = "Entrez un numéro international valide (ex : +243812345678)."
                                selectedRole == UserRole.MOTARD && cleanMoto.isEmpty() ->
                                    helperHint = "Renseignez le modèle de votre moto."
                                selectedRole == UserRole.MOTARD && cleanPlaque.isEmpty() ->
                                    helperHint = "Renseignez votre numéro de plaque."
                                else -> {
                                    val registration = PendingRegistration(
                                        nom = cleanName,
                                        phone = normalizedPhone,
                                        role = selectedRole,
                                        motoModele = cleanMoto,
                                        plaque = cleanPlaque
                                    )
                                    launchGoogleAuth(registration, normalizedPhone)
                                }
                            }
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("google_register_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = "S'inscrire avec Google",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }

            AuthStep.LOGIN -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .widthIn(max = 540.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    IconButton(
                        onClick = {
                            if (!isLoading) {
                                helperHint = null
                                step = AuthStep.LANDING
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("login_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Retour",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Connexion",
                            style = MaterialTheme.typography.displaySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Text(
                            text = "Connectez-vous avec votre compte Google pour retrouver automatiquement votre espace Client ou Motard.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (!isOnline) {
                        OfflineAuthNotice()
                    }

                    WewaFilledField(
                        value = phoneInput,
                        onValueChange = {
                            phoneInput = it
                            helperHint = null
                        },
                        label = "Numéro de téléphone (optionnel)",
                        placeholder = "+243...",
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        modifier = Modifier.testTag("auth_phone_input")
                    )

                    if (helperHint != null) {
                        NeutralAuthHint(text = helperHint!!)
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            launchGoogleAuth(null, normalizePhoneE164(phoneInput))
                        },
                        enabled = !isLoading,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .testTag("google_sign_in_button")
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Text(
                                text = "Continuer avec Google",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun NeutralAuthHint(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun OfflineAuthNotice() {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Outlined.WifiOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = "Mode hors ligne détecté : la lecture reste disponible, mais une connexion Internet est requise pour la première connexion.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun LandingStepContent(
    isOnline: Boolean,
    onCreateAccountClick: () -> Unit,
    onExistingAccountClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .widthIn(max = 540.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            if (!isOnline) {
                OfflineAuthNotice()
            }

            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.onBackground)) {
                        append("Votre moto,\n")
                    }
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("en un\nmessage.")
                    }
                },
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 42.sp,
                    lineHeight = 46.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            )

            Text(
                text = "Choisissez un motard proche, discutez, mettez-vous d'accord et consultez vos trajets même hors ligne.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 24.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                LandingFeatureRow(
                    icon = Icons.Outlined.LocationOn,
                    text = "Motards disponibles en temps réel"
                )
                LandingFeatureRow(
                    icon = Icons.Outlined.ChatBubbleOutline,
                    text = "Discutez par texte ou vocal"
                )
                LandingFeatureRow(
                    icon = Icons.Outlined.Payments,
                    text = "Proposez votre prix, payez après accord"
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 36.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Button(
                onClick = onCreateAccountClick,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("landing_create_account_button")
            ) {
                Text(
                    text = "Créer un compte",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            OutlinedButton(
                onClick = onExistingAccountClick,
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.65f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("landing_login_button")
            ) {
                Text(
                    text = "J'ai déjà un compte",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Text(
                text = "Accès sécurisé avec votre compte Google",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun LandingFeatureRow(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
fun RoleSegmentedSwitch(
    selectedRole: UserRole,
    onRoleSelected: (UserRole) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(50),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            RoleSegmentItem(
                label = "Client",
                selected = selectedRole == UserRole.CLIENT,
                onClick = { onRoleSelected(UserRole.CLIENT) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("role_client_card")
            )
            RoleSegmentItem(
                label = "Motard",
                selected = selectedRole == UserRole.MOTARD,
                onClick = { onRoleSelected(UserRole.MOTARD) },
                modifier = Modifier
                    .weight(1f)
                    .testTag("role_motard_card")
            )
        }
    }
}

@Composable
private fun RoleSegmentItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(50))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
