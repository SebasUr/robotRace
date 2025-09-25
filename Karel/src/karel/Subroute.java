package karel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Controla el acceso de robots a segmentos de ruta restringidos:
 * - Permite hasta 'threshold' robots simultáneos en la misma dirección
 * - Implementa fairness direccional para evitar starvation
 * - Sistema de batches: se cierra al llenarse hasta que todos salgan
 */
public final class Subroute {
    private final String id;
    private final List<Long> cells = new ArrayList<>();
    private final int threshold;

    private int count = 0;
    private boolean closedBatch = false;
    public enum FlowDir { NORTH, SOUTH, EAST, WEST }
    private FlowDir flowDir = null;
    private FlowDir lastBatchDir = null;
    private FlowDir preferredDir = null;

    private final Map<FlowDir,Integer> waiting = new EnumMap<>(FlowDir.class);

    public Subroute(String id, int threshold) {
        this.id = id;
        this.threshold = threshold;
        for (FlowDir d : FlowDir.values()) waiting.put(d, 0);
    }

    public String getId() { return id; }

    public void addCell(int street, int avenue) { cells.add(key(street, avenue)); }

    public boolean contains(int street, int avenue) { return cells.contains(key(street, avenue)); }

    public boolean isEntryCell(int street, int avenue) { return !cells.isEmpty() && cells.get(0) == key(street, avenue); }
    public boolean isExitCell (int street, int avenue) { return !cells.isEmpty() && cells.get(cells.size()-1) == key(street, avenue); }

    public synchronized boolean tryEnter(long timeoutMillis) throws InterruptedException {
        return tryEnterDirectional(null, timeoutMillis);
    }
    public synchronized boolean tryEnterDirectional(FlowDir dir, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        boolean registeredWaiting = false;

        while (true) {
            if (count == 0) {
                // Subruta vacía: decidir si este hilo puede iniciar batch
                if (preferredDir != null && dir != null && dir != preferredDir) {
                    // Debe esperar su turno (fairness)
                } else {
                    // Inicia nuevo batch
                    flowDir = dir; // fija dirección (puede ser null si no se pasó)
                    lastBatchDir = dir; // recordar para la próxima rotación
                    closedBatch = false;
                    count = 1;
                    if (registeredWaiting && dir != null) {
                        waiting.put(dir, waiting.get(dir) - 1);
                        registeredWaiting = false;
                    }
                    if (count >= threshold) closedBatch = true;
                    return true;
                }
            } else {
                // Hay batch activo
                if (!closedBatch && (flowDir == null || flowDir == dir)) {
                    // Mismo sentido y hay cupo
                    count++;
                    if (registeredWaiting && dir != null) {
                        waiting.put(dir, waiting.get(dir) - 1);
                        registeredWaiting = false;
                    }
                    if (count >= threshold) closedBatch = true;
                    return true;
                }
                // Caso contrario: batch lleno, cerrado o dirección distinta -> esperar
            }

            if (!registeredWaiting && dir != null) {
                waiting.put(dir, waiting.get(dir) + 1);
                registeredWaiting = true;
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                if (registeredWaiting && dir != null) {
                    waiting.put(dir, waiting.get(dir) - 1);
                }
                return false;
            }
            wait(remaining);
        }
    }

    public synchronized void exit() {
        if (count > 0) {
            count--;
            if (count == 0) {
                closedBatch = false;
                FlowDir opp = opposite(lastBatchDir);
                if (opp != null && waiting.get(opp) > 0) {
                    preferredDir = opp;
                } else {
                    preferredDir = pickAnyWaiting();
                }
                flowDir = null;
                notifyAll();
            }
        }
    }

    private FlowDir opposite(FlowDir d) {
        if (d == null) return null;
        switch (d) {
            case NORTH: return FlowDir.SOUTH;
            case SOUTH: return FlowDir.NORTH;
            case EAST:  return FlowDir.WEST;
            case WEST:  return FlowDir.EAST;
        }
        return null;
    }

    private FlowDir pickAnyWaiting() {
        for (FlowDir d : FlowDir.values()) {
            if (waiting.get(d) > 0) return d;
        }
        return null;
    }

    public synchronized int getCount() { return count; }
    public synchronized boolean isClosed() { return closedBatch; }
    public int getThreshold() { return threshold; }
    public synchronized FlowDir getFlowDir() { return flowDir; }
    public synchronized FlowDir getPreferredDir() { return preferredDir; }

    private static long key(int street, int avenue) {
        return (((long) street) << 32) | (avenue & 0xffffffffL);
    }
}