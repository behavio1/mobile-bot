package one.behavio.mobilebotui.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import one.behavio.mobilebotui.R
import one.behavio.mobilebotui.workspace.SkillWorkshopViewModel
import one.behavio.mobilebotui.workspace.WorkspaceClient
import androidx.annotation.StringRes
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalResources

// Placeholder names the host gives a power until the workshop names it.
private val PLACEHOLDER_POWER_NAMES = setOf("Nowy skill", "Nowa moc", "New power")

@StringRes
private fun buildStatus(status: String): Int = when (status) {
    "queued" -> R.string.skill_status_queued
    "researching" -> R.string.skill_status_researching
    "building" -> R.string.skill_status_building
    "validating" -> R.string.skill_status_validating
    "needs_input" -> R.string.skill_status_needs_input
    "ready" -> R.string.skill_status_ready
    "interrupted" -> R.string.skill_status_interrupted
    else -> R.string.skill_status_failed
}

@Composable
fun SkillWorkshopScreen(agentId: String, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val resources = LocalResources.current
    val model: SkillWorkshopViewModel = viewModel(key = "skill-workshop-$agentId")
    val state by model.state.collectAsStateWithLifecycle()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(model, lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { model.watch() } }
    val selected = state.builds.firstOrNull { it.id == state.selectedId }
    var description by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable(selected?.id) { mutableStateOf("") }
    var showEvidence by rememberSaveable(selected?.id) { mutableStateOf(false) }
    var showInstructions by rememberSaveable(selected?.id) { mutableStateOf(false) }
    var instructions by remember(selected?.id) { mutableStateOf<String?>(null) }
    var instructionError by remember(selected?.id) { mutableStateOf(false) }
    LaunchedEffect(selected?.skillId, selected?.status, showInstructions) {
        if (selected?.status == "ready" && showInstructions && instructions == null) {
            try { instructions = WorkspaceClient().skillInstructions(selected.skillId); instructionError = false }
            catch (error: kotlinx.coroutines.CancellationException) { throw error }
            catch (_: Exception) { instructionError = true }
        }
    }
    val back = { if (state.selectedId != null) model.select(null) else onBack() }
    BackHandler(onBack = back)
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().testTag("skill-workshop-screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = back, modifier = Modifier.testTag("skill-workshop-back")) { Text(stringResource(R.string.back_short)) }
            Spacer(Modifier.weight(1f))
            Image(painterResource(themePowersResource(LocalGraphicTheme.current)), contentDescription = null, modifier = Modifier.size(46.dp))
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text(selected?.name?.takeUnless { it in PLACEHOLDER_POWER_NAMES } ?: stringResource(R.string.new_power), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
            (state.error ?: state.loadError)?.let { item { Text(stringResource(it), color = MaterialTheme.colorScheme.error) } }
            if (selected == null) {
                item {
                    OutlinedTextField(value = description, onValueChange = { if (it.length <= 12_000) description = it },
                        label = { Text(stringResource(R.string.power_what)) },
                        placeholder = { Text(stringResource(R.string.power_example)) },
                        minLines = 4, maxLines = 8, shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth().testTag("skill-build-description"))
                }
                item {
                    Button(onClick = { model.create(agentId, description) }, enabled = description.trim().length >= 12 && !state.saving,
                        shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("skill-build-submit")) {
                        if (state.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text(stringResource(R.string.power_build))
                    }
                }
                if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                if (state.builds.isNotEmpty()) item { Text(stringResource(R.string.workshop), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold) }
                items(state.builds, key = { it.id }) { build ->
                    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth().clickable { model.select(build.id) }.testTag("skill-build-${build.id}")) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(if (build.name in PLACEHOLDER_POWER_NAMES) build.description else build.name, maxLines = 2,
                                overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                            Text(stringResource(buildStatus(build.status)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            } else {
                item { Text(selected.description, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                item {
                    Surface(shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.secondaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text(stringResource(buildStatus(selected.status)), fontWeight = FontWeight.SemiBold, modifier = Modifier.testTag("skill-build-status"))
                            if (selected.status in listOf("queued", "researching", "building", "validating")) {
                                LinearProgressIndicator(Modifier.fillMaxWidth())
                                Text(stringResource(R.string.power_background), style = MaterialTheme.typography.bodySmall)
                            }
                            if (selected.status == "ready") Text(selected.summary)
                            if (selected.status == "needs_input") Text(selected.question)
                            if (selected.status in listOf("failed", "interrupted")) Text(selected.evidence)
                        }
                    }
                }
                if (selected.status in listOf("needs_input", "failed", "interrupted")) {
                    item {
                        OutlinedTextField(value = answer, onValueChange = { if (it.length <= 12_000) answer = it }, minLines = 3, maxLines = 6,
                            label = { Text(stringResource(if (selected.status == "needs_input") R.string.your_answer else R.string.extra_hints)) },
                            shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth().testTag("skill-build-answer"))
                    }
                    item {
                        Button(onClick = { model.resume(selected.id, answer) }, enabled = !state.saving && (selected.status != "needs_input" || answer.isNotBlank()),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("skill-build-resume")) { Text(stringResource(R.string.continue_)) }
                    }
                }
                if (selected.status == "ready") {
                    item {
                        Button(onClick = { model.select(null); onBack() }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("skill-build-library")) { Text(stringResource(R.string.go_to_library)) }
                    }
                    item {
                        TextButton(onClick = { showInstructions = !showInstructions }) { Text(stringResource(if (showInstructions) R.string.instructions_hide else R.string.instructions_show)) }
                        if (showInstructions) {
                            if (instructionError) Text(stringResource(R.string.instructions_load_failed), color = MaterialTheme.colorScheme.error)
                            else instructions?.let { MarkdownDocument(it) { android.widget.Toast.makeText(context, resources.getString(R.string.power_helper_files), android.widget.Toast.LENGTH_SHORT).show() } } ?: LinearProgressIndicator(Modifier.fillMaxWidth())
                        }
                    }
                }
                if (selected.evidence.isNotBlank() && selected.status == "ready") item {
                    TextButton(onClick = { showEvidence = !showEvidence }) { Text(stringResource(if (showEvidence) R.string.evidence_hide else R.string.evidence_show)) }
                    if (showEvidence) Text(selected.evidence, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
