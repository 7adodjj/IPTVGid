package com.iptv.gid.viewmodel

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.iptv.gid.data.Channel
import com.iptv.gid.data.ChannelGroup
import com.iptv.gid.data.EpgProgram
import com.iptv.gid.data.EpgRepository
import com.iptv.gid.data.FavoritesRepository
import com.iptv.gid.data.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class UiState<out T> {
    object Idle    : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String) : UiState<Nothing>()
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    companion object {
        private const val PREFS = "iptvgid_prefs"
        private const val KEY_URL = "last_url"
        private const val TAG = "GidVM"
    }

    private val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private val _allGroups = mutableListOf<ChannelGroup>()
    private var currentSearchQuery = ""
    private var currentGroup = "Все"
    private var showFavoritesOnly = false

    private val _channelState = MutableLiveData<UiState<List<Channel>>>(UiState.Idle)
    val channelState: LiveData<UiState<List<Channel>>> = _channelState

    private val _groups = MutableLiveData<List<String>>(emptyList())
    val groups: LiveData<List<String>> = _groups

    private val _epgState = MutableLiveData<UiState<List<EpgProgram>>>(UiState.Idle)
    val epgState: LiveData<UiState<List<EpgProgram>>> = _epgState

    private val _currentChannel = MutableLiveData<Channel?>()
    val currentChannel: LiveData<Channel?> = _currentChannel

    private val _epgStatus = MutableLiveData("")
    val epgStatus: LiveData<String> = _epgStatus

    val savedUrl: String get() = prefs.getString(KEY_URL, "") ?: ""

    init {
        FavoritesRepository.init(app)
    }

    fun loadPlaylist(url: String) {
        if (url.isBlank()) {
            _channelState.value = UiState.Error("Введите URL плейлиста")
            return
        }
        prefs.edit().putString(KEY_URL, url.trim()).apply()
        _channelState.value = UiState.Loading
        _epgStatus.value = ""
        showFavoritesOnly = false
        currentGroup = "Все"
        currentSearchQuery = ""

        viewModelScope.launch {
            try {
                val groupList = withContext(Dispatchers.IO) {
                    M3uParser.parseFromUrl(url.trim())
                }
                if (groupList.isEmpty()) {
                    _channelState.value = UiState.Error("Каналы не найдены в плейлисте")
                    return@launch
                }
                _allGroups.clear()
                _allGroups.addAll(groupList)

                val groupNames = listOf("Все") + groupList.map { it.name }
                _groups.value = groupNames

                applyFilter()

                val epgUrl = M3uParser.epgUrl
                if (epgUrl.isNotEmpty()) {
                    _epgStatus.value = "⏳ Загрузка EPG…"
                    withContext(Dispatchers.IO) {
                        EpgRepository.loadEpg(epgUrl) { success, count, error ->
                            if (success) {
                                _epgStatus.postValue("✓ EPG загружен ($count передач)")
                                applyFilter()
                            } else {
                                _epgStatus.postValue("✗ EPG: $error")
                            }
                        }
                    }
                } else {
                    _epgStatus.value = "EPG не найден в плейлисте"
                }

            } catch (e: Exception) {
                Log.e(TAG, "Ошибка загрузки", e)
                _channelState.value = UiState.Error("Ошибка: ${e.message}")
            }
        }
    }

    fun filterByGroup(groupName: String) {
        currentGroup = groupName
        showFavoritesOnly = false
        applyFilter()
    }

    fun showFavorites() {
        showFavoritesOnly = true
        currentGroup = "Все"
        applyFilter()
    }

    fun showAll() {
        showFavoritesOnly = false
        currentGroup = "Все"
        applyFilter()
    }

    fun searchChannels(query: String) {
        currentSearchQuery = query
        applyFilter()
    }

    fun toggleFavorite(channel: Channel): Boolean {
        val result = FavoritesRepository.toggle(getApplication(), channel)
        // Если мы в режиме избранного — обновляем список
        if (showFavoritesOnly) applyFilter()
        return result
    }

    private fun applyFilter() {
        var list = if (currentGroup == "Все") {
            _allGroups.flatMap { it.channels }
        } else {
            _allGroups.find { it.name == currentGroup }?.channels ?: emptyList()
        }

        if (showFavoritesOnly) {
            list = list.filter { FavoritesRepository.isFavorite(it) }
        }

        if (currentSearchQuery.isNotBlank()) {
            list = list.filter { it.name.contains(currentSearchQuery, ignoreCase = true) }
        }

        _channelState.postValue(UiState.Success(list))
    }

    fun selectChannel(channel: Channel) {
        _currentChannel.value = channel
        _epgState.value = UiState.Loading

        viewModelScope.launch {
            try {
                val programs = withContext(Dispatchers.IO) {
                    EpgRepository.getProgramsForChannel(channel)
                }
                _epgState.value = if (programs.isEmpty())
                    UiState.Error(
                        if (EpgRepository.isCacheLoaded())
                            "Нет данных EPG для\n${channel.name}"
                        else "EPG ещё загружается…"
                    )
                else UiState.Success(programs)
            } catch (e: Exception) {
                _epgState.value = UiState.Error("Ошибка: ${e.message}")
            }
        }
    }
}
