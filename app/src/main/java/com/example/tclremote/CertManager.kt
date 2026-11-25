package com.example.tclremote

import android.content.Context
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.net.InetSocketAddress
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager

/**
 * La TV identifie la télécommande par son certificat TLS.
 * On le génère une seule fois et on le garde dans les fichiers de l'app.
 */
object CertManager {
    private const val FILE = "client.p12"
    private const val ALIAS = "client"
    private val PASS = "atvremote".toCharArray()

    fun keyStore(context: Context): KeyStore {
        val file = File(context.filesDir, FILE)
        val ks = KeyStore.getInstance("PKCS12")
        if (file.exists()) {
            file.inputStream().use { ks.load(it, PASS) }
            return ks
        }
        val kp = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
        val name = X500Name("CN=atvremote, O=TclRemote")
        val now = Date()
        val tenYears = Date(now.time + 3650L * 24 * 3600 * 1000)
        val bc = BouncyCastleProvider()
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider(bc).build(kp.private)
        val holder = JcaX509v3CertificateBuilder(name, BigInteger.valueOf(now.time), now, tenYears, name, kp.public)
            .build(signer)
        val cert = JcaX509CertificateConverter().setProvider(bc).getCertificate(holder)

        ks.load(null, PASS)
        ks.setKeyEntry(ALIAS, kp.private, PASS, arrayOf(cert))
        file.outputStream().use { ks.store(it, PASS) }
        return ks
    }

    fun clientPublicKey(context: Context) =
        keyStore(context).getCertificate(ALIAS).publicKey as RSAPublicKey

    fun connect(context: Context, host: String, port: Int): SSLSocket {
        val kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
        kmf.init(keyStore(context), PASS)

        // La TV utilise un certificat auto-signé : on l'accepte (réseau local uniquement).
        val trustTv = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
            override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
        }
        val ctx = SSLContext.getInstance("TLS").apply { init(kmf.keyManagers, arrayOf(trustTv), null) }

        val socket = ctx.socketFactory.createSocket() as SSLSocket
        socket.connect(InetSocketAddress(host, port), 5000)
        socket.startHandshake()
        return socket
    }
}
