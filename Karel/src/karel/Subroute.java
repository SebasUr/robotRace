package karel;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public final class Subroute {
    private final String id;
    private final List<Long> cells = new ArrayList<>(); // encoded keys (street<<32|avenue)
    private final ReentrantLock lock;

    public Subroute(String id, boolean fair) {
        this.id = id;
        this.lock = new ReentrantLock(fair);
    }

    public String getId() { return id; }

    public void addCell(int street, int avenue) {
        cells.add(key(street, avenue));
    }

    public boolean contains(int street, int avenue) {
        return cells.contains(key(street, avenue));
    }

    public boolean isEntryCell(int street, int avenue) {
        if (cells.isEmpty()) return false;
        return cells.get(0) == key(street, avenue);
    }

    public boolean isExitCell(int street, int avenue) {
        if (cells.isEmpty()) return false;
        return cells.get(cells.size()-1) == key(street, avenue);
    }

    public void lock() { lock.lock(); }

    public boolean tryLock(long timeoutMillis) throws InterruptedException {
        return lock.tryLock(timeoutMillis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public void unlock() { if (lock.isHeldByCurrentThread()) lock.unlock(); }

    private static long key(int street, int avenue) {
        return (((long) street) << 32) | (avenue & 0xffffffffL);
    }

    // en Subroute.java
    public boolean isHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

}
