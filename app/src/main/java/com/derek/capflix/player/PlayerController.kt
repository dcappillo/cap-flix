package com.derek.capflix.player

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaCodecList
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.derek.capflix.CapFlixApp
import com.derek.capflix.data.Item
import com.derek.capflix.data.MediaSource
import com.derek.capflix.data.MediaStream
import com.derek.capflix.data.PlaybackReport
import com.derek.capflix.data.Segment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import org.videolan.libvlc.util.VLCVideoLayout
import java.util.UUID

/** One entry in the audio or subtitle menu. */
data class TrackOption(val label: String, val stream: MediaStream?, val vlcId: Int?)

enum class Scale(val label: String, val vlc: MediaPlayer.ScaleType) {
    Fit("Fit", MediaPlayer.ScaleType.SURFACE_BEST_FIT),
    Fill("Fill screen", MediaPlayer.ScaleType.SURFACE_FILL),
    Crop("Crop", MediaPlayer.ScaleType.SURFACE_FIT_SCREEN),
}

/**
 * Everything about playing one title: picking direct play vs. transcode,
 * driving libVLC, choosing audio/subtitle tracks the way Derek likes, intro
 * skipping, next episode, and telling Jellyfin where we are (which is what
 * feeds Continue Watching and "watched").
 */
class PlayerController(private val context: Context) {
    private val api = CapFlixApp.api
    private val session = CapFlixApp.session
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val libVlc = LibVLC(context, arrayListOf(
        "--http-reconnect",
        "--audio-time-stretch",        // keeps pitch right at 1.25x / 1.5x
        "--avcodec-skiploopfilter=1",  // cheaper decoding when software decode is needed
        "--no-stats",
    ))
    val player = MediaPlayer(libVlc)

    // ---- UI state ----
    var item by mutableStateOf<Item?>(null)
        private set
    var source by mutableStateOf<MediaSource?>(null)
        private set
    var title by mutableStateOf("")
        private set
    var subtitle by mutableStateOf("")
        private set
    var playing by mutableStateOf(false)
        private set
    var buffering by mutableStateOf(true)
        private set
    var timeMs by mutableLongStateOf(0L)
        private set
    var lengthMs by mutableLongStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var transcoding by mutableStateOf(false)
        private set
    var modeNote by mutableStateOf("")
        private set
    var audioOptions by mutableStateOf<List<TrackOption>>(emptyList())
        private set
    var subOptions by mutableStateOf<List<TrackOption>>(emptyList())
        private set
    var audioSel by mutableIntStateOf(0)
        private set
    var subSel by mutableIntStateOf(-1)   // -1 = off
        private set
    var segments by mutableStateOf<List<Segment>>(emptyList())
        private set
    var next by mutableStateOf<Item?>(null)
        private set
    var showNextCard by mutableStateOf(false)
        private set
    var countdown by mutableIntStateOf(0)
        private set
    var scale by mutableStateOf(Scale.Fit)
        private set
    var rate by mutableFloatStateOf(1f)
        private set
    var finished by mutableStateOf(false)
        private set

    var quality: Long = session.quality
        private set
    private var playSessionId = UUID.randomUUID().toString()
    private var audioStreamIndex: Int? = null
    private var burnSubIndex: Int? = null
    private var tracksApplied = false
    private var triedFallback = false
    private var creditsDismissed = false
    private var reportJob: Job? = null
    private var countdownJob: Job? = null
    private var introSkipped = false
    private var layout: VLCVideoLayout? = null

    init {
        player.setEventListener { e ->
            when (e.type) {
                MediaPlayer.Event.Playing -> { playing = true; buffering = false; error = null; onPlaying() }
                MediaPlayer.Event.Paused -> { playing = false; report("progress") }
                MediaPlayer.Event.Stopped -> playing = false
                MediaPlayer.Event.Buffering -> buffering = e.buffering < 100f
                MediaPlayer.Event.TimeChanged -> { timeMs = e.timeChanged; onTick() }
                MediaPlayer.Event.LengthChanged -> if (e.lengthChanged > 0) lengthMs = e.lengthChanged
                MediaPlayer.Event.ESAdded, MediaPlayer.Event.ESDeleted -> refreshTracks()
                MediaPlayer.Event.EndReached -> onEnded()
                MediaPlayer.Event.EncounteredError -> onError()
            }
        }
    }

