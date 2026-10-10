package com.inventorysmartai.app.data.assistant.files;

import com.inventorysmartai.app.data.importing.parser.XlsxSaxReader;

import org.xml.sax.helpers.DefaultHandler;

import java.io.InputStream;

import javax.xml.parsers.SAXParserFactory;

/** Plain JAXP SAX driver for the plain-JVM tests (on a phone the app uses android.util.Xml instead). */
final class TestSax {

    static final XlsxSaxReader.SaxRunner RUNNER = new XlsxSaxReader.SaxRunner() {
        @Override
        public void parse(InputStream input, DefaultHandler handler) throws Exception {
            SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.newSAXParser().parse(input, handler);
        }
    };

    private TestSax() {
    }
}
