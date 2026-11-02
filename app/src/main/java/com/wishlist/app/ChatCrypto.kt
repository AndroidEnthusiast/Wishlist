package com.wishlist.app

import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption of messages (2.98, owner: "No one should feel their messages can be read by me").
 *
 * Each phone has its own key pair (elliptic curve P-256). The public half goes to the server; the private half never
 * leaves the phone (and is not in the backup file). Two friends' phones each work out the same secret from their own
 * private key and the other's public key (ECDH); from it, and both profile ids, comes a message key (HKDF-SHA256).
 * Every message is locked with AES-256-GCM and a fresh random 12-byte nonce; the ids of sender and receiver are bound
 * to it, so a message can't be passed off as another conversation's. The server only stores the locked text.
 *
 * Only standard Java crypto (available on every Android version the app runs on).
 */
object ChatCrypto {
    private val rnd = SecureRandom()
    private fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)

    /** A new key pair: (private, public), both as text. */
    fun newKeyPair(): Pair<String, String> {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"), rnd)
        val kp = g.generateKeyPair()
        return b64(kp.private.encoded) to b64(kp.public.encoded)
    }

    private fun priv(s: String): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(unb64(s)))
    private fun pub(s: String): PublicKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(unb64(s)))

    private fun hmac(key: ByteArray, data: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(data) }

    /**
     * The message key between [myPrivate] and [theirPublic], for the conversation of profiles [idA] and [idB] (in any
     * order: both phones get the same key).
     */
    fun sharedKey(myPrivate: String, theirPublic: String, idA: String, idB: String): ByteArray {
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(priv(myPrivate))
        ka.doPhase(pub(theirPublic), true)
        val secret = ka.generateSecret()
        // HKDF-SHA256 (extract with the sorted ids as salt, expand to one 32-byte block).
        val salt = listOf(idA, idB).sorted().joinToString("|").toByteArray()
        val prk = hmac(salt, secret)
        return hmac(prk, "wishlist-chat-v1".toByteArray() + byteArrayOf(1))
    }

    private fun aad(from: String, to: String) = ("$from>$to").toByteArray()

    /** Locks [plain] from profile [from] to profile [to]: base64 of nonce + ciphertext (with its tag). */
    fun lock(key: ByteArray, plain: String, from: String, to: String): String {
        val nonce = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(aad(from, to))
        return b64(nonce + c.doFinal(plain.toByteArray(Charsets.UTF_8)))
    }

    /** Unlocks a message, or null when it can't be read with this key (another phone's key, or damaged). */
    fun unlock(key: ByteArray, body: String, from: String, to: String): String? = try {
        val all = unb64(body)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, all, 0, 12))
        c.updateAAD(aad(from, to))
        String(c.doFinal(all, 12, all.size - 12), Charsets.UTF_8)
    } catch (e: Exception) {
        null
    }
}
