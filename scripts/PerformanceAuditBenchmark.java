import java.lang.management.ManagementFactory;
import com.nisovin.magicspells.util.performance.PerformanceExecution;
import com.nisovin.magicspells.util.performance.PerformanceRecorder;
import static com.nisovin.magicspells.util.performance.PerformanceExecution.Operation.*;

/** Synthetic overhead check, not a gameplay benchmark. Run with Java 25 after :core:classes. */
class PerformanceAuditBenchmark {
    private static volatile long sink;
    private static final int ITERATIONS = 200_000;
    private static long work(long value) {
        for (int i = 0; i < 32; i++) value = value * 1664525 + 1013904223;
        return value;
    }

    public static void main(String[] args) {
        var memory = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        for (int round = 0; round < 7; round++) {
            for (String mode : new String[] {"baseline", "idle", "recording"}) {
                var recorder = new PerformanceRecorder();
                var execution = new PerformanceExecution(recorder);
                Runnable callback = () -> {
                    try (var passive = execution.enter(PASSIVE, "test", "")) {
                        try (var scan = execution.enter(INVENTORY_SCAN, null, "")) {
                            for (int i = 0; i < 36; i++) execution.matchCall();
                            sink = work(sink);
                        }
                    }
                };
                Runnable task = mode.equals("baseline") ? () -> sink = work(sink)
                        : execution.task("test", "interval:1", callback);
                if (mode.equals("recording")) recorder.start(300);
                for (int i = 0; i < 20_000; i++) task.run();
                long bytes = memory.getThreadAllocatedBytes(thread);
                long start = System.nanoTime();
                for (int i = 0; i < ITERATIONS; i++) task.run();
                long elapsed = System.nanoTime() - start;
                bytes = memory.getThreadAllocatedBytes(thread) - bytes;
                if (round >= 4) System.out.printf("%s: %.1f ns/operation, %.2f bytes/operation%n",
                        mode, elapsed / (double) ITERATIONS, bytes / (double) ITERATIONS);
                if (recorder.isActive()) recorder.stop("benchmark");
            }
        }
    }
}
