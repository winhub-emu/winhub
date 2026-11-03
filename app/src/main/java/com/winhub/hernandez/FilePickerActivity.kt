package com.winhub.hernandez

import android.app.Activity
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import com.winhub.hernandez.ui.screens.FileManagerScreen
import com.winhub.hernandez.ui.screens.LocalDeckPortrait
import com.winhub.hernandez.ui.theme.AppThemeState
import com.winhub.hernandez.ui.theme.WinlatorTheme
import java.io.File

/**
 * Themed host for the app's own File Manager in pick mode (issue #73), in its Deck style (portrait or landscape layout). Every import call site — the
 * Compose screens and the legacy Java Activities/Fragments — launches this via ActivityResult and
 * reads the picked path back from the {@code selectedFile} extra. Downstream import code is
 * unchanged: callers wrap the path as {@code Uri.fromFile(File(path))}.
 *
 * Launch extras:
 *  - {@code extensions}       String[] of allowed lowercase extensions (no dot). Absent/empty = all.
 *  - {@code initialDirectory} start path. Absent falls back to the remembered dir, then Download.
 *  - {@code pickerTitle}      optional header title.
 *  - {@code containerDriveC}  optional abs path of the chosen container's C: drive; when present the
 *                             picker offers a "Drive C:" location (browse/pick a game living on C:).
 * Result: {@code selectedFile} (absolute path) on RESULT_OK.
 */
class FilePickerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // WinlatorTheme reads AppThemeState; init it in case MainActivity hasn't run yet.
        AppThemeState.init(this)

        val extensions = intent.getStringArrayExtra(EXTRA_EXTENSIONS)?.toList() ?: emptyList()
        val initialDir = intent.getStringExtra(EXTRA_INITIAL_DIRECTORY)?.let { File(it) }
        val title = intent.getStringExtra(EXTRA_PICKER_TITLE)
        val pickDir = intent.getBooleanExtra(EXTRA_PICK_DIRECTORY, false)
        // Optional: the chosen container's C: drive, so the picker can offer a "Drive C:" location
        // (add-a-game-from-C: flow). Absent for every other picker → C: option stays hidden.
        val driveCPath = intent.getStringExtra(EXTRA_CONTAINER_DRIVE_C)?.let { File(it) }

        setContent {
            WinlatorTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    // The Deck file manager in the orientation the picker was opened in: portrait gets
                    // the touch-first layout (locations strip on top), landscape the rail layout.
                    val portrait = LocalConfiguration.current.orientation == Configuration.ORIENTATION_PORTRAIT
                    CompositionLocalProvider(LocalDeckPortrait provides portrait) {
                        Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                            FileManagerScreen(
                                pickMode = true,
                                pickDirMode = pickDir,
                                pickExtensions = extensions,
                                initialDir = initialDir,
                                pickerTitle = title,
                                driveCPath = driveCPath,
                                onPick = { file ->
                                    setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_SELECTED_FILE, file.absolutePath))
                                    finish()
                                },
                                deckMode = true,
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_EXTENSIONS = "extensions"
        const val EXTRA_INITIAL_DIRECTORY = "initialDirectory"
        const val EXTRA_PICKER_TITLE = "pickerTitle"
        const val EXTRA_PICK_DIRECTORY = "pickDirectory"
        const val EXTRA_CONTAINER_DRIVE_C = "containerDriveC"
        const val EXTRA_SELECTED_FILE = "selectedFile"
    }
}
