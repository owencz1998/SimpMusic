package com.maxrave.simpmusic.ui

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.text.util.Linkify
import android.util.Log
import android.view.View
import android.view.animation.AnimationUtils
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.maxrave.simpmusic.R
import com.maxrave.simpmusic.common.Config
import com.maxrave.simpmusic.common.FIRST_TIME_MIGRATION
import com.maxrave.simpmusic.common.SELECTED_LANGUAGE
import com.maxrave.simpmusic.common.STATUS_DONE
import com.maxrave.simpmusic.common.SUPPORTED_LANGUAGE
import com.maxrave.simpmusic.data.dataStore.DataStoreManager
import com.maxrave.simpmusic.databinding.ActivityMainBinding
import com.maxrave.simpmusic.extension.isMyServiceRunning
import com.maxrave.simpmusic.extension.navigateSafe
import com.maxrave.simpmusic.service.SimpleMediaService
import com.maxrave.simpmusic.ui.screen.MiniPlayer
import com.maxrave.simpmusic.ui.theme.AppTheme
import com.maxrave.simpmusic.utils.VersionManager
import com.maxrave.simpmusic.viewModel.SharedViewModel
import dev.chrisbanes.insetter.applyInsetter
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import pub.devrel.easypermissions.EasyPermissions
import java.util.Locale

@UnstableApi
@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    val viewModel by viewModels<SharedViewModel>()
    private var action: String? = null
    private var data: Uri? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            if (service is SimpleMediaService.MusicBinder) {
                Log.w("MainActivity", "onServiceConnected: ")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            Log.w("MainActivity", "onServiceDisconnected: ")
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        action = intent.action
        data = intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri()
        Log.d("MainActivity", "onNewIntent: $data")
        viewModel.intent.value = intent
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onResume() {
        super.onResume()
        Log.d("MainActivity", "onResume: ")
    }

    @UnstableApi
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        VersionManager.initialize(applicationContext)
        checkForUpdate()
        if (viewModel.recreateActivity.value == true) {
            viewModel.activityRecreateDone()
        } else {
            startMusicService()
        }
        Log.d("MainActivity", "onCreate: ")
        action = intent.action
        data = intent?.data ?: intent?.getStringExtra(Intent.EXTRA_TEXT)?.toUri()
        if (data != null) {
            viewModel.intent.value = intent
        }
        Log.d("Italy", "Key: ${Locale.ITALY.toLanguageTag()}")

        // Check if the migration has already been done or not
        if (getString(FIRST_TIME_MIGRATION) != STATUS_DONE) {
            Log.d("Locale Key", "onCreate: ${Locale.getDefault().toLanguageTag()}")
            if (SUPPORTED_LANGUAGE.codes.contains(Locale.getDefault().toLanguageTag())) {
                Log.d("Contains", "onCreate: ${SUPPORTED_LANGUAGE.codes.contains(Locale.getDefault().toLanguageTag())}")
                putString(SELECTED_LANGUAGE, Locale.getDefault().toLanguageTag())
                putString("location", if (SUPPORTED_LOCATION.items.contains(Locale.getDefault().country)) Locale.getDefault().country else "US")
            } else {
                putString(SELECTED_LANGUAGE, "en-US")
            }
            getString(SELECTED_LANGUAGE)?.let {
                Log.d("Locale Key", "getString: $it")
                val localeList = LocaleListCompat.forLanguageTags(it)
                AppCompatDelegate.setApplicationLocales(localeList)
                putString(FIRST_TIME_MIGRATION, STATUS_DONE)
            }
        }
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() != getString(SELECTED_LANGUAGE)) {
            Log.d("Locale Key", "onCreate: ${AppCompatDelegate.getApplicationLocales().toLanguageTags()}")
            putString(SELECTED_LANGUAGE, AppCompatDelegate.getApplicationLocales().toLanguageTags())
        }

        enableEdgeToEdge(
            navigationBarStyle = SystemBarStyle.auto(
                lightScrim = Color.Transparent.toArgb(),
                darkScrim = Color.Transparent.toArgb(),
            ),
        )
        viewModel.checkIsRestoring()
        viewModel.runWorker()

        if (!EasyPermissions.hasPermissions(this, Manifest.permission.POST_NOTIFICATIONS)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                EasyPermissions.requestPermissions(
                    this,
                    getString(R.string.this_app_needs_to_access_your_notification),
                    1,
                    Manifest.permission.POST_NOTIFICATIONS,
                )
            }
        }
        viewModel.getLocation()
        viewModel.checkAllDownloadingSongs()
        runBlocking { delay(500) }

        binding = ActivityMainBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        // Navigation setup
        val navHostFragment = supportFragmentManager.findFragmentById(R.id.fragment_container_view)
        val navController = navHostFragment?.findNavController()

        // MiniPlayer setup
        binding.miniplayer.setContent {
            AppTheme {
                MiniPlayer(
                    sharedViewModel = viewModel,
                    onClose = { onCloseMiniplayer() },
                    onOpenMainPlayer = {
                        // Navigate to the main player when opening
                        val bundle = Bundle()
                        bundle.putString("type", Config.MINIPLAYER_CLICK)
                        navController?.navigateSafe(R.id.action_global_nowPlayingFragment, bundle)
                    }
                )
            }
        }

        // Set visibility of MiniPlayer based on nowPlayingState
        if (viewModel.nowPlayingState.value?.mediaItem == MediaItem.EMPTY || viewModel.nowPlayingState.value?.mediaItem == null) {
            binding.miniplayer.visibility = View.GONE
        }

        binding.root.addOnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            val rect = Rect(left, top, right, bottom)
            val oldRect = Rect(oldLeft, oldTop, oldRight, oldBottom)
            if ((rect.width() != oldRect.width() || rect.height() != oldRect.height()) && oldRect != Rect(0, 0, 0, 0)) {
                viewModel.activityRecreate()
            }
        }

        binding.bottomNavigationView.applyInsetter {
            type(navigationBars = true) {
                padding()
            }
        }

        if (!isMyServiceRunning(SimpleMediaService::class.java)) {
            binding.miniplayer.visibility = View.GONE
        }
    }

    private fun onCloseMiniplayer() {
        // Logic to handle closing the MiniPlayer
        binding.miniplayer.visibility = View.GONE
    }

    private fun startMusicService() {
        // Logic to start your music service
        Intent(this, SimpleMediaService::class.java).also { intent ->
            startService(intent)
            bindService(intent, serviceConnection, BIND_AUTO_CREATE)
        }
    }

    private fun checkForUpdate() {
        // Logic to check for updates
    }

    private fun putString(key: String, value: String) {
        // Logic to store a string in SharedPreferences or DataStore
    }
}
