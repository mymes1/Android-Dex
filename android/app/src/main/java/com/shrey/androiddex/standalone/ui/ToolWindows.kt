package com.shrey.androiddex.standalone.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shrey.androiddex.standalone.data.*
import com.shrey.androiddex.standalone.system.ShizukuManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun NotesContent(vm: DesktopViewModel, window: DesktopWindow) {
    val noteId = window.noteId ?: return
    var text by remember(noteId) { mutableStateOf(vm.preferences.note(noteId)) }
    var saved by remember(noteId) { mutableStateOf(true) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val latestText by rememberUpdatedState(text)
    val notes = vm.preferences.notes()
    LaunchedEffect(noteId, text) {
        saved = false
        delay(450)
        if (vm.preferences.notes().any { it.id == noteId }) { vm.saveNote(noteId, text); saved = true }
    }
    DisposableEffect(noteId) {
        onDispose { if (vm.preferences.notes().any { it.id == noteId }) vm.saveNote(noteId, latestText) }
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { menu = true }) { Text(notes.firstOrNull { it.id == noteId }?.title ?: "Notes"); Icon(Icons.Default.ArrowDropDown, "Choose note") }
                DropdownMenu(menu, { menu = false }) {
                    notes.forEach { note -> DropdownMenuItem(text = { Text(note.title, maxLines = 1) }, onClick = { menu = false; vm.openNote(note.id) }) }
                }
            }
            Spacer(Modifier.weight(1f))
            Text(if (saved) "Saved locally" else "Saving…", color = DexMuted, fontSize = 10.sp)
            IconButton(onClick = { vm.openNote(null) }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.NoteAdd, "New note", Modifier.size(19.dp)) }
            IconButton(onClick = { confirmDelete = true }, modifier = Modifier.size(36.dp)) { Icon(Icons.Default.Delete, "Delete note", Modifier.size(18.dp)) }
        }
        OutlinedTextField(text, { text = it }, Modifier.fillMaxSize(), placeholder = { Text("Start writing. Your note saves automatically on this tablet.") },
            textStyle = MaterialTheme.typography.bodyMedium, minLines = 6)
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Delete this note?") },
        text = { Text("The note will be removed from local storage. This cannot be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.deleteNote(noteId) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } })
}

private data class FileItem(val uri: Uri, val id: String, val name: String, val directory: Boolean, val mime: String, val size: Long, val modified: Long)

@Composable
fun FilesContent(vm: DesktopViewModel) {
    val context = LocalContext.current
    var root by remember { mutableStateOf(vm.preferences.savedFolder()?.let(Uri::parse)) }
    val path = remember(root) { mutableStateListOf<Uri>().apply { root?.let { add(it) } } }
    var items by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var revision by remember { mutableIntStateOf(0) }
    var create by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf("") }
    var delete by remember { mutableStateOf<FileItem?>(null) }
    val current = path.lastOrNull() ?: root
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
                .recoverCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                .onFailure { vm.notify("Folder opened, but Android did not grant persistent access.") }
            root = uri; path.clear(); path.add(uri); vm.preferences.saveFolder(uri.toString()); revision++
        }
    }
    LaunchedEffect(current, revision) {
        if (current == null || root == null) { items = emptyList(); return@LaunchedEffect }
        loading = true; error = null
        runCatching { withContext(Dispatchers.IO) { listDocuments(context, root!!, current) } }
            .onSuccess { items = it }.onFailure { items = emptyList(); error = "Cannot read this folder. Access may have been revoked. Choose the folder again." }
        loading = false
    }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            IconButton(onClick = { if (path.size > 1) path.removeAt(path.lastIndex) }, enabled = path.size > 1) { Icon(Icons.Default.ArrowUpward, "Parent folder") }
            Text(if (root == null) "Files you choose" else "Folder · ${path.size - 1} level${if (path.size != 2) "s" else ""}", Modifier.weight(1f), fontSize = 13.sp)
            IconButton(onClick = { fileName = ""; create = true }, enabled = current != null && error == null) { Icon(Icons.Default.NoteAdd, "Create text file") }
            IconButton(onClick = { revision++ }, enabled = current != null) { Icon(Icons.Default.Refresh, "Refresh folder") }
            TextButton(onClick = { picker.launch(null) }) { Text(if (root == null) "Choose folder" else "Change") }
        }
        when {
            root == null -> EmptyMessage("Private by default", "Choose a folder with Android's file picker. DeX receives access only to that folder and its children. No broad storage permission is requested.")
            loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(Modifier.size(28.dp)) }
            error != null -> EmptyMessage("Folder unavailable", error!!)
            items.isEmpty() -> EmptyMessage("This folder is empty", "Create a text file or choose another folder.")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(items, key = { it.id }) { item ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.weight(1f).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Icon(if (item.directory) Icons.Default.Folder else Icons.Default.InsertDriveFile, null, tint = if (item.directory) DexBlue else DexMuted)
                            Column(Modifier.weight(1f)) {
                                Text(item.name, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(if (item.directory) "Folder" else "${formatSize(item.size)} · ${DateFormat.getDateInstance(DateFormat.SHORT).format(Date(item.modified))}", color = DexMuted, fontSize = 10.sp)
                            }
                        }
                        if (item.directory) IconButton(onClick = { path.add(item.uri) }) { Icon(Icons.Default.ChevronRight, "Open ${item.name}") }
                        else {
                            IconButton(onClick = {
                                val intent = Intent(Intent.ACTION_VIEW).setDataAndType(item.uri, item.mime.ifBlank { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                if (!com.shrey.androiddex.standalone.system.SystemActions.open(context, intent)) vm.notify("No installed app can open this file type.")
                            }) { Icon(Icons.Default.OpenInNew, "Open ${item.name}", Modifier.size(18.dp)) }
                            IconButton(onClick = { delete = item }) { Icon(Icons.Default.Delete, "Delete ${item.name}", Modifier.size(17.dp)) }
                        }
                    }
                    HorizontalDivider(color = DexBorder.copy(alpha = .5f))
                }
            }
        }
    }
    if (create) AlertDialog(onDismissRequest = { create = false }, title = { Text("Create a text file") },
        text = { OutlinedTextField(fileName, { fileName = it }, label = { Text("File name") }, singleLine = true) },
        confirmButton = { TextButton(enabled = fileName.isNotBlank() && !fileName.contains('/') && current != null, onClick = {
            runCatching { DocumentsContract.createDocument(context.contentResolver, current!!, "text/plain", fileName.trim()) }
                .onSuccess { create = false; revision++; vm.notify("Text file created.") }.onFailure { vm.notify("Android could not create the file here.") }
        }) { Text("Create") } }, dismissButton = { TextButton(onClick = { create = false }) { Text("Cancel") } })
    delete?.let { item -> AlertDialog(onDismissRequest = { delete = null }, title = { Text("Delete ${item.name}?") }, text = { Text("This deletes the actual file from the folder you selected.") },
        confirmButton = { TextButton(onClick = {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, item.uri) }.onSuccess { revision++ }.onFailure { vm.notify("The file could not be deleted.") }; delete = null
        }) { Text("Delete") } }, dismissButton = { TextButton(onClick = { delete = null }) { Text("Cancel") } })
    }
}

