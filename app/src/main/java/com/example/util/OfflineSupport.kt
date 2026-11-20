package com.example.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.example.R
import com.example.models.Message
import com.example.models.MotardPublic
import com.example.models.Ride
import com.example.models.User
import com.example.models.Withdrawal
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.persistentCacheSettings
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

object FirestoreProvider {
    @Volatile
    private var configuredDb: FirebaseFirestore? = null

    fun getDatabase(context: Context): FirebaseFirestore {
        val existing = configuredDb
        if (existing != null) return existing
        return synchronized(this) {
            configuredDb ?: run {
                val appContext = context.applicationContext
                val databaseId = appContext.getString(R.string.firestore_database_id)
                val instance = FirebaseFirestore.getInstance(databaseId)
                try {
                    val settings = firestoreSettings {
                        setLocalCacheSettings(
                            persistentCacheSettings {
                                setSizeBytes(FirebaseFirestoreSettings.CACHE_SIZE_UNLIMITED)
                            }
                        )
                    }
                    instance.firestoreSettings = settings
                } catch (e: Exception) {
                    Log.d("FirestoreProvider", "Firestore settings already initialized: ${e.message}")
                }
                configuredDb = instance
                instance
            }
        }
    }
}

fun isNetworkAvailable(context: Context): Boolean {
    return try {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val activeNetwork = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
        caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    } catch (_: Exception) {
        true
    }
}

fun observeNetworkConnectivity(context: Context): Flow<Boolean> = callbackFlow {
    val appContext = context.applicationContext
    val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
    if (cm == null) {
        trySend(true)
        awaitClose {}
        return@callbackFlow
    }

    trySend(isNetworkAvailable(appContext))

    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            trySend(isNetworkAvailable(appContext))
        }

        override fun onLost(network: Network) {
            trySend(isNetworkAvailable(appContext))
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            trySend(networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        }
    }

    val request = NetworkRequest.Builder()
        .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        .build()
    try {
        cm.registerNetworkCallback(request, callback)
    } catch (e: Exception) {
        Log.w("NetworkMonitor", "Impossible d'enregistrer NetworkCallback: ${e.message}")
        trySend(true)
    }

    awaitClose {
        try {
            cm.unregisterNetworkCallback(callback)
        } catch (_: Exception) {
        }
    }
}.distinctUntilChanged()

