package dev.gotlou.bettertrophies

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

data class CachedDashboardSnapshot(
    val snapshot: DashboardSnapshot,
    val updatedAtEpochMs: Long,
)

data class CachedTrophyEntries(
    val trophies: List<TrophyEntry>,
    val updatedAtEpochMs: Long,
)

data class CachedCaptureGroups(
    val groups: List<CaptureGroup>,
    val updatedAtEpochMs: Long,
)

class AppCacheStore(
    context: Context,
) {
    private val databaseHelper = CacheDatabaseHelper(context)

    fun readDashboard(accountKey: String): CachedDashboardSnapshot? {
        val database = databaseHelper.readableDatabase
        database.query(
            DASHBOARD_TABLE,
            arrayOf(COLUMN_PAYLOAD_JSON, COLUMN_UPDATED_AT),
            "$COLUMN_ACCOUNT_KEY = ?",
            arrayOf(accountKey),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }

            return CachedDashboardSnapshot(
                snapshot = deserializeDashboardSnapshot(cursor.getString(0)),
                updatedAtEpochMs = cursor.getLong(1),
            )
        }
    }

    fun writeDashboard(
        accountKey: String,
        snapshot: DashboardSnapshot,
        updatedAtEpochMs: Long,
    ) {
        val values = ContentValues().apply {
            put(COLUMN_ACCOUNT_KEY, accountKey)
            put(COLUMN_PAYLOAD_JSON, serializeDashboardSnapshot(snapshot))
            put(COLUMN_UPDATED_AT, updatedAtEpochMs)
        }
        databaseHelper.writableDatabase.insertWithOnConflict(
            DASHBOARD_TABLE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun readTrophies(accountKey: String, titleId: String): CachedTrophyEntries? {
        val database = databaseHelper.readableDatabase
        database.query(
            TROPHY_TABLE,
            arrayOf(COLUMN_PAYLOAD_JSON, COLUMN_UPDATED_AT),
            "$COLUMN_ACCOUNT_KEY = ? AND $COLUMN_TITLE_ID = ?",
            arrayOf(accountKey, titleId),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }

            return CachedTrophyEntries(
                trophies = deserializeTrophyEntries(cursor.getString(0)),
                updatedAtEpochMs = cursor.getLong(1),
            )
        }
    }

    fun writeTrophies(
        accountKey: String,
        titleId: String,
        trophies: List<TrophyEntry>,
        updatedAtEpochMs: Long,
    ) {
        val values = ContentValues().apply {
            put(COLUMN_ACCOUNT_KEY, accountKey)
            put(COLUMN_TITLE_ID, titleId)
            put(COLUMN_PAYLOAD_JSON, serializeTrophyEntries(trophies))
            put(COLUMN_UPDATED_AT, updatedAtEpochMs)
        }
        databaseHelper.writableDatabase.insertWithOnConflict(
            TROPHY_TABLE,
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE,
        )
    }

    fun readCaptureGroups(accountKey: String): CachedCaptureGroups? {
        val database = databaseHelper.readableDatabase
        val updatedAtEpochMs = database.query(
            CAPTURE_SYNC_TABLE,
            arrayOf(COLUMN_UPDATED_AT),
            "$COLUMN_ACCOUNT_KEY = ?",
            arrayOf(accountKey),
            null,
            null,
            null,
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                return null
            }
            cursor.getLong(0)
        }

        val captures = mutableListOf<CaptureEntry>()
        database.query(
            CAPTURE_TABLE,
            arrayOf(COLUMN_PAYLOAD_JSON),
            "$COLUMN_ACCOUNT_KEY = ?",
            arrayOf(accountKey),
            null,
            null,
            null,
        ).use { cursor ->
            while (cursor.moveToNext()) {
                captures += deserializeCaptureEntry(cursor.getString(0))
            }
        }

        if (captures.isEmpty()) {
            return null
        }

        return CachedCaptureGroups(
            groups = buildCaptureGroups(captures),
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }

    fun writeCaptureGroups(
        accountKey: String,
        groups: List<CaptureGroup>,
        updatedAtEpochMs: Long,
    ) {
        val database = databaseHelper.writableDatabase
        database.beginTransaction()
        try {
            groups.forEach { group ->
                group.captures.forEach { capture ->
                    upsertCaptureInternal(
                        database = database,
                        accountKey = accountKey,
                        capture = capture.copy(isCachedOnly = false),
                        updatedAtEpochMs = updatedAtEpochMs,
                    )
                }
            }
            writeCaptureSyncInternal(database, accountKey, updatedAtEpochMs)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun upsertCapture(
        accountKey: String,
        capture: CaptureEntry,
        updatedAtEpochMs: Long,
    ) {
        val database = databaseHelper.writableDatabase
        database.beginTransaction()
        try {
            upsertCaptureInternal(database, accountKey, capture.copy(isCachedOnly = false), updatedAtEpochMs)
            writeCaptureSyncInternal(database, accountKey, updatedAtEpochMs)
            database.setTransactionSuccessful()
        } finally {
            database.endTransaction()
        }
    }

    fun clearAccount(accountKey: String) {
        val database = databaseHelper.writableDatabase
        database.delete(DASHBOARD_TABLE, "$COLUMN_ACCOUNT_KEY = ?", arrayOf(accountKey))
        database.delete(TROPHY_TABLE, "$COLUMN_ACCOUNT_KEY = ?", arrayOf(accountKey))
        database.delete(CAPTURE_SYNC_TABLE, "$COLUMN_ACCOUNT_KEY = ?", arrayOf(accountKey))
        database.delete(CAPTURE_TABLE, "$COLUMN_ACCOUNT_KEY = ?", arrayOf(accountKey))
    }

    private fun upsertCaptureInternal(
        database: SQLiteDatabase,
        accountKey: String,
        capture: CaptureEntry,
        updatedAtEpochMs: Long,
    ) {
        val values = ContentValues().apply {
            put(COLUMN_ACCOUNT_KEY, accountKey)
            put(COLUMN_UGC_ID, capture.ugcId)
            put(COLUMN_PAYLOAD_JSON, serializeCaptureEntry(capture))
            put(COLUMN_UPDATED_AT, updatedAtEpochMs)
        }
        database.insertWithOnConflict(CAPTURE_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun writeCaptureSyncInternal(
        database: SQLiteDatabase,
        accountKey: String,
        updatedAtEpochMs: Long,
    ) {
        val values = ContentValues().apply {
            put(COLUMN_ACCOUNT_KEY, accountKey)
            put(COLUMN_UPDATED_AT, updatedAtEpochMs)
        }
        database.insertWithOnConflict(CAPTURE_SYNC_TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun buildCaptureGroups(captures: List<CaptureEntry>): List<CaptureGroup> {
        return captures
            .groupBy { it.titleId to it.titleName }
            .map { (key, groupedCaptures) ->
                val latest = groupedCaptures.maxByOrNull { it.uploadDate.orEmpty() }
                CaptureGroup(
                    titleId = key.first,
                    titleName = key.second,
                    conceptId = null,
                    titleImageUrl = latest?.titleImageUrl,
                    captures = groupedCaptures.sortedByDescending { it.uploadDate.orEmpty() },
                )
            }
            .sortedByDescending { it.latestUploadDate.orEmpty() }
    }

    private fun serializeDashboardSnapshot(snapshot: DashboardSnapshot): String {
        return JSONObject()
            .put("profile", serializeUserProfile(snapshot.profile))
            .put("summary", serializeSummary(snapshot.summary))
            .put("trophyTitles", JSONArray().apply {
                snapshot.trophyTitles.forEach { put(serializeGameTitle(it)) }
            })
            .put("recentTitles", JSONArray().apply {
                snapshot.recentTitles.forEach { put(serializeRecentTitle(it)) }
            })
            .toString()
    }

    private fun deserializeDashboardSnapshot(json: String): DashboardSnapshot {
        val root = JSONObject(json)
        return DashboardSnapshot(
            profile = deserializeUserProfile(root.getJSONObject("profile")),
            summary = deserializeSummary(root.getJSONObject("summary")),
            trophyTitles = root.getJSONArray("trophyTitles").toGameTitles(),
            recentTitles = root.getJSONArray("recentTitles").toRecentTitles(),
        )
    }

    private fun serializeUserProfile(profile: UserProfile): JSONObject {
        return JSONObject()
            .put("onlineId", profile.onlineId)
            .put("firstName", profile.firstName)
            .put("lastName", profile.lastName)
            .put("aboutMe", profile.aboutMe)
            .put("isPlus", profile.isPlus)
            .put("isVerified", profile.isVerified)
            .put("languages", JSONArray(profile.languages))
            .put("avatarUrl", profile.avatarUrl)
    }

    private fun deserializeUserProfile(json: JSONObject): UserProfile {
        return UserProfile(
            onlineId = json.getString("onlineId"),
            firstName = json.optNullableString("firstName"),
            lastName = json.optNullableString("lastName"),
            aboutMe = json.optNullableString("aboutMe"),
            isPlus = json.optBoolean("isPlus"),
            isVerified = json.optBoolean("isVerified"),
            languages = json.getJSONArray("languages").toStringList(),
            avatarUrl = json.optNullableString("avatarUrl"),
        )
    }

    private fun serializeSummary(summary: TrophySummaryRecord): JSONObject {
        return JSONObject()
            .put("trophyLevel", summary.trophyLevel)
            .put("progress", summary.progress)
            .put("tier", summary.tier)
            .put("trophyPoints", summary.trophyPoints)
            .put("earnedTrophies", serializeTotals(summary.earnedTrophies))
    }

    private fun deserializeSummary(json: JSONObject): TrophySummaryRecord {
        return TrophySummaryRecord(
            trophyLevel = json.getInt("trophyLevel"),
            progress = json.getInt("progress"),
            tier = json.getInt("tier"),
            trophyPoints = json.getInt("trophyPoints"),
            earnedTrophies = deserializeTotals(json.getJSONObject("earnedTrophies")),
        )
    }

    private fun serializeGameTitle(title: GameTitle): JSONObject {
        return JSONObject()
            .put("id", title.id)
            .put("npTitleId", title.npTitleId)
            .put("titleName", title.titleName)
            .put("platform", title.platform)
            .put("progress", title.progress)
            .put("iconUrl", title.iconUrl)
            .put("lastUpdated", title.lastUpdated)
            .put("communicationId", title.communicationId)
            .put("serviceName", title.serviceName)
            .put("earnedTrophies", serializeTotals(title.earnedTrophies))
            .put("definedTrophies", serializeTotals(title.definedTrophies))
    }

    private fun deserializeGameTitle(json: JSONObject): GameTitle {
        return GameTitle(
            id = json.getString("id"),
            npTitleId = json.optNullableString("npTitleId"),
            titleName = json.getString("titleName"),
            platform = json.getString("platform"),
            progress = json.getInt("progress"),
            iconUrl = json.getString("iconUrl"),
            lastUpdated = json.optNullableString("lastUpdated"),
            communicationId = json.getString("communicationId"),
            serviceName = json.getString("serviceName"),
            earnedTrophies = deserializeTotals(json.getJSONObject("earnedTrophies")),
            definedTrophies = deserializeTotals(json.getJSONObject("definedTrophies")),
        )
    }

    private fun serializeRecentTitle(title: RecentTitle): JSONObject {
        return JSONObject()
            .put("id", title.id)
            .put("npTitleId", title.npTitleId)
            .put("titleName", title.titleName)
            .put("platform", title.platform)
            .put("playTimeHours", title.playTimeHours)
            .put("storyProgress", title.storyProgress)
            .put("coverUrl", title.coverUrl)
            .put("hasHelpContent", title.hasHelpContent)
            .put("hasCodex", title.hasCodex)
    }

    private fun deserializeRecentTitle(json: JSONObject): RecentTitle {
        return RecentTitle(
            id = json.getString("id"),
            npTitleId = json.getString("npTitleId"),
            titleName = json.getString("titleName"),
            platform = json.getString("platform"),
            playTimeHours = json.getInt("playTimeHours"),
            storyProgress = json.optNullableInt("storyProgress"),
            coverUrl = json.optNullableString("coverUrl"),
            hasHelpContent = json.optBoolean("hasHelpContent"),
            hasCodex = json.optBoolean("hasCodex"),
        )
    }

    private fun serializeTotals(totals: TrophyTotals): JSONObject {
        return JSONObject()
            .put("bronze", totals.bronze)
            .put("silver", totals.silver)
            .put("gold", totals.gold)
            .put("platinum", totals.platinum)
    }

    private fun deserializeTotals(json: JSONObject): TrophyTotals {
        return TrophyTotals(
            bronze = json.getInt("bronze"),
            silver = json.getInt("silver"),
            gold = json.getInt("gold"),
            platinum = json.getInt("platinum"),
        )
    }

    private fun serializeTrophyEntries(trophies: List<TrophyEntry>): String {
        return JSONArray().apply {
            trophies.forEach { trophy ->
                put(
                    JSONObject()
                        .put("trophyId", trophy.trophyId)
                        .put("name", trophy.name)
                        .put("detail", trophy.detail)
                        .put("trophyType", trophy.trophyType)
                        .put("iconUrl", trophy.iconUrl)
                        .put("hidden", trophy.hidden)
                        .put("earned", trophy.earned)
                        .put("earnedAt", trophy.earnedAt)
                        .put("progress", trophy.progress)
                        .put("progressRate", trophy.progressRate)
                        .put("rare", trophy.rare)
                        .put("earnedRate", trophy.earnedRate)
                )
            }
        }.toString()
    }

    private fun deserializeTrophyEntries(json: String): List<TrophyEntry> {
        val array = JSONArray(json)
        return buildList(array.length()) {
            repeat(array.length()) { index ->
                val item = array.getJSONObject(index)
                add(
                    TrophyEntry(
                        trophyId = item.getString("trophyId"),
                        name = item.optNullableString("name"),
                        detail = item.optNullableString("detail"),
                        trophyType = item.optNullableString("trophyType"),
                        iconUrl = item.optNullableString("iconUrl"),
                        hidden = item.optNullableBoolean("hidden"),
                        earned = item.optNullableBoolean("earned"),
                        earnedAt = item.optNullableString("earnedAt"),
                        progress = item.optNullableString("progress"),
                        progressRate = item.optNullableInt("progressRate"),
                        rare = item.optNullableInt("rare"),
                        earnedRate = item.optNullableString("earnedRate"),
                    ),
                )
            }
        }
    }

    private fun serializeCaptureEntry(capture: CaptureEntry): String {
        return JSONObject()
            .put("ugcId", capture.ugcId)
            .put("titleId", capture.titleId)
            .put("titleName", capture.titleName)
            .put("titleImageUrl", capture.titleImageUrl)
            .put("uploadDate", capture.uploadDate)
            .put("captureType", capture.captureType)
            .put("description", capture.description)
            .put("fileType", capture.fileType)
            .put("resolution", capture.resolution)
            .put("fileSizeBytes", capture.fileSizeBytes)
            .put("videoDurationSeconds", capture.videoDurationSeconds)
            .put("platform", capture.platform)
            .put("isSpoiler", capture.isSpoiler)
            .put("expireAt", capture.expireAt)
            .put("thumbnailUrl", capture.thumbnailUrl)
            .put("localThumbnailPath", capture.localThumbnailPath)
            .put("primaryAssetUrl", capture.primaryAssetUrl)
            .put("localPrimaryAssetPath", capture.localPrimaryAssetPath)
            .put("localPrimaryAssetGalleryUri", capture.localPrimaryAssetGalleryUri)
            .put("localPrimaryAssetContentType", capture.localPrimaryAssetContentType)
            .put("localPrimaryAssetFileName", capture.localPrimaryAssetFileName)
            .toString()
    }

    private fun deserializeCaptureEntry(json: String): CaptureEntry {
        val item = JSONObject(json)
        return CaptureEntry(
            ugcId = item.getString("ugcId"),
            titleId = item.getString("titleId"),
            titleName = item.getString("titleName"),
            titleImageUrl = item.optNullableString("titleImageUrl"),
            uploadDate = item.optNullableString("uploadDate"),
            captureType = item.optNullableString("captureType"),
            description = item.optNullableString("description"),
            fileType = item.optNullableString("fileType"),
            resolution = item.optNullableString("resolution"),
            fileSizeBytes = item.optNullableLong("fileSizeBytes"),
            videoDurationSeconds = item.optNullableLong("videoDurationSeconds"),
            platform = item.optNullableString("platform"),
            isSpoiler = item.optNullableBoolean("isSpoiler"),
            expireAt = item.optNullableString("expireAt"),
            thumbnailUrl = item.optNullableString("thumbnailUrl"),
            localThumbnailPath = item.optNullableString("localThumbnailPath"),
            primaryAssetUrl = item.optNullableString("primaryAssetUrl"),
            localPrimaryAssetPath = item.optNullableString("localPrimaryAssetPath"),
            localPrimaryAssetGalleryUri = item.optNullableString("localPrimaryAssetGalleryUri"),
            localPrimaryAssetContentType = item.optNullableString("localPrimaryAssetContentType"),
            localPrimaryAssetFileName = item.optNullableString("localPrimaryAssetFileName"),
            isCachedOnly = false,
        )
    }

    private fun JSONArray.toGameTitles(): List<GameTitle> {
        return buildList(length()) {
            repeat(length()) { index ->
                add(deserializeGameTitle(getJSONObject(index)))
            }
        }
    }

    private fun JSONArray.toRecentTitles(): List<RecentTitle> {
        return buildList(length()) {
            repeat(length()) { index ->
                add(deserializeRecentTitle(getJSONObject(index)))
            }
        }
    }

    private fun JSONArray.toStringList(): List<String> {
        return buildList(length()) {
            repeat(length()) { index ->
                add(getString(index))
            }
        }
    }

    private fun JSONObject.optNullableString(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return getString(key)
    }

    private fun JSONObject.optNullableInt(key: String): Int? {
        if (!has(key) || isNull(key)) return null
        return getInt(key)
    }

    private fun JSONObject.optNullableLong(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return getLong(key)
    }

    private fun JSONObject.optNullableBoolean(key: String): Boolean? {
        if (!has(key) || isNull(key)) return null
        return getBoolean(key)
    }

    private class CacheDatabaseHelper(
        context: Context,
    ) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $DASHBOARD_TABLE (
                    $COLUMN_ACCOUNT_KEY TEXT PRIMARY KEY NOT NULL,
                    $COLUMN_PAYLOAD_JSON TEXT NOT NULL,
                    $COLUMN_UPDATED_AT INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE $TROPHY_TABLE (
                    $COLUMN_ACCOUNT_KEY TEXT NOT NULL,
                    $COLUMN_TITLE_ID TEXT NOT NULL,
                    $COLUMN_PAYLOAD_JSON TEXT NOT NULL,
                    $COLUMN_UPDATED_AT INTEGER NOT NULL,
                    PRIMARY KEY ($COLUMN_ACCOUNT_KEY, $COLUMN_TITLE_ID)
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE $CAPTURE_SYNC_TABLE (
                    $COLUMN_ACCOUNT_KEY TEXT PRIMARY KEY NOT NULL,
                    $COLUMN_UPDATED_AT INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL(
                """
                CREATE TABLE $CAPTURE_TABLE (
                    $COLUMN_ACCOUNT_KEY TEXT NOT NULL,
                    $COLUMN_UGC_ID TEXT NOT NULL,
                    $COLUMN_PAYLOAD_JSON TEXT NOT NULL,
                    $COLUMN_UPDATED_AT INTEGER NOT NULL,
                    PRIMARY KEY ($COLUMN_ACCOUNT_KEY, $COLUMN_UGC_ID)
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            db.execSQL("DROP TABLE IF EXISTS $DASHBOARD_TABLE")
            db.execSQL("DROP TABLE IF EXISTS $TROPHY_TABLE")
            db.execSQL("DROP TABLE IF EXISTS $CAPTURE_SYNC_TABLE")
            db.execSQL("DROP TABLE IF EXISTS $CAPTURE_TABLE")
            onCreate(db)
        }
    }

    private companion object {
        const val DATABASE_NAME = "app-cache.db"
        const val DATABASE_VERSION = 2
        const val DASHBOARD_TABLE = "dashboard_cache"
        const val TROPHY_TABLE = "trophy_cache"
        const val CAPTURE_SYNC_TABLE = "capture_sync"
        const val CAPTURE_TABLE = "capture_cache"
        const val COLUMN_ACCOUNT_KEY = "account_key"
        const val COLUMN_TITLE_ID = "title_id"
        const val COLUMN_UGC_ID = "ugc_id"
        const val COLUMN_PAYLOAD_JSON = "payload_json"
        const val COLUMN_UPDATED_AT = "updated_at"
    }
}
