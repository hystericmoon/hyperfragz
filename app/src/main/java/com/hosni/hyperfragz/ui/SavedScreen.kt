package com.hosni.hyperfragz.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hosni.hyperfragz.Detail
import com.hosni.hyperfragz.SiteController

/** Heart + count in the index header; opens [SavedScreen]. */
@Composable
fun SavedButton(count: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .heightIn(min = 48.dp)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = if (count == 0) "Saved perfumes, none yet" else "Saved perfumes, $count"
            }
            .padding(horizontal = 10.dp),
    ) {
        Icon(Icons.Default.FavoriteBorder, null, tint = V.Text2, modifier = Modifier.size(20.dp))
        if (count > 0) {
            Spacer(Modifier.width(5.dp))
            Text(count.toString(), style = sans(13, V.Text2, 500))
        }
    }
}

/** Perfumes saved with the heart on a perfume page, newest first. */
@Composable
fun SavedScreen(site: SiteController, onBack: () -> Unit) {
    val items = site.favorites.items
    Column(
        Modifier
            .fillMaxSize()
            .background(V.Ground)
            .statusBarsPadding()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 20.dp, top = 14.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Back", tint = V.Text)
            }
            Text("Saved", style = serif(34), modifier = Modifier
                .weight(1f)
                .semantics { heading() })
            if (items.isNotEmpty()) Text(
                if (items.size == 1) "1 perfume" else "${items.size} perfumes",
                style = sans(14, V.Text2),
            )
        }
        Hairline(Modifier.padding(top = 17.dp))
        if (items.isEmpty()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 40.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Nothing saved yet.", style = serif(28))
                Text("Tap the heart on a perfume to keep it here.", style = sans(15, V.Text2))
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(23.dp),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 17.dp, bottom = 40.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(items, key = { it.url }) { h -> HitCard(h) { site.openDetail(Detail.preview(h)) } }
            }
        }
    }
}
