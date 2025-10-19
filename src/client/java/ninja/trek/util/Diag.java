package ninja.trek.util;

import ninja.trek.Craneshot;
import org.slf4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class Diag {
    private static final Logger LOG = Craneshot.LOGGER;
    public static volatile boolean ENABLED = true;
    private static final Map<String, Long> lastByKey = new ConcurrentHashMap<>();

    private Diag() {}

    public static void ev(String fmt, Object... args) {
        if (!ENABLED) return;
        try { LOG.info("(diag) " + fmt, args); } catch (Throwable ignore) { }
    }

    public static void trans(String fmt, Object... args) {
        if (!ENABLED) return;
        try { LOG.info("(diag) TRANS " + fmt, args); } catch (Throwable ignore) { }
    }

    public static void warnOnce(String key, long minIntervalMs, String fmt, Object... args) {
        if (!ENABLED) return;
        long now = System.currentTimeMillis();
        Long last = lastByKey.get(key);
        if (last != null && now - last < minIntervalMs) return;
        lastByKey.put(key, now);
        try { LOG.warn("(diag) " + fmt, args); } catch (Throwable ignore) { }
    }

    public static void warnWithStack(String key, long minIntervalMs, String fmt, int maxFrames, Object... args) {
        if (!ENABLED) return;
        long now = System.currentTimeMillis();
        Long last = lastByKey.get(key);
        if (last != null && now - last < minIntervalMs) return;
        lastByKey.put(key, now);
        try {
            LOG.warn("(diag) " + fmt, args);
            StackTraceElement[] st = new Throwable().getStackTrace();
            int limit = Math.min(maxFrames, st.length);
            for (int i = 1; i < limit; i++) { // skip frame 0 (this method)
                LOG.warn("    at {}", st[i]);
            }
        } catch (Throwable ignore) { }
    }
}

