package com.yukino.counter;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.atomic.DoubleAdder;
import java.util.function.Consumer;

public final class CounterService implements AutoCloseable {
    public record DayKey(LocalDate day, int keyCode) {}
    public enum ImportStatus { FULL, MOUSE_ONLY, DUPLICATE }

    private final CounterRepository repository;
    private final ConcurrentMap<DayKey, LongAdder> pending = new ConcurrentHashMap<>();
    private final ConcurrentMap<LocalDate, DoubleAdder> pendingMouse = new ConcurrentHashMap<>();
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "counter-writer");
        t.setDaemon(true);
        return t;
    });
    private volatile Consumer<Throwable> errorHandler = Throwable::printStackTrace;

    public CounterService(CounterRepository repository) {
        this.repository = repository;
        writer.scheduleWithFixedDelay(this::safeFlush, 3, 3, TimeUnit.SECONDS);
    }

    public void setErrorHandler(Consumer<Throwable> handler) { this.errorHandler = handler; }

    public void record(int keyCode) {
        pending.computeIfAbsent(new DayKey(LocalDate.now(), keyCode), ignored -> new LongAdder()).increment();
    }

    public void recordMouseDistance(double meters) {
        if (meters > 0 && Double.isFinite(meters))
            pendingMouse.computeIfAbsent(LocalDate.now(), ignored -> new DoubleAdder()).add(meters);
    }

    public Map<Integer, Long> snapshot(LocalDate day) throws SQLException {
        Map<Integer, Long> result = repository.counts(day);
        pending.forEach((key, value) -> {
            if (day == null || day.equals(key.day())) result.merge(key.keyCode(), value.sum(), Long::sum);
        });
        return result;
    }

    public Map<LocalDate, Long> days() throws SQLException {
        Map<LocalDate, Long> result = new HashMap<>(repository.days());
        pending.forEach((key, value) -> result.merge(key.day(), value.sum(), Long::sum));
        return result;
    }

    public double mouseDistance(LocalDate day) throws SQLException {
        double result = repository.mouseDistance(day);
        for (var item : pendingMouse.entrySet())
            if (day == null || day.equals(item.getKey())) result += item.getValue().sum();
        return result;
    }

    public synchronized ImportStatus importCounts(String hash, String fileName, Map<DayKey, Long> counts,
                                                  Map<LocalDate, Double> mouseDistances) throws SQLException {
        flush();
        return repository.importBatch(hash, fileName, counts, mouseDistances);
    }

    private void safeFlush() {
        try { flush(); } catch (Throwable e) { errorHandler.accept(e); }
    }

    public synchronized void flush() throws SQLException {
        Map<DayKey, Long> batch = new HashMap<>();
        Map<LocalDate, Double> mouseBatch = new HashMap<>();
        pending.forEach((key, value) -> {
            long count = value.sumThenReset();
            if (count > 0) batch.put(key, count);
        });
        pendingMouse.forEach((day, value) -> {
            double distance = value.sumThenReset();
            if (distance > 0) mouseBatch.put(day, distance);
        });
        try {
            repository.addBatch(batch, mouseBatch);
        } catch (SQLException e) {
            batch.forEach((key, count) -> pending.computeIfAbsent(key, ignored -> new LongAdder()).add(count));
            mouseBatch.forEach((day, distance) -> pendingMouse.computeIfAbsent(day, ignored -> new DoubleAdder()).add(distance));
            throw e;
        }
        pending.entrySet().removeIf(e -> e.getValue().sum() == 0);
        pendingMouse.entrySet().removeIf(e -> e.getValue().sum() == 0);
    }

    @Override public void close() throws Exception {
        writer.shutdown();
        if (!writer.awaitTermination(2, TimeUnit.SECONDS)) writer.shutdownNow();
        flush();
        repository.close();
    }
}
