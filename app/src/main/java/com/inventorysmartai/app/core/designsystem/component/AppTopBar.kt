package com.inventorysmartai.app.core.designsystem.component

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** ارتفاع الترويسة الموحّدة (دون شريط الحالة). */
val AppTopBarHeight = 40.dp

/**
 * حالة البحث القابل للطيّ داخل [AppTopBar]: أيقونة بحث فقط، وبلمسها يحلّ حقل البحث محلّ العنوان في نفس الشريط
 * (لا صفّ بحث منفصل يأكل من مساحة القائمة). أنشئها بـ [rememberTopBarSearch].
 */
@Stable
class TopBarSearch(
    val query: String,
    val onQueryChange: (String) -> Unit,
    val isOpen: Boolean,
    private val setOpen: (Boolean) -> Unit,
    private val onClosed: () -> Unit,
    val placeholder: String
) {
    fun show() = setOpen(true)

    /** يغلق البحث ويمسح النص (فتعود القائمة كاملة). */
    fun hide() {
        setOpen(false)
        onQueryChange("")
        onClosed()
    }
}

/**
 * [query] هو نص البحث الحالي في الـ ViewModel. إن وصل نص من الخارج (فتح المساعد القائمة وهي مفلترة مثلًا)
 * يُفتح الحقل تلقائيًا كي لا تبدو القائمة مفلترة بلا سبب ظاهر. [onClosed] لتنظيف أي فلاتر إضافية عند الإغلاق.
 */
@Composable
fun rememberTopBarSearch(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String = "بحث",
    onClosed: () -> Unit = {}
): TopBarSearch {
    var open by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    LaunchedEffect(query) { if (query.isNotEmpty()) open = true }
    return TopBarSearch(
        query = query,
        onQueryChange = onQueryChange,
        isOpen = open,
        setOpen = { open = it },
        onClosed = onClosed,
        placeholder = placeholder
    )
}

/**
 * ترويسة موحّدة ومدمجة (40dp) لكل الصفحات: العنوان في البداية، الرجوع اختياري، والإجراءات في النهاية.
 * مرّر [search] لتظهر أيقونة بحث تفتح حقل البحث داخل الشريط نفسه.
 */
@Composable
fun AppTopBar(
    title: String,
    onBack: (() -> Unit)? = null,
    search: TopBarSearch? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        if (search != null && search.isOpen) {
            SearchBarContent(search)
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().statusBarsPadding().height(AppTopBarHeight).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (onBack != null) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "رجوع")
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (search != null) {
                    IconButton(onClick = search::show) {
                        Icon(Icons.Filled.Search, contentDescription = "بحث")
                    }
                }
                actions()
            }
        }
    }
}

/** الشريط وهو في وضع البحث: رجوع (يغلق البحث) + حقل على شكل كبسولة. زر الرجوع في النظام يغلق البحث أولًا. */
@Composable
private fun SearchBarContent(search: TopBarSearch) {
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val close = {
        focusManager.clearFocus()
        search.hide()
    }

    BackHandler(onBack = close)
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    Row(
        modifier = Modifier.fillMaxWidth().statusBarsPadding().height(AppTopBarHeight).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = close) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "إغلاق البحث")
        }
        BasicTextField(
            value = search.query,
            onValueChange = search.onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
            modifier = Modifier.weight(1f).height(34.dp).focusRequester(focusRequester),
            decorationBox = { inner ->
                Row(
                    modifier = Modifier.fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(50))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.Search, contentDescription = null, tint = muted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (search.query.isEmpty()) {
                            Text(
                                search.placeholder,
                                style = MaterialTheme.typography.bodyMedium,
                                color = muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        inner()
                    }
                    if (search.query.isNotEmpty()) {
                        Box(
                            modifier = Modifier.size(28.dp).clickable { search.onQueryChange("") },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.Clear, contentDescription = "مسح", tint = muted, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        )
        Spacer(Modifier.width(8.dp))
    }
}
