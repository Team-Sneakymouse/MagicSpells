package com.nisovin.magicspells.util.performance;

import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Retains queued exports so shutdown can complete writes cancelled by the scheduler. */
final class PerformanceExports {
    private final Set<Export> pending = new HashSet<>();
    private final ConcurrentLinkedQueue<Runnable> notifications = new ConcurrentLinkedQueue<>();

    void enqueueNotification(Runnable notification) { notifications.add(notification); }

    /** Called by the server thread, including after shutdown flushes. */
    void deliverNotifications() {
        Runnable notification;
        while ((notification = notifications.poll()) != null) notification.run();
    }

    boolean isIdle() {
        synchronized (pending) { return pending.isEmpty() && notifications.isEmpty(); }
    }

    void submit(Runnable write, Consumer<Runnable> scheduler) {
        Export export = new Export(write);
        synchronized (pending) { pending.add(export); }
        try {
            scheduler.accept(export);
        } catch (RuntimeException e) {
            export.run();
            throw e;
        }
    }

    void flush() {
        Export[] exports;
        synchronized (pending) { exports = pending.toArray(Export[]::new); }
        for (Export export : exports) export.run();
    }

    private final class Export implements Runnable {
        private Runnable write;
        private Export(Runnable write) { this.write = write; }

        @Override public synchronized void run() {
            if (write == null) return;
            try {
                write.run();
            } finally {
                write = null;
                synchronized (pending) { pending.remove(this); }
            }
        }
    }
}
