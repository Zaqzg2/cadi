package com.inventorysmartai.app.data.assistant.files;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Builds a self-contained, right-to-left HTML dashboard: KPI cards, charts and tables. No JavaScript and no network:
 * charts are plain HTML/CSS bars or inline SVG, so the page renders offline inside a WebView with scripts turned off,
 * and a Content-Security-Policy forbids anything else.
 *
 * Spec: { title, subtitle, kpis: [{label, value, unit, note, tone}],
 *         charts: [{type: bar|column|line|pie|donut, title, unit, labels[], values[] | series[{name, values[]}]}],
 *         tables: [{title, headers[], rows[][]}], notes: [..] }
 * Every text is escaped. Limits keep the page small enough for a phone.
 */
public final class DashboardBuilder {

    private static final int MAX_KPIS = 12;
    private static final int MAX_CHARTS = 6;
    private static final int MAX_POINTS = 30;
    private static final int MAX_TABLES = 4;
    private static final int MAX_TABLE_ROWS = 100;
    private static final int MAX_NOTES = 8;
    private static final String[] PALETTE = {"#2563eb", "#16a34a", "#f59e0b", "#dc2626", "#7c3aed", "#0891b2", "#db2777", "#65a30d"};

    private DashboardBuilder() {
    }

    public static String build(Map<String, ?> spec, String generatedAt) {
        String title = textOr(spec.get("title"), "لوحة البيانات");
        String subtitle = Specs.string(spec.get("subtitle")).trim();
        StringBuilder body = new StringBuilder(8192);

        body.append("<header><h1>").append(XmlText.escape(title)).append("</h1>");
        if (!subtitle.isEmpty()) {
            body.append("<p class=\"sub\">").append(XmlText.escape(subtitle)).append("</p>");
        }
        body.append("</header>");

        List<Object> kpis = Specs.list(spec.get("kpis"));
        if (!kpis.isEmpty()) {
            body.append("<section class=\"kpis\">");
            for (int i = 0; i < Math.min(kpis.size(), MAX_KPIS); i++) {
                appendKpi(body, Specs.map(kpis.get(i)));
            }
            body.append("</section>");
        }

        List<Object> charts = Specs.list(spec.get("charts"));
        for (int i = 0; i < Math.min(charts.size(), MAX_CHARTS); i++) {
            appendChart(body, Specs.map(charts.get(i)), i);
        }

        List<Object> tables = Specs.list(spec.get("tables"));
        for (int i = 0; i < Math.min(tables.size(), MAX_TABLES); i++) {
            appendTable(body, Specs.map(tables.get(i)));
        }

        List<Object> notes = Specs.list(spec.get("notes"));
        if (!notes.isEmpty()) {
            body.append("<section class=\"card notes\"><ul>");
            for (int i = 0; i < Math.min(notes.size(), MAX_NOTES); i++) {
                body.append("<li>").append(XmlText.escape(Specs.string(notes.get(i)))).append("</li>");
            }
            body.append("</ul></section>");
        }
        if (generatedAt != null && !generatedAt.isEmpty()) {
            body.append("<footer>أُنشئت بواسطة المساعد الذكي · ").append(XmlText.escape(generatedAt)).append("</footer>");
        }

        return "<!DOCTYPE html>\n<html lang=\"ar\" dir=\"rtl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'; img-src data:\">"
                + "<title>" + XmlText.escape(title) + "</title><style>" + CSS + "</style></head><body>"
                + body + "</body></html>";
    }

    // ------------------------------------------------------------------ KPI cards

    private static void appendKpi(StringBuilder sb, Map<String, Object> kpi) {
        String label = Specs.string(kpi.get("label")).trim();
        Object rawValue = kpi.get("value");
        String value = rawValue instanceof Number ? format(((Number) rawValue).doubleValue()) : Specs.string(rawValue).trim();
        Double plain = rawValue instanceof Number ? null : CellValues.plainNumber(value);
        if (plain != null) {
            value = format(plain);
        }
        if (label.isEmpty() && value.isEmpty()) {
            return;
        }
        String unit = Specs.string(kpi.get("unit")).trim();
        String note = Specs.string(kpi.get("note")).trim();
        String tone = Specs.string(kpi.get("tone")).trim().toLowerCase(Locale.ROOT);
        if (!(tone.equals("good") || tone.equals("warn") || tone.equals("bad"))) {
            tone = "neutral";
        }
        sb.append("<div class=\"kpi ").append(tone).append("\"><div class=\"kl\">").append(XmlText.escape(label)).append("</div>");
        sb.append("<div class=\"kv\">").append(XmlText.escape(value));
        if (!unit.isEmpty()) {
            sb.append(" <span class=\"ku\">").append(XmlText.escape(unit)).append("</span>");
        }
        sb.append("</div>");
        if (!note.isEmpty()) {
            sb.append("<div class=\"kn\">").append(XmlText.escape(note)).append("</div>");
        }
        sb.append("</div>");
    }

