package com.inventorysmartai.app.core.designsystem.component

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * حقل رقمي يحدّد قيمته القديمة كاملة عند الدخول إليه، فيحلّ ما يكتبه المستخدم محلّها مباشرة بلا مسح يدوي.
 * يتصرف كـ [OutlinedTextField] عادي من الخارج (قيمة نصية + [onValueChange])؛ التحديد حالة داخلية فقط.
 */
@Composable
fun NumberTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    keyboardType: KeyboardType = KeyboardType.Decimal,
    enabled: Boolean = true
) {
    var field by remember { mutableStateOf(TextFieldValue(value)) }
    // لو غيّر الخارج النص (مسح، إعادة تعيين) نتبعه ونضع المؤشر في النهاية.
    val shown = if (field.text == value) field else TextFieldValue(value, TextRange(value.length))
    val scope = rememberCoroutineScope()
    val latestValue by rememberUpdatedState(value)

    OutlinedTextField(
        value = shown,
        onValueChange = {
            field = it
            if (it.text != value) onValueChange(it.text)
        },
        label = label?.let { { Text(it) } },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        modifier = modifier.onFocusChanged { state ->
            if (state.isFocused) {
                val atFocus = latestValue
                // بعد لحظة قصيرة: لمسة المؤشر الأولى تضع المؤشر بعد حصول التركيز فتلغي التحديد لو سبقته.
                // وإن بدأ المستخدم الكتابة خلال هذه اللحظة (تغيّرت القيمة) لا نمسّ ما كتبه.
                scope.launch {
                    delay(SELECT_DELAY_MS)
                    if (latestValue == atFocus) field = TextFieldValue(atFocus, TextRange(0, atFocus.length))
                }
            }
        }
    )
}

private const val SELECT_DELAY_MS = 60L
