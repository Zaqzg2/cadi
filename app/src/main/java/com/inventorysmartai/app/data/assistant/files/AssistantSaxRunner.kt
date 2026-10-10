package com.inventorysmartai.app.data.assistant.files

import android.util.Xml
import com.inventorysmartai.app.data.importing.parser.XlsxSaxReader
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream

/**
 * Drives the .xlsx reader with the phone's own XML parser — no factory lookup and no StAX, the same choice
 * ExcelImportParser makes (a JAXP/StAX provider is not guaranteed on Android).
 */
internal object AssistantSaxRunner : XlsxSaxReader.SaxRunner {
    override fun parse(input: InputStream, handler: DefaultHandler) {
        Xml.parse(input, Xml.Encoding.UTF_8, handler)
    }
}