    // ------------------------------------------------------------------ charts

    private static final class Series {
        final String name;
        final List<Double> values = new ArrayList<Double>();

        Series(String name) {
            this.name = name;
        }
    }

    private static void appendChart(StringBuilder sb, Map<String, Object> chart, int index) {
        List<String> labels = Specs.strings(chart.get("labels"));
        List<Series> series = new ArrayList<Series>();
        List<Object> given = Specs.list(chart.get("series"));
        if (!given.isEmpty()) {
            for (Object raw : given) {
                Map<String, Object> s = Specs.map(raw);
                Series one = new Series(Specs.string(s.get("name")).trim());
                for (Object v : Specs.list(s.get("values"))) {
                    Double d = Specs.number(v);
                    one.values.add(d == null ? 0.0 : d);
                }
                series.add(one);
            }
        } else {
            Series one = new Series("");
            for (Object v : Specs.list(chart.get("values"))) {
                Double d = Specs.number(v);
                one.values.add(d == null ? 0.0 : d);
            }
            series.add(one);
        }
        int points = labels.size();
        for (Series s : series) {
            points = Math.min(points, s.values.size());
        }
        points = Math.min(points, MAX_POINTS);
        if (points <= 0) {
            return;
        }

        String type = Specs.string(chart.get("type")).trim().toLowerCase(Locale.ROOT);
        String unit = Specs.string(chart.get("unit")).trim();
        String title = Specs.string(chart.get("title")).trim();
        List<String> cats = labels.subList(0, points);

        sb.append("<section class=\"card\">");
        if (!title.isEmpty()) {
            sb.append("<h2>").append(XmlText.escape(title)).append("</h2>");
        }
        if (type.equals("pie") || type.equals("donut")) {
            appendPie(sb, cats, series.get(0), points, unit, type.equals("donut"));
        } else if (type.equals("line")) {
            appendLine(sb, cats, series, points, unit);
        } else if (type.equals("column") && points <= 8) {
            appendColumns(sb, cats, series, points, unit);
        } else {
            appendHorizontalBars(sb, cats, series.get(0), points, unit, PALETTE[index % PALETTE.length]);
        }
        if (series.size() > 1 && !type.equals("pie") && !type.equals("donut")) {
            appendLegend(sb, series);
        }
        sb.append("</section>");
    }

    private static void appendHorizontalBars(StringBuilder sb, List<String> cats, Series s, int points, String unit, String color) {
        double max = 0;
        for (int i = 0; i < points; i++) {
            max = Math.max(max, Math.abs(s.values.get(i)));
        }
        sb.append("<div class=\"hbars\">");
        for (int i = 0; i < points; i++) {
            double v = s.values.get(i);
            double pct = max <= 0 ? 0 : Math.abs(v) / max * 100.0;
            sb.append("<div class=\"hb\"><div class=\"hbt\"><span>").append(XmlText.escape(cats.get(i))).append("</span><b>")
                    .append(XmlText.escape(format(v) + (unit.isEmpty() ? "" : " " + unit))).append("</b></div>")
                    .append("<div class=\"track\"><div class=\"fill\" style=\"width:").append(oneDecimal(pct)).append("%;background:")
                    .append(v < 0 ? "#dc2626" : color).append("\"></div></div></div>");
        }
        sb.append("</div>");
    }