class WewaLocalCache(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveUser(user: User) {
        val obj = JSONObject().apply {
            put("userId", user.userId)
            put("role", user.role)
            put("phone", user.phone)
            put("nom", user.nom)
            put("motoModele", user.motoModele)
            put("plaque", user.plaque)
            put("note", user.note)
            put("totalGains", user.totalGains)
            put("totalDepenses", user.totalDepenses)
            put("totalRetraits", user.totalRetraits)
            put("completedRides", user.completedRides)
            put("enLigne", user.enLigne)
            put("createdAtMs", user.createdAt?.toDate()?.time ?: 0L)
            put("updatedAtMs", user.updatedAt?.toDate()?.time ?: 0L)
        }
        prefs.edit().putString("user_${user.userId}", obj.toString()).apply()
    }

    fun getUser(uid: String): User? {
        val raw = prefs.getString("user_$uid", null) ?: return null
        return try {
            val obj = JSONObject(raw)
            User(
                id = uid,
                userId = obj.optString("userId", uid),
                role = obj.optString("role", "CLIENT"),
                phone = obj.optString("phone", ""),
                nom = obj.optString("nom", ""),
                motoModele = obj.optString("motoModele", ""),
                plaque = obj.optString("plaque", ""),
                documentTexte = "",
                note = obj.optDouble("note", 0.0),
                totalGains = obj.optLong("totalGains", 0L),
                totalDepenses = obj.optLong("totalDepenses", 0L),
                totalRetraits = obj.optLong("totalRetraits", 0L),
                completedRides = obj.optLong("completedRides", 0L),
                enLigne = obj.optBoolean("enLigne", false),
                createdAt = millisToTimestamp(obj.optLong("createdAtMs", 0L)),
                updatedAt = millisToTimestamp(obj.optLong("updatedAtMs", 0L))
            )
        } catch (_: Exception) {
            null
        }
    }

    fun saveMotards(motards: List<MotardPublic>) {
        val arr = JSONArray()
        motards.forEach { m ->
            arr.put(
                JSONObject().apply {
                    put("id", m.id.ifBlank { m.motardId })
                    put("motardId", m.motardId)
                    put("nom", m.nom)
                    put("motoModele", m.motoModele)
                    put("plaque", m.plaque)
                    put("note", m.note)
                    put("distanceKm", m.distanceKm)
                    put("quartier", m.quartier)
                    put("enLigne", m.enLigne)
                    put("createdAtMs", m.createdAt?.toDate()?.time ?: 0L)
                    put("updatedAtMs", m.updatedAt?.toDate()?.time ?: 0L)
                }
            )
        }
        prefs.edit().putString("online_motards", arr.toString()).apply()
    }

    fun getMotards(): List<MotardPublic> {
        val raw = prefs.getString("online_motards", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        MotardPublic(
                            id = obj.optString("id", obj.optString("motardId", "")),
                            motardId = obj.optString("motardId", ""),
                            nom = obj.optString("nom", ""),
                            motoModele = obj.optString("motoModele", ""),
                            plaque = obj.optString("plaque", ""),
                            note = obj.optDouble("note", 0.0),
                            distanceKm = obj.optDouble("distanceKm", 0.0),
                            quartier = obj.optString("quartier", ""),
                            enLigne = obj.optBoolean("enLigne", true),
                            createdAt = millisToTimestamp(obj.optLong("createdAtMs", 0L)),
                            updatedAt = millisToTimestamp(obj.optLong("updatedAtMs", 0L))
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveRides(uid: String, rides: List<Ride>) {
        val arr = JSONArray()
        rides.forEach { r ->
            arr.put(rideToJson(r))
        }
        prefs.edit().putString("rides_$uid", arr.toString()).apply()
        rides.forEach { r ->
            if (r.id.isNotBlank()) {
                prefs.edit().putString("ride_${r.id}", rideToJson(r).toString()).apply()
            }
        }
    }

    fun saveSingleRide(ride: Ride) {
        if (ride.id.isNotBlank()) {
            prefs.edit().putString("ride_${ride.id}", rideToJson(ride).toString()).apply()
        }
    }

    fun getRides(uid: String): List<Ride> {
        val raw = prefs.getString("rides_$uid", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    add(jsonToRide(arr.getJSONObject(i)))
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getRide(rideId: String): Ride? {
        val raw = prefs.getString("ride_$rideId", null) ?: return null
        return try {
            jsonToRide(JSONObject(raw))
        } catch (_: Exception) {
            null
        }
    }

    fun saveMessages(rideId: String, messages: List<Message>) {
        val arr = JSONArray()
        messages.forEach { msg ->
            arr.put(
                JSONObject().apply {
                    put("id", msg.id)
                    put("type", msg.type)
                    put("texte", msg.texte)
                    if (msg.montant != null) put("montant", msg.montant)
                    put("id_expediteur", msg.id_expediteur)
                    put("nom_expediteur", msg.nom_expediteur)
                    put("statut_proposition", msg.statut_proposition)
                    put("participantUids", JSONArray(msg.participantUids))
                    put("createdAtMs", msg.createdAt?.toDate()?.time ?: 0L)
                }
            )
        }
        prefs.edit().putString("messages_$rideId", arr.toString()).apply()
    }

    fun getMessages(rideId: String): List<Message> {
        val raw = prefs.getString("messages_$rideId", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val pArr = obj.optJSONArray("participantUids")
                    val participants = buildList {
                        if (pArr != null) {
                            for (j in 0 until pArr.length()) {
                                add(pArr.getString(j))
                            }
                        }
                    }
                    add(
                        Message(
                            id = obj.optString("id", ""),
                            type = obj.optString("type", "TEXTE"),
                            texte = obj.optString("texte", ""),
                            montant = if (obj.has("montant")) obj.optLong("montant") else null,
                            id_expediteur = obj.optString("id_expediteur", ""),
                            nom_expediteur = obj.optString("nom_expediteur", ""),
                            statut_proposition = obj.optString("statut_proposition", "EN_ATTENTE"),
                            participantUids = participants,
                            createdAt = millisToTimestamp(obj.optLong("createdAtMs", 0L))
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun saveWithdrawals(uid: String, withdrawals: List<Withdrawal>) {
        val arr = JSONArray()
        withdrawals.forEach { w ->
            arr.put(
                JSONObject().apply {
                    put("id", w.id)
                    put("userId", w.userId)
                    put("operateur", w.operateur)
                    put("numero", w.numero)
                    put("montant", w.montant)
                    put("statut", w.statut)
                    put("createdAtMs", w.createdAt?.toDate()?.time ?: 0L)
                }
            )
        }
        prefs.edit().putString("withdrawals_$uid", arr.toString()).apply()
    }

    fun getWithdrawals(uid: String): List<Withdrawal> {
        val raw = prefs.getString("withdrawals_$uid", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList(arr.length()) {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        Withdrawal(
                            id = obj.optString("id", ""),
                            userId = obj.optString("userId", uid),
                            operateur = obj.optString("operateur", "M-Pesa"),
                            numero = obj.optString("numero", ""),
                            montant = obj.optLong("montant", 0L),
                            statut = obj.optString("statut", "EFFECTUE"),
                            createdAt = millisToTimestamp(obj.optLong("createdAtMs", 0L))
                        )
                    )
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun rideToJson(r: Ride): JSONObject = JSONObject().apply {
        put("id", r.id)
        put("id_client", r.id_client)
        put("id_motard", r.id_motard)
        put("nom_client", r.nom_client)
        put("nom_motard", r.nom_motard)
        put("moto_motard", r.moto_motard)
        put("depart", r.depart)
        put("destination", r.destination)
        put("status", r.status)
        put("prix_actuel", r.prix_actuel)
        put("dernier_proposeur_id", r.dernier_proposeur_id)
        put("participantUids", JSONArray(r.participantUids))
        put("createdAtMs", r.createdAt?.toDate()?.time ?: 0L)
        put("updatedAtMs", r.updatedAt?.toDate()?.time ?: 0L)
    }

    private fun jsonToRide(obj: JSONObject): Ride {
        val pArr = obj.optJSONArray("participantUids")
        val participants = buildList {
            if (pArr != null) {
                for (j in 0 until pArr.length()) {
                    add(pArr.getString(j))
                }
            }
        }
        return Ride(
            id = obj.optString("id", ""),
            id_client = obj.optString("id_client", ""),
            id_motard = obj.optString("id_motard", ""),
            nom_client = obj.optString("nom_client", ""),
            nom_motard = obj.optString("nom_motard", ""),
            moto_motard = obj.optString("moto_motard", ""),
            depart = obj.optString("depart", ""),
            destination = obj.optString("destination", ""),
            status = obj.optString("status", "EN_NEGOCIATION"),
            prix_actuel = obj.optLong("prix_actuel", 0L),
            dernier_proposeur_id = obj.optString("dernier_proposeur_id", ""),
            participantUids = participants,
            createdAt = millisToTimestamp(obj.optLong("createdAtMs", 0L)),
            updatedAt = millisToTimestamp(obj.optLong("updatedAtMs", 0L))
        )
    }

    private fun millisToTimestamp(ms: Long): Timestamp? {
        return if (ms > 0L) Timestamp(Date(ms)) else null
    }

    private companion object {
        const val PREFS_NAME = "wewa_offline_cache"
    }
}
