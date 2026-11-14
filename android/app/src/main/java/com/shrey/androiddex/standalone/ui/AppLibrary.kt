package com.shrey.androiddex.standalone.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrey.androiddex.standalone.data.AppEntry

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppLibrary(vm: DesktopViewModel, modifier: Modifier = Modifier, compact: Boolean = false, onOpened: () -> Unit = {}) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val loading by vm.appsLoading.collectAsStateWithLifecycle()
    val pins by vm.pins.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val filtered = remember(apps, query, filter, pins, recents) {
        val base = when (filter) { 1 -> apps.filter { it.packageName in pins }; 2 -> recents.mapNotNull { r -> apps.find { it.packageName == r.packageName } }; else -> apps }
        base.filter { it.label.contains(query, true) || it.packageName.contains(query, true) }
    }
    Column(modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().semantics { contentDescription = "Search applications" }, placeholder = { Text("Search applications", fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(19.dp)) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear search", Modifier.size(17.dp)) } },
            singleLine = true, textStyle = MaterialTheme.typography.bodyMedium, shape = RoundedCornerShape(10.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("All apps", "Pinned", "Recent").forEachIndexed { index, label ->
                FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label, fontSize = 11.sp) })
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = vm::refreshApps, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Refresh, "Refresh installed applications", Modifier.size(17.dp)) }
        }
        if (loading && apps.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
        else if (filtered.isEmpty()) EmptyMessage(if (query.isNotBlank()) "No matching applications" else if (filter == 1) "No pinned apps yet" else if (filter == 2) "No recent launches" else "No launchable apps found",
            if (filter == 1) "Long-press an app to pin it to the desktop and taskbar." else if (filter == 2) "Apps you open from DeX appear here. This is local launch history, not a running-process list." else "Try refreshing the app list.")
        else LazyVerticalGrid(columns = GridCells.Adaptive(if (compact) 82.dp else 96.dp), modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp), contentPadding = PaddingValues(bottom = 10.dp)) {
            items(filtered, key = { it.component }) { app ->
                Column(Modifier.fillMaxWidth().combinedClickable(onClick = { vm.launchApp(app); onOpened() }, onLongClick = {
                    vm.togglePin(app.packageName); vm.notify(if (app.packageName in vm.pins.value) "${app.label} pinned" else "${app.label} unpinned")
                }).padding(vertical = 9.dp, horizontal = 3.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box {
                        AppIcon(vm.appsRepository, app.packageName, if (compact) 40.dp else 46.dp)
                        if (app.packageName in pins) Icon(Icons.Default.Star, "Pinned", Modifier.size(13.dp).align(Alignment.TopEnd), tint = DexMint)
                    }
                    Text(app.label, fontSize = if (compact) 10.sp else 11.sp, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, lineHeight = 14.sp)
                }
            }
        }
        if (!compact) Text("Long-press an app to pin or unpin. Right-click desktop shortcuts for more options.", color = DexMuted, fontSize = 10.sp)
    }
}
