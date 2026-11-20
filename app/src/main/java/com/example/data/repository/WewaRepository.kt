package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.models.Message
import com.example.models.MessageType
import com.example.models.MotardPublic
import com.example.models.ProposalStatus
import com.example.models.Ride
import com.example.models.RideStatus
import com.example.models.User
import com.example.models.UserRole
import com.example.models.Withdrawal
import com.example.util.FirestoreProvider
import com.example.util.WewaLocalCache
import com.example.util.isNetworkAvailable
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.Source
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

class WewaRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth,
    private val localCache: WewaLocalCache? = null,
    private val isNetworkOnline: () -> Boolean = { true }
) {
    constructor(context: Context) : this(
        db = FirestoreProvider.getDatabase(context),
        auth = Firebase.auth,
        localCache = WewaLocalCache(context.applicationContext),
        isNetworkOnline = { isNetworkAvailable(context.applicationContext) }
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("L'utilisateur doit être authentifié avant d'accéder à Firestore.")
    }

    private suspend fun Task<Void>.awaitOrQueueOffline() {
        if (isNetworkOnline()) {
            withTimeoutOrNull(3500L) {
                this@awaitOrQueueOffline.await()
            }
        } else {
            this.addOnFailureListener { err ->
                Log.w("WewaRepository", "Erreur de synchronisation différée: ${err.message}")
            }
        }
    }

    private suspend fun DocumentReference.getOfflineSafe(): DocumentSnapshot? {
        if (!isNetworkOnline()) {
            return try {
                get(Source.CACHE).await()
            } catch (_: Exception) {
                null
            }
        }
        return try {
            withTimeoutOrNull(3500L) { get(Source.DEFAULT).await() }
                ?: get(Source.CACHE).await()
        } catch (_: Exception) {
            try {
                get(Source.CACHE).await()
            } catch (_: Exception) {
                null
            }
        }
    }

    private suspend fun Query.getOfflineSafe(): QuerySnapshot? {
        if (!isNetworkOnline()) {
            return try {
                get(Source.CACHE).await()
            } catch (_: Exception) {
                null
            }
        }
        return try {
            withTimeoutOrNull(3500L) { get(Source.DEFAULT).await() }
                ?: get(Source.CACHE).await()
        } catch (_: Exception) {
            try {
                get(Source.CACHE).await()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun observeUserProfile(uid: String): Flow<User?> = flow {
        require(uid.isNotBlank()) { "UID requis pour observer le profil." }
        val path = "users/$uid"
        val cachedUser = localCache?.getUser(uid)
        if (cachedUser != null) {
            emit(cachedUser)
        }
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(User::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.also { localCache?.saveUser(it) }
                    } else if (snapshot.metadata.isFromCache && !isNetworkOnline()) {
                        localCache?.getUser(uid)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val fallback = localCache?.getUser(uid)
                    if (!isNetworkOnline() && fallback != null) {
                        emit(fallback)
                        return@catch
                    }
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    if (fallback != null && firestoreError?.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                        emit(fallback)
                    } else {
                        throw error
                    }
                }
        )
    }

    suspend fun saveNewUserProfile(user: User): Result<Unit> {
        var docPath = "users/${user.userId}"
        return try {
            val uid = requireUserId()
            check(uid == user.userId) { "UID utilisateur incohérent." }
            val cleanName = user.nom.trim().take(60)
            require(cleanName.isNotEmpty()) { "Le nom est obligatoire." }
            val cleanPhone = user.phone.trim().take(30)
            val cleanMoto = user.motoModele.trim().take(60)
            val cleanPlaque = user.plaque.trim().take(25)

            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val sanitizedUser = user.copy(
                userId = uid,
                nom = cleanName,
                phone = cleanPhone,
                motoModele = cleanMoto,
                plaque = cleanPlaque,
                documentTexte = ""
            )

            localCache?.saveUser(sanitizedUser)

            val existingSnap = docRef.getOfflineSafe()
            if (existingSnap != null && existingSnap.exists()) {
                docRef.update(
                    mapOf(
                        "role" to sanitizedUser.role,
                        "phone" to sanitizedUser.phone,
                        "nom" to sanitizedUser.nom,
                        "motoModele" to sanitizedUser.motoModele,
                        "plaque" to sanitizedUser.plaque,
                        "enLigne" to sanitizedUser.enLigne,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitOrQueueOffline()
            } else {
                docRef.set(sanitizedUser.toCreateMap()).awaitOrQueueOffline()
            }

            if (sanitizedUser.role == UserRole.MOTARD.name) {
                syncMotardPublicListing(sanitizedUser)
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    private suspend fun syncMotardPublicListing(user: User) {
        val publicRef = db.collection("motards_public").document(user.userId)
        val existingPublic = publicRef.getOfflineSafe()
        if (existingPublic != null && existingPublic.exists()) {
            publicRef.update(
                mapOf(
                    "nom" to user.nom,
                    "motoModele" to user.motoModele,
                    "plaque" to user.plaque,
                    "note" to user.note,
                    "enLigne" to user.enLigne,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).awaitOrQueueOffline()
        } else {
            val listing = MotardPublic(
                motardId = user.userId,
                nom = user.nom,
                motoModele = user.motoModele,
                plaque = user.plaque,
                note = user.note,
                distanceKm = 0.0,
                quartier = "",
                enLigne = user.enLigne
            )
            publicRef.set(listing.toCreateMap()).awaitOrQueueOffline()
        }
    }

    suspend fun updateMotardOnlineStatus(user: User, enLigne: Boolean): Result<Unit> {
        val docPath = "users/${user.userId}"
        return try {
            val authUid = requireUserId()
            check(authUid == user.userId) { "Accès non autorisé." }
            localCache?.saveUser(user.copy(enLigne = enLigne))
            db.collection("users").document(authUid)
                .update(
                    mapOf(
                        "enLigne" to enLigne,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitOrQueueOffline()
            if (user.role == UserRole.MOTARD.name) {
                syncMotardPublicListing(user.copy(enLigne = enLigne))
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    fun observeOnlineMotards(): Flow<List<MotardPublic>> = flow {
        val uid = requireUserId()
        val path = "motards_public"
        val cached = localCache?.getMotards().orEmpty()
        if (cached.isNotEmpty() || !isNetworkOnline()) {
            emit(cached)
        }
        emitAll(
            db.collection(path)
                .whereEqualTo("enLigne", true)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        MotardPublic::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.nom.lowercase() }
                }
                .onEach { list -> localCache?.saveMotards(list) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (!isNetworkOnline() || firestoreError?.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                        emit(localCache?.getMotards().orEmpty())
                        return@catch
                    }
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeUserRides(uid: String): Flow<List<Ride>> = flow {
        require(uid.isNotBlank()) { "UID requis pour observer les courses." }
        val path = "rides"
        val cached = localCache?.getRides(uid).orEmpty()
        if (cached.isNotEmpty() || !isNetworkOnline()) {
            emit(cached)
        }
        emitAll(
            db.collection(path)
                .whereArrayContains("participantUids", uid)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Ride::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }
                }
                .onEach { list -> localCache?.saveRides(uid, list) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (!isNetworkOnline() || firestoreError?.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                        emit(localCache?.getRides(uid).orEmpty())
                        return@catch
                    }
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    fun observeWithdrawals(uid: String): Flow<List<Withdrawal>> = flow {
        require(uid.isNotBlank()) { "UID requis pour observer les retraits." }
        val path = "users/$uid/withdrawals"
        val cached = localCache?.getWithdrawals(uid).orEmpty()
        if (cached.isNotEmpty() || !isNetworkOnline()) {
            emit(cached)
        }
        emitAll(
            db.collection("users").document(uid).collection("withdrawals")
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Withdrawal::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedByDescending { it.createdAt ?: Timestamp(0, 0) }
                }
                .onEach { list -> localCache?.saveWithdrawals(uid, list) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (!isNetworkOnline() || firestoreError?.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                        emit(localCache?.getWithdrawals(uid).orEmpty())
                        return@catch
                    }
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun withdrawEarnings(
        user: User,
        operateur: String,
        numero: String,
        montant: Long,
        maxAvailable: Long
    ): Result<Unit> {
        val path = "users/${user.userId}/withdrawals"
        return try {
            val uid = requireUserId()
            check(uid == user.userId) { "Action non autorisée." }
            require(user.role == UserRole.MOTARD.name) { "Seuls les motards peuvent retirer leurs gains." }
            require(isNetworkOnline()) { "Connexion Internet requise pour effectuer un retrait Mobile Money." }
            val cleanOperator = operateur.trim().take(40)
            val cleanPhone = numero.trim().take(30)
            require(cleanOperator.isNotEmpty()) { "Opérateur requis." }
            require(cleanPhone.isNotEmpty()) { "Numéro Mobile Money requis." }
            require(montant in 1L..maxAvailable) { "Montant supérieur au solde disponible." }

            val userRef = db.collection("users").document(uid)
            val withdrawalRef = userRef.collection("withdrawals").document()
            val withdrawal = Withdrawal(
                userId = uid,
                operateur = cleanOperator,
                numero = cleanPhone,
                montant = montant,
                statut = "EFFECTUE"
            )

            val batch = db.batch()
            batch.set(withdrawalRef, withdrawal.toCreateMap())
            batch.update(
                userRef,
                mapOf(
                    "totalRetraits" to FieldValue.increment(montant),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    fun observeRide(rideId: String): Flow<Ride?> = flow {
        val uid = requireUserId()
        require(rideId.isNotBlank()) { "rideId requis." }
        val path = "rides/$rideId"
        val cachedRide = localCache?.getRide(rideId)
        if (cachedRide != null) {
            emit(cachedRide)
        }
        emitAll(
            db.collection("rides").document(rideId)
                .snapshots()
                .map { snapshot ->
                    if (snapshot.exists()) {
                        snapshot.toObject(Ride::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                            ?.also { localCache?.saveSingleRide(it) }
                    } else if (snapshot.metadata.isFromCache && !isNetworkOnline()) {
                        localCache?.getRide(rideId)
                    } else {
                        null
                    }
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val fallback = localCache?.getRide(rideId)
                    if (fallback != null && (!isNetworkOnline() || firestoreError?.code == FirebaseFirestoreException.Code.UNAVAILABLE)) {
                        emit(fallback)
                        return@catch
                    }
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == uid) {
                        handleFirestoreError(exceptionToLog, OperationType.GET, path)
                    }
                    throw error
                }
        )
    }

    fun observeMessages(rideId: String, currentUserId: String): Flow<List<Message>> = flow {
        require(rideId.isNotBlank() && currentUserId.isNotBlank()) { "rideId et currentUserId requis." }
        val path = "rides/$rideId/messages"
        val cached = localCache?.getMessages(rideId).orEmpty()
        if (cached.isNotEmpty() || !isNetworkOnline()) {
            emit(cached)
        }
        emitAll(
            db.collection("rides").document(rideId).collection("messages")
                .whereArrayContains("participantUids", currentUserId)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        Message::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    ).sortedBy { it.createdAt ?: Timestamp(0, 0) }
                }
                .onEach { list -> localCache?.saveMessages(rideId, list) }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    if (!isNetworkOnline() || firestoreError?.code == FirebaseFirestoreException.Code.UNAVAILABLE) {
                        emit(localCache?.getMessages(rideId).orEmpty())
                        return@catch
                    }
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == currentUserId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    /**
     * Ouvre une conversation existante (non terminée) avec ce motard si elle existe déjà,
     * sinon crée une nouvelle session vierge (aucun message pré-rempli).
     * Fonctionne aussi hors ligne via le cache local et la file de synchronisation Firestore.
     */
    suspend fun openOrCreateConversationWithMotard(
        client: User,
        motard: MotardPublic
    ): Result<String> {
        var docPath = "rides"
        return try {
            val uid = requireUserId()
            check(uid == client.userId) { "UID incohérent." }

            val existingSnap = db.collection("rides")
                .whereArrayContains("participantUids", uid)
                .getOfflineSafe()

            val fromFirestore = existingSnap?.documents
                ?.mapNotNull { it.toObject(Ride::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .orEmpty()
            val candidateList = if (fromFirestore.isNotEmpty()) {
                fromFirestore
            } else {
                localCache?.getRides(uid).orEmpty()
            }

            val existingRide = candidateList
                .filter {
                    it.id_client == uid &&
                        it.id_motard == motard.motardId &&
                        it.status != RideStatus.TERMINEE.name
                }
                .maxByOrNull { it.updatedAt ?: it.createdAt ?: Timestamp(0, 0) }

            if (existingRide != null && existingRide.id.isNotBlank()) {
                return Result.success(existingRide.id)
            }

            val rideRef = db.collection("rides").document()
            docPath = rideRef.path
            val participants = listOf(uid, motard.motardId)
            val ride = Ride(
                id = rideRef.id,
                id_client = uid,
                id_motard = motard.motardId,
                nom_client = client.nom.take(60),
                nom_motard = motard.nom.take(60),
                moto_motard = motard.motoModele.take(60),
                depart = "",
                destination = "",
                status = RideStatus.EN_NEGOCIATION.name,
                prix_actuel = 0L,
                dernier_proposeur_id = "",
                participantUids = participants
            )
            localCache?.saveSingleRide(ride)
            rideRef.set(ride.toCreateMap()).awaitOrQueueOffline()
            Result.success(rideRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    suspend fun sendTextMessage(
        ride: Ride,
        senderId: String,
        senderName: String,
        text: String
    ): Result<Unit> {
        val path = "rides/${ride.id}/messages"
        return try {
            val uid = requireUserId()
            check(uid == senderId) { "Expéditeur non autorisé." }
            val cleanText = text.trim().take(2000)
            require(cleanText.isNotEmpty()) { "Le message ne peut pas être vide." }
            val msg = Message(
                type = MessageType.TEXTE.name,
                texte = cleanText,
                id_expediteur = uid,
                nom_expediteur = senderName.trim().ifBlank { "Utilisateur" }.take(60),
                statut_proposition = ProposalStatus.EN_ATTENTE.name,
                participantUids = ride.participantUids
            )
            db.collection("rides").document(ride.id)
                .collection("messages").document()
                .set(msg.toCreateMap())
                .awaitOrQueueOffline()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, path)
            Result.failure(e)
        }
    }

    suspend fun sendPriceProposal(
        ride: Ride,
        senderId: String,
        senderName: String,
        amount: Long,
        noteText: String = ""
    ): Result<Unit> {
        val path = "rides/${ride.id}"
        return try {
            val uid = requireUserId()
            check(uid == senderId) { "Expéditeur non autorisé." }
            require(amount in 1L..10_000_000L) { "Montant invalide." }
            require(ride.status == RideStatus.EN_NEGOCIATION.name) {
                "Les négociations ne sont possibles que lorsque la course est en négociation."
            }

            db.collection("rides").document(ride.id)
                .update(
                    mapOf(
                        "prix_actuel" to amount,
                        "dernier_proposeur_id" to uid,
                        "status" to RideStatus.EN_NEGOCIATION.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitOrQueueOffline()

            val msgText = noteText.trim().ifBlank { "Nouvelle proposition de prix : $amount FC" }.take(2000)
            val msg = Message(
                type = MessageType.PROPOSITION_PRIX.name,
                texte = msgText,
                montant = amount,
                id_expediteur = uid,
                nom_expediteur = senderName.trim().ifBlank { "Utilisateur" }.take(60),
                statut_proposition = ProposalStatus.EN_ATTENTE.name,
                participantUids = ride.participantUids
            )
            db.collection("rides").document(ride.id)
                .collection("messages").document()
                .set(msg.toCreateMap())
                .awaitOrQueueOffline()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun acceptProposal(
        ride: Ride,
        proposalMessageId: String,
        agreedAmount: Long,
        actorId: String,
        actorName: String
    ): Result<Unit> {
        val path = "rides/${ride.id}/messages/$proposalMessageId"
        return try {
            val uid = requireUserId()
            check(uid == actorId) { "Action non autorisée." }
            require(agreedAmount in 1L..10_000_000L) { "Montant invalide." }

            db.collection("rides").document(ride.id)
                .collection("messages").document(proposalMessageId)
                .update(mapOf("statut_proposition" to ProposalStatus.ACCEPTEE.name))
                .awaitOrQueueOffline()

            db.collection("rides").document(ride.id)
                .update(
                    mapOf(
                        "prix_actuel" to agreedAmount,
                        "dernier_proposeur_id" to uid,
                        "status" to RideStatus.ACCORD_TROUVE.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitOrQueueOffline()

            val sysMsg = Message(
                type = MessageType.SYSTEME.name,
                texte = "Accord trouvé à $agreedAmount FC ! En attente du paiement client.",
                id_expediteur = uid,
                nom_expediteur = actorName.trim().ifBlank { "Système" }.take(60),
                statut_proposition = ProposalStatus.EN_ATTENTE.name,
                participantUids = ride.participantUids
            )
            db.collection("rides").document(ride.id)
                .collection("messages").document()
                .set(sysMsg.toCreateMap())
                .awaitOrQueueOffline()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun refuseProposal(
        ride: Ride,
        proposalMessageId: String,
        actorId: String,
        actorName: String
    ): Result<Unit> {
        val path = "rides/${ride.id}/messages/$proposalMessageId"
        return try {
            val uid = requireUserId()
            check(uid == actorId) { "Action non autorisée." }

            db.collection("rides").document(ride.id)
                .collection("messages").document(proposalMessageId)
                .update(mapOf("statut_proposition" to ProposalStatus.REFUSEE.name))
                .awaitOrQueueOffline()

            val sysMsg = Message(
                type = MessageType.SYSTEME.name,
                texte = "${actorName.trim().ifBlank { "Un participant" }} a refusé l'offre. Faites une contre-proposition.",
                id_expediteur = uid,
                nom_expediteur = actorName.trim().ifBlank { "Système" }.take(60),
                statut_proposition = ProposalStatus.EN_ATTENTE.name,
                participantUids = ride.participantUids
            )
            db.collection("rides").document(ride.id)
                .collection("messages").document()
                .set(sysMsg.toCreateMap())
                .awaitOrQueueOffline()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun payRide(
        ride: Ride,
        clientUid: String,
        clientName: String,
        paymentMethod: String
    ): Result<Unit> {
        val path = "rides/${ride.id}"
        return try {
            val uid = requireUserId()
            check(uid == clientUid && uid == ride.id_client) {
                "Seul le client de cette course peut effectuer le paiement."
            }
            require(ride.status == RideStatus.ACCORD_TROUVE.name) {
                "Le paiement n'est disponible qu'après accord trouvé."
            }

            db.collection("rides").document(ride.id)
                .update(
                    mapOf(
                        "status" to RideStatus.EN_COURS.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitOrQueueOffline()

            if (ride.prix_actuel > 0) {
                db.collection("users").document(uid)
                    .update(
                        mapOf(
                            "totalDepenses" to FieldValue.increment(ride.prix_actuel),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).awaitOrQueueOffline()
            }

            val sysMsg = Message(
                type = MessageType.SYSTEME.name,
                texte = "Le client a payé. La course commence.",
                id_expediteur = uid,
                nom_expediteur = clientName.trim().ifBlank { "Client" }.take(60),
                statut_proposition = ProposalStatus.EN_ATTENTE.name,
                participantUids = ride.participantUids
            )
            db.collection("rides").document(ride.id)
                .collection("messages").document()
                .set(sysMsg.toCreateMap())
                .awaitOrQueueOffline()

            val detailMsg = Message(
                type = MessageType.SYSTEME.name,
                texte = "Paiement confirmé via $paymentMethod (${ride.prix_actuel} FC).",
                id_expediteur = uid,
                nom_expediteur = clientName.trim().ifBlank { "Client" }.take(60),
                statut_proposition = ProposalStatus.EN_ATTENTE.name,
                participantUids = ride.participantUids
            )
            db.collection("rides").document(ride.id)
                .collection("messages").document()
                .set(detailMsg.toCreateMap())
                .awaitOrQueueOffline()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }

    suspend fun completeRide(
        ride: Ride,
        currentUser: User
    ): Result<Unit> {
        val path = "rides/${ride.id}"
        return try {
            val uid = requireUserId()
            check(uid == currentUser.userId) { "Utilisateur non autorisé." }
            require(ride.status == RideStatus.EN_COURS.name) {
                "La course doit être en cours pour être terminée."
            }

            db.collection("rides").document(ride.id)
                .update(
                    mapOf(
                        "status" to RideStatus.TERMINEE.name,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).awaitOrQueueOffline()

            val statsUpdate = mutableMapOf<String, Any>(
                "completedRides" to FieldValue.increment(1L),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            if (currentUser.role == UserRole.MOTARD.name && ride.prix_actuel > 0) {
                statsUpdate["totalGains"] = FieldValue.increment(ride.prix_actuel)
            }
            db.collection("users").document(uid).update(statsUpdate).awaitOrQueueOffline()

            val sysMsg = Message(
                type = MessageType.SYSTEME.name,
                texte = "Course terminée. Merci d'avoir roulé avec Wewa !",
                id_expediteur = uid,
                nom_expediteur = currentUser.nom.trim().ifBlank { "Wewa" }.take(60),
                statut_proposition = ProposalStatus.EN_ATTENTE.name,
                participantUids = ride.participantUids
            )
            db.collection("rides").document(ride.id)
                .collection("messages").document()
                .set(sysMsg.toCreateMap())
                .awaitOrQueueOffline()

            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, path)
            Result.failure(e)
        }
    }
}