    private static void appendColumns(StringBuilder sb, List<String> cats, List<Series> series, int points, String unit) {
        double max = 0;
        for (Series s : series) {
            for (int i = 0; i < points; i++) {
                max = Math.max(max, s.values.get(i));
            }
        }
        if (max <= 0) {
            max = 1;
        }
        int width = 360;
        int height = 220;
        int left = 8;
        int right = 8;
        int top = 22;
        int bottom = 34;
        double plotW = width - left - right;
        double plotH = height - top - bottom;
        double group = plotW / points;
        double barW = Math.min(36.0, group * 0.7 / series.size());
        sb.append("<svg class=\"chart\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\" role=\"img\">");
        sb.append("<line x1=\"").append(left).append("\" y1=\"").append(top + (int) plotH).append("\" x2=\"").append(width - right)
                .append("\" y2=\"").append(top + (int) plotH).append("\" class=\"axis\"/>");
        for (int i = 0; i < points; i++) {
            int slot = points - 1 - i; // right-to-left: the first category sits on the right, like the rest of the page
            double groupX = left + group * slot + (group - barW * series.size()) / 2.0;
            for (int k = 0; k < series.size(); k++) {
                double v = Math.max(0, series.get(k).values.get(i));
                double h = v / max * plotH;
                double x = groupX + barW * (series.size() - 1 - k);
                double y = top + plotH - h;
                sb.append("<rect x=\"").append(oneDecimal(x)).append("\" y=\"").append(oneDecimal(y)).append("\" width=\"")
                        .append(oneDecimal(barW - 2)).append("\" height=\"").append(oneDecimal(h)).append("\" rx=\"3\" fill=\"")
                        .append(PALETTE[k % PALETTE.length]).append("\"/>");
                if (series.size() == 1) {
                    sb.append("<text x=\"").append(oneDecimal(x + (barW - 2) / 2)).append("\" y=\"").append(oneDecimal(y - 5))
                            .append("\" class=\"val\" text-anchor=\"middle\">").append(XmlText.escape(compact(v))).append("</text>");
                }
            }
            sb.append("<text x=\"").append(oneDecimal(left + group * slot + group / 2)).append("\" y=\"").append(height - 14)
                    .append("\" class=\"lab\" text-anchor=\"middle\">").append(XmlText.escape(truncate(cats.get(i), 9))).append("</text>");
        }
        sb.append("</svg>");
        if (!unit.isEmpty()) {
            sb.append("<div class=\"unit\">الوحدة: ").append(XmlText.escape(unit)).append("</div>");
        }
    }

    private static void appendLine(StringBuilder sb, List<String> cats, List<Series> series, int points, String unit) {
        double dataMin = 0;
        double dataMax = 0;
        for (Series s : series) {
            for (int i = 0; i < points; i++) {
                dataMin = Math.min(dataMin, s.values.get(i));
                dataMax = Math.max(dataMax, s.values.get(i));
            }
        }
        double step = niceStep((dataMax - dataMin) / 4.0);
        double axisMin = Math.floor(dataMin / step) * step;
        double axisMax = Math.ceil(dataMax / step) * step;
        if (axisMax <= axisMin) {
            axisMax = axisMin + step;
        }
        int ticks = (int) Math.round((axisMax - axisMin) / step);
        int width = 360;
        int height = 220;
        int left = 12;
        int right = 46; // the value axis sits on the right, as in a right-to-left page
        int top = 14;
        int bottom = 34;
        double plotW = width - left - right;
        double plotH = height - top - bottom;
        sb.append("<svg class=\"chart\" viewBox=\"0 0 ").append(width).append(' ').append(height).append("\" role=\"img\">");
        for (int g = 0; g <= ticks; g++) {
            double v = axisMin + step * g;
            double y = top + plotH - plotH * g / ticks;
            sb.append("<line x1=\"").append(left).append("\" y1=\"").append(oneDecimal(y)).append("\" x2=\"").append(width - right)
                    .append("\" y2=\"").append(oneDecimal(y)).append("\" class=\"grid\"/>");
            sb.append("<text x=\"").append(width - right + 6).append("\" y=\"").append(oneDecimal(y + 4)).append("\" class=\"lab\" text-anchor=\"start\">")
                    .append(XmlText.escape(compact(v))).append("</text>");
        }
        int labelStep = Math.max(1, (int) Math.ceil(points / 6.0));
        for (int i = 0; i < points; i += labelStep) {
            double x = points == 1 ? left + plotW / 2 : left + plotW * (points - 1 - i) / (points - 1);
            sb.append("<text x=\"").append(oneDecimal(x)).append("\" y=\"").append(height - 14).append("\" class=\"lab\" text-anchor=\"middle\">")
                    .append(XmlText.escape(truncate(cats.get(i), 9))).append("</text>");
        }
        for (int k = 0; k < series.size(); k++) {
            StringBuilder path = new StringBuilder();
            StringBuilder dots = new StringBuilder();
            for (int i = 0; i < points; i++) {
                double x = points == 1 ? left + plotW / 2 : left + plotW * (points - 1 - i) / (points - 1);
                double y = top + plotH - (series.get(k).values.get(i) - axisMin) / (axisMax - axisMin) * plotH;
                path.append(i == 0 ? "M" : " L").append(oneDecimal(x)).append(' ').append(oneDecimal(y));
                dots.append("<circle cx=\"").append(oneDecimal(x)).append("\" cy=\"").append(oneDecimal(y)).append("\" r=\"3\" fill=\"")
                        .append(PALETTE[k % PALETTE.length]).append("\"/>");
            }
            sb.append("<path d=\"").append(path).append("\" fill=\"none\" stroke=\"").append(PALETTE[k % PALETTE.length])
                    .append("\" stroke-width=\"2.5\" stroke-linejoin=\"round\" stroke-linecap=\"round\"/>").append(dots);
        }
        sb.append("</svg>");
        if (!unit.isEmpty()) {
            sb.append("<div class=\"unit\">الوحدة: ").append(XmlText.escape(unit)).append("</div>");
        }
    }

