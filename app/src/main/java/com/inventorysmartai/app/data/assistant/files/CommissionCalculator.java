package com.inventorysmartai.app.data.assistant.files;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tiered targets and commissions, as on the promoters' monthly target sheet: each product group has several levels
 * (الفئة الأولى / الثانية / الثالثة), each with a quantity target and the commission paid for reaching it.
 *
 * Spec: { mode: "HIGHEST_TIER" (default) | "CUMULATIVE",
 *         groups: [ { name, achieved, tiers: [ { target, commission } ... ] } ... ] }
 * A group may also give targets: [..] and commissions: [..] instead of tiers.
 *
 *  - HIGHEST_TIER: the commission is the one of the highest level reached (reaching level 2 pays level 2's amount).
 *  - CUMULATIVE:   the commissions of every level reached are added up.
 */
public final class CommissionCalculator {

    private static final double EPS = 1e-9;

    private CommissionCalculator() {
    }

    private static final class Tier {
        final double target;
        final double commission;

        Tier(double target, double commission) {
            this.target = target;
            this.commission = commission;
        }
    }

    public static Map<String, Object> calculate(Map<String, ?> spec) {
        String mode = Specs.string(spec.get("mode")).trim().toUpperCase(java.util.Locale.ROOT);
        boolean cumulative = mode.equals("CUMULATIVE");
        List<Object> groups = Specs.list(spec.get("groups"));
        if (groups.isEmpty()) {
            throw new IllegalArgumentException("لا توجد مجموعات (groups) لحساب العمولة");
        }

        List<Object> results = new ArrayList<Object>();
        List<String> notes = new ArrayList<String>();
        BigDecimal total = BigDecimal.ZERO;
        int paidGroups = 0;
        int index = 0;
        for (Object raw : groups) {
            index++;
            Map<String, Object> g = Specs.map(raw);
            String name = Specs.string(g.get("name")).trim();
            if (name.isEmpty()) {
                name = "المجموعة " + index;
            }
            List<Tier> tiers = readTiers(g, name);
            Double achievedValue = Specs.number(g.get("achieved"));
            if (achievedValue == null) {
                notes.add("لم يُذكر المحقَّق للمجموعة \"" + name + "\" فاعتُبر صفرًا");
            }
            double achieved = achievedValue == null ? 0.0 : achievedValue;

            int reached = 0;
            BigDecimal commission = BigDecimal.ZERO;
            for (int i = 0; i < tiers.size(); i++) {
                if (achieved + EPS >= tiers.get(i).target) {
                    reached = i + 1;
                    BigDecimal amount = BigDecimal.valueOf(tiers.get(i).commission);
                    commission = cumulative ? commission.add(amount) : amount;
                }
            }

            Map<String, Object> out = new LinkedHashMap<String, Object>();
            out.put("group", name);
            out.put("achieved", CellValues.numberObject(achieved));
            out.put("tierReached", reached);
            out.put("commission", CellValues.numberObject(commission.doubleValue()));
            if (reached < tiers.size()) {
                Tier next = tiers.get(reached);
                out.put("nextTier", reached + 1);
                out.put("nextTarget", CellValues.numberObject(next.target));
                out.put("remainingToNext", CellValues.numberObject(Math.max(0.0, next.target - achieved)));
                out.put("nextTierCommission", CellValues.numberObject(next.commission));
            } else {
                out.put("allTiersReached", true);
            }
            results.add(out);
            total = total.add(commission);
            if (reached > 0) {
                paidGroups++;
            }
        }

        Map<String, Object> result = new LinkedHashMap<String, Object>();
        result.put("mode", cumulative ? "CUMULATIVE" : "HIGHEST_TIER");
        result.put("groups", results);
        result.put("totalCommission", CellValues.numberObject(total.doubleValue()));
        result.put("groupsWithCommission", paidGroups);
        result.put("groupsCount", groups.size());
        if (!notes.isEmpty()) {
            result.put("notes", notes);
        }
        return result;
    }

    private static List<Tier> readTiers(Map<String, Object> group, String groupName) {
        List<Tier> tiers = new ArrayList<Tier>();
        List<Object> given = Specs.list(group.get("tiers"));
        if (!given.isEmpty()) {
            for (Object t : given) {
                Map<String, Object> tier = Specs.map(t);
                Double target = Specs.number(tier.get("target"));
                Double commission = Specs.number(tier.get("commission"));
                if (target == null || commission == null) {
                    throw new IllegalArgumentException("مستوى غير مكتمل في \"" + groupName + "\": يلزم target و commission رقمان");
                }
                tiers.add(new Tier(target, commission));
            }
        } else {
            List<Object> targets = Specs.list(group.get("targets"));
            List<Object> commissions = Specs.list(group.get("commissions"));
            if (targets.isEmpty() || targets.size() != commissions.size()) {
                throw new IllegalArgumentException("المجموعة \"" + groupName + "\" تحتاج tiers أو قائمتين متساويتين targets وcommissions");
            }
            for (int i = 0; i < targets.size(); i++) {
                Double target = Specs.number(targets.get(i));
                Double commission = Specs.number(commissions.get(i));
                if (target == null || commission == null) {
                    throw new IllegalArgumentException("قيمة غير رقمية في مستويات \"" + groupName + "\"");
                }
                tiers.add(new Tier(target, commission));
            }
        }
        Collections.sort(tiers, new Comparator<Tier>() {
            @Override
            public int compare(Tier a, Tier b) {
                return Double.compare(a.target, b.target);
            }
        });
        return tiers;
    }
}
