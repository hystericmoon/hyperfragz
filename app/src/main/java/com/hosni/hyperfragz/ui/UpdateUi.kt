package com.hosni.hyperfragz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hosni.hyperfragz.Updater
import com.hosni.hyperfragz.Updater.State
import kotlin.math.roundToInt

val LocalUpdater = staticCompositionLocalOf<Updater?> { null }

/** "HyperFragz 1.2.0 is available · Update", under the index header. */
@Composable
fun UpdateBanner() {
    val updater = LocalUpdater.current ?: return
    val s = updater.state
    val text = when (s) {
        is State.Available -> "HyperFragz ${s.version} is available"
        is State.Downloading -> "Downloading ${s.version}… ${(s.progress * 100).roundToInt()}%"
        else -> return
    }
    if (updater.dismissed && s is State.Available) return
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .background(V.Surface, V.Radius)
            .padding(start = 16.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Column(Modifier.weight(1f).padding(vertical = 10.dp)) {
            Text(text, style = sans(14, V.Text, 500))
            if (s is State.Downloading) MeterBar(s.progress, V.Primary, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
        if (s is State.Available) {
            Text(
                "Update",
                style = sans(14, V.Primary, 500),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .clickable { updater.installAsync(s) }
                    .semantics { role = Role.Button }
                    .padding(horizontal = 12.dp, vertical = 14.dp),
            )
            IconButton(onClick = { updater.dismissed = true }) {
                Icon(Icons.Default.Close, contentDescription = "Dismiss update", tint = V.Text2, modifier = Modifier.size(18.dp))
            }
        } else {
            Row(Modifier.size(16.dp)) {}
        }
    }
}

/** Overflow-menu entry: "Check for updates", with the installed version under it. */
@Composable
fun UpdateMenuItem(onDismiss: () -> Unit, onResult: (String) -> Unit) {
    val updater = LocalUpdater.current ?: return
    DropdownMenuItem(
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("Check for updates", style = sans(15))
                Text("Version ${updater.currentVersion}", style = sans(12, V.Text3))
            }
        },
        onClick = {
            onDismiss()
            updater.dismissed = false
            updater.checkAsync { s ->
                when (s) {
                    is State.UpToDate -> onResult("HyperFragz is up to date (${updater.currentVersion}).")
                    is State.Failed -> onResult(s.message)
                    else -> Unit // the banner shows the update
                }
            }
        },
        modifier = Modifier.heightIn(min = 56.dp),
    )
}
