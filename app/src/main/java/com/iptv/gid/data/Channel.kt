package com.iptv.gid.data

data class Channel(
    val name: String,
    val url: String,
    val logoUrl: String = "",
    val tvgId: String = "",
    val group: String = ""
)
