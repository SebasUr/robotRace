package karel;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Subruta con política "batch":
 *  - Hasta 'threshold' robots simultáneamente (mismo sentido).
 *  - Cuando se llena se cierra hasta que TODOS salgan (count==0).
 *  - Fairness direccional: al vaciar el batch se prioriza la dirección opuesta
 *    a la del último batch si hay robots esperando en ese sentido (evita starvation).
 */
public final class Subroute {
    private final String id;
    private final List<Long> cells = new ArrayList<>();
    private final int threshold;

    private int count = 0;                  // robots dentro del batch actual
    private boolean closedBatch = false;    // true si alcanzó el threshold
    public enum FlowDir { NORTH, SOUTH, EAST, WEST }
    private FlowDir flowDir = null;         // dirección activa del batch en curso (cuando count>0)
    private FlowDir lastBatchDir = null;    // dirección del batch que recién terminó
    private FlowDir preferredDir = null;    // dirección prioritaria para el próximo batch (cuando count==0)

    // Robots esperando por dirección (para fairness)
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

    /** Compatibilidad (sin dirección explícita) */
    public synchronized boolean tryEnter(long timeoutMillis) throws InterruptedException {
        return tryEnterDirectional(null, timeoutMillis);
    }

    /**
     * Intenta entrar con control de dirección + batch + fairness.
     * Reglas:
     *  - Si la subruta está vacía:
     *      * Si existe preferredDir y la dirección solicitada != preferredDir -> esperar.
     *      * Si no hay preferredDir o coincide -> este hilo inicia nuevo batch (flowDir=dir), count=1.
     *  - Si hay robots dentro:
     *      * Sólo entra si !closedBatch y (flowDir == dir o flowDir == null) y la dirección coincide.
     *  - Al llenarse (count == threshold) se cierra hasta vaciar.
     *  - Al vaciar (exit): se calcula preferredDir:
     *        -> opuesta a lastBatchDir si hay espera allí;
     *        -> si no, alguna dirección con espera;
     *        -> si ninguna, null (primero que llegue define).
     */
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
                    flowDir = dir;              // fija dirección (puede ser null si no se pasó)
                    lastBatchDir = dir;         // recordar para la próxima rotación
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

            // Registrar espera (una sola vez)
            if (!registeredWaiting && dir != null) {
                waiting.put(dir, waiting.get(dir) + 1);
                registeredWaiting = true;
            }

            long remaining = deadline - System.currentTimeMillis();
            if (remaining <= 0) {
                if (registeredWaiting && dir != null) {
                    waiting.put(dir, waiting.get(dir) - 1);
                }
                return false; // timeout
            }
            wait(remaining);
        }
    }

    /** Llamar cuando el robot abandona completamente la subruta. */
    public synchronized void exit() {
        if (count > 0) {
            count--;
            if (count == 0) {
                // Batch finalizado: preparar fairness para el siguiente
                closedBatch = false;
                // Determinar preferredDir: priorizar opuesta a la última si hay espera
                FlowDir opp = opposite(lastBatchDir);
                if (opp != null && waiting.get(opp) > 0) {
                    preferredDir = opp;
                } else {
                    // Buscar cualquiera con espera distinta (o nulificar si no hay nadie)
                    preferredDir = pickAnyWaiting();
                }
                flowDir = null; // liberada; la fijará el primer hilo aceptado
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
        return null; // nadie esperando
    }

    // Getters de diagnóstico
    public synchronized int getCount() { return count; }
    public synchronized boolean isClosed() { return closedBatch; }
    public int getThreshold() { return threshold; }
    public synchronized FlowDir getFlowDir() { return flowDir; }
    public synchronized FlowDir getPreferredDir() { return preferredDir; }

    private static long key(int street, int avenue) {
        return (((long) street) << 32) | (avenue & 0xffffffffL);
    }
}