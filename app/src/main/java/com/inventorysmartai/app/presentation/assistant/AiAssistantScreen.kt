package com.inventorysmartai.app.presentation.assistant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.ActionConfirmationDialog
import com.inventorysmartai.app.domain.assistant.AssistantQuickCommands
import com.inventorysmartai.app.domain.assistant.AttachmentInfo
import com.inventorysmartai.app.domain.assistant.AttachmentKind
import com.inventorysmartai.app.domain.assistant.CardAction
import com.inventorysmartai.app.domain.assistant.ChatMessage
import com.inventorysmartai.app.domain.assistant.ChatRole
import com.inventorysmartai.app.domain.assistant.GeneratedFile
import com.inventorysmartai.app.domain.assistant.GeneratedFileType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(navController: NavController, viewModel: AiAssistantViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var dashboardToView by remember { mutableStateOf<GeneratedFile?>(null) }

    // Any kind of file: the assistant itself checks what it can read and says so in Arabic when it cannot.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        viewModel.onFilesPicked(uris.map { it.toString() })
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    // While the chat is off screen (the assistant opened another screen for the person) its answers are shown over that screen.
    DisposableEffect(viewModel) {
        viewModel.onChatVisible(true)
        onDispose { viewModel.onChatVisible(false) }
    }

    if (state.pendingConfirmationTextAr != null) {
        ActionConfirmationDialog(
            actionDescriptionAr = state.pendingConfirmationTextAr.orEmpty(),
            onConfirm = { viewModel.onConfirmPendingAction(approved = true) },
            onDismiss = { viewModel.onConfirmPendingAction(approved = false) }
        )
    }

    dashboardToView?.let { file ->
        DashboardViewerDialog(file = file, onShare = { shareFile(context, file) }, onClose = { dashboardToView = null })
    }

    Scaffold(
        topBar = { AssistantHeader(onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.messages.isEmpty()) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        AssistantWelcome(onCommand = viewModel::sendQuick)
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        ChatBubble(
                            message = message,
                            onCardAction = viewModel::onCardAction,
                            onOpenFile = { file ->
                                if (file.type == GeneratedFileType.DASHBOARD) dashboardToView = file else openFile(context, file)
                            },
                            onShareFile = { file -> shareFile(context, file) }
                        )
                    }
                    if (state.isSending) {
                        item(key = "typing-indicator") { TypingIndicator() }
                    }
                }
            }

            ChatInputBar(
                text = state.inputText,
                enabled = !state.isSending && state.pendingConfirmationTextAr == null,
                attaching = state.isAttaching,
                pendingAttachments = state.pendingAttachments,
                onAttach = { filePicker.launch(arrayOf("*/*")) },
                onRemoveAttachment = viewModel::onRemoveAttachment,
                onQuickPrompt = viewModel::onInputChanged,
                showCommandChips = state.messages.isNotEmpty(),
                onCommand = viewModel::sendQuick,
                onTextChanged = viewModel::onInputChanged,
                onSend = viewModel::sendMessage
            )
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    onCardAction: (CardAction) -> Unit,
    onOpenFile: (GeneratedFile) -> Unit,
    onShareFile: (GeneratedFile) -> Unit
) {
    val isUser = message.role == ChatRole.USER
    // The design: the person's words in a blue bubble on the right (the start side in RTL); the assistant on the left with its avatar.
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.Start else Arrangement.End) {
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = if (isUser) Modifier.widthIn(max = 300.dp) else Modifier.weight(1f, fill = false).widthIn(max = 380.dp)
        ) {
            message.attachments.forEach { attachment ->
                AttachmentChip(info = attachment, onRemove = null)
                Spacer(Modifier.height(4.dp))
            }
            Card(
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = if (isUser) 4.dp else 18.dp, bottomEnd = if (isUser) 18.dp else 4.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Text(
                    message.text,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    color = if (isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start
                )
            }
            if (!isUser && message.cards.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                AssistantCards(cards = message.cards, onAction = onCardAction)
            }
            message.files.forEach { file ->
                Spacer(Modifier.height(6.dp))
                GeneratedFileCard(file = file, onOpen = { onOpenFile(file) }, onShare = { onShareFile(file) })
            }
            // Phase 4 spec: "AI source/reference display" — a small, honest marker distinguishing
            // an answer grounded in real app data from plain conversation (a greeting, a
            // clarifying question, ...).
            if (!isUser && message.usedLocalData) {
                Text(
                    "المصدر: بيانات التطبيق",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp, end = 4.dp, start = 4.dp)
                )
            }
        }
        if (!isUser) {
            Spacer(Modifier.width(8.dp))
            AssistantAvatar(size = 30.dp)
        }
    }
}