    /** A round axis step (1, 2, 5 x 10^n) close to {@code raw}, so gridlines read 0, 500, 1,000 rather than 621, 1,242. */
    static double niceStep(double raw) {
        if (raw <= 0 || Double.isNaN(raw) || Double.isInfinite(raw)) {
            return 1.0;
        }
        double exponent = Math.floor(Math.log10(raw));
        double base = Math.pow(10.0, exponent);
        double f = raw / base;
        double nice = f <= 1.0 ? 1.0 : f <= 2.0 ? 2.0 : f <= 5.0 ? 5.0 : 10.0;
        return nice * base;
    }

    private static void appendPie(StringBuilder sb, List<String> cats, Series s, int points, String unit, boolean donut) {
        double total = 0;
        for (int i = 0; i < points; i++) {
            total += Math.max(0, s.values.get(i));
        }
        if (total <= 0) {
            sb.append("<p class=\"muted\">لا توجد قيم موجبة للرسم</p>");
            return;
        }
        sb.append("<div class=\"pie\"><svg viewBox=\"0 0 42 42\" role=\"img\" class=\"pieSvg\">");
        sb.append("<circle cx=\"21\" cy=\"21\" r=\"15.9155\" fill=\"none\" class=\"pieBg\" stroke-width=\"").append(donut ? "6" : "16").append("\"/>");
        double offset = 25.0;
        for (int i = 0; i < points; i++) {
            double pct = Math.max(0, s.values.get(i)) / total * 100.0;
            if (pct <= 0) {
                continue;
            }
            sb.append("<circle cx=\"21\" cy=\"21\" r=\"15.9155\" fill=\"none\" stroke=\"").append(PALETTE[i % PALETTE.length])
                    .append("\" stroke-width=\"").append(donut ? "6" : "16").append("\" stroke-dasharray=\"").append(oneDecimal(pct)).append(' ')
                    .append(oneDecimal(100.0 - pct)).append("\" stroke-dashoffset=\"").append(oneDecimal(offset)).append("\"/>");
            offset -= pct;
        }
        if (donut) {
            sb.append("<text x=\"21\" y=\"22.5\" text-anchor=\"middle\" class=\"pieTotal\">").append(XmlText.escape(compact(total))).append("</text>");
        }
        sb.append("</svg><ul class=\"legend\">");
        for (int i = 0; i < points; i++) {
            double v = Math.max(0, s.values.get(i));
            sb.append("<li><i style=\"background:").append(PALETTE[i % PALETTE.length]).append("\"></i><span>").append(XmlText.escape(cats.get(i)))
                    .append("</span><b>").append(XmlText.escape(format(v) + (unit.isEmpty() ? "" : " " + unit))).append(" · ")
                    .append(oneDecimal(v / total * 100.0)).append("%</b></li>");
        }
        sb.append("</ul></div>");
    }

