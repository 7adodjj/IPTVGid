package com.iptv.gid.data

import android.content.Context

object FavoritesRepository {

    private const val PREFS = "favorites_prefs"
    private const val KEY = "favorite_urls"

    private val favorites = mutableSetOf<String>()
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        favorites.addAll(prefs.getStringSet(KEY, emptySet()) ?: emptySet())
        initialized = true
    }

    fun isFavorite(channel: Channel) = favorites.contains(channel.url)

    fun toggle(context: Context, channel: Channel): Boolean {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return if (favorites.contains(channel.url)) {
            favorites.remove(channel.url)
            prefs.edit().putStringSet(KEY, favorites).apply()
            false
        } else {
            favorites.add(channel.url)
            prefs.edit().putStringSet(KEY, favorites).apply()
            true
        }
    }

    fun getFavoriteUrls(): Set<String> = favorites.toSet()
}
