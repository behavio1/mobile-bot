package one.behavio.mobilebotui.ui

import android.widget.TextView
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.MarkwonConfiguration
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.tables.TablePlugin
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tasklist.TaskListPlugin
import one.behavio.mobilebotui.R
import one.behavio.mobilebotui.workspace.AgentFilesViewModel
import one.behavio.mobilebotui.workspace.resolveDocumentLink
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.ui.res.stringResource
import java.time.format.FormatStyle
import androidx.compose.ui.platform.LocalResources

@Composable
fun AgentFilesScreen(agent: AgentSummaryUi, onBack: () -> Unit) {
    val model: AgentFilesViewModel = viewModel(key = "agent-files-${agent.id}")
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(agent.id) { model.start(agent.id) }
    val back = { if (!model.back()) onBack() }
    BackHandler(onBack = back)
    var filter by rememberSaveable(agent.id, state.folder) { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val resources = LocalResources.current
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("agent-files-screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back, modifier = Modifier.testTag("files-back")) { Text(stringResource(R.string.back_short)) }
            Spacer(Modifier.weight(1f))
            AgentAvatar(agent, 32)
            Text(agent.name, Modifier.padding(start = 8.dp, end = 12.dp), fontWeight = FontWeight.SemiBold)
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(if (state.filePath == null) stringResource(R.string.agent_files) else state.filePath!!.substringAfterLast('/'),
                    style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.read_only), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = model::refresh, modifier = Modifier.testTag("files-refresh")) { Text(stringResource(R.string.refresh)) }
        }
        if (state.folder.isNotBlank() || state.filePath != null) {
            Text(state.filePath ?: state.folder, Modifier.padding(horizontal = 22.dp, vertical = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when {
            state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            state.error != null -> Column(Modifier.padding(24.dp)) {
                Text(stringResource(state.error!!), modifier = Modifier.testTag("files-error"))
                TextButton(onClick = model::refresh) { Text(stringResource(R.string.try_again)) }
            }
            state.document != null -> {
                val document = state.document!!
                LazyColumn(Modifier.weight(1f).testTag("document-reader"), contentPadding = PaddingValues(18.dp)) {
                    item {
                        Surface(shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 3.dp) {
                            Column(Modifier.padding(20.dp)) {
                                MarkdownDocument(document.content) { link ->
                                    val path = resolveDocumentLink(document.path, link)
                                    if (path != null) model.open(path)
                                    else Toast.makeText(context, resources.getString(R.string.files_link_outside), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(documentDate(document.modifiedAt), Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { clipboard.setText(AnnotatedString(document.content)) }) { Text(stringResource(R.string.copy_text)) }
                        }
                    }
                }
            }
            else -> {
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedIconButton(
                        onClick = { model.folder(state.folder.substringBeforeLast('/', "")) },
                        enabled = state.folder.isNotEmpty(), shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.size(52.dp).testTag("files-parent-folder"),
                    ) { FolderUpIcon(enabled = state.folder.isNotEmpty()) }
                    OutlinedTextField(value = filter, onValueChange = { filter = it }, placeholder = { Text(stringResource(R.string.search_folder)) },
                        singleLine = true, shape = MaterialTheme.shapes.medium,
                        modifier = Modifier.weight(1f).testTag("files-search"))
                }
                val entries = state.entries.filter { it.name.contains(filter, ignoreCase = true) }
                LazyColumn(Modifier.weight(1f).testTag("files-list"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (entries.isEmpty()) item {
                        Text(stringResource(if (filter.isNotBlank()) R.string.files_no_match else R.string.files_empty),
                            Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(entries, key = { it.path }) { entry ->
                        Surface(onClick = { if (entry.directory) model.folder(entry.path) else model.open(entry.path) },
                            shape = MaterialTheme.shapes.large,
                            border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant), shadowElevation = 2.dp,
                            color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth().testTag("agent-file-${entry.path}")) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Image(painterResource(if (entry.directory) themeFilesResource(LocalGraphicTheme.current) else R.drawable.agent_file_document),
                                    contentDescription = stringResource(if (entry.directory) R.string.folder else R.string.document), modifier = Modifier.size(60.dp))
                                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                                    Text(entry.name, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    if (!entry.directory) Text(documentDate(entry.modifiedAt), style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("›", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun documentDate(value: String): String = runCatching {
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT).withZone(ZoneId.systemDefault()).format(Instant.parse(value))
}.getOrDefault("")

@Composable
internal fun MarkdownDocument(content: String, onLink: (String) -> Unit) {
    // Keep frontmatter accessible without presenting it as the document's main text.
    val normalized = remember(content) { content.replace("\r\n", "\n").removePrefix("\uFEFF") }
    val frontmatterEnd = if (normalized.startsWith("---\n")) normalized.indexOf("\n---\n", 4) else -1
    val body = if (frontmatterEnd >= 0) normalized.substring(frontmatterEnd + 5) else normalized
    var showMetadata by rememberSaveable(content) { mutableStateOf(false) }
    MarkdownText(body, onLink = onLink)
    if (frontmatterEnd >= 0) {
        TextButton(onClick = { showMetadata = !showMetadata }) { Text(stringResource(if (showMetadata) R.string.file_info_hide else R.string.file_info_show)) }
        if (showMetadata) SelectionContainer { Text(normalized.substring(4, frontmatterEnd), style = MaterialTheme.typography.bodySmall) }
    }
}

@Composable
internal fun MarkdownText(content: String, textScale: Float = 1f, onLink: (String) -> Unit) {
    val colors = MaterialTheme.colorScheme
    val ink = colors.onSurface.toArgb()
    val accent = colors.primary.toArgb()
    val muted = colors.surfaceVariant.toArgb()
    val outline = colors.outlineVariant.toArgb()
    val currentOnLink by rememberUpdatedState(onLink)
    val context = LocalContext.current
    val resources = LocalResources.current
    val renderer = remember(ink, accent, muted, outline, textScale) {
        Markwon.builder(context)
            .usePlugin(TablePlugin.create { it.tableBorderColor(outline).tableBorderWidth(1)
                .tableCellPadding(14).tableHeaderRowBackgroundColor(muted) })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(TaskListPlugin.create(context))
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(builder: MarkwonTheme.Builder) {
                    builder.linkColor(accent).codeTextColor(ink).codeBackgroundColor(muted)
                        .blockQuoteColor(accent).headingBreakColor(outline)
                }
                override fun configureConfiguration(builder: MarkwonConfiguration.Builder) {
                    builder.linkResolver { _, link -> currentOnLink(link) }
                }
            }).build()
    }
    AndroidView(factory = { ctx -> TextView(ctx).apply {
        textSize = 17f
        setLineSpacing(6f, 1.15f)
        movementMethod = io.noties.markwon.ext.tables.TableAwareMovementMethod.create()
        importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_YES
    } }, update = { view ->
        view.textSize = 17f * textScale
        view.setTextColor(ink)
        if (view.tag != (content to renderer)) {
            renderer.setMarkdown(view, content)
            view.tag = content to renderer
        }
    }, modifier = Modifier.fillMaxWidth().testTag("markdown-content"))

}

@Composable
private fun FolderUpIcon(enabled: Boolean) {
    val ink = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.35f)
    val fill = MaterialTheme.colorScheme.secondary.copy(alpha = if (enabled) 1f else 0.25f)
    val label = stringResource(R.string.folder_up)
    Canvas(Modifier.size(30.dp).semantics { contentDescription = label }) {
        val u = size.width / 30f
        val folder = Path().apply {
            moveTo(3*u, 10*u); lineTo(3*u, 7*u); quadraticTo(3*u, 5*u, 5*u, 5*u)
            lineTo(12*u, 5*u); lineTo(15*u, 8*u); lineTo(25*u, 8*u)
            quadraticTo(27*u, 8*u, 27*u, 10*u); lineTo(27*u, 25*u)
            quadraticTo(27*u, 27*u, 25*u, 27*u); lineTo(5*u, 27*u)
            quadraticTo(3*u, 27*u, 3*u, 25*u); close()
        }
        drawPath(folder, fill)
        drawPath(folder, ink, style = Stroke(2*u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        val arrow = Path().apply {
            moveTo(15*u, 23*u); lineTo(15*u, 13*u)
            moveTo(10*u, 18*u); lineTo(15*u, 13*u); lineTo(20*u, 18*u)
        }
        drawPath(arrow, ink, style = Stroke(2.5f*u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
