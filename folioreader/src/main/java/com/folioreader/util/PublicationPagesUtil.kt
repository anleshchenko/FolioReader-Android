package com.folioreader.util

import org.readium.r2.shared.Link
import org.readium.r2.shared.Locations
import org.readium.r2.shared.Locator
import org.readium.r2.streamer.container.Container
import org.readium.r2.streamer.container.ContainerEpub
import org.readium.r2.streamer.parser.PubBox
import java.io.FileNotFoundException
import java.net.URI
import java.util.zip.ZipEntry
import kotlin.math.ceil

object PublicationPagesUtil {

    fun getTotalPageCount(pubBox: PubBox) = getPublicationPagesLocators(pubBox)

    fun getPageLocator(pubBox: PubBox, pageNumber: Int) =
        getPublicationPagesLocators(pubBox)[pageNumber - 1]

    private fun getPublicationPagesLocators(pubBox: PubBox): List<Locator> {
        var lastPositionOfPreviousResource = 0
        return pubBox.publication.readingOrder.flatMap { link ->
            val positions =
                if (link.properties.layout == "fixed") {
                    createFixed(link, lastPositionOfPreviousResource)
                } else {
                    createReflowable(link, lastPositionOfPreviousResource, pubBox.container)
                }

            positions.lastOrNull()?.locations?.position?.let {
                lastPositionOfPreviousResource = it.toInt()
            }

            positions
        }
    }

    private fun createFixed(link: Link, startPosition: Int) = listOf(
        createLocator(
            link,
            progression = 0.0,
            position = startPosition + 1
        )
    )

    private fun createReflowable(
        link: Link,
        startPosition: Int,
        container: Container,
        reflowablePositionLength: Long = 1024L
    ): List<Locator> {
        val length = link.properties.encryption?.originalLength?.toLong()
            ?: dataLength(container as ContainerEpub, link.href!!)

        val pageCount = ceil(length / reflowablePositionLength.toDouble()).toInt()
            .coerceAtLeast(1)

        return (1..pageCount).map { position ->
            createLocator(
                link,
                progression = (position - 1) / pageCount.toDouble(),
                position = startPosition + position
            )
        }
    }

    private fun createLocator(link: Link, progression: Double, position: Int) = Locator(
        href = link.href!!,
        title = link.title.orEmpty(),
        locations = Locations(
            progression = progression,
            position = position.toLong()
        ),
        created = System.currentTimeMillis(),
        text = null
    )

    private fun dataLength(containerEpub: ContainerEpub, relativePath: String): Long {
        return try {
            getEntry(containerEpub, relativePath).size
        } catch (e: Exception) {
            0L
        }
    }

    private fun getEntry(containerEpub: ContainerEpub, relativePath: String): ZipEntry {
        var path: String = try {
            URI(relativePath).path
        } catch (e: Exception) {
            relativePath
        }

        path = path.removePrefix("/")

        var zipEntry = containerEpub.zipFile.getEntry(path)
        if (zipEntry != null)
            return zipEntry

        val zipEntries = containerEpub.zipFile.entries()
        while (zipEntries.hasMoreElements()) {
            zipEntry = zipEntries.nextElement()
            if (path.equals(zipEntry.name, true))
                return zipEntry
        }

        throw FileNotFoundException()
    }
}