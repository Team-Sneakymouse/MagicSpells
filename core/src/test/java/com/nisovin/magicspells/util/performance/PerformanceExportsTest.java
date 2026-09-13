package com.nisovin.magicspells.util.performance;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PerformanceExportsTest {
    @Test void notificationsWaitForServerThreadDeliveryEvenAfterWriteCompletes() throws Exception {
        var exports = new PerformanceExports();
        List<String> messages = new ArrayList<>();
        Thread server = Thread.currentThread();
        Thread writer = new Thread(() -> exports.submit(() -> exports.enqueueNotification(() -> {
            assertSame(server, Thread.currentThread());
            messages.add("saved");
        }), Runnable::run));
        writer.start();
        writer.join();
        assertTrue(messages.isEmpty());
        assertFalse(exports.isIdle());
        exports.deliverNotifications();
        assertEquals(List.of("saved"), messages);
        assertTrue(exports.isIdle());
        exports.deliverNotifications();
        assertEquals(1, messages.size());
    }

    @Test void separateCapturesKeepTheirOriginalNotificationRecipients() {
        var exports = new PerformanceExports();
        List<Runnable> scheduler = new ArrayList<>();
        List<String> first = new ArrayList<>();
        List<String> second = new ArrayList<>();
        exports.submit(() -> exports.enqueueNotification(() -> first.add("first saved")), scheduler::add);
        exports.submit(() -> exports.enqueueNotification(() -> second.add("second saved")), scheduler::add);
        scheduler.get(1).run();
        scheduler.getFirst().run();
        exports.deliverNotifications();
        assertEquals(List.of("first saved"), first);
        assertEquals(List.of("second saved"), second);
        assertTrue(exports.isIdle());
    }

    @Test void unloadCanDeliverCompletionWithoutAnotherSchedulerTick() {
        var exports = new PerformanceExports();
        List<String> messages = new ArrayList<>();
        exports.submit(() -> exports.enqueueNotification(() -> messages.add("saved")), task -> {});
        exports.flush();
        exports.deliverNotifications();
        assertEquals(List.of("saved"), messages);
        assertTrue(exports.isIdle());
    }

    @Test void unloadFlushesCancelledPendingWriteExactlyOnce() {
        var exports = new PerformanceExports();
        List<Runnable> scheduler = new ArrayList<>();
        var writes = new AtomicInteger();
        exports.submit(writes::incrementAndGet, scheduler::add);
        assertEquals(0, writes.get());
        exports.flush();
        scheduler.getFirst().run();
        exports.flush();
        assertEquals(1, writes.get());
    }

    @Test void completedWriteIsNotRepeatedByUnload() {
        var exports = new PerformanceExports();
        var writes = new AtomicInteger();
        exports.submit(writes::incrementAndGet, Runnable::run);
        exports.flush();
        assertEquals(1, writes.get());
    }

    @Test void schedulerRejectionStillWritesReport() {
        var exports = new PerformanceExports();
        var writes = new AtomicInteger();
        assertThrows(IllegalStateException.class, () -> exports.submit(writes::incrementAndGet,
                task -> { throw new IllegalStateException("disabled"); }));
        exports.flush();
        assertEquals(1, writes.get());
    }

    @Test void failedWriteIsNotRetainedOrRetriedImplicitly() {
        var exports = new PerformanceExports();
        var writes = new AtomicInteger();
        assertThrows(IllegalArgumentException.class, () -> exports.submit(() -> {
            writes.incrementAndGet();
            throw new IllegalArgumentException("disk failure");
        }, Runnable::run));
        exports.flush();
        assertEquals(1, writes.get());
    }

    @Test void unloadWaitsForAnAlreadyRunningWrite() throws Exception {
        var exports = new PerformanceExports();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var finished = new CountDownLatch(1);
        var writes = new AtomicInteger();
        List<Runnable> scheduler = new ArrayList<>();
        exports.submit(() -> {
            entered.countDown();
            try { release.await(); } catch (InterruptedException e) { throw new AssertionError(e); }
            writes.incrementAndGet();
        }, scheduler::add);
        Thread writer = new Thread(scheduler.getFirst());
        writer.start();
        assertTrue(entered.await(5, java.util.concurrent.TimeUnit.SECONDS));
        Thread unload = new Thread(() -> { exports.flush(); finished.countDown(); });
        unload.start();
        try {
            assertFalse(finished.await(50, java.util.concurrent.TimeUnit.MILLISECONDS));
        } finally { release.countDown(); }
        writer.join(5000);
        unload.join(5000);
        assertEquals(0, finished.getCount());
        assertEquals(1, writes.get());
    }
}
