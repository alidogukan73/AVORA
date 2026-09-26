package com.alidogukan.avora.backup;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure-Java merge policy used by NAS sync and JVM tests. */
final class NasTwoWaySyncMerge {
    private NasTwoWaySyncMerge() { }

    static Result plan(Map<String, Object> currentBackup,
                       Map<String, Object> nasBackup) {
        if (currentBackup == null) {
            throw new IllegalArgumentException("Current backup is required.");
        }
        Map<String, Object> current = copyMap(currentBackup);
        Map<String, Object> remote = nasBackup == null
                ? new LinkedHashMap<>() : copyMap(nasBackup);
        int toCurrent = missingLeafCount(section(remote, "firebase_data"),
                section(current, "firebase_data"));
        int toNas = missingLeafCount(section(current, "firebase_data"),
                section(remote, "firebase_data"));
        mergeMissingSection(current, remote, "firebase_data");
        mergeMissingSection(current, remote, "local_preferences");
        return new Result(current, toCurrent, toNas);
    }

    private static void mergeMissingSection(Map<String, Object> targetRoot,
                                            Map<String, Object> sourceRoot,
                                            String section) {
        Map<String, Object> source = section(sourceRoot, section);
        if (source == null) return;
        Map<String, Object> target = section(targetRoot, section);
        if (target == null) {
            targetRoot.put(section, copyMap(source));
            return;
        }
        mergeMissingMaps(target, source);
    }

    private static void mergeMissingMaps(Map<String, Object> target,
                                         Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object sourceValue = entry.getValue();
            if (!target.containsKey(key) || target.get(key) == null) {
                target.put(key, copy(sourceValue));
                continue;
            }
            Object targetValue = target.get(key);
            if (targetValue instanceof Map && sourceValue instanceof Map) {
                mergeMissingMaps(map(targetValue), map(sourceValue));
            }
        }
    }

    private static int missingLeafCount(Map<String, Object> source,
                                        Map<String, Object> target) {
        if (source == null) return 0;
        int count = 0;
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object sourceValue = entry.getValue();
            if (target == null || !target.containsKey(key) || target.get(key) == null) {
                count += leafCount(sourceValue);
            } else if (sourceValue instanceof Map && target.get(key) instanceof Map) {
                count += missingLeafCount(map(sourceValue), map(target.get(key)));
            }
        }
        return count;
    }

    private static int leafCount(Object value) {
        if (value instanceof Map) {
            Map<String, Object> values = map(value);
            if (values.isEmpty()) return 1;
            int count = 0;
            for (Object child : values.values()) count += leafCount(child);
            return count;
        }
        if (value instanceof List) return Math.max(1, ((List<?>) value).size());
        return 1;
    }

    private static Object copy(Object value) {
        if (value instanceof Map) return copyMap(map(value));
        if (value instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) value) result.add(copy(item));
            return result;
        }
        return value;
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            result.put(entry.getKey(), copy(entry.getValue()));
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }

    private static Map<String, Object> section(Map<String, Object> root, String key) {
        Object value = root.get(key);
        return value instanceof Map ? map(value) : null;
    }

    static final class Result {
        final Map<String, Object> mergedBackup;
        final int dataMissingOnCurrent;
        final int dataMissingOnNas;

        Result(Map<String, Object> mergedBackup,
               int dataMissingOnCurrent,
               int dataMissingOnNas) {
            this.mergedBackup = mergedBackup;
            this.dataMissingOnCurrent = dataMissingOnCurrent;
            this.dataMissingOnNas = dataMissingOnNas;
        }
    }
}
