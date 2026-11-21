package com.derek.capflix.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val code: Int, message: String) : IOException(message)

/**
 * Thin client for the Jellyfin 10.11 REST API. Everything is authenticated
 * with the "Authorization: MediaBrowser ..." header; stream URLs (which VLC
 * opens itself) carry the token as the ApiKey query parameter.
 */
class Jellyfin(private val session: Session) {

    val json = Json { ignoreUnknownKeys = true; explicitNulls = false; coerceInputValues = true }

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("Authorization", session.authHeader())
                .header("Accept", "application/json")
                .build()
            chain.proceed(req)
        }
        .build()

    private val server get() = session.server
    private val uid get() = session.userId

    // Fields every list needs for cards, progress bars and badges.
    private val cardFields = "PrimaryImageAspectRatio,Overview,Genres,ProductionYear,ParentId,MediaSourceCount,UserData"

    private fun url(path: String, vararg q: Pair<String, Any?>): HttpUrl {
        val b = (server + path).toHttpUrl().newBuilder()
        for ((k, v) in q) if (v != null && v.toString().isNotEmpty()) b.addQueryParameter(k, v.toString())
        return b.build()
    }

    private suspend fun get(u: HttpUrl): String = withContext(Dispatchers.IO) {
        http.newCall(Request.Builder().url(u).get().build()).execute().use { r ->
            if (!r.isSuccessful) throw ApiException(r.code, "HTTP ${r.code}")
            r.body?.string() ?: ""
        }
    }

    private suspend fun send(method: String, u: HttpUrl, body: String? = null): String = withContext(Dispatchers.IO) {
        val rb = (body ?: "").toRequestBody("application/json".toMediaType())
        val req = Request.Builder().url(u).method(method, if (method == "DELETE" && body == null) null else rb).build()
        http.newCall(req).execute().use { r ->
            if (!r.isSuccessful) throw ApiException(r.code, "HTTP ${r.code}")
            r.body?.string() ?: ""
        }
    }

    private suspend inline fun <reified T> getAs(u: HttpUrl): T = json.decodeFromString(get(u))

    // ---- sign in -------------------------------------------------------------

    suspend fun publicInfo(serverUrl: String): PublicInfo = withContext(Dispatchers.IO) {
        val u = (serverUrl.trimEnd('/') + "/System/Info/Public").toHttpUrl()
        OkHttpClient.Builder().connectTimeout(6, TimeUnit.SECONDS).build()
            .newCall(Request.Builder().url(u).build()).execute().use { r ->
                if (!r.isSuccessful) throw ApiException(r.code, "HTTP ${r.code}")
                json.decodeFromString(r.body?.string() ?: "{}")
            }
    }

    suspend fun signIn(serverUrl: String, user: String, password: String): AuthResult {
        session.signOut()            // never send an old token with a new sign-in
        session.server = serverUrl
        val body = buildJsonObject { put("Username", user); put("Pw", password) }.toString()
        val text = send("POST", url("/Users/AuthenticateByName"), body)
        val res: AuthResult = json.decodeFromString(text)
        session.token = res.accessToken
        session.userId = res.user.id
        session.userName = res.user.name
        return res
    }

    suspend fun signOut() {
        runCatching { send("POST", url("/Sessions/Logout")) }
        session.signOut()
    }

    // ---- browsing --------------------------------------------------------------

    suspend fun views(): List<Item> = getAs<ItemsResult>(url("/UserViews", "userId" to uid)).items

    suspend fun resume(limit: Int = 20): List<Item> = getAs<ItemsResult>(
        url("/UserItems/Resume", "userId" to uid, "limit" to limit, "mediaTypes" to "Video",
            "fields" to cardFields, "enableImageTypes" to "Primary,Backdrop,Thumb,Logo", "imageTypeLimit" to 1)
    ).items

    suspend fun nextUp(limit: Int = 20, seriesId: String? = null): List<Item> = getAs<ItemsResult>(
        url("/Shows/NextUp", "userId" to uid, "limit" to limit, "seriesId" to seriesId, "fields" to cardFields,
            "enableResumable" to false, "enableRewatching" to false, "disableFirstEpisode" to false)
    ).items

    suspend fun latest(parentId: String?, types: String, limit: Int = 24): List<Item> = getAs(
        url("/Items/Latest", "userId" to uid, "parentId" to parentId, "includeItemTypes" to types,
            "limit" to limit, "fields" to cardFields, "groupItems" to true)
    )

    suspend fun items(
        types: String = "Movie,Series",
        parentId: String? = null,
        sortBy: String = "SortName",
        sortOrder: String = "Ascending",
        genre: String? = null,
        search: String? = null,
        filters: String? = null,
        isPlayed: Boolean? = null,
        minRating: Double? = null,
        ids: String? = null,
        start: Int = 0,
        limit: Int = 60,
    ): ItemsResult = getAs(
        url("/Items", "userId" to uid, "includeItemTypes" to types, "parentId" to parentId, "recursive" to true,
            "sortBy" to sortBy, "sortOrder" to sortOrder, "genres" to genre, "searchTerm" to search,
            "filters" to filters, "isPlayed" to isPlayed, "minCommunityRating" to minRating, "ids" to ids,
            "startIndex" to start, "limit" to limit, "fields" to cardFields, "enableTotalRecordCount" to true,
            "imageTypeLimit" to 1)
    )

    suspend fun genres(types: String = "Movie,Series", limit: Int = 40): List<GenreItem> = getAs<GenreItemsResult>(
        url("/Genres", "userId" to uid, "includeItemTypes" to types, "sortBy" to "SortName", "limit" to limit)
    ).items

    suspend fun item(id: String): Item = getAs(url("/Items/$id", "userId" to uid))

    suspend fun similar(id: String, limit: Int = 12): List<Item> = getAs<ItemsResult>(
        url("/Items/$id/Similar", "userId" to uid, "limit" to limit, "fields" to cardFields)
    ).items

    suspend fun seasons(seriesId: String): List<Item> = getAs<ItemsResult>(
        url("/Shows/$seriesId/Seasons", "userId" to uid, "fields" to "UserData")
    ).items

    suspend fun episodes(seriesId: String, seasonId: String? = null, startItemId: String? = null, limit: Int? = null): List<Item> =
        getAs<ItemsResult>(
            url("/Shows/$seriesId/Episodes", "userId" to uid, "seasonId" to seasonId, "startItemId" to startItemId,
                "limit" to limit, "fields" to "Overview,MediaSources,UserData")
        ).items

    /** The episode after [episode], or null at the end of the show. */
    suspend fun nextEpisode(episode: Item): Item? {
        val sid = episode.seriesId ?: return null
        val list = episodes(sid, startItemId = episode.id, limit = 2)
        return list.getOrNull(1)?.takeIf { it.id != episode.id }
    }

    suspend fun segments(itemId: String): List<Segment> = runCatching {
        getAs<SegmentsResult>(url("/MediaSegments/$itemId", "includeSegmentTypes" to "Intro"))
            .items + getAs<SegmentsResult>(url("/MediaSegments/$itemId", "includeSegmentTypes" to "Outro")).items
    }.getOrDefault(emptyList())

    // ---- user actions ----------------------------------------------------------

    suspend fun setFavorite(id: String, fav: Boolean) {
        send(if (fav) "POST" else "DELETE", url("/UserFavoriteItems/$id", "userId" to uid))
    }

    suspend fun setPlayed(id: String, played: Boolean) {
        send(if (played) "POST" else "DELETE", url("/UserPlayedItems/$id", "userId" to uid))
    }

    // ---- playback --------------------------------------------------------------

    suspend fun reportStart(r: PlaybackReport) = runCatching { send("POST", url("/Sessions/Playing"), json.encodeToString(r)) }
    suspend fun reportProgress(r: PlaybackReport) = runCatching { send("POST", url("/Sessions/Playing/Progress"), json.encodeToString(r)) }
    suspend fun reportStop(r: PlaybackReport) = runCatching { send("POST", url("/Sessions/Playing/Stopped"), json.encodeToString(r)) }

    /** The original file, byte for byte. VLC handles every codec in it. */
    fun directUrl(itemId: String, mediaSourceId: String, container: String?): String =
        url("/Videos/$itemId/stream", "static" to true, "mediaSourceId" to mediaSourceId,
            "container" to container, "ApiKey" to session.token).toString()

    /**
     * Jellyfin transcodes (on the RTX card) to a smaller HLS stream. Used away
     * from home, or for Dolby Vision files a phone can't show in color.
     */
    fun transcodeUrl(
        itemId: String, mediaSourceId: String, playSessionId: String, maxBitrate: Long,
        audioStreamIndex: Int? = null, burnSubtitleIndex: Int? = null,
    ): String {
        val audioBr = 192_000L
        return url(
            "/Videos/$itemId/master.m3u8",
            "MediaSourceId" to mediaSourceId,
            "DeviceId" to session.deviceId,
            "PlaySessionId" to playSessionId,
            "ApiKey" to session.token,
            "VideoCodec" to "hevc,h264",
            "AudioCodec" to "aac",
            "TranscodingMaxAudioChannels" to 2,
            "MaxStreamingBitrate" to maxBitrate,
            "VideoBitrate" to (maxBitrate - audioBr).coerceAtLeast(500_000L),
            "AudioBitrate" to audioBr,
            "SegmentContainer" to "ts",
            "MinSegments" to 1,
            "BreakOnNonKeyFrames" to true,
            "AudioStreamIndex" to audioStreamIndex,
            "SubtitleStreamIndex" to burnSubtitleIndex,
            "SubtitleMethod" to burnSubtitleIndex?.let { "Encode" },
            "EnableAutoStreamCopy" to true,
            "AllowVideoStreamCopy" to true,
            "AllowAudioStreamCopy" to false,
        ).toString()
    }

    /** External or text subtitle as a file VLC can load next to a stream. */
    fun subtitleUrl(itemId: String, mediaSourceId: String, index: Int, format: String = "srt"): String =
        url("/Videos/$itemId/$mediaSourceId/Subtitles/$index/0/Stream.$format", "ApiKey" to session.token).toString()

    suspend fun stopTranscode(playSessionId: String) = runCatching {
        send("DELETE", url("/Videos/ActiveEncodings", "deviceId" to session.deviceId, "playSessionId" to playSessionId))
    }

    // ---- images ----------------------------------------------------------------

    fun image(id: String, type: String = "Primary", tag: String? = null, maxWidth: Int? = null, maxHeight: Int? = null, index: Int? = null): String {
        val path = if (index != null) "/Items/$id/Images/$type/$index" else "/Items/$id/Images/$type"
        return url(path, "tag" to tag, "fillWidth" to maxWidth, "fillHeight" to maxHeight, "quality" to 90).toString()
    }

    fun poster(item: Item, h: Int = 450): String? = when {
        item.isEpisode && item.seriesId != null && item.seriesPrimaryTag != null ->
            image(item.seriesId, "Primary", item.seriesPrimaryTag, maxHeight = h)
        item.imageTags["Primary"] != null -> image(item.id, "Primary", item.imageTags["Primary"], maxHeight = h)
        else -> null
    }

    /** Wide 16:9 art: episode still, else backdrop, else series thumb. */
    fun landscape(item: Item, w: Int = 640): String? = when {
        item.isEpisode && item.imageTags["Primary"] != null -> image(item.id, "Primary", item.imageTags["Primary"], maxWidth = w)
        item.imageTags["Thumb"] != null -> image(item.id, "Thumb", item.imageTags["Thumb"], maxWidth = w)
        item.backdropTags.isNotEmpty() -> image(item.id, "Backdrop", item.backdropTags[0], maxWidth = w, index = 0)
        item.parentThumbItemId != null -> image(item.parentThumbItemId, "Thumb", item.parentThumbTag, maxWidth = w)
        item.parentBackdropItemId != null && item.parentBackdropTags.isNotEmpty() ->
            image(item.parentBackdropItemId, "Backdrop", item.parentBackdropTags[0], maxWidth = w, index = 0)
        else -> poster(item)
    }

    fun backdrop(item: Item, w: Int = 1280): String? = when {
        item.backdropTags.isNotEmpty() -> image(item.id, "Backdrop", item.backdropTags[0], maxWidth = w, index = 0)
        item.parentBackdropItemId != null && item.parentBackdropTags.isNotEmpty() ->
            image(item.parentBackdropItemId, "Backdrop", item.parentBackdropTags[0], maxWidth = w, index = 0)
        else -> landscape(item, w)
    }

    fun logo(item: Item, w: Int = 600): String? = when {
        item.imageTags["Logo"] != null -> image(item.id, "Logo", item.imageTags["Logo"], maxWidth = w)
        item.parentLogoItemId != null && item.parentLogoTag != null -> image(item.parentLogoItemId, "Logo", item.parentLogoTag, maxWidth = w)
        else -> null
    }

    fun personImage(p: Person): String? = p.primaryTag?.let { image(p.id, "Primary", it, maxHeight = 300) }
}

@kotlinx.serialization.Serializable
data class GenreItemsResult(@kotlinx.serialization.SerialName("Items") val items: List<GenreItem> = emptyList())
