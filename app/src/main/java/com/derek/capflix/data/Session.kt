package com.derek.capflix.data

import android.content.Context
import android.os.Build
import java.net.URI
import java.util.UUID

/**
 * Who is signed in, to which server, plus the app's settings.
 * Only the server's access token is stored, never the password.
 */
class Session(context: Context) {
    private val prefs = context.getSharedPreferences("capflix", Context.MODE_PRIVATE)

    var server: String
        get() = prefs.getString("server", "") ?: ""
        set(v) = prefs.edit().putString("server", v.trimEnd('/')).apply()

    var userId: String
        get() = prefs.getString("userId", "") ?: ""
        set(v) = prefs.edit().putString("userId", v).apply()

    var userName: String
        get() = prefs.getString("userName", "") ?: ""
        set(v) = prefs.edit().putString("userName", v).apply()

    var token: String
        get() = prefs.getString("token", "") ?: ""
        set(v) = prefs.edit().putString("token", v).apply()

    // Read from both the OkHttp thread and main: synchronized so only one ID is ever generated.
    val deviceId: String
        @Synchronized get() = prefs.getString("deviceId", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("deviceId", it).apply()
        }

    val deviceName: String = (Build.MANUFACTURER.replaceFirstChar { it.uppercase() } + " " + Build.MODEL).trim()

    val isSignedIn: Boolean get() = server.isNotBlank() && token.isNotBlank() && userId.isNotBlank()

    /** Max streaming bitrate in bits/s. 0 = Auto, Long.MAX_VALUE = always original file. */
    var quality: Long
        get() = prefs.getLong("quality", 0L)
        set(v) = prefs.edit().putLong("quality", v).apply()

    /** "eng", "jpn" or "" (whatever the file marks as default). */
    var audioLanguage: String
        get() = prefs.getString("audioLang", "eng") ?: "eng"
        set(v) = prefs.edit().putString("audioLang", v).apply()

    /** "off", "eng" (English when there is one), "forced" (forced/foreign parts only). */
    var subtitleMode: String
        get() = prefs.getString("subMode", "forced") ?: "forced"
        set(v) = prefs.edit().putString("subMode", v).apply()

    var autoplayNext: Boolean
        get() = prefs.getBoolean("autoplayNext", true)
        set(v) = prefs.edit().putBoolean("autoplayNext", v).apply()

    var autoSkipIntro: Boolean
        get() = prefs.getBoolean("autoSkipIntro", false)
        set(v) = prefs.edit().putBoolean("autoSkipIntro", v).apply()

    fun authHeader(): String {
        val base = "MediaBrowser Client=\"Cap Flix\", Device=\"${deviceName.replace("\"", "")}\", " +
            "DeviceId=\"$deviceId\", Version=\"1.0\""
        return if (token.isNotBlank()) "$base, Token=\"$token\"" else base
    }

    fun signOut() {
        prefs.edit().remove("token").remove("userId").remove("userName").apply()
    }

    companion object {
        /**
         * Plain http is only allowed where the network itself is private:
         * Tailscale (100.64.0.0/10 or *.ts.net or a bare machine name), the home
         * network, or this phone. Anything else must be https.
         */
        fun isSafeServer(url: String): Boolean {
            val u = runCatching { URI(url.trim()) }.getOrNull() ?: return false
            val host = u.host ?: return false
            if (u.scheme == "https") return true
            if (u.scheme != "http") return false
            if (host == "localhost" || host.endsWith(".ts.net") || !host.contains('.')) return true
            val p = host.split('.').mapNotNull { it.toIntOrNull() }
            if (p.size != 4) return false
            return p[0] == 10 ||
                (p[0] == 192 && p[1] == 168) ||
                (p[0] == 172 && p[1] in 16..31) ||
                (p[0] == 100 && p[1] in 64..127) ||
                p[0] == 127
        }

        fun normalize(input: String): String {
            var s = input.trim().trimEnd('/')
            if (!s.startsWith("http://") && !s.startsWith("https://")) s = "http://$s"
            val u = runCatching { URI(s) }.getOrNull()
            if (u != null && u.port == -1 && u.scheme == "http") s = "$s:8096"
            return s
        }
    }
}
