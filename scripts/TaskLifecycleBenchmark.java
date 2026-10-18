import com.nisovin.magicspells.util.performance.TaskLifecycle;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;

/** Synthetic registry smoke measurement; no server, gameplay or CI timing threshold. */
class TaskLifecycleBenchmark {
    private static volatile long sink;
    private static final int POPULATION = 36_000, EXECUTIONS = 200_000;

    public static void main(String[] args) {
        var memory = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        for (int round = 0; round < 6; round++) {
            var tasks = new TaskLifecycle();
            var callback = new AtomicReference<Runnable>();
            var pending = new ArrayList<TaskLifecycle.Pending>();
            long started = System.nanoTime();
            for (int i = 0; i < POPULATION; i++) {
                int id = i;
                tasks.schedule("benchmark", TaskLifecycle.schedulingOrigin(), 100, 5, () -> sink++, body -> {
                    if (id == 0) callback.set(body);
                    return id;
                });
            }
            long registrations = System.nanoTime() - started;
            for (int i = 0; i < POPULATION; i++) pending.add(new TaskLifecycle.Pending(i, true));
            Runnable body = callback.get();
            for (int i = 0; i < 20_000; i++) body.run();
            for (boolean capture : new boolean[]{false, true}) {
                long snapshot = 0;
                if (capture) {
                    started = System.nanoTime();
                    tasks.start(started, 60, () -> pending);
                    snapshot = System.nanoTime() - started;
                }
                long bytes = memory.getThreadAllocatedBytes(thread);
                started = System.nanoTime();
                for (int i = 0; i < EXECUTIONS; i++) body.run();
                long elapsed = System.nanoTime() - started;
                bytes = memory.getThreadAllocatedBytes(thread) - bytes;
                if (round >= 3) System.out.printf("%s: %.1f ns/callback, %.2f bytes/callback; registration %.1f ns/task; start snapshot %.2f ms%n",
                        capture ? "capturing" : "idle", elapsed / (double) EXECUTIONS, bytes / (double) EXECUTIONS,
                        registrations / (double) POPULATION, snapshot / 1_000_000.0);
                if (capture) tasks.stop(() -> pending);
            }
            tasks.clear();
            if (tasks.retainedTasks() != 0) throw new AssertionError("Registry cleanup failed");
        }
    }
}