    /** Re-bind VLC to the video view after the window changes size (PiP in/out). */
    fun reattach() {
        val v = layout ?: return
        runCatching { player.detachViews() }
        player.attachViews(v, null, true, false)
        player.setVideoScale(scale.vlc)
    }

    fun attach(view: VLCVideoLayout) {
        layout = view
        player.attachViews(view, null, true, false)
        player.setVideoScale(scale.vlc)
    }

    // ---- loading -------------------------------------------------------------

    fun open(itemId: String, mediaSourceId: String?) {
        scope.launch {
            try {
                var it = api.item(itemId)
                if (it.isSeries) { // "Play" on a show: next up, else the first episode
                    val ep = api.nextUp(1, seriesId = it.id).firstOrNull()
                        ?: api.resume(50).firstOrNull { r -> r.seriesId == itemId }
                        ?: api.episodes(it.id).firstOrNull()
                        ?: throw IllegalStateException("This show has no episodes yet.")
                    it = api.item(ep.id)
                }
                start(it, mediaSourceId, resumeTicks = it.userData?.positionTicks ?: 0L)
            } catch (e: Exception) {
                error = e.message ?: "Couldn't load this title."
                buffering = false
            }
        }
    }

    private suspend fun start(it: Item, mediaSourceId: String?, resumeTicks: Long) {
        item = it
        val ms = it.mediaSources.firstOrNull { s -> s.id == mediaSourceId } ?: it.mediaSources.firstOrNull()
            ?: throw IllegalStateException("Jellyfin has no playable file for this title.")
        source = ms
        title = if (it.isEpisode) it.seriesName ?: it.name else it.name
        subtitle = if (it.isEpisode) "${it.episodeCode}  ${it.name}" else listOfNotNull(it.year?.toString(), it.officialRating).joinToString(" · ")
        tracksApplied = false; introSkipped = false; creditsDismissed = false; showNextCard = false; finished = false
        audioStreamIndex = null; burnSubIndex = null; triedFallback = false; wantAudio = null; wantSub = null
        next = null; segments = emptyList()
        playSessionId = UUID.randomUUID().toString()
        val len = it.runTimeTicks ?: 0L
        val startMs = if (resumeTicks > 0 && (len == 0L || resumeTicks < len * 0.95)) resumeTicks / 10_000 else 0L
        load(startMs)
        report("start")
        startReporting()
        scope.launch { segments = api.segments(it.id) }
        if (it.isEpisode) scope.launch { next = runCatching { api.nextEpisode(it) }.getOrNull() }
    }

    /** Decide direct play vs transcode and hand the URL to VLC. */
    private fun load(startMs: Long) {
        val it = item ?: return
        val ms = source ?: return
        val dv = needsDolbyVisionFix(ms)
        val cap = effectiveCap()
        val direct = !dv && (cap == Long.MAX_VALUE || (ms.bitrate != null && ms.bitrate <= cap))
        transcoding = !direct
        if (!direct && audioStreamIndex == null) {
            // The server picks the file's default track (often Italian on ITA/ENG releases); ask for ours.
            val pref = session.audioLanguage
            audioStreamIndex = audioStreams(ms).firstOrNull { pref.isNotEmpty() && lang(it) == pref }?.index
        }
        val url = if (direct) {
            modeNote = "Original file"
            api.directUrl(it.id, ms.id, ms.container)
        } else {
            val br = if (dv && cap == Long.MAX_VALUE) 40_000_000L else cap
            modeNote = (if (dv) "Converted from Dolby Vision · " else "") + "${br / 1_000_000} Mbps"
            api.transcodeUrl(it.id, ms.id, playSessionId, br, audioStreamIndex, burnSubIndex)
        }

        val media = Media(libVlc, Uri.parse(url))
        media.setHWDecoderEnabled(true, false)
        media.addOption(":network-caching=" + if (direct) 1500 else 3000)
        if (startMs > 0) media.addOption(":start-time=" + (startMs / 1000.0))
        if (direct && session.audioLanguage.isNotEmpty()) media.addOption(":audio-language=" + session.audioLanguage)
        player.media = media
        media.release()

        // Subtitles VLC can't see inside the stream: external .srt files, and
        // (when transcoding) the text subtitles inside the original file.
        subStreams(ms).forEach { s ->
            if (s.isExternal || (!direct && s.isText)) {
                val fmt = when (s.codec?.lowercase()) { "ass", "ssa" -> "ass"; "vtt", "webvtt" -> "vtt"; else -> "srt" }
                player.addSlave(IMedia.Slave.Type.Subtitle, Uri.parse(api.subtitleUrl(it.id, ms.id, s.index, fmt)), false)
            }
        }
        player.play()
        buffering = true
    }