// ---------------------------------------------------------------------- the look of the design

@Composable
private fun AssistantAvatar(size: Dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(size).background(MaterialTheme.colorScheme.primary, CircleShape)
    ) {
        Icon(Icons.Filled.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.58f))
    }
}

/** The title bar of the design: the robot, "المحلل الذكي للمخزون", and the reassurance that the basic commands need no internet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AssistantHeader(onBack: () -> Unit) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AssistantAvatar(size = 38.dp)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("المحلل الذكي للمخزون", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(7.dp).background(MaterialTheme.colorScheme.secondary, CircleShape))
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "الأوامر الأساسية تعمل بدون إنترنت",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع") }
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest)
    )
}

/** The empty chat: who the assistant is, and a grid of commands to tap — each one a sentence that works with no internet. */
@Composable
private fun AssistantWelcome(onCommand: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        AssistantAvatar(size = 72.dp)
        Text("كيف أساعدك اليوم؟", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "اكتب أمرك أو اضغط على أحد الأوامر — أنفّذه على بيانات تطبيقك مباشرة، وبدون إنترنت.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 340.dp)
        )
        Spacer(Modifier.height(2.dp))
        AssistantQuickCommands.all.chunked(2).forEachIndexed { rowIndex, pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                pair.forEachIndexed { column, command ->
                    CommandCard(
                        label = command.label,
                        icon = commandIcon(rowIndex * 2 + column),
                        onClick = { onCommand(command.text) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Text(
            "يمكنك أيضًا إرفاق ملف (Excel أو CSV أو Word أو PDF أو صورة) لتحليله — وقراءة الصور تحتاج إنترنت.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.outline,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp).widthIn(max = 340.dp)
        )
    }
}

@Composable
private fun CommandCard(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 14.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape)
            ) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 2)
        }
    }
}

/** One icon per command, in the order of [AssistantQuickCommands.all]. */
private fun commandIcon(index: Int): ImageVector = when (index) {
    0 -> Icons.Filled.Inventory2
    1 -> Icons.Filled.FilterList
    2 -> Icons.Filled.DateRange
    3 -> Icons.Filled.Warning
    4 -> Icons.Filled.Flag
    5 -> Icons.Filled.BarChart
    6 -> Icons.Filled.Dashboard
    7 -> Icons.Filled.ShoppingCart
    else -> Icons.Filled.Search
}

