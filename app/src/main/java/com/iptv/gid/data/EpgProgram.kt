package com.iptv.gid.data

data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L
)
