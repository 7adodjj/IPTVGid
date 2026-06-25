package com.iptv.gid.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.chip.Chip
import com.google.android.material.snackbar.Snackbar
import com.iptv.gid.R
import com.iptv.gid.data.EpgProgram
import com.iptv.gid.databinding.ActivityMainBinding
import com.iptv.gid.viewmodel.MainViewModel
import com.iptv.gid.viewmodel.UiState

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: MainViewModel by viewModels()

    private lateinit var channelAdapter: ChannelAdapter
    private lateinit var epgAdapter: EpgAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupDrawer()
        setupRecyclers()
        setupListeners()
        observeVm()

        if (vm.savedUrl.isNotEmpty()) {
            binding.etUrl.setText(vm.savedUrl)
        }
    }

    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
    }

    private fun setupDrawer() {
        val toggle = ActionBarDrawerToggle(
            this, binding.drawerLayout, binding.toolbar,
            R.string.app_name, R.string.app_name
        )
        binding.drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        binding.navigationView.setNavigationItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_all -> {
                    vm.showAll()
                    binding.etSearch.setText("")
                }
                R.id.nav_favorites -> {
                    vm.showFavorites()
                    binding.etSearch.setText("")
                }
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun setupRecyclers() {
        channelAdapter = ChannelAdapter(
            onClick = { channel -> vm.selectChannel(channel) },
            onFavoriteToggle = { channel ->
                val added = vm.toggleFavorite(channel)
                val msg = if (added) "⭐ Добавлено в избранное" else "Удалено из избранного"
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
        )
        binding.rvChannels.layoutManager = LinearLayoutManager(this)
        binding.rvChannels.adapter = channelAdapter
        binding.rvChannels.setHasFixedSize(false)

        epgAdapter = EpgAdapter()
        binding.rvEpg.layoutManager = LinearLayoutManager(this)
        binding.rvEpg.adapter = epgAdapter
    }

    private fun setupListeners() {
        // Загрузка плейлиста
        binding.btnLoad.setOnClickListener {
            hideKeyboard()
            vm.loadPlaylist(binding.etUrl.text.toString().trim())
        }
        binding.etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO) {
                binding.btnLoad.performClick(); true
            } else false
        }

        // Поиск — TextWatcher срабатывает на каждый символ
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                vm.searchChannels(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun observeVm() {
        vm.channelState.observe(this) { state ->
            when (state) {
                is UiState.Idle -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.visibility = View.GONE
                }
                is UiState.Loading -> {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.tvError.visibility = View.GONE
                    binding.rvChannels.visibility = View.GONE
                    binding.scrollGroups.visibility = View.GONE
                    binding.tilSearch.visibility = View.GONE
                }
                is UiState.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.visibility = View.GONE
                    binding.rvChannels.visibility = View.VISIBLE
                    binding.tilSearch.visibility = View.VISIBLE
                    channelAdapter.submitList(state.data)
                }
                is UiState.Error -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvError.visibility = View.VISIBLE
                    binding.tvError.text = state.message
                    binding.rvChannels.visibility = View.GONE
                    binding.tilSearch.visibility = View.GONE
                }
            }
        }

        vm.groups.observe(this) { groups ->
            if (groups.size > 1) {
                binding.scrollGroups.visibility = View.VISIBLE
                binding.chipGroupFilters.removeAllViews()
                groups.forEach { groupName ->
                    val chip = Chip(this).apply {
                        text = groupName
                        isCheckable = true
                        isChecked = groupName == "Все"
                        setChipBackgroundColorResource(R.color.colorPrimary)
                        setTextColor(getColor(android.R.color.white))
                        chipStrokeWidth = 0f
                        setOnCheckedChangeListener { _, checked ->
                            if (checked) {
                                binding.etSearch.setText("")
                                vm.filterByGroup(groupName)
                            }
                        }
                    }
                    binding.chipGroupFilters.addView(chip)
                }
            }
        }

        vm.epgStatus.observe(this) { status ->
            binding.tvEpgStatus.visibility = if (status.isNullOrEmpty()) View.GONE else View.VISIBLE
            binding.tvEpgStatus.text = status
        }

        vm.currentChannel.observe(this) { channel ->
            if (channel != null) {
                binding.panelEpg.visibility = View.VISIBLE
                binding.dividerEpg.visibility = View.VISIBLE
                binding.tvCurrentChannel.text = channel.name
            }
        }

        vm.epgState.observe(this) { state ->
            when (state) {
                is UiState.Idle -> {
                    binding.progressEpg.visibility = View.GONE
                    binding.rvEpg.visibility = View.GONE
                    binding.tvEpgError.visibility = View.GONE
                }
                is UiState.Loading -> {
                    binding.progressEpg.visibility = View.VISIBLE
                    binding.rvEpg.visibility = View.GONE
                    binding.tvEpgError.visibility = View.GONE
                }
                is UiState.Success -> {
                    binding.progressEpg.visibility = View.GONE
                    binding.tvEpgError.visibility = View.GONE
                    binding.rvEpg.visibility = View.VISIBLE
                    epgAdapter.submitList(state.data)
                }
                is UiState.Error -> {
                    binding.progressEpg.visibility = View.GONE
                    binding.rvEpg.visibility = View.GONE
                    binding.tvEpgError.visibility = View.VISIBLE
                    binding.tvEpgError.text = state.message
                }
            }
        }
    }

    override fun onBackPressed() {
        if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
            binding.drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.etUrl.windowToken, 0)
    }
}