private fun listDocuments(context: Context, root: Uri, directory: Uri): List<FileItem> {
    val id = if (directory == root) DocumentsContract.getTreeDocumentId(root) else DocumentsContract.getDocumentId(directory)
    val uri = DocumentsContract.buildChildDocumentsUriUsingTree(root, id)
    val projection = arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE, DocumentsContract.Document.COLUMN_LAST_MODIFIED)
    val result = mutableListOf<FileItem>()
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        while (cursor.moveToNext() && result.size < 2000) {
            val documentId = cursor.getString(0) ?: continue
            val mime = cursor.getString(2) ?: ""
            result.add(FileItem(DocumentsContract.buildDocumentUriUsingTree(root, documentId), documentId, cursor.getString(1) ?: "Unnamed",
                mime == DocumentsContract.Document.MIME_TYPE_DIR, mime, cursor.getLong(3), cursor.getLong(4)))
        }
    } ?: error("Folder provider returned no cursor")
    return result.sortedWith(compareByDescending<FileItem> { it.directory }.thenBy { it.name.lowercase() })
}
private fun formatSize(bytes: Long): String = when { bytes < 0 -> "Size unknown"; bytes < 1024 -> "$bytes B"; bytes < 1024 * 1024 -> "${bytes / 1024} KB"; else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0) }

