package com.inventorysmartai.app.domain.importing

import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer

/**
 * Canonical semantic fields a spreadsheet/CSV column can be mapped to. Deliberately flat (not
 * per-ImportType) — the same "بالباركود" column means BARCODE whether the file is a product
 * list or an inventory count; which fields a given [ImportType] actually *requires* is decided
 * separately by [ImportType.requiredFields].
 *
 * Split per the spec's explicit warning: العدد/المخزون/الرصيد/الكمية must NOT all collapse onto
 * one field. CURRENT_STOCK ("كم يوجد الآن"), REQUESTED_QUANTITY ("كم أطلب"), COUNTED_QUANTITY
 * ("كم وجدت فعليًا عند الجرد") and the generic QUANTITY are four different questions.
 */
enum class ImportField(val labelAr: String, val isNumeric: Boolean = false) {
    ITEM_NUMBER("رقم الصنف"),
    BARCODE("الباركود"),
    PRODUCT_NAME("اسم الصنف"),
    CATEGORY("التصنيف"),
    UNIT("الوحدة"),
    BRANCH("الفرع"),
    MIN_STOCK("الحد الأدنى للمخزون", isNumeric = true),
    REORDER_POINT("نقطة إعادة الطلب", isNumeric = true),
    CURRENT_STOCK("الرصيد الحالي", isNumeric = true),
    QUANTITY("الكمية", isNumeric = true),
    REQUESTED_QUANTITY("الكمية المطلوبة", isNumeric = true),
    COUNTED_QUANTITY("الكمية المجرودة (الفعلية)", isNumeric = true),
    TARGET("الهدف (المستهدف)", isNumeric = true),
    NOTES("ملاحظات"),
    // --- Phase 4 additions: AI-extracted sales invoice lines and goal/commission tiers. Added at
    // the end, additive-only — nothing above was renumbered or renamed, so no existing dictionary
    // entry, when(...) branch, or persisted ImportRowEntity JSON referencing an older field name
    // is affected (see domain/importing/ImportField.kt's own file-level doc for why this enum is
    // deliberately flat and append-only).
    UNIT_PRICE("سعر الوحدة", isNumeric = true),
    DISCOUNT_PERCENT("نسبة الخصم", isNumeric = true),
    /** One row = one (product, tier) pair for a goals/commission sheet with any number of target
     *  groups — see gemini/ExtractionSchemas.kt (backend) and ImportRepositoryImpl.approveGoals
     *  for how rows sharing a product are grouped back into one Goal with N CommissionGroups. */
    TARGET_GROUP("مجموعة الهدف"),
    COMMISSION_VALUE("قيمة العمولة", isNumeric = true),
    IGNORE("تجاهل هذا العمود");

    /** Declared here (a plain nested class of [ImportField]), NOT inside the `companion object`
     *  below — Kotlin only lets you drop the `.Companion.` qualifier for functions/properties
     *  defined in a companion object, never for a class nested inside one, so `ImportField.
     *  Suggestion` used from another file would otherwise fail to resolve at all. As a direct
     *  nested class it is still visible from the companion object's own members below (sibling
     *  nested declarations of the same outer class see each other), and callers elsewhere can
     *  write the natural `ImportField.Suggestion`. */
    data class Suggestion(val field: ImportField, val requiresConfirmation: Boolean, val exact: Boolean)

