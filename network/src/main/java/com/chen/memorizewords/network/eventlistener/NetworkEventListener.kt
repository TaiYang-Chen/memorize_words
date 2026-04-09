package com.chen.memorizewords.network.eventlistener

import android.util.Log
import com.chen.memorizewords.network.GlobalConfig
import okhttp3.Call
import okhttp3.EventListener
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Proxy
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkEventListener @Inject constructor() : EventListener() {

    private val TAG = "NetworkEventListener"

    override fun callStart(call: Call) {
        Log.d(TAG, "callStart: ${call.request().url}")
    }

    override fun dnsStart(call: Call, domainName: String) {
        Log.d(TAG, "dnsStart: $domainName")
    }

    override fun dnsEnd(call: Call, domainName: String, inetAddressList: List<InetAddress>) {
        Log.d(TAG, "dnsEnd: $domainName, addresses: ${inetAddressList.joinToString()}")
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        Log.d(TAG, "connectStart: $inetSocketAddress, proxy: $proxy")
    }

    override fun secureConnectStart(call: Call) {
        Log.d(TAG, "secureConnectStart")
    }

    override fun secureConnectEnd(call: Call, handshake: okhttp3.Handshake?) {
        Log.d(TAG, "secureConnectEnd: $handshake")
    }

    override fun connectEnd(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: okhttp3.Protocol?) {
        Log.d(TAG, "connectEnd: $inetSocketAddress, protocol: $protocol")
    }

    override fun connectFailed(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy, protocol: okhttp3.Protocol?, ioe: IOException) {
        Log.e(TAG, "connectFailed: $inetSocketAddress, protocol: $protocol", ioe)
    }

    override fun callEnd(call: Call) {
        Log.d(TAG, "callEnd: ${call.request().url}")
    }

    override fun callFailed(call: Call, ioe: IOException) {
        Log.e(TAG, "callFailed: ${call.request().url}", ioe)
    }

    class Factory @Inject constructor(private val listener: NetworkEventListener) : EventListener.Factory {
        override fun create(call: Call): EventListener {
            return if (GlobalConfig.isDebug) listener else EventListener.NONE
        }
    }
}
