package com.alidogukan.avora.firebase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A small, detached fertilizer snapshot with tracked read/write preconditions. */
final class FertilizerData {
    private final Map<String, Object> before;
    private final Map<String, Object> working;
    private final Map<String, Object> writes;
    private final Map<String, Object> checks;
    private final String path;

    FertilizerData(Map<String, Object> snapshot) {
        before = copyMap(snapshot);
        working = copyMap(snapshot);
        writes = new LinkedHashMap<>();
        checks = new LinkedHashMap<>();
        path = "";
    }

    private FertilizerData(FertilizerData parent, String path) {
        before = parent.before;
        working = parent.working;
        writes = parent.writes;
        checks = parent.checks;
        this.path = path;
    }

    FertilizerData child(String child) {
        return new FertilizerData(this, path.isEmpty() ? child : path + "/" + child);
    }

    String getKey() { return path.substring(path.lastIndexOf('/') + 1); }

    Object getValue() {
        check(path, read(before, path));
        return read(working, path);
    }

    boolean exists() {
        checkShallow(path, read(before, path));
        return read(working, path) != null;
    }

    Iterable<FertilizerData> getChildren() {
        Object value = getValue();
        List<FertilizerData> result = new ArrayList<>();
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            for (Object key : map.keySet()) result.add(child(key.toString()));
        }
        return result;
    }

    void setValue(Object value) {
        // Guard overwritten fields too, including an absent destination. This
        // preserves stock/history against older clients that have no revision.
        check(path, read(before, path));
        put(working, path, normalize(value));
        writes.keySet().removeIf(key -> key.equals(path) || key.startsWith(path + "/"));
        String ancestor = null;
        for (String key : writes.keySet()) {
            if (path.startsWith(key + "/")) { ancestor = key; break; }
        }
        if (ancestor == null) writes.put(path, normalize(value));
        else writes.put(ancestor, normalize(read(working, ancestor)));
    }

    Map<String, Object> updates(long revision, String operationId) {
        Map<String, Object> result = new LinkedHashMap<>(writes);
        Map<String, Object> indexed = new LinkedHashMap<>();
        int index = 0;
        for (Object check : new java.util.TreeMap<>(checks).values()) indexed.put("c" + index++, check);
        result.put("fertilizer_write_guard", Map.of("revision", revision + 1, "operation_id", operationId, "checks", indexed));
        return result;
    }

    private void check(String target, Object value) {
        checkShallow(target, value);
        if (value instanceof Map<?, ?>) {
            Map<?, ?> map = (Map<?, ?>) value;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                check(target + "/" + entry.getKey(), entry.getValue());
            }
        }
    }

    private void checkShallow(String target, Object value) {
        if (target.isEmpty()) throw new IllegalArgumentException("Unscoped fertilizer read");
        Map<String, Object> check = new LinkedHashMap<>();
        check.put("path", target);
        check.put("exists", value != null);
        check.put("branch", value instanceof Map);
        if (value != null && !(value instanceof Map)) check.put("value", value);
        checks.put(target, check);
    }

    static Object read(Map<String, Object> root, String path) {
        Object value = root;
        if (path.isEmpty()) return value;
        for (String segment : path.split("/")) {
            if (!(value instanceof Map<?, ?>)) return null;
            value = ((Map<?, ?>) value).get(segment);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    static void put(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("/");
        Map<String, Object> node = root;
        for (int i = 0; i < parts.length - 1; i++) {
            if (!(node.get(parts[i]) instanceof Map)) node.put(parts[i], new LinkedHashMap<String, Object>());
            node = (Map<String, Object>) node.get(parts[i]);
        }
        if (value == null) node.remove(parts[parts.length - 1]);
        else node.put(parts[parts.length - 1], value);
    }

    private static Map<String, Object> copyMap(Map<?, ?> input) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : input.entrySet()) result.put(entry.getKey().toString(), normalize(entry.getValue()));
        return result;
    }

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?>) return copyMap((Map<?, ?>) value);
        if (value instanceof List<?>) {
            List<?> list = (List<?>) value;
            Map<String, Object> result = new LinkedHashMap<>();
            for (int i = 0; i < list.size(); i++) if (list.get(i) != null) result.put(Integer.toString(i), normalize(list.get(i)));
            return result;
        }
        return value;
    }
}
