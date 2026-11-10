package com.inventorysmartai.app.domain.model

/**
 * ألوان يخصصها المستخدم فوق ثيم التطبيق (قيم ARGB). null = اللون الافتراضي للثيم الحالي.
 * [background] لون خلفية الصفحات، [text] لون الخط، [card] لون البطاقات والحقول.
 */
data class ColorOverrides(
    val background: Int? = null,
    val text: Int? = null,
    val card: Int? = null
) {
    val isDefault: Boolean get() = background == null && text == null && card == null
}
