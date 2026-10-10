package com.inventorysmartai.app.data.assistant.files;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A small, safe calculator for the arithmetic the assistant must not do "in its head": + - * / ^ ( ) % (percent),
 * and sum, avg, min, max, round, floor, ceil, abs, sqrt, pow, mod, percent(part, whole).
 * Nothing here can reach files, the network or reflection — it only parses numbers and operators.
 */
public final class ExpressionEvaluator {

    private static final int MAX_LENGTH = 800;
    private static final int MAX_DEPTH = 40;

    private final String text;
    private int pos;
    private int depth;

    private ExpressionEvaluator(String text) {
        this.text = text;
    }

    /** @throws IllegalArgumentException with an Arabic explanation when the expression cannot be evaluated */
    public static double evaluate(String expression) {
        if (expression == null || CellValues.isBlank(expression)) {
            throw new IllegalArgumentException("التعبير فارغ");
        }
        if (expression.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("التعبير أطول من الحد المسموح");
        }
        String s = prepare(expression);
        ExpressionEvaluator parser = new ExpressionEvaluator(s);
        double value = parser.parseExpression();
        parser.skipSpaces();
        if (parser.pos < s.length()) {
            throw new IllegalArgumentException("رمز غير متوقع \"" + s.charAt(parser.pos) + "\" في التعبير");
        }
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            throw new IllegalArgumentException("النتيجة غير محدودة (قسمة على صفر أو رقم ضخم)");
        }
        return value;
    }

    private static String prepare(String raw) {
        String s = CellValues.normalizeDigits(raw)
                .replace('\u00D7', '*')
                .replace('\u00F7', '/')
                .replace('\u2212', '-')
                .replace('\u2013', '-')
                .replace('\u066A', '%')
                .replace('\u060C', ',')
                .replace("**", "^");
        boolean hasLetters = false;
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) {
                hasLetters = true;
                break;
            }
        }
        if (!hasLetters) {
            s = s.replace(",", ""); // thousands separators; commas only mean "next argument" inside a function call
        }
        return s.trim();
    }

    // ------------------------------------------------------------------ grammar

    private double parseExpression() {
        double value = parseTerm();
        while (true) {
            skipSpaces();
            if (accept('+')) {
                value += parseTerm();
            } else if (accept('-')) {
                value -= parseTerm();
            } else {
                return value;
            }
        }
    }

    private double parseTerm() {
        double value = parseUnary();
        while (true) {
            skipSpaces();
            if (accept('*')) {
                value *= parseUnary();
            } else if (accept('/')) {
                double divisor = parseUnary();
                if (divisor == 0.0) {
                    throw new IllegalArgumentException("قسمة على صفر");
                }
                value /= divisor;
            } else {
                return value;
            }
        }
    }

    private double parseUnary() {
        skipSpaces();
        if (accept('-')) {
            return -parseUnary();
        }
        if (accept('+')) {
            return parseUnary();
        }
        return parsePower();
    }

    private double parsePower() {
        double base = parsePostfix();
        skipSpaces();
        if (accept('^')) {
            double exponent = parseUnary();
            return Math.pow(base, exponent);
        }
        return base;
    }

    private double parsePostfix() {
        double value = parsePrimary();
        skipSpaces();
        while (accept('%')) {
            value = value / 100.0;
            skipSpaces();
        }
        return value;
    }

    private double parsePrimary() {
        skipSpaces();
        if (++depth > MAX_DEPTH) {
            throw new IllegalArgumentException("التعبير متداخل أكثر من اللازم");
        }
        try {
            if (pos >= text.length()) {
                throw new IllegalArgumentException("التعبير ناقص");
            }
            char c = text.charAt(pos);
            if (c == '(') {
                pos++;
                double inner = parseExpression();
                skipSpaces();
                if (!accept(')')) {
                    throw new IllegalArgumentException("قوس ) مفقود");
                }
                return inner;
            }
            if ((c >= '0' && c <= '9') || c == '.') {
                return parseNumber();
            }
            if (Character.isLetter(c)) {
                return parseNameOrCall();
            }
            throw new IllegalArgumentException("رمز غير متوقع \"" + c + "\" في التعبير");
        } finally {
            depth--;
        }
    }

    private double parseNumber() {
        int start = pos;
        boolean dot = false;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' && !dot) {
                dot = true;
                pos++;
            } else {
                break;
            }
        }
        String literal = text.substring(start, pos);
        if (literal.equals(".")) {
            throw new IllegalArgumentException("رقم غير صالح");
        }
        try {
            return Double.parseDouble(literal);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("رقم غير صالح: " + literal);
        }
    }

    private double parseNameOrCall() {
        int start = pos;
        while (pos < text.length() && (Character.isLetter(text.charAt(pos)) || Character.isDigit(text.charAt(pos)) || text.charAt(pos) == '_')) {
            pos++;
        }
        String name = text.substring(start, pos).toLowerCase(Locale.ROOT);
        skipSpaces();
        if (!accept('(')) {
            if (name.equals("pi")) {
                return Math.PI;
            }
            if (name.equals("e")) {
                return Math.E;
            }
            throw new IllegalArgumentException("اسم غير معروف \"" + name + "\"");
        }
        List<Double> args = new ArrayList<Double>();
        skipSpaces();
        if (!accept(')')) {
            while (true) {
                args.add(parseExpression());
                skipSpaces();
                if (accept(',') || accept(';')) {
                    continue;
                }
                if (accept(')')) {
                    break;
                }
                throw new IllegalArgumentException("قوس ) مفقود بعد وسائط الدالة " + name);
            }
        }
        return call(name, args);
    }

    private static double call(String name, List<Double> a) {
        switch (name) {
            case "sum": {
                needAtLeast(name, a, 1);
                double total = 0;
                for (double v : a) {
                    total += v;
                }
                return total;
            }
            case "avg":
            case "average": {
                needAtLeast(name, a, 1);
                double total = 0;
                for (double v : a) {
                    total += v;
                }
                return total / a.size();
            }
            case "min": {
                needAtLeast(name, a, 1);
                double m = a.get(0);
                for (double v : a) {
                    m = Math.min(m, v);
                }
                return m;
            }
            case "max": {
                needAtLeast(name, a, 1);
                double m = a.get(0);
                for (double v : a) {
                    m = Math.max(m, v);
                }
                return m;
            }
            case "round": {
                needBetween(name, a, 1, 2);
                int digits = a.size() == 2 ? (int) Math.round(a.get(1)) : 0;
                if (digits < -9 || digits > 9) {
                    throw new IllegalArgumentException("عدد المنازل في round يجب أن يكون بين -9 و9");
                }
                return new BigDecimal(Double.toString(a.get(0))).setScale(digits, RoundingMode.HALF_UP).doubleValue();
            }
            case "floor":
                needBetween(name, a, 1, 1);
                return Math.floor(a.get(0));
            case "ceil":
                needBetween(name, a, 1, 1);
                return Math.ceil(a.get(0));
            case "abs":
                needBetween(name, a, 1, 1);
                return Math.abs(a.get(0));
            case "sqrt":
                needBetween(name, a, 1, 1);
                if (a.get(0) < 0) {
                    throw new IllegalArgumentException("جذر عدد سالب");
                }
                return Math.sqrt(a.get(0));
            case "pow":
                needBetween(name, a, 2, 2);
                return Math.pow(a.get(0), a.get(1));
            case "mod":
                needBetween(name, a, 2, 2);
                if (a.get(1) == 0.0) {
                    throw new IllegalArgumentException("باقي القسمة على صفر");
                }
                return a.get(0) % a.get(1);
            case "percent":
                needBetween(name, a, 2, 2);
                if (a.get(1) == 0.0) {
                    throw new IllegalArgumentException("النسبة المئوية من صفر");
                }
                return a.get(0) / a.get(1) * 100.0;
            default:
                throw new IllegalArgumentException("الدالة \"" + name + "\" غير مدعومة. المتاح: sum, avg, min, max, round, floor, ceil, abs, sqrt, pow, mod, percent");
        }
    }

    private static void needAtLeast(String name, List<Double> a, int n) {
        if (a.size() < n) {
            throw new IllegalArgumentException("الدالة " + name + " تحتاج " + n + " قيمة على الأقل");
        }
    }

    private static void needBetween(String name, List<Double> a, int min, int max) {
        if (a.size() < min || a.size() > max) {
            throw new IllegalArgumentException("الدالة " + name + " تحتاج " + (min == max ? String.valueOf(min) : min + " إلى " + max) + " قيمة");
        }
    }

    private void skipSpaces() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }

    private boolean accept(char c) {
        if (pos < text.length() && text.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }
}
