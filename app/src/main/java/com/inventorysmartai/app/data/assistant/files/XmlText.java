package com.inventorysmartai.app.data.assistant.files;

/** Escaping for the XML/HTML text this package writes, and unescaping for the XML it reads. Plain JDK only. */
public final class XmlText {

    private XmlText() {
    }

    /** True for characters XML 1.0 allows (tab, newline, carriage return, and everything from space up, minus the noncharacters). */
    private static boolean legal(int c) {
        return c == 0x9 || c == 0xA || c == 0xD || (c >= 0x20 && c <= 0xD7FF) || (c >= 0xE000 && c <= 0xFFFD) || (c >= 0x10000 && c <= 0x10FFFF);
    }

    /** Escapes & < > " ' and drops characters that cannot appear in XML at all. Safe for text nodes, attributes and HTML. */
    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            i += Character.charCount(cp);
            if (!legal(cp)) {
                continue;
            }
            switch (cp) {
                case '&':
                    sb.append("&amp;");
                    break;
                case '<':
                    sb.append("&lt;");
                    break;
                case '>':
                    sb.append("&gt;");
                    break;
                case '"':
                    sb.append("&quot;");
                    break;
                case '\'':
                    sb.append("&#39;");
                    break;
                default:
                    sb.appendCodePoint(cp);
            }
        }
        return sb.toString();
    }

    /** The five named entities plus numeric references. Unknown entities are left as written. */
    public static String unescape(String s) {
        if (s == null || s.indexOf('&') < 0) {
            return s == null ? "" : s;
        }
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c != '&') {
                sb.append(c);
                i++;
                continue;
            }
            int semi = s.indexOf(';', i + 1);
            if (semi < 0 || semi - i > 10) {
                sb.append(c);
                i++;
                continue;
            }
            String entity = s.substring(i + 1, semi);
            String replacement = null;
            if (entity.equals("amp")) {
                replacement = "&";
            } else if (entity.equals("lt")) {
                replacement = "<";
            } else if (entity.equals("gt")) {
                replacement = ">";
            } else if (entity.equals("quot")) {
                replacement = "\"";
            } else if (entity.equals("apos")) {
                replacement = "'";
            } else if (entity.length() > 1 && entity.charAt(0) == '#') {
                try {
                    int cp = entity.charAt(1) == 'x' || entity.charAt(1) == 'X'
                            ? Integer.parseInt(entity.substring(2), 16)
                            : Integer.parseInt(entity.substring(1));
                    if (cp > 0 && cp <= 0x10FFFF) {
                        replacement = new String(Character.toChars(cp));
                    }
                } catch (NumberFormatException e) {
                    replacement = null;
                }
            }
            if (replacement == null) {
                sb.append(c);
                i++;
            } else {
                sb.append(replacement);
                i = semi + 1;
            }
        }
        return sb.toString();
    }
}