    private static void appendLegend(StringBuilder sb, List<Series> series) {
        sb.append("<ul class=\"legend inline\">");
        for (int k = 0; k < series.size(); k++) {
            String name = series.get(k).name.isEmpty() ? "السلسلة " + (k + 1) : series.get(k).name;
            sb.append("<li><i style=\"background:").append(PALETTE[k % PALETTE.length]).append("\"></i><span>").append(XmlText.escape(name)).append("</span></li>");
        }
        sb.append("</ul>");
    }

    // ------------------------------------------------------------------ tables

    private static void appendTable(StringBuilder sb, Map<String, Object> table) {
        List<String> headers = Specs.strings(table.get("headers"));
        List<Object> rows = Specs.list(table.get("rows"));
        if (headers.isEmpty() && rows.isEmpty()) {
            return;
        }
        String title = Specs.string(table.get("title")).trim();
        sb.append("<section class=\"card\">");
        if (!title.isEmpty()) {
            sb.append("<h2>").append(XmlText.escape(title)).append("</h2>");
        }
        sb.append("<div class=\"scroll\"><table>");
        if (!headers.isEmpty()) {
            sb.append("<thead><tr>");
            for (String h : headers) {
                sb.append("<th>").append(XmlText.escape(h)).append("</th>");
            }
            sb.append("</tr></thead>");
        }
        sb.append("<tbody>");
        for (int r = 0; r < Math.min(rows.size(), MAX_TABLE_ROWS); r++) {
            sb.append("<tr>");
            for (Object cell : Specs.list(rows.get(r))) {
                boolean numeric = cell instanceof Number;
                sb.append(numeric ? "<td class=\"num\">" : "<td>")
                        .append(XmlText.escape(numeric ? format(((Number) cell).doubleValue()) : Specs.string(cell))).append("</td>");
            }
            sb.append("</tr>");
        }
        sb.append("</tbody></table></div>");
        if (rows.size() > MAX_TABLE_ROWS) {
            sb.append("<p class=\"muted\">عُرض أول ").append(MAX_TABLE_ROWS).append(" صف من ").append(rows.size()).append("</p>");
        }
        sb.append("</section>");
    }

    // ------------------------------------------------------------------ formatting

    private static String textOr(Object value, String fallback) {
        String s = Specs.string(value).trim();
        return s.isEmpty() ? fallback : s;
    }

    /** 1234567.5 -> "1,234,567.5" (western digits, at most two decimals). */
    static String format(double v) {
        if (Double.isNaN(v) || Double.isInfinite(v)) {
            return "";
        }
        boolean negative = v < 0;
        double abs = Math.abs(v);
        String plain = CellValues.formatNumber(Math.round(abs * 100.0) / 100.0);
        String integer = plain;
        String fraction = "";
        int dot = plain.indexOf('.');
        if (dot >= 0) {
            integer = plain.substring(0, dot);
            fraction = plain.substring(dot);
        }
        StringBuilder grouped = new StringBuilder();
        for (int i = 0; i < integer.length(); i++) {
            if (i > 0 && (integer.length() - i) % 3 == 0) {
                grouped.append(',');
            }
            grouped.append(integer.charAt(i));
        }
        return (negative && abs > 0 ? "-" : "") + grouped + fraction;
    }

    /** Short form for chart axes: 1.5K, 2.3M. */
    static String compact(double v) {
        double abs = Math.abs(v);
        if (abs >= 1e9) {
            return trim(v / 1e9) + "B";
        }
        if (abs >= 1e6) {
            return trim(v / 1e6) + "M";
        }
        if (abs >= 1e4) {
            return trim(v / 1e3) + "K";
        }
        return format(v);
    }

    private static String trim(double v) {
        return CellValues.formatNumber(Math.round(v * 10.0) / 10.0);
    }

    private static String oneDecimal(double v) {
        return CellValues.formatNumber(Math.round(v * 10.0) / 10.0);
    }

    private static String truncate(String s, int max) {
        String t = CellValues.clean(s);
        return t.length() <= max ? t : t.substring(0, max - 1) + "…";
    }

