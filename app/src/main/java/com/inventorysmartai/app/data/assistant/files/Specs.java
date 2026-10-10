package com.inventorysmartai.app.data.assistant.files;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tolerant readers for tool arguments that arrive as parsed JSON (maps, lists, Doubles, Strings). Models are loose
 * about types — "5" for 5, 5.0 for 5, a single value where a list was asked — so every reader accepts the sloppy form.
 */
public final class Specs {

    private Specs() {
    }

    public static Map<String, Object> map(Object value) {
        Map<String, Object> out = new LinkedHashMap<String, Object>();
        if (value instanceof Map) {
            for (Map.Entry<?, ?> e : ((Map<?, ?>) value).entrySet()) {
                out.put(String.valueOf(e.getKey()), e.getValue());
            }
        }
        return out;
    }

    /** A list; a single non-list value becomes a one-element list; null becomes an empty list. */
    public static List<Object> list(Object value) {
        List<Object> out = new ArrayList<Object>();
        if (value instanceof List) {
            out.addAll((List<?>) value);
        } else if (value != null) {
            out.add(value);
        }
        return out;
    }

    public static String string(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof Number) {
            return CellValues.formatNumber(((Number) value).doubleValue());
        }
        return String.valueOf(value);
    }

    public static List<String> strings(Object value) {
        List<String> out = new ArrayList<String>();
        for (Object o : list(value)) {
            out.add(string(o));
        }
        return out;
    }

    public static int integer(Object value, int fallback) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            Double d = CellValues.parseNumber((String) value);
            if (d != null) {
                return d.intValue();
            }
        }
        return fallback;
    }

    public static Double number(Object value) {
        if (value instanceof Number) {
            double d = ((Number) value).doubleValue();
            return Double.isNaN(d) || Double.isInfinite(d) ? null : Double.valueOf(d);
        }
        if (value instanceof String) {
            return CellValues.parseNumber((String) value);
        }
        return null;
    }

    public static boolean bool(Object value, boolean fallback) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String s = ((String) value).trim().toLowerCase(java.util.Locale.ROOT);
            if (s.equals("true") || s.equals("1") || s.equals("نعم")) {
                return true;
            }
            if (s.equals("false") || s.equals("0") || s.equals("لا")) {
                return false;
            }
        }
        return fallback;
    }
}
