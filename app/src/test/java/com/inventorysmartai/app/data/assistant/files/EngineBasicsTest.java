package com.inventorysmartai.app.data.assistant.files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Addresses, number reading, the calculator and the tiered-commission rules. */
public class EngineBasicsTest {

    // ------------------------------------------------------------------ A1

    @Test
    public void columnLettersRoundTrip() {
        assertEquals("A", A1.columnLetters(0));
        assertEquals("Z", A1.columnLetters(25));
        assertEquals("AA", A1.columnLetters(26));
        assertEquals("AE", A1.columnLetters(30));
        assertEquals("XFD", A1.columnLetters(16383));
        assertEquals(31, A1.columnIndex("AF"));
        assertEquals(-1, A1.columnIndex("XFE"));
        assertEquals(-1, A1.columnIndex("A1"));
    }

    @Test
    public void parsesAddressesAndRanges() {
        A1.Ref ref = A1.parse("$AE$5");
        assertEquals(5, ref.row);
        assertEquals(30, ref.col);
        assertNull(A1.parse("5A"));
        assertNull(A1.parse("A0"));
        assertNull(A1.parse(""));
        assertNull(A1.parse(null));
        A1.Range range = A1.parseRange("B1:AG1");
        assertTrue(range.contains(1, 5));
        assertFalse(range.contains(2, 5));
        assertEquals("B1:AG1", range.toString());
        assertEquals("C3", A1.parseRange("C3").toString());
    }

    // ------------------------------------------------------------------ numbers

    @Test
    public void numbersWithUnitsAreRead() {
        assertEquals(9.0, CellValues.parseNumber("9 حبه"), 0.0);
        assertEquals("حبه", CellValues.unitOf("9 حبه"));
        assertEquals(50.0, CellValues.parseNumber("٥٠ كرتون"), 0.0);
        assertEquals(1234.5, CellValues.parseNumber("1,234.5"), 0.0);
        assertEquals(1.5, CellValues.parseNumber("1,5"), 0.0);
        assertEquals(-5.0, CellValues.parseNumber("(5)"), 0.0);
        assertEquals(12.0, CellValues.parseNumber("12%"), 0.0);
        assertEquals("%", CellValues.unitOf("12%"));
        assertEquals("", CellValues.unitOf("42"));
    }

    @Test
    public void datesAndSentencesAreNotNumbers() {
        assertNull(CellValues.parseNumber("10/29"));
        assertNull(CellValues.parseNumber("04/07 2027"));
        assertNull(CellValues.parseNumber("2026 \\ 07 \\ 30"));
        assertNull(CellValues.parseNumber("12 شارع الملك 5"));
        assertNull(CellValues.parseNumber("-"));
        assertNull(CellValues.parseNumber(""));
        assertNull(CellValues.parseNumber(null));
        assertNull(CellValues.parseNumber("سيتي مارت"));
    }

    @Test
    public void onlyPlainNumbersBecomeNumbers() {
        assertEquals(120.0, CellValues.plainNumber("120"), 0.0);
        assertEquals(15.5, CellValues.plainNumber("15.5"), 0.0);
        assertEquals(1234.0, CellValues.plainNumber("1,234"), 0.0);
        assertEquals(50.0, CellValues.plainNumber("٥٠"), 0.0);
        assertEquals(-5.0, CellValues.plainNumber(" -5 "), 0.0);
        assertEquals(0.5, CellValues.plainNumber("0.5"), 0.0);
        assertNull(CellValues.plainNumber("007"));
        assertNull(CellValues.plainNumber("6281001234567"));
        assertNull(CellValues.plainNumber("10 كرتون"));
        assertNull(CellValues.plainNumber("12/05"));
        assertNull(CellValues.plainNumber("1,23"));
        assertNull(CellValues.plainNumber(""));
        assertNull(CellValues.plainNumber(null));
    }

    @Test
    public void numbersAreFormattedPlainly() {
        assertEquals("51", CellValues.formatNumber(51.0));
        assertEquals("0.3", CellValues.formatNumber(0.1 + 0.2));
        assertEquals("1234.567891", CellValues.formatNumber(1234.5678912));
        assertEquals("-7", CellValues.formatNumber(-7.0));
    }

