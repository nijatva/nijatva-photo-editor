package com.nijatva.photo.editor

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.nijatva.photo.editor.ui.AboutScreen
import com.nijatva.photo.editor.ui.AmoledBlack
import com.nijatva.photo.editor.ui.EditorScreen
import com.nijatva.photo.editor.ui.HomeScreen
import com.nijatva.photo.editor.ui.NijatvaTheme

class MainActivity : ComponentActivity() {

    private var incoming by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming = uriFrom(intent)
        setContent {
            NijatvaTheme {
                NijatvaApp(initialUri = incoming)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        uriFrom(intent)?.let { incoming = it }
    }

    private fun uriFrom(intent: Intent?): Uri? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_SEND ->
                @Suppress("DEPRECATION") intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
            Intent.ACTION_EDIT, Intent.ACTION_VIEW -> intent.data
            else -> null
        }
    }
}

private enum class Screen { HOME, EDITOR, ABOUT }

@Composable
private fun NijatvaApp(initialUri: Uri?) {
    var screen by remember { mutableStateOf(if (initialUri != null) Screen.EDITOR else Screen.HOME) }
    var editUri by remember { mutableStateOf(initialUri) }

    LaunchedEffect(initialUri) {
        if (initialUri != null) {
            editUri = initialUri
            screen = Screen.EDITOR
        }
    }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { picked ->
        if (picked != null) {
            editUri = picked
            screen = Screen.EDITOR
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(AmoledBlack)) {
        when (screen) {
            Screen.HOME -> HomeScreen(
                onOpenPhoto = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
                onAbout = { screen = Screen.ABOUT }
            )

            Screen.EDITOR -> {
                val current = editUri
                if (current != null) {
                    EditorScreen(uri = current, onBack = { screen = Screen.HOME })
                } else {
                    LaunchedEffect(Unit) { screen = Screen.HOME }
                }
            }

            Screen.ABOUT -> AboutScreen(onBack = { screen = Screen.HOME })
        }
    }
}
