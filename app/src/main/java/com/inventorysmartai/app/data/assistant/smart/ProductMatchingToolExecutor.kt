package com.inventorysmartai.app.data.assistant.smart

import com.inventorysmartai.app.data.ai.provider.AiJson
import com.inventorysmartai.app.data.assistant.ToolArgs
import com.inventorysmartai.app.domain.matching.ArabicTextNormalizer
import com.inventorysmartai.app.domain.matching.MatchCandidate
import com.inventorysmartai.app.domain.matching.MatchConfidence
import com.inventorysmartai.app.domain.matching.NameSuggestion
import com.inventorysmartai.app.domain.matching.ProductNameMatcher
import com.inventorysmartai.app.domain.model.Product
import com.inventorysmartai.app.domain.repository.ProductRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.cancellation.CancellationException

/**
 * Name matching for the assistant — "مطابقة أسماء الأصناف المكتوبة بصيغ مختلفة" (and the lines of a photographed purchase invoice):
 *
 *  - `matchProductNames` (read-only) compares each written name with the catalog using [ProductNameMatcher] and returns, per name,
 *    a confidence, the best product and a few alternatives. These are SUGGESTIONS: only EXACT may be used without asking, and the
 *    assistant is told to show everything else to the person (the import contract's rule: never silently merge an uncertain match);
 *  - `addProductAlias` (a small WRITE, so the app asks first) saves a spelling the person confirmed as an alternate name of the
 *    product, so the same spelling is recognised next time — the matcher reads those aliases.
 */