    @Test
    public void arabicLabelsMatchDespiteSpellingVariants() {
        assertEquals(CellValues.normalizeForSearch("سيتي مارت الدائري"), CellValues.normalizeForSearch("  سِيتي   مارت  الدائري "));
        assertEquals(CellValues.normalizeForSearch("أحمد"), CellValues.normalizeForSearch("احمد"));
        assertEquals(CellValues.normalizeForSearch("شركة"), CellValues.normalizeForSearch("شركه"));
        assertEquals(CellValues.normalizeForSearch("٢٤ × ٢٠٠"), CellValues.normalizeForSearch("24 × 200"));
    }

    // ------------------------------------------------------------------ calculator

    @Test
    public void calculatorFollowsPrecedence() {
        assertEquals(7.0, ExpressionEvaluator.evaluate("1+2*3"), 0.0);
        assertEquals(9.0, ExpressionEvaluator.evaluate("(1+2)*3"), 0.0);
        assertEquals(512.0, ExpressionEvaluator.evaluate("2^3^2"), 0.0);
        assertEquals(-4.0, ExpressionEvaluator.evaluate("-2^2"), 0.0);
        assertEquals(0.1, ExpressionEvaluator.evaluate("10%"), 1e-12);
        assertEquals(30.0, ExpressionEvaluator.evaluate("200*15%"), 1e-9);
    }

    @Test
    public void calculatorUnderstandsArabicInput() {
        assertEquals(12.0, ExpressionEvaluator.evaluate("٣ × ٤"), 0.0);
        assertEquals(3500.0, ExpressionEvaluator.evaluate("1,000 + 2,500"), 0.0);
        assertEquals(2.5, ExpressionEvaluator.evaluate("٥ ÷ ٢"), 0.0);
    }

    @Test
    public void calculatorFunctions() {
        assertEquals(6.0, ExpressionEvaluator.evaluate("sum(1,2,3)"), 0.0);
        assertEquals(3.0, ExpressionEvaluator.evaluate("avg(2,4)"), 0.0);
        assertEquals(5.0, ExpressionEvaluator.evaluate("max(1,5,3)"), 0.0);
        assertEquals(2.35, ExpressionEvaluator.evaluate("round(2.345, 2)"), 1e-12);
        assertEquals(3.0, ExpressionEvaluator.evaluate("round(2.5)"), 0.0);
        assertEquals(12.5, ExpressionEvaluator.evaluate("percent(25, 200)"), 1e-12);
        assertEquals(1.0, ExpressionEvaluator.evaluate("mod(10, 3)"), 0.0);
    }