    companion object {
        /**
         * One dictionary entry: a set of known header variants (already run through
         * [ArabicTextNormalizer.normalize], so ta-marbuta/alef/diacritic/digit spelling never
         * matters) mapping to a field, plus whether a hit is confident enough to auto-apply
         * without flagging it for the user's explicit attention on the mapping screen.
         *
         * [requiresConfirmation] = true is exactly the spec's "استخدام تعيين صريح عند وجود لبس"
         * case: a bare "الكمية"/"العدد" is *suggested* toward [QUANTITY] (better than leaving it
         * unmapped) but the column-mapping screen must visibly flag it rather than silently
         * treating it as e.g. CURRENT_STOCK.
         */
        private data class DictEntry(val field: ImportField, val variants: Set<String>, val requiresConfirmation: Boolean = false)

        private fun n(s: String) = ArabicTextNormalizer.normalize(s)

        private val dictionary: List<DictEntry> = listOf(
            DictEntry(
                ITEM_NUMBER,
                setOf("رقم الصنف", "رقم المنتج", "كود الصنف", "الكود", "كود المنتج", "رقم", "item number", "item no", "sku", "code")
            ),
            DictEntry(
                BARCODE,
                setOf("باركود", "الباركود", "barcode", "ean", "upc")
            ),
            DictEntry(
                PRODUCT_NAME,
                setOf("اسم الصنف", "اسم المنتج", "المنتج", "الصنف", "وصف الصنف", "اسم الصنف/المنتج", "product name", "name", "description")
            ),
            DictEntry(
                CATEGORY,
                setOf("التصنيف", "تصنيف", "الفئة", "فئة", "القسم", "category")
            ),
            DictEntry(
                UNIT,
                setOf("الوحدة", "وحدة", "وحدة القياس", "unit", "uom")
            ),
            DictEntry(
                BRANCH,
                setOf("الفرع", "فرع", "المخزن", "المستودع", "branch", "warehouse")
            ),
            DictEntry(
                MIN_STOCK,
                setOf("الحد الأدنى", "الحد الأدنى للمخزون", "حد أدنى", "min stock", "minimum stock")
            ),
            DictEntry(
                REORDER_POINT,
                setOf("نقطة إعادة الطلب", "نقطة الطلب", "reorder point", "reorder")
            ),
            // Specific, unambiguous stock-on-hand phrasings.
            DictEntry(
                CURRENT_STOCK,
                setOf("الرصيد الحالي", "المخزون الحالي", "الكمية الحالية", "رصيد المخزون", "current stock", "stock on hand")
            ),
            // The spec's own worked example: "المطلوب" -> REQUESTED_QUANTITY.
            DictEntry(
                REQUESTED_QUANTITY,
                setOf("المطلوب", "الكمية المطلوبة", "كمية الطلب", "كمية مطلوبة", "requested quantity", "requested qty")
            ),
            DictEntry(
                COUNTED_QUANTITY,
                setOf("الكمية المجرودة", "الكمية الفعلية", "الفعلي", "الجرد الفعلي", "counted quantity", "actual quantity", "counted qty")
            ),
            DictEntry(
                TARGET,
                setOf("الهدف", "المستهدف", "الهدف الشهري", "target", "goal target")
            ),
            DictEntry(
                NOTES,
                setOf("ملاحظات", "ملاحظة", "notes", "remarks", "comment")
            ),
            DictEntry(
                UNIT_PRICE,
                setOf("سعر الوحدة", "السعر", "سعر البيع", "unit price", "price")
            ),
            DictEntry(
                DISCOUNT_PERCENT,
                setOf("نسبة الخصم", "الخصم", "discount", "discount %", "discount percent")
            ),
            DictEntry(
                TARGET_GROUP,
                setOf("مجموعة الهدف", "المجموعة", "الفئة المستهدفة", "target group", "tier")
            ),
            DictEntry(
                COMMISSION_VALUE,
                setOf("قيمة العمولة", "العمولة", "commission", "commission value")
            ),
            // Bare, genuinely ambiguous terms — per spec these must NOT be silently folded into
            // CURRENT_STOCK/REQUESTED_QUANTITY/COUNTED_QUANTITY. Suggested as QUANTITY, flagged.
            DictEntry(
                QUANTITY,
                setOf("الكمية", "العدد", "المخزون", "الرصيد", "quantity", "qty", "amount", "stock"),
                requiresConfirmation = true
            )
        )

        private val lookup: Map<String, DictEntry> = buildMap {
            dictionary.forEach { entry -> entry.variants.forEach { variant -> put(n(variant), entry) } }
        }

        /** Best-guess field for a raw header string, or null if nothing in the dictionary is
         *  even a normalized-exact or normalized-contains match. Never throws, never guesses a
         *  numeric field for an obviously non-numeric header (out of scope for this simple
         *  dictionary — the column-mapping screen always lets a human override). */
        fun suggest(headerText: String): Suggestion? {
            val normalized = n(headerText)
            if (normalized.isEmpty()) return null

            lookup[normalized]?.let { return Suggestion(it.field, requiresConfirmation = it.requiresConfirmation, exact = true) }

            // Fall back to "header contains a known variant" (handles things like "اسم الصنف *"
            // or "كود المنتج (اختياري)" where the sheet author added extra decoration), preferring
            // the longest variant match so "الكمية المطلوبة" wins over the bare "الكمية" entry.
            val candidate = dictionary
                .flatMap { entry -> entry.variants.map { variant -> Triple(entry, variant, n(variant)) } }
                .filter { (_, _, normVariant) -> normVariant.isNotEmpty() && normalized.contains(normVariant) }
                .maxByOrNull { (_, _, normVariant) -> normVariant.length }

            return candidate?.let { (entry, _, _) -> Suggestion(entry.field, requiresConfirmation = entry.requiresConfirmation, exact = false) }
        }
    }
}
