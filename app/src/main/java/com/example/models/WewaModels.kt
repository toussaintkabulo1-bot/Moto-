package com.example.models

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

enum class UserRole(val labelFr: String) {
    CLIENT("Client"),
    MOTARD("Motard");

    companion object {
        fun fromRaw(raw: String?): UserRole =
            entries.firstOrNull { it.name == raw } ?: CLIENT
    }
}

enum class RideStatus(val labelFr: String) {
    EN_NEGOCIATION("En négociation"),
    ACCORD_TROUVE("Accord trouvé"),
    EN_COURS("En cours"),
    TERMINEE("Terminée");

    companion object {
        fun fromRaw(raw: String?): RideStatus =
            entries.firstOrNull { it.name == raw } ?: EN_NEGOCIATION
    }
}

enum class MessageType {
    TEXTE,
    PROPOSITION_PRIX,
    SYSTEME;

    companion object {
        fun fromRaw(raw: String?): MessageType =
            entries.firstOrNull { it.name == raw } ?: TEXTE
    }
}

enum class ProposalStatus {
    EN_ATTENTE,
    ACCEPTEE,
    REFUSEE;

    companion object {
        fun fromRaw(raw: String?): ProposalStatus =
            entries.firstOrNull { it.name == raw } ?: EN_ATTENTE
    }
}

@Keep
@IgnoreExtraProperties
data class User(
    @DocumentId val id: String = "",
    val userId: String = "",
    val role: String = UserRole.CLIENT.name,
    val phone: String = "",
    val nom: String = "",
    val motoModele: String = "",
    val plaque: String = "",
    val documentTexte: String = "",
    val note: Double = 0.0,
    val totalGains: Long = 0L,
    val totalDepenses: Long = 0L,
    val totalRetraits: Long = 0L,
    val completedRides: Long = 0L,
    val enLigne: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun roleEnum(): UserRole = UserRole.fromRaw(role)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "role" to role,
        "phone" to phone,
        "nom" to nom,
        "motoModele" to motoModele,
        "plaque" to plaque,
        "documentTexte" to documentTexte,
        "note" to note,
        "totalGains" to totalGains,
        "totalDepenses" to totalDepenses,
        "totalRetraits" to totalRetraits,
        "completedRides" to completedRides,
        "enLigne" to enLigne,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Withdrawal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val operateur: String = "M-Pesa",
    val numero: String = "",
    val montant: Long = 0L,
    val statut: String = "EFFECTUE",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "operateur" to operateur,
        "numero" to numero,
        "montant" to montant,
        "statut" to statut,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class MotardPublic(
    @DocumentId val id: String = "",
    val motardId: String = "",
    val nom: String = "",
    val motoModele: String = "",
    val plaque: String = "",
    val note: Double = 0.0,
    val distanceKm: Double = 0.0,
    val quartier: String = "",
    val enLigne: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "motardId" to motardId,
        "nom" to nom,
        "motoModele" to motoModele,
        "plaque" to plaque,
        "note" to note,
        "distanceKm" to distanceKm,
        "quartier" to quartier,
        "enLigne" to enLigne,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Ride(
    @DocumentId val id: String = "",
    val id_client: String = "",
    val id_motard: String = "",
    val nom_client: String = "",
    val nom_motard: String = "",
    val moto_motard: String = "",
    val depart: String = "",
    val destination: String = "",
    val status: String = RideStatus.EN_NEGOCIATION.name,
    val prix_actuel: Long = 0L,
    val dernier_proposeur_id: String = "",
    val participantUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun statusEnum(): RideStatus = RideStatus.fromRaw(status)

    fun toCreateMap(): Map<String, Any> = mapOf(
        "id_client" to id_client,
        "id_motard" to id_motard,
        "nom_client" to nom_client,
        "nom_motard" to nom_motard,
        "moto_motard" to moto_motard,
        "depart" to depart,
        "destination" to destination,
        "status" to status,
        "prix_actuel" to prix_actuel,
        "dernier_proposeur_id" to dernier_proposeur_id,
        "participantUids" to participantUids,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Message(
    @DocumentId val id: String = "",
    val type: String = MessageType.TEXTE.name,
    val texte: String = "",
    val montant: Long? = null,
    val id_expediteur: String = "",
    val nom_expediteur: String = "",
    val statut_proposition: String = ProposalStatus.EN_ATTENTE.name,
    val participantUids: List<String> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun typeEnum(): MessageType = MessageType.fromRaw(type)
    fun proposalStatusEnum(): ProposalStatus = ProposalStatus.fromRaw(statut_proposition)

    @Suppress("UNCHECKED_CAST")
    fun toCreateMap(): Map<String, Any> = mapOf(
        "type" to type,
        "texte" to texte,
        "montant" to montant,
        "id_expediteur" to id_expediteur,
        "nom_expediteur" to nom_expediteur,
        "statut_proposition" to statut_proposition,
        "participantUids" to participantUids,
        "createdAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