@Composable
fun CalculatorContent() {
    var expression by rememberSaveable { mutableStateOf("") }
    var result by rememberSaveable { mutableStateOf("0") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    fun input(value: String) { if (expression.length < 80) expression += value; error = null }
    fun evaluate() { runCatching { Calculator.evaluate(expression) }.onSuccess { result = it; error = null }.onFailure { error = it.message ?: "Invalid expression" } }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(expression, { expression = it.take(80); error = null }, Modifier.fillMaxWidth(), singleLine = true,
            placeholder = { Text("Type an expression") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
            textStyle = MaterialTheme.typography.bodyLarge)
        SelectionContainer { Text(result, Modifier.fillMaxWidth(), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (error != null) Text(error!!, color = DexError, fontSize = 11.sp)
        val rows = listOf(listOf("C", "(", ")", "⌫"), listOf("7", "8", "9", "÷"), listOf("4", "5", "6", "×"), listOf("1", "2", "3", "−"), listOf("0", ".", "%", "+"))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            rows.forEach { row -> Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                row.forEach { label -> Button(onClick = {
                    when (label) { "C" -> { expression = ""; result = "0"; error = null }; "⌫" -> expression = expression.dropLast(1); else -> input(mapOf("÷" to "/", "×" to "*", "−" to "-")[label] ?: label) }
                }, Modifier.weight(1f).fillMaxHeight(), contentPadding = PaddingValues(0.dp), shape = MaterialTheme.shapes.small,
                    colors = ButtonDefaults.buttonColors(containerColor = if (label.any { it.isDigit() || it == '.' }) DexRaised else DexSurface, contentColor = Color.White)) { Text(label, fontSize = 17.sp) } }
            } }
            Button(onClick = ::evaluate, Modifier.fillMaxWidth().height(42.dp)) { Text("=  Calculate") }
        }
    }
}

@Composable
fun TasksContent(vm: DesktopViewModel) {
    val tasks by vm.tasks.collectAsStateWithLifecycle()
    val known by vm.tasksKnown.collectAsStateWithLifecycle()
    val error by vm.taskError.collectAsStateWithLifecycle()
    val shizuku by ShizukuManager.state.collectAsStateWithLifecycle()
    val recents by vm.recents.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var close by remember { mutableStateOf<NativeTask?>(null) }
    LaunchedEffect(shizuku.bridgeReady) { while (true) { vm.refreshTasks(); delay(15000) } }
    Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(if (known) "Running Android tasks" else "Android app switching", if (known) "Live list from the authorized shell task API · refreshes every 15 seconds while open" else "No fake running-app indicators")
            Spacer(Modifier.weight(1f))
            IconButton(onClick = vm::refreshTasks) { Icon(Icons.Default.Refresh, "Refresh Android tasks") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ActionButton("Android Recents", Icons.Default.SwapHoriz) { vm.showRecents() }
            if (!shizuku.bridgeReady) TextButton(onClick = { vm.openTool(WindowKind.SETTINGS) }) { Text("Set up Shizuku") }
        }
        if (!known) Text(error, color = DexMuted, fontSize = 12.sp)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (known) items(tasks, key = { it.id }) { task ->
                DexCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        AppIcon(vm.appsRepository, task.packageName, 36.dp)
                        Column(Modifier.weight(1f)) {
                            Text(vm.appLabel(task.packageName), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("Task ${task.id} · ${if (task.focused) "focused" else if (task.visible) "visible" else "running"} · ${if (task.windowingMode == 5) "freeform" else "system window"}", color = DexMuted, fontSize = 10.sp)
                        }
                        IconButton(onClick = { vm.switchTask(task) }) { Icon(Icons.Default.OpenInNew, "Switch to ${vm.appLabel(task.packageName)}") }
                        IconButton(enabled = task.packageName != context.packageName, onClick = { close = task }) { Icon(Icons.Default.Close, "Close Android task") }
                    }
                }
            }
            item { Text("Recent launches · history, not running status", color = DexMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp)) }
            items(recents, key = { "recent_${it.packageName}" }) { recent ->
                val app = apps.firstOrNull { it.packageName == recent.packageName }
                if (app != null) TextButton(onClick = { vm.launchApp(app) }) { AppIcon(vm.appsRepository, app.packageName, 26.dp); Spacer(Modifier.width(9.dp)); Text(app.label, fontSize = 12.sp) }
            }
        }
    }
    close?.let { task -> AlertDialog(onDismissRequest = { close = null }, title = { Text("Close ${vm.appLabel(task.packageName)}?") },
        text = { Text("Ask Android to remove this task. Unsaved work in that app may be lost. This does not close a DeX tool window.") },
        confirmButton = { TextButton(onClick = { vm.closeTask(task); close = null }) { Text("Close task") } },
        dismissButton = { TextButton(onClick = { close = null }) { Text("Cancel") } })
    }
}

@Composable
fun ToolContent(vm: DesktopViewModel, window: DesktopWindow, status: SystemStatus, onReviewSetup: () -> Unit) {
    when (window.kind) {
        WindowKind.NOTES -> NotesContent(vm, window)
        WindowKind.FILES -> FilesContent(vm)
        WindowKind.CALCULATOR -> CalculatorContent()
        WindowKind.SETTINGS -> SettingsScreen(vm, status, onReviewSetup = onReviewSetup)
        WindowKind.DEVICE -> SettingsScreen(vm, status, deviceOnly = true)
        WindowKind.APPS -> AppLibrary(vm, Modifier.fillMaxSize())
        WindowKind.TASKS -> TasksContent(vm)
    }
}
