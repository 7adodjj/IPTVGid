package com.iptv.gid.data

import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

object EpgRepository {

    private val programs = mutableMapOf<String, MutableList<EpgProgram>>()
    private var loaded = false
    private val fmt = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US)

    fun isCacheLoaded() = loaded
    fun cacheSize() = programs.size

    fun loadEpg(url: String, callback: (Boolean, Int, String?) -> Unit) {
        try {
            programs.clear()
            loaded = false
            val connection = URL(url).openConnection()
            connection.connectTimeout = 15_000
            connection.readTimeout = 60_000
            var stream: InputStream = connection.getInputStream()
            if (url.endsWith(".gz", ignoreCase = true) ||
                connection.contentType?.contains("gzip") == true) {
                stream = GZIPInputStream(stream)
            }
            parseXml(stream)
            loaded = true
            val total = programs.values.sumOf { it.size }
            Log.d("EpgRepo", "Загружено $total передач для ${programs.size} каналов")
            callback(true, total, null)
        } catch (e: Exception) {
            Log.e("EpgRepo", "Ошибка загрузки EPG", e)
            callback(false, 0, e.message)
        }
    }

    private fun parseXml(stream: InputStream) {
        val factory = XmlPullParserFactory.newInstance()
        val parser = factory.newPullParser()
        parser.setInput(stream, null)
        var channelId = ""; var title = ""; var desc = ""
        var start = 0L; var end = 0L
        var inTitle = false; var inDesc = false
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> when (parser.name) {
                    "programme" -> {
                        channelId = parser.getAttributeValue(null, "channel") ?: ""
                        start = parseTime(parser.getAttributeValue(null, "start"))
                        end = parseTime(parser.getAttributeValue(null, "stop"))
                        title = ""; desc = ""
                    }
                    "title" -> inTitle = true
                    "desc"  -> inDesc = true
                }
                XmlPullParser.TEXT -> {
                    if (inTitle) title += parser.text
                    if (inDesc)  desc  += parser.text
                }
                XmlPullParser.END_TAG -> when (parser.name) {
                    "programme" -> {
                        if (channelId.isNotEmpty() && title.isNotEmpty()) {
                            programs.getOrPut(channelId) { mutableListOf() }
                                .add(EpgProgram(channelId, title, desc, start, end))
                        }
                    }
                    "title" -> inTitle = false
                    "desc"  -> inDesc  = false
                }
            }
            event = parser.next()
        }
    }

    private fun parseTime(raw: String?): Long {
        if (raw.isNullOrEmpty()) return 0L
        return try { fmt.parse(raw.trim())?.time ?: 0L } catch (e: Exception) { 0L }
    }

    // Только будущие и текущие передачи
    fun getProgramsForChannel(channel: Channel): List<EpgProgram> {
        val now = System.currentTimeMillis()
        return findPrograms(channel)?.filter { it.endTime > now } ?: emptyList()
    }

    // Все передачи включая прошедшие (для отображения в списке каналов)
    fun getProgramsForChannelAll(channel: Channel): List<EpgProgram> {
        return findPrograms(channel) ?: emptyList()
    }

    private fun findPrograms(channel: Channel): List<EpgProgram>? {
        val id = channel.tvgId
        programs[id]?.let { return it }
        val key = programs.keys.firstOrNull { it.equals(id, ignoreCase = true) }
        return programs[key]
    }
}
