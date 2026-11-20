package com.example.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.WewaRepository
import com.example.models.Message
import com.example.models.ProposalStatus
import com.example.models.Ride
import com.example.models.RideStatus
import com.example.models.UiState
import com.example.models.User
import com.example.models.UserRole
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: WewaRepository,
    private val rideId: String,
    private val currentUserId: String
) : ViewModel() {

    val rideState: StateFlow<UiState<Ride?>> = repository.observeRide(rideId)
        .map<Ride?, UiState<Ride?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Erreur observation course", firestoreError ?: error)
            emit(UiState.Success(null))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val messagesState: StateFlow<UiState<List<Message>>> = repository.observeMessages(rideId, currentUserId)
        .map<List<Message>, UiState<List<Message>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Erreur observation messages", firestoreError ?: error)
            emit(UiState.Success(emptyList()))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _isProcessingAction = MutableStateFlow(false)
    val isProcessingAction: StateFlow<Boolean> = _isProcessingAction.asStateFlow()

    private val _showCounterProposalField = MutableStateFlow(false)
    val showCounterProposalField: StateFlow<Boolean> = _showCounterProposalField.asStateFlow()

    private val _showPaymentModal = MutableStateFlow(false)
    val showPaymentModal: StateFlow<Boolean> = _showPaymentModal.asStateFlow()

    fun shouldShowPayButton(ride: Ride?, currentUser: User?): Boolean {
        if (ride == null || currentUser == null) return false
        val isClient = currentUser.role == UserRole.CLIENT.name && ride.id_client == currentUser.userId
        return isClient && ride.status == RideStatus.ACCORD_TROUVE.name
    }

    fun canRespondToProposal(message: Message, ride: Ride): Boolean {
        if (message.statut_proposition != ProposalStatus.EN_ATTENTE.name) return false
        if (ride.status != RideStatus.EN_NEGOCIATION.name) return false
        return message.id_expediteur != currentUserId
    }

    fun openPaymentModal() {
        _showPaymentModal.value = true
    }

    fun dismissPaymentModal() {
        _showPaymentModal.value = false
    }

    fun sendTextMessage(ride: Ride, currentUser: User, text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            repository.sendTextMessage(
                ride = ride,
                senderId = currentUser.userId,
                senderName = currentUser.nom,
                text = text
            ).onFailure { err ->
                Log.w(TAG, "Échec d'envoi du message", err)
            }
        }
    }

    fun sendVoiceTranscriptMessage(ride: Ride, currentUser: User, transcript: String) {
        if (transcript.isBlank()) return
        viewModelScope.launch {
            repository.sendTextMessage(
                ride = ride,
                senderId = currentUser.userId,
                senderName = currentUser.nom,
                text = "🎙️ ${transcript.trim()}"
            ).onFailure { err ->
                Log.w(TAG, "Échec d'envoi du message vocal", err)
            }
        }
    }

    fun sendPriceProposal(ride: Ride, currentUser: User, amountText: String) {
        val amount = amountText.filter { it.isDigit() }.toLongOrNull()
        if (amount == null || amount <= 0L) return
        viewModelScope.launch {
            _isProcessingAction.value = true
            repository.sendPriceProposal(
                ride = ride,
                senderId = currentUser.userId,
                senderName = currentUser.nom,
                amount = amount
            ).onSuccess {
                _showCounterProposalField.value = false
            }.onFailure { err ->
                Log.w(TAG, "Échec d'envoi de la proposition", err)
            }
            _isProcessingAction.value = false
        }
    }

    fun acceptProposal(ride: Ride, message: Message, currentUser: User) {
        val agreedAmount = message.montant ?: ride.prix_actuel
        if (agreedAmount <= 0L) return
        viewModelScope.launch {
            _isProcessingAction.value = true
            repository.acceptProposal(
                ride = ride,
                proposalMessageId = message.id,
                agreedAmount = agreedAmount,
                actorId = currentUser.userId,
                actorName = currentUser.nom
            ).onFailure { err ->
                Log.w(TAG, "Échec d'acceptation", err)
            }
            _isProcessingAction.value = false
        }
    }

    fun refuseProposal(ride: Ride, message: Message, currentUser: User) {
        viewModelScope.launch {
            _isProcessingAction.value = true
            repository.refuseProposal(
                ride = ride,
                proposalMessageId = message.id,
                actorId = currentUser.userId,
                actorName = currentUser.nom
            ).onSuccess {
                _showCounterProposalField.value = true
            }.onFailure { err ->
                Log.w(TAG, "Échec de refus", err)
            }
            _isProcessingAction.value = false
        }
    }

    fun confirmPayment(ride: Ride, currentUser: User, paymentMethod: String) {
        viewModelScope.launch {
            _isProcessingAction.value = true
            repository.payRide(
                ride = ride,
                clientUid = currentUser.userId,
                clientName = currentUser.nom,
                paymentMethod = paymentMethod
            ).onSuccess {
                _showPaymentModal.value = false
            }.onFailure { err ->
                Log.w(TAG, "Échec du paiement", err)
            }
            _isProcessingAction.value = false
        }
    }

    fun completeRide(ride: Ride, currentUser: User) {
        viewModelScope.launch {
            _isProcessingAction.value = true
            repository.completeRide(ride, currentUser)
                .onFailure { err ->
                    Log.w(TAG, "Échec clôture de course", err)
                }
            _isProcessingAction.value = false
        }
    }

    private companion object {
        const val TAG = "ChatViewModel"
    }
}
