package com.king.sunday

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.king.sunday.ui.MainScreen
import com.king.sunday.ui.SundayTheme

class MainActivity : ComponentActivity() {

    private val core: SundayCore get() = sunday

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            core.refreshSetup()
            PresenceService.sync(this)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val wanted = arrayOf(
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.CALL_PHONE,
            Manifest.permission.READ_CONTACTS
        ).filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (wanted.isNotEmpty()) permLauncher.launch(wanted.toTypedArray())

        setContent {
            SundayTheme {
                MainScreen(core)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        core.appInForeground = true
    }

    override fun onResume() {
        super.onResume()
        core.refreshSetup()
        core.onOpened()
        PresenceService.sync(this)
    }

    override fun onStop() {
        core.appInForeground = false
        super.onStop()
    }
}
