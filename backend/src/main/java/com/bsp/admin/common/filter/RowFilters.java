package com.bsp.admin.common.filter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 列表通用列筛选（2026-09-10 用户需求：所有字段都支持表头漏斗筛选）
 *
 * <p>前端以 `filters` 查询参数传 JSON：`{"字段":{"op":"like|eq|in|gte|lte|between","value":...}}`；
 * 各模块通过白名单字段映射（字段名 → 取值函数）接入，非白名单字段一律忽略。</p>
 */
public final class RowFilters {

    /** 固定对象映射器（仅用于解析 filters 参数） */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 单个筛选条件 */
    public record Condition(String op, Object value) {
    }

    /** 空条件（不筛选） */
    public static final Map<String, Condition> EMPTY = Map.of();

    private RowFilters() {
    }

    /** 解析 filters JSON（非法/空值一律视为不筛选，不抛异常） */
    public static Map<String, Condition> parse(String json) {
        if (json == null || json.isBlank()) {
            return EMPTY;
        }
        try {
            Map<String, Map<String, Object>> raw = MAPPER.readValue(json, new TypeReference<>() {
            });
            Map<String, Condition> result = new LinkedHashMap<>();
            raw.forEach((field, spec) -> {
                if (field == null || spec == null || spec.get("value") == null) {
                    return;
                }
                Object op = spec.get("op");
                result.put(field, new Condition(op == null ? "eq" : String.valueOf(op), spec.get("value")));
            });
            return result;
        } catch (Exception e) {
            return EMPTY;
        }
    }

    /** 按白名单字段匹配（记录/实体类） */
    public static <T> boolean match(T item, Map<String, Condition> filters,
                                    Map<String, Function<T, Object>> fields) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Condition> entry : filters.entrySet()) {
            Function<T, Object> getter = fields.get(entry.getKey());
            if (getter == null) {
                continue; // 非白名单字段忽略
            }
            if (!matchValue(getter.apply(item), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** 按行 Map（列表行已含全部展示字段）匹配：仅允许行内既有字段 */
    public static boolean matchRow(Map<String, Object> row, Map<String, Condition> filters) {
        if (filters == null || filters.isEmpty()) {
            return true;
        }
        for (Map.Entry<String, Condition> entry : filters.entrySet()) {
            if (!row.containsKey(entry.getKey())) {
                continue; // 非白名单字段忽略
            }
            if (!matchValue(row.get(entry.getKey()), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    /** 单值匹配（值可能是集合：任一元素命中即算命中） */
    private static boolean matchValue(Object actual, Condition condition) {
        if (actual == null) {
            return false;
        }
        if (actual instanceof Collection<?> collection) {
            return collection.stream().anyMatch(item -> matchOne(item, condition));
        }
        return matchOne(actual, condition);
    }

    private static boolean matchOne(Object actual, Condition condition) {
        String op = condition.op() == null ? "eq" : condition.op();
        switch (op) {
            case "like":
                return text(actual).toLowerCase().contains(text(condition.value()).toLowerCase());
            case "in":
                if (condition.value() instanceof Collection<?> values) {
                    return values.stream().anyMatch(v -> equalsValue(actual, v));
                }
                return equalsValue(actual, condition.value());
            case "gte":
                return compare(actual, condition.value()) >= 0;
            case "lte":
                return compare(actual, condition.value()) <= 0;
            case "between":
                if (condition.value() instanceof List<?> range && range.size() == 2) {
                    return compare(actual, range.get(0)) >= 0 && compare(actual, range.get(1)) <= 0;
                }
                return true;
            case "eq":
            default:
                return equalsValue(actual, condition.value());
        }
    }

    /** 等值：数值优先按数值比较，其余按字符串比较 */
    private static boolean equalsValue(Object actual, Object expected) {
        Double a = numberOf(actual);
        Double b = numberOf(expected);
        if (a != null && b != null) {
            return a.doubleValue() == b.doubleValue();
        }
        return text(actual).equals(text(expected));
    }

    /** 比较：数值按数值，日期/字符串按字典序（ISO 格式日期可直接比较） */
    private static int compare(Object actual, Object expected) {
        Double a = numberOf(actual);
        Double b = numberOf(expected);
        if (a != null && b != null) {
            return Double.compare(a, b);
        }
        return text(actual).compareTo(text(expected));
    }

    private static Double numberOf(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value instanceof String s && s.matches("-?\\d+(\\.\\d+)?")) {
            return Double.valueOf(s);
        }
        return null;
    }

    private static String text(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof java.time.LocalDateTime time) {
            return time.toLocalDate().toString();
        }
        return String.valueOf(value);
    }
}
