package com.runelegend.token;

import java.util.Comparator;
import java.util.PriorityQueue;
import java.util.UUID;

final class TokenScheduler {
    private record Task(long tick, long round, long order, OwnedEffects.Key key, Runnable action) {}
    private final PriorityQueue<Task> tasks = new PriorityQueue<>(Comparator.comparingLong(Task::tick).thenComparingLong(Task::order));
    private long order;

    void schedule(long tick, long round, OwnedEffects.Key key, Runnable action) {
        tasks.add(new Task(tick, round, order++, key, action));
    }

    void tick(long now, long round) {
        while (!tasks.isEmpty() && tasks.peek().tick() <= now) {
            var task = tasks.remove();
            if (task.round() == round) task.action().run();
        }
    }

    void cancel(OwnedEffects.Key key) { tasks.removeIf(task -> task.key().equals(key)); }
    void cancelPlayer(UUID player) { tasks.removeIf(task -> task.key().player().equals(player)); }
    void clear() { tasks.clear(); }
}
