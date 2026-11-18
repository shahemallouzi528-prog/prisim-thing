package dev.prism.engine.core;

import java.util.Arrays;

/** Fixed-size ring buffer of frame times (ms). No allocation after construction. */
public final class FrameStats {
    private final double[] ring;
    private final double[] scratch;
    private int count;
    private int head;

    public FrameStats(int capacity) {
        if (capacity < 2) throw new IllegalArgumentException("capacity");
        ring = new double[capacity];
        scratch = new double[capacity];
    }

    public void record(double frameMs) {
        if (!(frameMs > 0) || Double.isInfinite(frameMs)) return; // ignore NaN/zero/negative/inf
        ring[head] = frameMs;
        head = (head + 1) % ring.length;
        if (count < ring.length) count++;
    }

    public int size() { return count; }

    public void clear() { count = 0; head = 0; }

    public double averageMs() {
        if (count == 0) return 0;
        double s = 0;
        for (int i = 0; i < count; i++) s += ring[i];
        return s / count;
    }

    public double averageFps() {
        double a = averageMs();
        return a > 0 ? 1000.0 / a : 0;
    }

    /** Frame time at the given percentile (0-100), nearest-rank. Sorts a scratch copy; call at low frequency. */
    public double percentileMs(double pct) {
        if (count == 0) return 0;
        System.arraycopy(ring, 0, scratch, 0, count);
        Arrays.sort(scratch, 0, count);
        int idx = (int) Math.ceil(pct / 100.0 * count) - 1;
        return scratch[Math.max(0, Math.min(count - 1, idx))];
    }

    /** "1% low" FPS, i.e. FPS of the 99th percentile frame time. */
    public double onePercentLowFps() {
        double p = percentileMs(99);
        return p > 0 ? 1000.0 / p : 0;
    }

    /** Copies the newest {@code n} samples (oldest first) into out for graphing; returns how many were written. */
    public int copyRecent(double[] out) {
        int n = Math.min(out.length, count);
        for (int i = 0; i < n; i++) {
            int src = (head - n + i + ring.length * 2) % ring.length;
            out[i] = ring[src];
        }
        return n;
    }
}
