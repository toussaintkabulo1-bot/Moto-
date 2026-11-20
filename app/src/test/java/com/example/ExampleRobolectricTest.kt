package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.models.Message
import com.example.models.MotardPublic
import com.example.models.Ride
import com.example.models.User
import com.example.models.UserRole
import com.example.models.Withdrawal
import com.example.util.WewaLocalCache
import com.example.util.isValidE164Phone
import com.example.util.normalizePhoneE164
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Wewa", appName)
    }

    @Test
    fun `phone normalization and E164 validation work properly`() {
        val normalized = normalizePhoneE164(" +243 812-345 678 ")
        assertEquals("+243812345678", normalized)
        assertTrue(isValidE164Phone(normalized))
        assertTrue(isValidE164Phone(normalizePhoneE164("00243812345678")))
        assertFalse(isValidE164Phone("0812345678"))
        assertFalse(isValidE164Phone("+123"))
    }

    @Test
    fun `offline local cache persists and restores all pages data`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val cache = WewaLocalCache(context)

        val user = User(
            userId = "u1",
            role = UserRole.MOTARD.name,
            phone = "+243812345678",
            nom = "Utilisateur Test",
            motoModele = "TVS 125",
            plaque = "KN-001",
            totalGains = 15000L,
            totalRetraits = 5000L,
            completedRides = 3L
        )
        cache.saveUser(user)
        val restoredUser = cache.getUser("u1")
        assertNotNull(restoredUser)
        assertEquals("Utilisateur Test", restoredUser?.nom)
        assertEquals(15000L, restoredUser?.totalGains)

        val motards = listOf(MotardPublic(id = "m1", motardId = "m1", nom = "Conducteur 1", motoModele = "Boxer"))
        cache.saveMotards(motards)
        assertEquals(1, cache.getMotards().size)

        val rides = listOf(
            Ride(
                id = "r1",
                id_client = "c1",
                id_motard = "u1",
                nom_client = "Client A",
                nom_motard = "Utilisateur Test",
                participantUids = listOf("c1", "u1")
            )
        )
        cache.saveRides("u1", rides)
        assertEquals(1, cache.getRides("u1").size)
        assertEquals("Client A", cache.getRide("r1")?.nom_client)

        val messages = listOf(Message(id = "msg1", texte = "Bonjour", id_expediteur = "c1", nom_expediteur = "Client A"))
        cache.saveMessages("r1", messages)
        assertEquals("Bonjour", cache.getMessages("r1").first().texte)

        val withdrawals = listOf(Withdrawal(id = "w1", userId = "u1", operateur = "M-Pesa", numero = "+243812345678", montant = 5000L))
        cache.saveWithdrawals("u1", withdrawals)
        assertEquals(5000L, cache.getWithdrawals("u1").first().montant)
    }
}
