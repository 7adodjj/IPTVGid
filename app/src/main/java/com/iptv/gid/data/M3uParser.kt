package com.iptv.gid.data

import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream

object M3uParser {

    var epgUrl: String = ""
        private set

    private const val TAG = "M3uParser"

    fun parseFromUrl(url: String): List<ChannelGroup> {
        val text = fetchUrl(url)
        Log.d(TAG, "Получено ${text.length} символов, первые 300: ${text.take(300)}")
        return parse(text)
    }

    private fun fetchUrl(url: String): String {
        var currentUrl = url
        var redirectCount = 0
        while (redirectCount < 5) {
            val connection = URL(currentUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.setRequestProperty("Accept-Encoding", "gzip, deflate")
            connection.instanceFollowRedirects = false

            val code = connection.responseCode
            Log.d(TAG, "HTTP $code для $currentUrl")

            if (code in 300..399) {
                currentUrl = connection.getHeaderField("Location") ?: break
                redirectCount++
                connection.disconnect()
                continue
            }

            val stream = if (connection.contentEncoding?.contains("gzip") == true) {
                GZIPInputStream(connection.inputStream)
            } else {
                connection.inputStream
            }

            val charset = connection.contentType
                ?.let { Regex("charset=([\\w-]+)").find(it)?.groupValues?.get(1) }
                ?: "UTF-8"

            return BufferedReader(InputStreamReader(stream, charset)).use { it.readText() }
        }
        throw Exception("Слишком много редиректов")
    }

    fun parse(content: String): List<ChannelGroup> {
        epgUrl = ""
        val channels = mutableListOf<Channel>()

        // Нормализуем переносы строк
        val lines = content.replace("\r\n", "\n").replace("\r", "\n").lines()
        Log.d(TAG, "Строк в плейлисте: ${lines.size}")

        // Извлекаем EPG URL из заголовка #EXTM3U
        val header = lines.firstOrNull { it.trimStart().startsWith("#EXTM3U") } ?: ""
        epgUrl = Regex("""url-tvg="([^"]+)"""").find(header)?.groupValues?.get(1) ?: ""
        if (epgUrl.isEmpty()) {
            epgUrl = Regex("""tvg-url="([^"]+)"""").find(header)?.groupValues?.get(1) ?: ""
        }
        Log.d(TAG, "EPG URL: '$epgUrl'")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("#EXTINF")) {
                val name  = Regex(""",(.+)$""").find(line)?.groupValues?.get(1)?.trim() ?: ""
                val tvgId = Regex("""tvg-id="([^"]*)"""").find(line)?.groupValues?.get(1) ?: ""
                val logo  = Regex("""tvg-logo="([^"]*)"""").find(line)?.groupValues?.get(1) ?: ""

                // group-title внутри #EXTINF
                var group = Regex("""group-title="([^"]*)"""").find(line)?.groupValues?.get(1) ?: ""

                // Смотрим следующие строки — ищем #EXTGRP, пропускаем другие директивы
                var j = i + 1
                var streamUrl = ""
                while (j < lines.size) {
                    val next = lines[j].trim()
                    when {
                        next.startsWith("#EXTGRP:") -> {
                            // Группа из #EXTGRP (приоритет если group-title пустой)
                            if (group.isEmpty()) {
                                group = next.removePrefix("#EXTGRP:").trim()
                            }
                            j++
                        }
                        next.startsWith("#") -> j++ // другие директивы — пропускаем
                        next.isBlank() -> j++
                        else -> {
                            streamUrl = next
                            break
                        }
                    }
                }

                if (group.isEmpty()) group = "Без группы"

                if (streamUrl.isNotEmpty()) {
                    channels.add(Channel(name, streamUrl, logo, tvgId, group))
                } else {
                    Log.w(TAG, "Пустой URL для канала '$name'")
                }
                i = j + 1
            } else {
                i++
            }
        }

        Log.d(TAG, "Найдено каналов: ${channels.size}")

        return channels
            .groupBy { it.group }
            .map { (groupName, list) -> ChannelGroup(groupName, list) }
    }
}
