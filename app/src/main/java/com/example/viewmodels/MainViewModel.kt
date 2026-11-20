package com.example.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.repository.WewaRepository
import com.example.models.MotardPublic
import com.example.models.Ride
import com.example.models.UiState
import com.example.models.User
import com.example.models.UserRole
import com.example.models.Withdrawal
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

class MainViewModel(
    private val repository: WewaRepository,
    private val currentUserId: String
) : ViewModel() {

    val userProfileState: StateFlow<UiState<User?>> = repository.observeUserProfile(currentUserId)
        .map<User?, UiState<User?>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Erreur profil", firestoreError ?: error)
            emit(UiState.Error(""))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val onlineMotardsState: StateFlow<UiState<List<MotardPublic>>> = repository.observeOnlineMotards()
        .map<List<MotardPublic>, UiState<List<MotardPublic>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Erreur motards", firestoreError ?: error)
            emit(UiState.Success(emptyList()))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val userRidesState: StateFlow<UiState<List<Ride>>> = repository.observeUserRides(currentUserId)
        .map<List<Ride>, UiState<List<Ride>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Erreur courses", firestoreError ?: error)
            emit(UiState.Success(emptyList()))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    val withdrawalsState: StateFlow<UiState<List<Withdrawal>>> = repository.observeWithdrawals(currentUserId)
        .map<List<Withdrawal>, UiState<List<Withdrawal>>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            Log.w(TAG, "Erreur retraits", firestoreError ?: error)
            emit(UiState.Success(emptyList()))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000L),
            initialValue = UiState.Loading
        )

    private val _isSavingProfile = MutableStateFlow(false)
    val isSavingProfile: StateFlow<Boolean> = _isSavingProfile.asStateFlow()

    private val _isOpeningConversation = MutableStateFlow(false)
    val isOpeningConversation: StateFlow<Boolean> = _isOpeningConversation.asStateFlow()

    private val _isWithdrawing = MutableStateFlow(false)
    val isWithdrawing: StateFlow<Boolean> = _isWithdrawing.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    fun clearActionMessage() {
        _actionMessage.value = null
    }

    fun saveAccount(
        nom: String,
        phone: String,
        role: UserRole,
        motoModele: String,
        plaque: String
    ) {
        viewModelScope.launch {
            _isSavingProfile.value = true
            val newUser = User(
                userId = currentUserId,
                role = role.name,
                phone = phone,
                nom = nom,
                motoModele = if (role == UserRole.MOTARD) motoModele else "",
                plaque = if (role == UserRole.MOTARD) plaque else "",
                documentTexte = "",
                enLigne = role == UserRole.MOTARD
            )
            repository.saveNewUserProfile(newUser)
                .onFailure { err ->
                    Log.w(TAG, "Échec enregistrement compte", err)
                }
            _isSavingProfile.value = false
        }
    }

    fun toggleMotardOnline(user: User, enLigne: Boolean) {
        viewModelScope.launch {
            repository.updateMotardOnlineStatus(user, enLigne)
                .onFailure { err ->
                    Log.w(TAG, "Échec changement statut en ligne", err)
                }
        }
    }

    fun openOrCreateConversation(
        client: User,
        motard: MotardPublic,
        onRideReady: (String) -> Unit
    ) {
        if (_isOpeningConversation.value) return
        viewModelScope.launch {
            _isOpeningConversation.value = true
            repository.openOrCreateConversationWithMotard(client, motard)
                .onSuccess { rideId -> onRideReady(rideId) }
                .onFailure { err ->
                    Log.w(TAG, "Échec ouverture conversation", err)
                }
            _isOpeningConversation.value = false
        }
    }

    fun withdrawEarnings(
        user: User,
        operateur: String,
        numero: String,
        montant: Long,
        maxAvailable: Long
    ) {
        if (_isWithdrawing.value) return
        viewModelScope.launch {
            _isWithdrawing.value = true
            repository.withdrawEarnings(
                user = user,
                operateur = operateur,
                numero = numero,
                montant = montant,
                maxAvailable = maxAvailable
            ).onSuccess {
                _actionMessage.value = "Retrait de $montant FC envoyé vers $operateur ($numero)."
            }.onFailure { err ->
                Log.w(TAG, "Échec retrait gains", err)
            }
            _isWithdrawing.value = false
        }
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
