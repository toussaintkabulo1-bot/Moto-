package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.models.Ride
import com.example.models.RideStatus
import com.example.models.UiState
import com.example.models.User
import com.example.models.UserRole
import com.example.ui.components.ChatMessageItem
import com.example.ui.components.InitialsAvatar
import com.example.ui.components.PaymentConfirmationDialog
import com.example.ui.components.RideStatusBadge
import com.example.ui.components.SosAlertDialog
import com.example.ui.components.formatFcAmount
import com.example.viewmodels.ChatViewModel
import java.util.Locale

@Composable
fun ChatScreen(
    currentUser: User,
    viewModel: ChatViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val rideState by viewModel.rideState.collectAsStateWithLifecycle()
    val messagesState by viewModel.messagesState.collectAsStateWithLifecycle()
    val isBusy by viewModel.isProcessingAction.collectAsStateWithLifecycle()
    val showCounterProposal by viewModel.showCounterProposalField.collectAsStateWithLifecycle()
    val showPaymentModal by viewModel.showPaymentModal.collectAsStateWithLifecycle()

    var messageText by remember { mutableStateOf("") }
    var proposalAmountText by remember { mutableStateOf("") }
    var showSosDialog by remember { mutableStateOf(false) }

    // Vocal chat (SpeechRecognizer + TextToSpeech)
    var isListeningVoice by remember { mutableStateOf(false) }
    var liveTranscript by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val ride: Ride? = (rideState as? UiState.Success)?.data
    val messages = (messagesState as? UiState.Success)?.data.orEmpty()

    var ttsReady by remember { mutableStateOf(false) }
    val textToSpeech = remember(context) {
        var instance: TextToSpeech? = null
        instance = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                instance?.language = Locale.FRENCH
                ttsReady = true
            }
        }
        instance
    }

    val speechRecognizer = remember(context) {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            SpeechRecognizer.createSpeechRecognizer(context)
        } else {
            null
        }
    }

    DisposableEffect(speechRecognizer, textToSpeech) {
        onDispose {
            try {
                speechRecognizer?.destroy()
            } catch (_: Exception) {
            }
            try {
                textToSpeech.stop()
                textToSpeech.shutdown()
            } catch (_: Exception) {
            }
        }
    }

    val startVoiceDictation: () -> Unit = {
        val activeRide = ride
        if (speechRecognizer != null && activeRide != null) {
            liveTranscript = ""
            speechRecognizer.setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    isListeningVoice = true
                }
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray?) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    isListeningVoice = false
                    Log.w("ChatScreen", "SpeechRecognizer error code: $error")
                }
                override fun onResults(results: Bundle?) {
                    isListeningVoice = false
                    val spoken = results
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        ?.trim()
                        .orEmpty()
                    if (spoken.isNotEmpty()) {
                        liveTranscript = ""
                        viewModel.sendVoiceTranscriptMessage(activeRide, currentUser, spoken)
                    }
                }
                override fun onPartialResults(partialResults: Bundle?) {
                    val partial = partialResults
                        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                        ?.firstOrNull()
                        .orEmpty()
                    if (partial.isNotBlank()) {
                        liveTranscript = partial
                    }
                }
                override fun onEvent(eventType: Int, params: Bundle?) {}
            })
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "fr-FR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            }
            isListeningVoice = true
            speechRecognizer.startListening(intent)
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceDictation()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val partnerName = when {
        ride == null -> "Discussion Wewa"
        currentUser.role == UserRole.CLIENT.name -> ride.nom_motard
        else -> ride.nom_client
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
                .navigationBarsPadding()
                .imePadding()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("chat_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Retour",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                InitialsAvatar(
                    name = partnerName,
                    isOnline = true,
                    size = 42.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = partnerName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (ride != null && ride.depart.isNotBlank()) {
                            "en ligne · ${ride.depart}"
                        } else {
                            "en ligne · vocal & texte"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Surface(
                    onClick = { showSosDialog = true },
                    color = MaterialTheme.colorScheme.error,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("chat_sos_button")
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    ) {
                        Text(
                            text = "SOS",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Barre d'état & bouton Payer conditionnel (Côté Client uniquement si ACCORD_TROUVE)
            if (ride != null) {
                val showPay = viewModel.shouldShowPayButton(ride, currentUser)
                val showComplete = ride.status == RideStatus.EN_COURS.name
                val showWaitPay = ride.status == RideStatus.ACCORD_TROUVE.name &&
                    currentUser.role == UserRole.MOTARD.name

                if (showPay || showComplete || showWaitPay || ride.status == RideStatus.TERMINEE.name) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                RideStatusBadge(status = ride.statusEnum())
                                if (ride.prix_actuel > 0L) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = formatFcAmount(ride.prix_actuel),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }

                            when {
                                showPay -> {
                                    Button(
                                        onClick = { viewModel.openPaymentModal() },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.tertiary,
                                            contentColor = MaterialTheme.colorScheme.onTertiary
                                        ),
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("pay_ride_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Payments,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Payer",
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }

                                showComplete -> {
                                    Button(
                                        onClick = { viewModel.completeRide(ride, currentUser) },
                                        enabled = !isBusy,
                                        shape = RoundedCornerShape(50),
                                        modifier = Modifier
                                            .height(48.dp)
                                            .testTag("complete_ride_button")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Terminer", fontWeight = FontWeight.Bold)
                                    }
                                }

                                showWaitPay -> {
                                    Text(
                                        text = "En attente du paiement client",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.tertiary
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (messagesState) {
                    UiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    is UiState.Error, is UiState.Success -> {
                        if (messages.isEmpty()) {
                            // Conversation vierge lorsqu'il n'y avait pas encore d'échange
                            Text(
                                text = "Conversation ouverte avec $partnerName.\nEnvoyez un message texte, vocal ou proposez un prix.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(24.dp)
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(messages, key = { it.id }) { msg ->
                                    val canRespond = ride != null &&
                                        viewModel.canRespondToProposal(msg, ride)
                                    ChatMessageItem(
                                        message = msg,
                                        isMine = msg.id_expediteur == currentUser.userId,
                                        canRespondToProposal = canRespond,
                                        isBusy = isBusy,
                                        onAcceptProposal = {
                                            if (ride != null) {
                                                viewModel.acceptProposal(ride, msg, currentUser)
                                            }
                                        },
                                        onRefuseProposal = {
                                            if (ride != null) {
                                                viewModel.refuseProposal(ride, msg, currentUser)
                                            }
                                        },
                                        onSpeakMessage = { spoken ->
                                            if (ttsReady) {
                                                textToSpeech.speak(
                                                    spoken,
                                                    TextToSpeech.QUEUE_FLUSH,
                                                    null,
                                                    "wewa_voice_${msg.id}"
                                                )
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (ride != null && ride.status != RideStatus.TERMINEE.name) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AnimatedVisibility(visible = isListeningVoice) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = liveTranscript.ifBlank { "Écoute en cours... Parlez maintenant" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        speechRecognizer?.cancel()
                                        isListeningVoice = false
                                        liveTranscript = ""
                                    },
                                    modifier = Modifier.size(40.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Close,
                                        contentDescription = "Annuler l'écoute",
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }

                    if (ride.status == RideStatus.EN_NEGOCIATION.name) {
                        val validAmount = (proposalAmountText.toLongOrNull() ?: 0L) > 0L
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            TextField(
                                value = proposalAmountText,
                                onValueChange = { proposalAmountText = it.filter { ch -> ch.isDigit() } },
                                placeholder = {
                                    Text(
                                        text = if (showCounterProposal) {
                                            "Contre-proposition (FC)"
                                        } else {
                                            "Proposer un prix (FC)"
                                        },
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                shape = RoundedCornerShape(50),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    cursorColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("counter_proposal_input")
                            )

                            Button(
                                onClick = {
                                    viewModel.sendPriceProposal(ride, currentUser, proposalAmountText)
                                    proposalAmountText = ""
                                },
                                enabled = !isBusy && validAmount,
                                shape = RoundedCornerShape(50),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary,
                                    disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier
                                    .height(52.dp)
                                    .testTag("send_proposal_button")
                            ) {
                                Text(
                                    text = "Proposer",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = messageText,
                            onValueChange = { messageText = it },
                            placeholder = {
                                Text(
                                    text = "Message texte ou vocal",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("chat_message_input")
                        )

                        if (messageText.isNotBlank()) {
                            Surface(
                                onClick = {
                                    val textToSend = messageText
                                    messageText = ""
                                    viewModel.sendTextMessage(ride, currentUser, textToSend)
                                },
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(52.dp)
                                    .testTag("chat_send_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Send,
                                        contentDescription = "Envoyer"
                                    )
                                }
                            }
                        } else {
                            Surface(
                                onClick = {
                                    if (isListeningVoice) {
                                        speechRecognizer?.stopListening()
                                        isListeningVoice = false
                                    } else {
                                        val hasPerm = ContextCompat.checkSelfPermission(
                                            context,
                                            Manifest.permission.RECORD_AUDIO
                                        ) == PackageManager.PERMISSION_GRANTED
                                        if (hasPerm) {
                                            startVoiceDictation()
                                        } else {
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        }
                                    }
                                },
                                color = if (isListeningVoice) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                contentColor = if (isListeningVoice) {
                                    MaterialTheme.colorScheme.onError
                                } else {
                                    MaterialTheme.colorScheme.onPrimary
                                },
                                shape = CircleShape,
                                modifier = Modifier
                                    .size(52.dp)
                                    .testTag("chat_voice_button")
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Filled.Mic,
                                        contentDescription = "Envoyer un message vocal"
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSosDialog) {
        SosAlertDialog(
            plaque = currentUser.plaque,
            onDismiss = { showSosDialog = false }
        )
    }

    if (showPaymentModal && ride != null) {
        PaymentConfirmationDialog(
            amount = ride.prix_actuel,
            motardName = ride.nom_motard,
            isProcessing = isBusy,
            onConfirm = { method ->
                viewModel.confirmPayment(ride, currentUser, method)
            },
            onDismiss = { viewModel.dismissPaymentModal() }
        )
    }
}
