package karel;

/**
 * Gestiona el intercambio de beepers entre dos zonas:
 * - Zona A: origen (1,9) -> destino (15,30)
 * - Zona B: origen (11,23) -> destino (2,9)
 * - Controla stock disponible y beepers en tránsito
 * - Detecta finalización cuando se transfieren todos los beepers
 */
public final class BeeperExchangeManager {
    public enum Zone { A, B }

    private static final BeeperExchangeManager INSTANCE = new BeeperExchangeManager();

    private int aStreet, aAvenue;
    private int bStreet, bAvenue;
    private int aDestStreet, aDestAvenue;
    private int bDestStreet, bDestAvenue;

    private int remainingA;
    private int remainingB;
    private int carryingTotal;

    private boolean initialized = false;
    private boolean done = false;

    private BeeperExchangeManager() {}

    public static BeeperExchangeManager get() { return INSTANCE; }

    public synchronized void init(int aStreet, int aAvenue, int initA,
                                    int bStreet, int bAvenue, int initB,
                                    int aDestStreet, int aDestAvenue,
                                    int bDestStreet, int bDestAvenue) {
        this.aStreet = aStreet; this.aAvenue = aAvenue; this.remainingA = initA;
        this.bStreet = bStreet; this.bAvenue = bAvenue; this.remainingB = initB;
        this.aDestStreet = aDestStreet; this.aDestAvenue = aDestAvenue;
        this.bDestStreet = bDestStreet; this.bDestAvenue = bDestAvenue;
        this.carryingTotal = 0;
        this.done = (remainingA == 0 && remainingB == 0);
        this.initialized = true;
    }

    public synchronized Zone originAt(int street, int avenue) {
        if (street == aStreet && avenue == aAvenue) return Zone.A;
        if (street == bStreet && avenue == bAvenue) return Zone.B;
        return null;
    }

    public synchronized boolean isDropPointFor(Zone from, int street, int avenue) {
        if (from == Zone.A) return street == aDestStreet && avenue == aDestAvenue;
        if (from == Zone.B) return street == bDestStreet && avenue == bDestAvenue;
        return false;
    }

    public synchronized int reserve(Zone zone, int max) {
        if (!initialized || done || max <= 0) return 0;
        int can = Math.min(max, zone == Zone.A ? remainingA : remainingB);
        if (can <= 0) return 0;
        if (zone == Zone.A) remainingA -= can; else remainingB -= can;
        carryingTotal += can;
        return can;
    }

    public synchronized void refund(Zone zone, int count) {
        if (count <= 0) return;
        if (zone == Zone.A) remainingA += count; else remainingB += count;
        carryingTotal -= count;
        if (carryingTotal < 0) carryingTotal = 0;
        checkDone();
    }

    public synchronized void delivered(Zone from, int count) {
        if (count <= 0) return;
        carryingTotal -= count;
        if (carryingTotal < 0) carryingTotal = 0;
        checkDone();
    }

    private void checkDone() {
        if (remainingA == 0 && remainingB == 0 && carryingTotal == 0) {
            done = true;
        }
    }

    public synchronized boolean isDone() { return done; }
}