    @Test
    public void calculatorRefusesAnythingElse() {
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                ExpressionEvaluator.evaluate("1/(2-2)");
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                ExpressionEvaluator.evaluate("System.exit(0)");
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                ExpressionEvaluator.evaluate("2+");
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                ExpressionEvaluator.evaluate("unknown(1)");
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                ExpressionEvaluator.evaluate("1e3");
            }
        });
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                ExpressionEvaluator.evaluate("");
            }
        });
        final StringBuilder longText = new StringBuilder("1");
        for (int i = 0; i < 500; i++) {
            longText.append("+1");
        }
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                ExpressionEvaluator.evaluate(longText.toString());
            }
        });
    }

    // ------------------------------------------------------------------ commission

    private static Map<String, Object> group(String name, Object achieved, Integer[] targets, Integer[] commissions) {
        Map<String, Object> g = new LinkedHashMap<String, Object>();
        g.put("name", name);
        g.put("achieved", achieved);
        g.put("targets", new ArrayList<Object>(Arrays.asList((Object[]) targets)));
        g.put("commissions", new ArrayList<Object>(Arrays.asList((Object[]) commissions)));
        return g;
    }

    /** The monthly target sheet: [group, tier targets, tier commissions]. */
    private static Map<String, Object> plan(String mode, double... achieved) {
        Object[][] sheet = {
                {"عصير فاخر", new Integer[]{51, 75, 100}, new Integer[]{4000, 6000, 8000}},
                {"شراب فيمتو", new Integer[]{34, 56, 85}, new Integer[]{4000, 6000, 8000}},
                {"حليب السعودية صغير", new Integer[]{100, 150, 200}, new Integer[]{4000, 6000, 8000}},
                {"حليب السعودية كبير", new Integer[]{50, 75, 100}, new Integer[]{4500, 6000, 8000}},
                {"حليب السعودية منكهات", new Integer[]{100, 130, 170}, new Integer[]{4500, 6000, 8000}},
                {"حليب السعودية مدارس", new Integer[]{100, 150, 200}, new Integer[]{4500, 6000, 8000}},
                {"منتجات الخريف", new Integer[]{185, 278, 370}, new Integer[]{13000, 15000, 18000}},
        };
        List<Object> groups = new ArrayList<Object>();
        for (int i = 0; i < sheet.length; i++) {
            groups.add(group((String) sheet[i][0], Double.valueOf(achieved[i]), (Integer[]) sheet[i][1], (Integer[]) sheet[i][2]));
        }
        Map<String, Object> spec = new LinkedHashMap<String, Object>();
        spec.put("mode", mode);
        spec.put("groups", groups);
        return spec;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> groupResult(Map<String, Object> result, int index) {
        return (Map<String, Object>) ((List<Object>) result.get("groups")).get(index);
    }

    @Test
    public void commissionPaysTheHighestTierReached() {
        // 80 -> tier 2 (6000) | 34 -> exactly tier 1 (4000) | 99 -> none | 100 -> tier 3 | 170 -> tier 3 | 149 -> tier 1 | 300 -> tier 2
        Map<String, Object> result = CommissionCalculator.calculate(plan("HIGHEST_TIER", 80, 34, 99, 100, 170, 149, 300));
        assertEquals(45500L, ((Number) result.get("totalCommission")).longValue());
        assertEquals(6, ((Number) result.get("groupsWithCommission")).intValue());
        assertEquals(2, ((Number) groupResult(result, 0).get("tierReached")).intValue());
        assertEquals(6000L, ((Number) groupResult(result, 0).get("commission")).longValue());
        assertEquals(1, ((Number) groupResult(result, 1).get("tierReached")).intValue());
        assertEquals(0, ((Number) groupResult(result, 2).get("tierReached")).intValue());
        assertEquals(1L, ((Number) groupResult(result, 2).get("remainingToNext")).longValue());
        assertEquals(4000L, ((Number) groupResult(result, 2).get("nextTierCommission")).longValue());
        assertTrue(groupResult(result, 3).containsKey("allTiersReached"));
    }

    @Test
    public void cumulativeModeAddsEveryLevelReached() {
        Map<String, Object> result = CommissionCalculator.calculate(plan("CUMULATIVE", 80, 0, 0, 0, 0, 0, 0));
        assertEquals(10000L, ((Number) result.get("totalCommission")).longValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void missingAchievedCountsAsZeroAndIsReported() {
        Map<String, Object> spec = plan("HIGHEST_TIER", 0, 0, 0, 0, 0, 0, 0);
        ((Map<String, Object>) ((List<Object>) spec.get("groups")).get(0)).remove("achieved");
        Map<String, Object> result = CommissionCalculator.calculate(spec);
        assertEquals(0L, ((Number) result.get("totalCommission")).longValue());
        assertTrue(result.containsKey("notes"));
    }

    @Test
    public void tiersMayBeGivenAsObjectsInAnyOrder() {
        Map<String, Object> low = new LinkedHashMap<String, Object>();
        low.put("target", 10);
        low.put("commission", 100);
        Map<String, Object> high = new LinkedHashMap<String, Object>();
        high.put("target", 20);
        high.put("commission", 250);
        Map<String, Object> g = new LinkedHashMap<String, Object>();
        g.put("name", "x");
        g.put("achieved", "25");
        g.put("tiers", new ArrayList<Object>(Arrays.asList(high, low)));
        Map<String, Object> spec = new LinkedHashMap<String, Object>();
        spec.put("groups", new ArrayList<Object>(Arrays.asList(g)));
        assertEquals(250L, ((Number) CommissionCalculator.calculate(spec).get("totalCommission")).longValue());
    }

    @Test
    public void incompleteTiersAreRejected() {
        final Map<String, Object> g = new LinkedHashMap<String, Object>();
        g.put("name", "x");
        g.put("targets", new ArrayList<Object>(Arrays.asList(1, 2)));
        g.put("commissions", new ArrayList<Object>(Arrays.asList(5)));
        final Map<String, Object> spec = new LinkedHashMap<String, Object>();
        spec.put("groups", new ArrayList<Object>(Arrays.asList(g)));
        assertThrows(IllegalArgumentException.class, new org.junit.Assert.ThrowingRunnable() {
            public void run() {
                CommissionCalculator.calculate(spec);
            }
        });
    }
}
