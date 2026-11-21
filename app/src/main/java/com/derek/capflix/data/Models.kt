package com.derek.capflix.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The parts of Jellyfin's BaseItemDto that Cap Flix uses. */
@Serializable
data class Item(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("ProductionYear") val year: Int? = null,
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("EndDate") val endDate: String? = null,
    @SerialName("Status") val status: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("Taglines") val taglines: List<String> = emptyList(),
    @SerialName("CommunityRating") val rating: Double? = null,
    @SerialName("CriticRating") val criticRating: Double? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("SeasonName") val seasonName: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("IndexNumberEnd") val indexNumberEnd: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropTags: List<String> = emptyList(),
    @SerialName("ParentBackdropItemId") val parentBackdropItemId: String? = null,
    @SerialName("ParentBackdropImageTags") val parentBackdropTags: List<String> = emptyList(),
    @SerialName("ParentLogoItemId") val parentLogoItemId: String? = null,
    @SerialName("ParentLogoImageTag") val parentLogoTag: String? = null,
    @SerialName("ParentThumbItemId") val parentThumbItemId: String? = null,
    @SerialName("ParentThumbImageTag") val parentThumbTag: String? = null,
    @SerialName("SeriesPrimaryImageTag") val seriesPrimaryTag: String? = null,
    @SerialName("PrimaryImageAspectRatio") val primaryAspect: Double? = null,
    @SerialName("UserData") val userData: UserData? = null,
    @SerialName("People") val people: List<Person> = emptyList(),
    @SerialName("Studios") val studios: List<NameId> = emptyList(),
    @SerialName("MediaSources") val mediaSources: List<MediaSource> = emptyList(),
    @SerialName("MediaStreams") val mediaStreams: List<MediaStream> = emptyList(),
    @SerialName("LocationType") val locationType: String? = null,
    @SerialName("CollectionType") val collectionType: String? = null,
) {
    val isSeries get() = type == "Series"
    val isEpisode get() = type == "Episode"
    val isMovie get() = type == "Movie"
    val runtimeMinutes: Int? get() = runTimeTicks?.let { (it / 600_000_000L).toInt() }
    val progress: Float
        get() {
            val pos = userData?.positionTicks ?: 0L
            val len = runTimeTicks ?: 0L
            return if (pos > 0 && len > 0) (pos.toFloat() / len).coerceIn(0f, 1f) else 0f
        }

    /** "S2 E5" */
    val episodeCode: String
        get() = buildString {
            parentIndexNumber?.let { append("S$it ") }
            indexNumber?.let { append("E$it") }
            indexNumberEnd?.let { append("-$it") }
        }.trim()

    val videoStream: MediaStream? get() = (mediaSources.firstOrNull()?.streams ?: mediaStreams).firstOrNull { it.type == "Video" }
    val audioStreams: List<MediaStream> get() = (mediaSources.firstOrNull()?.streams ?: mediaStreams).filter { it.type == "Audio" }

    /** Badges like "4K", "HDR", "Dolby Vision", "5.1". */
    val qualityBadges: List<String>
        get() {
            val out = mutableListOf<String>()
            val v = videoStream
            val w = v?.width ?: 0
            when {
                w >= 3200 -> out += "4K"
                w >= 1800 -> out += "HD"
            }
            when (v?.videoRangeType?.uppercase()) {
                null, "", "SDR", "UNKNOWN" -> {}
                "DOVI", "DOVIWITHHDR10", "DOVIWITHHLG", "DOVIWITHSDR", "DOVIWITHEL", "DOVIWITHHDR10PLUS" -> out += "Dolby Vision"
                "HDR10PLUS" -> out += "HDR10+"
                else -> out += "HDR"
            }
            val ch = audioStreams.maxOfOrNull { it.channels ?: 0 } ?: 0
            if (ch >= 8) out += "7.1" else if (ch >= 6) out += "5.1"
            if (audioStreams.any { (it.displayTitle ?: "").contains("Atmos", true) || (it.profile ?: "").contains("Atmos", true) }) out += "Atmos"
            return out
        }
}

@Serializable
data class UserData(
    @SerialName("PlaybackPositionTicks") val positionTicks: Long = 0,
    @SerialName("PlayedPercentage") val playedPercentage: Double? = null,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("UnplayedItemCount") val unplayedCount: Int? = null,
    @SerialName("PlayCount") val playCount: Int = 0,
)

@Serializable
data class Person(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Role") val role: String? = null,
    @SerialName("Type") val type: String? = null,
    @SerialName("PrimaryImageTag") val primaryTag: String? = null,
)

@Serializable
data class NameId(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
)

@Serializable
data class MediaSource(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String? = null,
    @SerialName("Container") val container: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("Bitrate") val bitrate: Long? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("MediaStreams") val streams: List<MediaStream> = emptyList(),
)

@Serializable
data class MediaStream(
    @SerialName("Type") val type: String = "",
    @SerialName("Index") val index: Int = 0,
    @SerialName("Codec") val codec: String? = null,
    @SerialName("Language") val language: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String? = null,
    @SerialName("Profile") val profile: String? = null,
    @SerialName("IsDefault") val isDefault: Boolean = false,
    @SerialName("IsForced") val isForced: Boolean = false,
    @SerialName("IsExternal") val isExternal: Boolean = false,
    @SerialName("IsTextSubtitleStream") val isText: Boolean = false,
    @SerialName("Width") val width: Int? = null,
    @SerialName("Height") val height: Int? = null,
    @SerialName("Channels") val channels: Int? = null,
    @SerialName("VideoRange") val videoRange: String? = null,
    @SerialName("VideoRangeType") val videoRangeType: String? = null,
    @SerialName("BitRate") val bitrate: Long? = null,
)

@Serializable
data class ItemsResult(
    @SerialName("Items") val items: List<Item> = emptyList(),
    @SerialName("TotalRecordCount") val total: Int = 0,
)

@Serializable
data class AuthResult(
    @SerialName("AccessToken") val accessToken: String = "",
    @SerialName("User") val user: UserDto = UserDto(),
)

@Serializable
data class UserDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
)

@Serializable
data class Segment(
    @SerialName("Type") val type: String = "",
    @SerialName("StartTicks") val startTicks: Long = 0,
    @SerialName("EndTicks") val endTicks: Long = 0,
)

@Serializable
data class SegmentsResult(@SerialName("Items") val items: List<Segment> = emptyList())

@Serializable
data class GenreItem(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
)

@Serializable
data class PublicInfo(
    @SerialName("ServerName") val serverName: String = "",
    @SerialName("Version") val version: String = "",
)

/** Body for /Sessions/Playing, /Progress and /Stopped. */
@Serializable
data class PlaybackReport(
    @SerialName("ItemId") val itemId: String,
    @SerialName("MediaSourceId") val mediaSourceId: String,
    @SerialName("PlaySessionId") val playSessionId: String,
    @SerialName("PositionTicks") val positionTicks: Long,
    @SerialName("IsPaused") val isPaused: Boolean = false,
    @SerialName("CanSeek") val canSeek: Boolean = true,
    @SerialName("PlayMethod") val playMethod: String = "DirectStream",
    @SerialName("AudioStreamIndex") val audioStreamIndex: Int? = null,
    @SerialName("SubtitleStreamIndex") val subtitleStreamIndex: Int? = null,
)
