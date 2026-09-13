package com.nisovin.magicspells.util.performance;

/**
 * Execution ownership exists even between captures. Only immutable spell names travel
 * with scheduled work; recording sessions and elapsed waiting time never do.
 */
public final class PerformanceExecution {
    public static final String UNATTRIBUTED = "[unattributed]";

    public enum Operation {
        INITIALIZE("initialize"), SPELL("spell"), SUBSPELL("subspell"), PASSIVE("passive"), EVENT("event"),
        TASK("task"), REAGENTS("reagents"), INVENTORY_SCAN("inventory_scan");

        private final String label;
        Operation(String label) { this.label = label; }
    }

    private final PerformanceRecorder recorder;
    private final ThreadLocal<State> context = ThreadLocal.withInitial(State::new);

    public PerformanceExecution(PerformanceRecorder recorder) {
        this.recorder = recorder;
    }

    public String owner() {
        Scope top = context.get().top;
        return top == null ? UNATTRIBUTED : top.owner;
    }

    /** Lexical, thread-confined scope. Do not retain it or close it out of order. */
    public Scope enter(Operation operation, String owner, String detail) {
        State state = context.get();
        Scope parent = state.top;
        Scope scope;
        if (parent == null) {
            if (state.root == null) state.root = new Scope(state, null);
            scope = state.root;
        } else {
            if (parent.child == null) parent.child = new Scope(state, parent);
            scope = parent.child;
        }
        scope.owner = owner != null ? owner : owner();
        scope.measurement = recorder.enter(operation.label, scope.owner, detail);
        scope.closed = false;
        state.top = scope;
        return scope;
    }

    /** Wrap once at registration, not once per tick. Null means capture current ownership. */
    public Runnable task(String owner, String detail, Runnable task) {
        String capturedOwner = owner != null ? owner : owner();
        String taskClass = task.getClass().getName();
        int hiddenSuffix = taskClass.indexOf('/');
        if (hiddenSuffix >= 0) taskClass = taskClass.substring(0, hiddenSuffix);
        String taskDetail = taskClass + ":" + detail;
        return () -> {
            try (var ignored = enter(Operation.TASK, capturedOwner, taskDetail)) {
                task.run();
            }
        };
    }

    public void matchCall() { recorder.matchCall(); }

    private static final class State {
        Scope root;
        Scope top;
    }

    /** Frames are reused by nesting depth, avoiding per-call allocation when idle. */
    public static final class Scope implements AutoCloseable {
        private final State state;
        private final Scope parent;
        private Scope child;
        private String owner;
        private PerformanceRecorder.Scope measurement;
        private boolean closed;

        private Scope(State state, Scope parent) {
            this.state = state;
            this.parent = parent;
        }

        public void add(PerformanceRecorder.Counter counter, long amount) {
            if (!closed) measurement.add(counter, amount);
        }

        @Override public void close() {
            if (closed) return;
            try {
                measurement.close();
            } finally {
                state.top = parent;
                measurement = null;
                owner = null;
                closed = true;
            }
        }
    }
}