/** The commands as chips above the input box — one tap runs one. */
@Composable
private fun CommandChips(enabled: Boolean, onCommand: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(AssistantQuickCommands.all, key = { it.label }) { command ->
            Surface(
                modifier = Modifier.clip(RoundedCornerShape(20.dp)).clickable(enabled = enabled) { onCommand(command.text) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    command.label,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun TypingIndicator() {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start) {
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Row(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.height(14.dp).width(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("المساعد يكتب...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
    }
}

@Composable
private fun ChatInputBar(
    text: String,
    enabled: Boolean,
    attaching: Boolean,
    pendingAttachments: List<AttachmentInfo>,
    onAttach: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onQuickPrompt: (String) -> Unit,
    showCommandChips: Boolean,
    onCommand: (String) -> Unit,
    onTextChanged: (String) -> Unit,
    onSend: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
        if (showCommandChips && pendingAttachments.isEmpty()) {
            CommandChips(enabled = enabled, onCommand = onCommand)
        }
        if (pendingAttachments.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(pendingAttachments, key = { it.id }) { file ->
                    AttachmentChip(info = file, onRemove = { onRemoveAttachment(file.id) })
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(quickPromptsFor(pendingAttachments), key = { it }) { prompt ->
                    Surface(
                        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled) { onQuickPrompt(prompt) },
                        shape = RoundedCornerShape(16.dp),
                        color = Color.Transparent,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                    ) {
                        Text(prompt.trim(), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
        if (attaching) {
            Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(8.dp))
                Text("جارٍ قراءة الملف...", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAttach, enabled = enabled && !attaching) {
                Icon(Icons.Default.AttachFile, contentDescription = "إرفاق ملف")
            }
            OutlinedTextField(
                value = text,
                onValueChange = onTextChanged,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                shape = RoundedCornerShape(24.dp),
                placeholder = { Text(if (pendingAttachments.isEmpty()) "اكتب أمرك هنا..." else "ماذا تريد أن أفعل بالملف؟") },
                maxLines = 5
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onSend,
                enabled = enabled && !attaching && (text.isNotBlank() || pendingAttachments.isNotEmpty()),
                colors = IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContentColor = MaterialTheme.colorScheme.outline
                )
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "إرسال")
            }
        }
    }
}

/** What to offer as one-tap starting points, depending on what was attached. They fill the text box; the person can edit before sending. */
private fun quickPromptsFor(files: List<AttachmentInfo>): List<String> {
    val kinds = files.map { it.kind }.toSet()
    return buildList {
        if (AttachmentKind.SPREADSHEET in kinds) {
            add("لخّص محتوى الملف")
            add("حلّل البيانات واستخرج أهم الأرقام")
            add("حوّله إلى لوحة بيانات")
            add("املأ هذا النموذج بالبيانات التالية: ")
        }
        if (AttachmentKind.IMAGE in kinds || AttachmentKind.PDF in kinds) {
            add("اقرأ ما في الملف")
            add("اقرأ فاتورة الشراء وطابق أصنافها مع الكتالوج")
            add("حوّل الجدول إلى ملف Excel")
            add("احسب العمولة من هذا الجدول: ")
        }
        if (AttachmentKind.TEXT_DOCUMENT in kinds) {
            add("لخّص المستند")
        }
    }.distinct()
}

private fun iconFor(kind: AttachmentKind): ImageVector = when (kind) {
    AttachmentKind.SPREADSHEET -> Icons.Default.TableChart
    AttachmentKind.TEXT_DOCUMENT -> Icons.Default.Description
    AttachmentKind.IMAGE -> Icons.Default.Image
    AttachmentKind.PDF -> Icons.Default.PictureAsPdf
}

/** A file the person attached: its name and what was found in it. [onRemove] is null once the message has been sent. */
@Composable
private fun AttachmentChip(info: AttachmentInfo, onRemove: (() -> Unit)?) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = if (onRemove == null) 10.dp else 2.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(iconFor(info.kind), contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.widthIn(max = 160.dp)) {
                Text(info.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    info.summary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (onRemove != null) {
                IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "إزالة الملف", modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** A file the assistant created: a dashboard opens inside the app, a spreadsheet in whatever app can open it. */
@Composable
private fun GeneratedFileCard(file: GeneratedFile, onOpen: () -> Unit, onShare: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(modifier = Modifier.padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (file.type == GeneratedFileType.DASHBOARD) Icons.Default.Dashboard else Icons.Default.TableChart,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(file.name, modifier = Modifier.weight(1f, fill = false).widthIn(max = 150.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpen) { Text(if (file.type == GeneratedFileType.DASHBOARD) "عرض" else "فتح") }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "مشاركة") }
        }
    }
}

/**
 * The dashboard page the assistant built, shown full-screen. It is plain HTML with inline charts and no scripts, so the
 * WebView runs with JavaScript off and no file or content access — it can only draw what is in the page.
 */
@Composable
private fun DashboardViewerDialog(file: GeneratedFile, onShare: () -> Unit, onClose: () -> Unit) {
    val html: String? by produceState<String?>(initialValue = null, key1 = file.path) {
        value = withContext(Dispatchers.IO) { runCatching { File(file.path).readText(Charsets.UTF_8) }.getOrDefault("") }
    }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) { Icon(Icons.Default.Close, contentDescription = "إغلاق") }
                    Text(file.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                    IconButton(onClick = onShare) { Icon(Icons.Default.Share, contentDescription = "مشاركة") }
                }
                val content = html
                when {
                    content == null -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    content.isEmpty() -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("تعذّر فتح اللوحة") }
                    else -> AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { viewContext ->
                            WebView(viewContext).apply {
                                settings.javaScriptEnabled = false
                                settings.allowFileAccess = false
                                settings.allowContentAccess = false
                                settings.builtInZoomControls = true
                                settings.displayZoomControls = false
                                loadDataWithBaseURL(null, content, "text/html", "UTF-8", null)
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun contentUri(context: Context, file: GeneratedFile): Uri =
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(file.path))

private fun shareFile(context: Context, file: GeneratedFile) {
    try {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = file.mimeType
            putExtra(Intent.EXTRA_STREAM, contentUri(context, file))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "مشاركة الملف").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: Exception) {
        Toast.makeText(context, "تعذّرت مشاركة الملف", Toast.LENGTH_SHORT).show()
    }
}

private fun openFile(context: Context, file: GeneratedFile) {
    try {
        val view = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri(context, file), file.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(view)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "لا يوجد تطبيق لفتح هذا الملف على جهازك — استخدم زر المشاركة", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "تعذّر فتح الملف", Toast.LENGTH_SHORT).show()
    }
}
