package com.inventorysmartai.app.domain.access

import java.util.Base64

/**
 * ترميز قائمة المستخدمين نصًّا واحدًا يُخزَّن في DataStore: سطر لكل مستخدم، وحقوله مرمَّزة Base64 ومفصولة بنقطتين.
 * Base64 لا يحوي «:» ولا سطرًا جديدًا، فأي اسم (عربي أو فيه فواصل) يمرّ دون التباس، ولا حاجة لمكتبة JSON.
 */
object ProfileCodec {
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun encode(profiles: List<UserProfile>): String =
        profiles.joinToString("\n") { p ->
            listOf(p.id, p.name, p.role.name, p.pinSalt, p.pinHash).joinToString(":") { field -> encoder.encodeToString(field.toByteArray(Charsets.UTF_8)) }
        }

    /** سطر تالف يُتجاهل بدل أن يُسقط الباقي. */
    fun decode(text: String?): List<UserProfile> {
        if (text.isNullOrBlank()) return emptyList()
        return text.lineSequence().mapNotNull { line ->
            try {
                val fields = line.split(":").map { String(decoder.decode(it), Charsets.UTF_8) }
                if (fields.size != 5) return@mapNotNull null
                val role = AppRole.entries.firstOrNull { it.name == fields[2] } ?: return@mapNotNull null
                UserProfile(id = fields[0], name = fields[1], role = role, pinSalt = fields[3], pinHash = fields[4])
            } catch (e: IllegalArgumentException) {
                null
            }
        }.toList()
    }
}