    private fun effectiveCap(): Long {
        if (quality != 0L) return quality
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val caps = cm?.getNetworkCapabilities(cm.activeNetwork)
        val unmetered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true &&
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        return if (unmetered) Long.MAX_VALUE else 8_000_000L
    }

    /** Dolby Vision profile 5 has no normal-color layer: without DV support the picture is purple/green. */
    private fun needsDolbyVisionFix(ms: MediaSource): Boolean {
        val range = ms.streams.firstOrNull { it.type == "Video" }?.videoRangeType?.uppercase() ?: return false
        if (range != "DOVI") return false   // DOVIWithHDR10 etc. fall back fine
        return !deviceHasDolbyVision
    }

    private val deviceHasDolbyVision: Boolean by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.equals("video/dolby-vision", true) }
            }
        }.getOrDefault(false)
    }

    // ---- tracks --------------------------------------------------------------

    private fun audioStreams(ms: MediaSource) = ms.streams.filter { it.type == "Audio" }.sortedBy { it.index }
    private fun subStreams(ms: MediaSource): List<MediaStream> {
        val subs = ms.streams.filter { it.type == "Subtitle" }
        return subs.filter { !it.isExternal }.sortedBy { it.index } + subs.filter { it.isExternal }.sortedBy { it.index }
    }

    private fun refreshTracks() {
        val ms = source ?: return
        val vlcAudio = player.audioTracks?.filter { it.id != -1 } ?: emptyList()
        val vlcSubs = player.spuTracks?.filter { it.id != -1 } ?: emptyList()
        val jAudio = audioStreams(ms)

        audioOptions = if (transcoding) {
            jAudio.map { TrackOption(it.displayTitle ?: it.language ?: "Track ${it.index}", it, null) }
        } else if (jAudio.size == vlcAudio.size) {
            jAudio.zip(vlcAudio).map { (j, v) -> TrackOption(j.displayTitle ?: v.name, j, v.id) }
        } else vlcAudio.map { TrackOption(it.name, null, it.id) }

        val jSubs = subStreams(ms).let { all -> if (transcoding) all.filter { it.isText || it.isExternal } else all }
        val textOpts = if (jSubs.size == vlcSubs.size) {
            jSubs.zip(vlcSubs).map { (j, v) -> TrackOption(j.displayTitle ?: v.name, j, v.id) }
        } else vlcSubs.map { TrackOption(it.name, null, it.id) }
        // Picture subtitles (PGS/VobSub) can't ride along a transcode; offer them burned in.
        val burnOpts = if (transcoding) subStreams(ms).filter { !it.isText && !it.isExternal }
            .map { TrackOption((it.displayTitle ?: "Subtitle") + " (burned in)", it, null) } else emptyList()
        subOptions = textOpts + burnOpts

        val curA = player.audioTrack
        audioSel = if (transcoding) audioOptions.indexOfFirst { it.stream?.index == audioStreamIndex }.coerceAtLeast(0)
        else audioOptions.indexOfFirst { it.vlcId == curA }.coerceAtLeast(0)
        val curS = player.spuTrack
        subSel = if (burnSubIndex != null) subOptions.indexOfFirst { it.stream?.index == burnSubIndex }
        else subOptions.indexOfFirst { it.vlcId == curS && curS != -1 }
    }

    // What the viewer picked, by Jellyfin stream index, so a reload (new
    // quality, burned-in subtitle) comes back with the same tracks.
    private var wantAudio: Int? = null
    private var wantSub: Int? = null   // -1 = off, null = no choice yet

    private fun onPlaying() {
        refreshTracks()
        requestAudioFocus()
        // Embedded tracks and subtitle files arrive a moment after playback starts.
        scope.launch {
            delay(700)
            refreshTracks()
            if (!tracksApplied) { applyPreferences(); tracksApplied = true } else restoreSelections()
        }
    }

    private fun restoreSelections() {
        if (!transcoding) wantAudio?.let { w ->
            val i = audioOptions.indexOfFirst { it.stream?.index == w }
            if (i >= 0 && i != audioSel) audioOptions[i].vlcId?.let { id -> player.setAudioTrack(id); audioSel = i }
        }
        when (val w = wantSub) {
            null -> {}
            -1 -> if (burnSubIndex == null) { player.setSpuTrack(-1); subSel = -1 }
            else -> if (burnSubIndex == null) {
                val i = subOptions.indexOfFirst { it.stream?.index == w && it.vlcId != null }
                if (i >= 0) { player.setSpuTrack(subOptions[i].vlcId!!); subSel = i }
            }
        }
    }

    private fun lang(s: MediaStream?) = (s?.language ?: "").lowercase()
    private fun isEnglish(o: TrackOption) = lang(o.stream) in setOf("eng", "en") || o.label.contains("english", true)

    private fun applyPreferences() {
        val pref = session.audioLanguage
        if (pref.isNotEmpty() && !transcoding) {
            val want = audioOptions.indexOfFirst { o ->
                lang(o.stream) == pref || (pref == "eng" && o.label.contains("english", true)) ||
                    (pref == "jpn" && o.label.contains("japanese", true))
            }
            if (want >= 0 && want != audioSel) selectAudio(want)
        }
        val audioLang = lang(audioOptions.getOrNull(audioSel)?.stream)
        val foreignAudio = audioLang.isNotEmpty() && audioLang !in setOf("eng", "en", "und")
        val texts = subOptions.filter { it.vlcId != null }
        val choice = when (session.subtitleMode) {
            "off" -> null
            "eng" -> texts.firstOrNull { isEnglish(it) && it.stream?.isForced != true && !it.label.contains("forced", true) }
                ?: texts.firstOrNull { isEnglish(it) }
            else -> // forced parts only, unless the whole audio is in another language
                if (foreignAudio) texts.firstOrNull { isEnglish(it) && it.stream?.isForced != true } ?: texts.firstOrNull { isEnglish(it) }
                else texts.firstOrNull { isEnglish(it) && (it.stream?.isForced == true || it.label.contains("forced", true)) }
        }
        val idx = choice?.let { subOptions.indexOf(it) } ?: -1
        selectSubtitle(idx)
    }

    fun selectAudio(i: Int) {
        val o = audioOptions.getOrNull(i) ?: return
        wantAudio = o.stream?.index
        if (transcoding) {
            audioStreamIndex = o.stream?.index
            reload()
        } else {
            o.vlcId?.let { player.setAudioTrack(it) }
            audioSel = i
        }
    }

    fun selectSubtitle(i: Int) {
        wantSub = if (i < 0) -1 else subOptions.getOrNull(i)?.stream?.index
        if (i < 0) {
            player.setSpuTrack(-1)
            if (burnSubIndex != null) { burnSubIndex = null; reload() }
            subSel = -1
            return
        }
        val o = subOptions.getOrNull(i) ?: return
        if (o.vlcId != null) {
            if (burnSubIndex != null) { burnSubIndex = null; reload() }
            player.setSpuTrack(o.vlcId)
            subSel = i
        } else if (transcoding && o.stream != null) {
            burnSubIndex = o.stream.index
            subSel = i
            reload()
        }
    }

    /** Restart the stream at the same spot (new quality, audio track or burned-in subtitle). */
    private fun reload() {
        val at = player.time.coerceAtLeast(timeMs)
        scope.launch {
            if (transcoding) api.stopTranscode(playSessionId)
            playSessionId = UUID.randomUUID().toString()
            load(at)
            report("start")
        }
    }

    fun setQuality(q: Long) { quality = q; reload() }

    // ---- transport -----------------------------------------------------------

    fun togglePlay() { if (player.isPlaying) player.pause() else { player.play(); requestAudioFocus() } }
    fun pause() { if (player.isPlaying) player.pause() }
    fun seekTo(ms: Long) {
        val t = ms.coerceIn(0, if (lengthMs > 0) lengthMs - 1000 else Long.MAX_VALUE)
        player.setTime(t)
        timeMs = t
    }
    fun seekBy(deltaMs: Long) = seekTo(player.time + deltaMs)
    fun cycleScale() { scale = Scale.entries[(scale.ordinal + 1) % Scale.entries.size]; player.setVideoScale(scale.vlc) }
    fun setSpeed(r: Float) { rate = r; player.setRate(r) }

    // ---- intro / credits / next ---------------------------------------------

    private fun segment(type: String) = segments.firstOrNull { it.type.equals(type, true) }

    val introActive: Boolean
        get() {
            val s = segment("Intro") ?: return false
            val t = timeMs * 10_000
            return t >= s.startTicks && t < s.endTicks - 20_000_000
        }

    fun skipIntro() { segment("Intro")?.let { introSkipped = true; seekTo(it.endTicks / 10_000) } }

    private fun onTick() {
        if (session.autoSkipIntro && !introSkipped && introActive) skipIntro()
        val n = next ?: return
        if (creditsDismissed || lengthMs <= 0) return
        val outro = segment("Outro")
        val creditsAt = outro?.startTicks?.div(10_000) ?: (lengthMs - 30_000)
        if (timeMs >= creditsAt && !showNextCard) {
            showNextCard = true
            if (session.autoplayNext) startCountdown(n)
        }
    }

    private fun startCountdown(n: Item) {
        countdownJob?.cancel()
        countdownJob = scope.launch {
            for (i in 10 downTo 1) { countdown = i; delay(1000) }
            playNext(n)
        }
    }

    fun dismissNext() { creditsDismissed = true; showNextCard = false; countdownJob?.cancel(); countdown = 0 }

    fun playNext(n: Item? = next) {
        n ?: return
        countdownJob?.cancel(); countdown = 0
        scope.launch {
            report("stop")
            if (transcoding) api.stopTranscode(playSessionId)
            val full = runCatching { api.item(n.id) }.getOrNull() ?: return@launch
            start(full, null, resumeTicks = 0L)
        }
    }

    private fun onEnded() {
        playing = false
        val n = next
        if (n != null && session.autoplayNext && !creditsDismissed) playNext(n)
        else { report("stop"); finished = true }
    }

    private fun onError() {
        buffering = false
        if (!transcoding && !triedFallback) {
            // The original file wouldn't stream: let the server convert it instead.
            triedFallback = true
            quality = 20_000_000L
            reload()
        } else {
            error = "This video couldn't be played. Check that the PC is awake and Jellyfin is running."
        }
    }

    // ---- reporting -----------------------------------------------------------

    private fun reportBody(): PlaybackReport? {
        val it = item ?: return null
        val ms = source ?: return null
        return PlaybackReport(
            itemId = it.id, mediaSourceId = ms.id, playSessionId = playSessionId,
            positionTicks = timeMs * 10_000, isPaused = !playing,
            playMethod = if (transcoding) "Transcode" else "DirectStream",
            audioStreamIndex = audioOptions.getOrNull(audioSel)?.stream?.index,
            subtitleStreamIndex = subOptions.getOrNull(subSel)?.stream?.index,
        )
    }

    private fun report(kind: String) {
        val body = reportBody() ?: return
        scope.launch {
            withContext(NonCancellable) {
                when (kind) {
                    "start" -> api.reportStart(body)
                    "progress" -> api.reportProgress(body)
                    else -> api.reportStop(body)
                }
            }
        }
    }

    private fun startReporting() {
        reportJob?.cancel()
        reportJob = scope.launch {
            while (isActive) { delay(10_000); if (playing) report("progress") }
        }
    }

    // ---- audio focus ---------------------------------------------------------

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MOVIE).build())
        .setOnAudioFocusChangeListener { change ->
            if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) pause()
        }
        .build()

    private fun requestAudioFocus() { runCatching { audioManager?.requestAudioFocus(focusRequest) } }

    // ---- teardown ------------------------------------------------------------

    fun stop() {
        reportJob?.cancel(); countdownJob?.cancel()
        val body = reportBody()
        val tc = transcoding
        val psid = playSessionId
        // Fire-and-forget on a scope that outlives this controller.
        CoroutineScope(Dispatchers.IO).launch {
            body?.let { api.reportStop(it) }
            if (tc) api.stopTranscode(psid)
        }
        runCatching { audioManager?.abandonAudioFocusRequest(focusRequest) }
    }

    fun release() {
        scope.cancel()
        runCatching { player.stop() }
        runCatching { player.detachViews() }
        player.release()
        libVlc.release()
    }
}
