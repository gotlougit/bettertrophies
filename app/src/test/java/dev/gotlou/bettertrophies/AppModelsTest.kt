package dev.gotlou.bettertrophies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppModelsTest {
    @Test
    fun toCaptureTotalsCountsCapturesAcrossGroups() {
        val groups = listOf(
            captureGroup(
                titleId = "one",
                uploadDates = listOf("2026-04-10T10:00:00Z", "2026-04-11T10:00:00Z"),
            ),
            captureGroup(
                titleId = "two",
                uploadDates = listOf("2026-04-12T10:00:00Z"),
            ),
        )

        assertEquals(CaptureTotals(totalCaptures = 3, totalGames = 2), groups.toCaptureTotals())
    }

    @Test
    fun latestUploadDateIgnoresBlankValues() {
        val group = captureGroup(
            titleId = "one",
            uploadDates = listOf("", "2026-04-11T10:00:00Z", "2026-04-10T10:00:00Z"),
        )

        assertEquals("2026-04-11T10:00:00Z", group.latestUploadDate)
    }

    @Test
    fun latestUploadDateIsNullWhenEveryCaptureIsBlank() {
        val group = captureGroup(
            titleId = "one",
            uploadDates = listOf("", null),
        )

        assertNull(group.latestUploadDate)
    }

    private fun captureGroup(
        titleId: String,
        uploadDates: List<String?>,
    ): CaptureGroup = CaptureGroup(
        titleId = titleId,
        titleName = "Title $titleId",
        conceptId = null,
        titleImageUrl = null,
        captures = uploadDates.mapIndexed { index, uploadDate ->
            CaptureEntry(
                ugcId = "$titleId-$index",
                titleId = titleId,
                titleName = "Title $titleId",
                titleImageUrl = null,
                uploadDate = uploadDate,
                captureType = "SCREENSHOT",
                description = null,
                fileType = "image/jpeg",
                resolution = null,
                fileSizeBytes = null,
                videoDurationSeconds = null,
                platform = null,
                isSpoiler = null,
                expireAt = null,
                thumbnailUrl = null,
                localThumbnailPath = null,
                primaryAssetUrl = null,
                localPrimaryAssetPath = null,
                localPrimaryAssetGalleryUri = null,
                localPrimaryAssetContentType = null,
                localPrimaryAssetFileName = null,
                isCachedOnly = false,
            )
        },
    )
}