    private static final String CSS = ":root{--bg:#f4f6fb;--card:#fff;--text:#0f172a;--muted:#64748b;--line:#e2e8f0;--track:#e8edf5}"
            + "@media (prefers-color-scheme:dark){:root{--bg:#0b1220;--card:#162033;--text:#f1f5f9;--muted:#94a3b8;--line:#2a3a52;--track:#243247}}"
            + "*{box-sizing:border-box}html,body{margin:0}"
            + "body{background:var(--bg);color:var(--text);font-family:system-ui,-apple-system,'Segoe UI',Tahoma,'Noto Sans Arabic',sans-serif;"
            + "padding:14px 12px 28px;line-height:1.55;-webkit-text-size-adjust:100%}"
            + "header{margin:4px 2px 14px}h1{font-size:1.35rem;margin:0}.sub{margin:4px 0 0;color:var(--muted);font-size:.9rem}"
            + "h2{font-size:1rem;margin:0 0 12px}"
            + ".card{background:var(--card);border:1px solid var(--line);border-radius:14px;padding:14px;margin-bottom:12px}"
            + ".kpis{display:grid;grid-template-columns:repeat(2,1fr);gap:10px;margin-bottom:12px}"
            + "@media (min-width:640px){.kpis{grid-template-columns:repeat(4,1fr)}}"
            + ".kpi{background:var(--card);border:1px solid var(--line);border-inline-start:5px solid #94a3b8;border-radius:14px;padding:11px 12px;min-width:0}"
            + ".kpi.good{border-inline-start-color:#16a34a}.kpi.warn{border-inline-start-color:#f59e0b}.kpi.bad{border-inline-start-color:#dc2626}"
            + ".kl{color:var(--muted);font-size:.8rem}.kv{font-size:1.45rem;font-weight:700;margin-top:2px;overflow-wrap:anywhere}"
            + ".ku{font-size:.8rem;font-weight:500;color:var(--muted)}.kn{color:var(--muted);font-size:.75rem;margin-top:3px}"
            + ".hb{margin-bottom:11px}.hbt{display:flex;justify-content:space-between;gap:10px;font-size:.88rem;margin-bottom:4px}"
            + ".hbt span{min-width:0;overflow-wrap:anywhere}.hbt b{white-space:nowrap}"
            + ".track{background:var(--track);border-radius:99px;height:10px;overflow:hidden}.fill{height:100%;border-radius:99px}"
            + ".chart{width:100%;height:auto;direction:ltr;display:block}.axis{stroke:var(--line);stroke-width:1}.grid{stroke:var(--line);stroke-width:1;stroke-dasharray:3 3}"
            + ".lab{fill:var(--muted);font-size:9.5px}.val{fill:var(--text);font-size:10px;font-weight:700}"
            + ".unit,.muted{color:var(--muted);font-size:.78rem;margin:6px 0 0}"
            + ".pie{display:flex;flex-wrap:wrap;gap:14px;align-items:center;justify-content:center}.pieSvg{width:170px;height:170px;flex:none}"
            + ".pieBg{stroke:var(--track)}.pieTotal{fill:var(--text);font-size:5px;font-weight:700}"
            + ".legend{list-style:none;margin:0;padding:0;flex:1;min-width:150px}.legend li{display:flex;align-items:center;gap:8px;font-size:.85rem;padding:3px 0}"
            + ".legend i{width:11px;height:11px;border-radius:3px;flex:none}.legend span{flex:1;min-width:0;overflow-wrap:anywhere}.legend b{white-space:nowrap;font-weight:600}"
            + ".legend.inline{display:flex;flex-wrap:wrap;gap:4px 14px;margin-top:8px}.legend.inline li{padding:0}"
            + ".scroll{overflow-x:auto}table{border-collapse:collapse;width:100%;font-size:.85rem}"
            + "th,td{padding:8px 10px;border-bottom:1px solid var(--line);text-align:start;white-space:nowrap}th{color:var(--muted);font-weight:600;font-size:.78rem}"
            + "td.num{font-variant-numeric:tabular-nums}.notes ul{margin:0;padding-inline-start:20px}.notes li{margin:4px 0;font-size:.88rem}"
            + "footer{text-align:center;color:var(--muted);font-size:.72rem;margin-top:14px}";
}
