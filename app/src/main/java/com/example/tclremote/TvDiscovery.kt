package com.example.tclremote

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Handler
import android.os.Looper

data class TvDevice(val name: String, val host: String)

/** Trouve les TV Google TV / Android TV qui s'annoncent sur le Wi-Fi (mDNS). */
class TvDiscovery(context: Context, private val onChange: (List<TvDevice>) -> Unit) {
    private val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val main = Handler(Looper.getMainLooper())
    private val devices = linkedMapOf<String, TvDevice>()
    private val queue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var listener: NsdManager.DiscoveryListener? = null

    fun start() {
        if (listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onServiceFound(info: NsdServiceInfo) {
                main.post { queue.add(info); resolveNext() }
            }
            override fun onServiceLost(info: NsdServiceInfo) {
                main.post { devices.remove(info.serviceName); publish() }
            }
            override fun onDiscoveryStarted(serviceType: String) {}
            override fun onDiscoveryStopped(serviceType: String) {}
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) { listener = null }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
        }
        listener = l
        nsd.discoverServices("_androidtvremote2._tcp", NsdManager.PROTOCOL_DNS_SD, l)
    }

    fun stop() {
        listener?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        listener = null
    }

    // Android ne résout qu'un service à la fois : on fait la queue.
    @Suppress("DEPRECATION")
    private fun resolveNext() {
        if (resolving) return
        val info = queue.removeFirstOrNull() ?: return
        resolving = true
        nsd.resolveService(info, object : NsdManager.ResolveListener {
            override fun onServiceResolved(r: NsdServiceInfo) {
                main.post {
                    r.host?.hostAddress?.let { devices[r.serviceName] = TvDevice(r.serviceName, it) }
                    publish()
                    resolving = false
                    resolveNext()
                }
            }
            override fun onResolveFailed(i: NsdServiceInfo, errorCode: Int) {
                main.post { resolving = false; resolveNext() }
            }
        })
    }

    private fun publish() = onChange(devices.values.toList())
}