@Singleton
class ProductMatchingToolExecutor @Inject constructor(
    private val productRepository: ProductRepository
) {

    suspend fun describeForConfirmation(name: String, argumentsJson: String): String {
        if (name != "addProductAlias") return "تنفيذ العملية: $name"
        return try {
            val args = AiJson.parseObject(argumentsJson).orEmpty()
            val alias = ToolArgs.string(args, "alias").orEmpty()
            val productId = ToolArgs.long(args, "productId")
            val product = productRepository.observeProducts().first().firstOrNull { it.id == productId }
            "حفظ «$alias» كاسم بديل للصنف «${product?.name ?: "#$productId"}» ليُتعرَّف عليه تلقائيًا في المرات القادمة"
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            "حفظ اسم بديل لصنف لتسهيل المطابقة لاحقًا"
        }
    }

    suspend fun execute(name: String, argumentsJson: String): String {
        val args: Map<String, Any?> = AiJson.parseObject(argumentsJson).orEmpty()
        return try {
            AiJson.toJson(
                when (name) {
                    "matchProductNames" -> matchProductNames(args)
                    "addProductAlias" -> addProductAlias(args)
                    else -> mapOf("error" to "unknown_tool", "message" to "أداة غير معروفة: $name")
                }
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: IllegalArgumentException) {
            AiJson.toJson(ToolArgs.invalid(e.message ?: "وسيط غير صالح"))
        } catch (e: Exception) {
            AiJson.toJson(mapOf("error" to "tool_failed", "message" to "تعذّر تنفيذ الأداة $name"))
        }
    }

    // ------------------------------------------------------------------ matchProductNames

    private class NameInput(val name: String, val quantity: Double?, val unitPrice: Double?)

    private suspend fun matchProductNames(args: Map<String, Any?>): Map<String, Any?> {
        val inputs = parseInputs(args)
        if (inputs.isEmpty()) return ToolArgs.missing("items")
        if (inputs.size > MAX_NAMES) return ToolArgs.invalid("at most $MAX_NAMES names per call")

        val products = productRepository.observeProducts().first()
        val matcher = ProductNameMatcher(products.map { it.toCandidate() })

        val matched = inputs.map { input -> input to matcher.match(input.name) }
        val results = matched.map { (input, match) ->
            mapOf(
                "input" to input.name,
                "quantity" to input.quantity,
                "unitPrice" to input.unitPrice,
                "confidence" to match.confidence.name,
                "needsReview" to match.needsReview,
                "match" to match.best?.toJson(),
                "alternatives" to match.alternatives.map { it.toJson() },
                "reasons" to match.reasons
            )
        }
        val confidences = matched.map { (_, match) -> match.confidence }
        return mapOf(
            "results" to results,
            "summary" to mapOf(
                "total" to inputs.size,
                "exact" to confidences.count { it == MatchConfidence.EXACT },
                "needReview" to confidences.count { it != MatchConfidence.EXACT && it != MatchConfidence.NONE },
                "noMatch" to confidences.count { it == MatchConfidence.NONE }
            ),
            "reminder" to "هذه اقتراحات: ما ليس EXACT اعرضه على المستخدم ليختار أو يؤكّد، ولا تعتمده بنفسك. بعد تأكيده يمكنك حفظ الاسم بـ addProductAlias."
        )
    }

    /** Accepts `items` as objects ({name, quantity, unitPrice}) or plain strings, and `names` as plain strings (a model's natural slip). */
    private fun parseInputs(args: Map<String, Any?>): List<NameInput> {
        val out = ArrayList<NameInput>()
        for (key in listOf("items", "names")) {
            for (raw in ToolArgs.list(args, key).orEmpty()) {
                if (raw is String) {
                    val text = raw.trim()
                    if (text.isNotEmpty()) out.add(NameInput(text, null, null))
                    continue
                }
                val obj = ToolArgs.obj(raw) ?: continue
                val text = ToolArgs.string(obj, "name") ?: continue
                out.add(NameInput(text, ToolArgs.double(obj, "quantity"), ToolArgs.double(obj, "unitPrice")))
            }
        }
        return out
    }

    private fun NameSuggestion.toJson(): Map<String, Any?> = mapOf(
        "productId" to productId,
        "productName" to name,
        "score" to score,
        "matchedAlias" to matchedOn
    )

    // ------------------------------------------------------------------ addProductAlias

    private suspend fun addProductAlias(args: Map<String, Any?>): Map<String, Any?> {
        val productId = ToolArgs.long(args, "productId") ?: return ToolArgs.missing("productId")
        val alias = ToolArgs.string(args, "alias") ?: return ToolArgs.missing("alias")
        val product = productRepository.observeProducts().first().firstOrNull { it.id == productId }
            ?: return mapOf("saved" to false, "error" to "not_found", "message" to "لم يُعثر على الصنف رقم $productId")

        val normalized = ArabicTextNormalizer.normalize(alias)
        if (normalized.isEmpty()) return ToolArgs.invalid("alias is empty")
        if (normalized == ArabicTextNormalizer.normalize(product.name)) {
            return mapOf("saved" to false, "reason" to "same_as_name", "message" to "الاسم مطابق لاسم الصنف نفسه، لا حاجة لحفظه")
        }
        val existing = aliasesOf(product)
        if (existing.any { ArabicTextNormalizer.normalize(it) == normalized }) {
            return mapOf("saved" to false, "reason" to "already_saved", "message" to "هذا الاسم البديل محفوظ مسبقًا")
        }

        productRepository.updateProduct(product.copy(alternateNames = (existing + alias).joinToString("\n")))
        return mapOf("saved" to true, "productId" to productId, "productName" to product.name, "alias" to alias, "aliasCount" to existing.size + 1)
    }

    // ------------------------------------------------------------------ shared

    private fun Product.toCandidate() = MatchCandidate(
        productId = id,
        name = name,
        itemNumber = itemNumber,
        barcode = barcode,
        aliases = aliasesOf(this)
    )

    /** `Product.alternateNames` is one name per line. */
    private fun aliasesOf(product: Product): List<String> =
        product.alternateNames?.split('\n')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()

    private companion object {
        const val MAX_NAMES = 60
    }
}
