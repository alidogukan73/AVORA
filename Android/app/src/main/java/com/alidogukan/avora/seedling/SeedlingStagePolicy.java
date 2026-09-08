package com.alidogukan.avora.seedling;
import java.util.Arrays;
import java.util.List;

/** Ordered seedling stages with one-step forward and recovery operations. */
public final class SeedlingStagePolicy {
    public static final String SOWN = "SOWN";
    public static final String GERMINATING = "GERMINATING";
    public static final String COTYLEDON = "COTYLEDON";
    public static final String TRUE_LEAVES = "TRUE_LEAVES";
    public static final String HARDENING = "HARDENING";
    public static final String READY = "READY";
    private static final List<String> ORDER = Arrays.asList(
            SOWN, GERMINATING, COTYLEDON, TRUE_LEAVES, HARDENING, READY);
    private SeedlingStagePolicy() { }
    public static String normalize(String value) {
        return ORDER.contains(value) ? value : SOWN;
    }
    public static String next(String value) {
        int index = ORDER.indexOf(normalize(value));
        return ORDER.get(Math.min(index + 1, ORDER.size() - 1));
    }
    public static String previous(String value) {
        int index = ORDER.indexOf(normalize(value));
        return ORDER.get(Math.max(index - 1, 0));
    }
    public static int progress(String value) {
        return ORDER.indexOf(normalize(value)) + 1;
    }
    public static boolean canAdvance(String value) {
        return !READY.equals(normalize(value));
    }
    public static boolean canRetreat(String value) {
        return !SOWN.equals(normalize(value));
    }
}
