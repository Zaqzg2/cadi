package com.inventorysmartai.app.data.assistant.files;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How cell text is understood: digits in any script, numbers followed by a unit word ("10 كرتون"), and the
 * Arabic-aware normalisation used for searching labels. Plain JDK only.
 */
public final class CellValues {

    private static final Pattern NUMBER = Pattern.compile("^([+-]?)\\s*(\\d[\\d,]*)(\\.\\d+)?\\s*(%?)\\s*(.*)$", Pattern.DOTALL);
    private static final Pattern THOUSANDS = Pattern.compile("\\d{1,3}(,\\d{3})+");
    private static final Pattern DECIMAL_COMMA = Pattern.compile("\\d+,\\d{1,2}");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern PLAIN_NUMBER = Pattern.compile("^-?(0|[1-9]\\d{0,8}|[1-9]\\d{0,2}(,\\d{3})+)(\\.\\d{1,6})?$");

    private CellValues() {
    }

    /** Arabic-Indic (٠-٩) and Persian (۰-۹) digits to ASCII, Arabic decimal/thousands marks to . and , */
    public static String normalizeDigits(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '\u0660' && c <= '\u0669') {
                sb.append((char) ('0' + (c - '\u0660')));
            } else if (c >= '\u06F0' && c <= '\u06F9') {
                sb.append((char) ('0' + (c - '\u06F0')));
            } else if (c == '\u066B') {
                sb.append('.');
            } else if (c == '\u066C') {
                sb.append(',');
            } else if (c == '\u00A0' || c == '\u200F' || c == '\u200E' || c == '\u202B' || c == '\u202C' || c == '\u202A') {
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Whitespace and line breaks collapsed to single spaces, ends trimmed. */
    public static String clean(String s) {
        if (s == null) {
            return "";
        }
        return WHITESPACE.matcher(s.replace('\u00A0', ' ')).replaceAll(" ").trim();
    }

    public static boolean isBlank(String s) {
        return s == null || clean(s).isEmpty();
    }

    /**
     * The leading number of a cell, tolerating a trailing unit word: "9 حبه" -> 9, "٥٠ كرتون" -> 50,
     * "1,234.5" -> 1234.5, "(5)" -> -5. Null when the text does not start with a number, or when what follows the
     * number is not a short unit (so "10/29" and "12 شارع 5" are not numbers).
     */
    public static Double parseNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String s = clean(normalizeDigits(raw));
        if (s.isEmpty()) {
            return null;
        }
        boolean parenthesised = false;
        if (s.length() > 2 && s.startsWith("(") && s.endsWith(")")) {
            parenthesised = true;
            s = s.substring(1, s.length() - 1).trim();
        }
        Matcher m = NUMBER.matcher(s);
        if (!m.matches()) {
            return null;
        }
        String sign = m.group(1);
        String intPart = m.group(2);
        String fraction = m.group(3) == null ? "" : m.group(3);
        String rest = m.group(5).trim();
        if (!rest.isEmpty()) {
            if (rest.length() > 24) {
                return null;
            }
            for (int i = 0; i < rest.length(); i++) {
                char c = rest.charAt(i);
                if (Character.isDigit(c) || c == '/' || c == '\\' || c == ':') {
                    return null;
                }
            }
        }
        String digits;
        if (intPart.indexOf(',') < 0) {
            digits = intPart;
        } else if (THOUSANDS.matcher(intPart).matches()) {
            digits = intPart.replace(",", "");
        } else if (fraction.isEmpty() && DECIMAL_COMMA.matcher(intPart).matches()) {
            digits = intPart.replace(',', '.');
        } else {
            return null;
        }
        try {
            double value = Double.parseDouble(digits + fraction);
            if (Double.isNaN(value) || Double.isInfinite(value)) {
                return null;
            }
            return ("-".equals(sign) || parenthesised) ? -value : value;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * A cell text that is NOTHING but a number — "120", "15.5", "1,234", "٥٠" — as a number; null for everything else. Strict on
     * purpose: "007", phone numbers with a leading zero, barcodes (more than nine digits) and anything with a word in it stay text,
     * so turning a model's strings into spreadsheet numbers can never damage a code.
     */
    public static Double plainNumber(String raw) {
        if (raw == null) {
            return null;
        }
        String s = clean(normalizeDigits(raw));
        if (s.isEmpty() || !PLAIN_NUMBER.matcher(s).matches()) {
            return null;
        }
        try {
            return Double.valueOf(s.replace(",", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** The unit word after the number ("كرتون" in "10 كرتون"), "%" for percentages, "" when there is none. */
    public static String unitOf(String raw) {
        if (raw == null || parseNumber(raw) == null) {
            return "";
        }
        String s = clean(normalizeDigits(raw));
        if (s.startsWith("(") && s.endsWith(")") && s.length() > 2) {
            s = s.substring(1, s.length() - 1).trim();
        }
        Matcher m = NUMBER.matcher(s);
        if (!m.matches()) {
            return "";
        }
        return (m.group(4) + m.group(5)).trim();
    }

    /** Plain decimal text: 51 not 51.0, at most six decimals, no exponent. */
    public static String formatNumber(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "";
        }
        if (Math.abs(v) < 1e15 && v == Math.rint(v)) {
            return Long.toString((long) v);
        }
        BigDecimal d = new BigDecimal(v).setScale(6, RoundingMode.HALF_UP).stripTrailingZeros();
        return d.toPlainString();
    }

    /** A JSON-friendly number: Long when whole, otherwise a Double rounded to six decimals. */
    public static Object numberObject(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return null;
        }
        if (Math.abs(v) < 1e15 && v == Math.rint(v)) {
            return Long.valueOf((long) v);
        }
        return Double.valueOf(new BigDecimal(v).setScale(6, RoundingMode.HALF_UP).doubleValue());
    }

    /**
     * For matching labels typed by a person against labels typed by someone else: lower-case, no diacritics or
     * tatweel, alef/yaa/taa-marbuta variants unified, digits ASCII, whitespace collapsed.
     */
    public static String normalizeForSearch(String s) {
        if (s == null) {
            return "";
        }
        String t = normalizeDigits(s);
        t = Normalizer.normalize(t, Normalizer.Form.NFKC);
        StringBuilder sb = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if ((c >= '\u064B' && c <= '\u065F') || c == '\u0670' || c == '\u0640') {
                continue; // diacritics, dagger alef, tatweel
            }
            switch (c) {
                case '\u0623':
                case '\u0625':
                case '\u0622':
                case '\u0671':
                    sb.append('\u0627');
                    break;
                case '\u0649':
                    sb.append('\u064A');
                    break;
                case '\u0629':
                    sb.append('\u0647');
                    break;
                case '\u0624':
                    sb.append('\u0648');
                    break;
                case '\u0626':
                    sb.append('\u064A');
                    break;
                default:
                    sb.append(c);
            }
        }
        return clean(sb.toString().toLowerCase(Locale.ROOT));
    }
}
