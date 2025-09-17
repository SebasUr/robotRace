package karel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simple serialized traffic controller to prevent two robots from occupying
 * the same cell at the same time. All occupancy changes are done inside
 * synchronized methods to keep the logic simple for the first step.
 */
public final class TrafficController {
    private static final TrafficController INSTANCE = new TrafficController();

    // key: (street,avenue) encoded in a long. value: robot id (for debugging)
    private final Map<Long, Long> occupied = new HashMap<>();

    // subroutes
    private final Map<String, Subroute> subroutes = new HashMap<>();
    private final List<Subroute> subrouteList = new ArrayList<>();

    // map para rutas alternativas asociadas a una celda decisionn
    private final Map<Long, AlternateRouteSpec> alternateRoutes = new HashMap<>();

    // Clase que describe la ruta alternativa + triggers + requisito de ocupación
    public static final class AlternateRouteSpec {
        public final List<int[]> altRoute;       // lista de {street,avenue}
        public final int rejoinStreet;
        public final int rejoinAvenue;
        public final List<int[]> triggerCells;   // celdas a comprobar (por ejemplo 1,12 .. 1,15)
        public final int requiredOccupied;       // número mínimo de celdas ocupadas para disparar (4)

        public AlternateRouteSpec(List<int[]> altRoute, int rejoinStreet, int rejoinAvenue,
                                  List<int[]> triggerCells, int requiredOccupied) {
            this.altRoute = altRoute;
            this.rejoinStreet = rejoinStreet;
            this.rejoinAvenue = rejoinAvenue;
            this.triggerCells = triggerCells == null ? new ArrayList<>() : new ArrayList<>(triggerCells);
            this.requiredOccupied = requiredOccupied;
        }
    }


    private TrafficController() {}

    public static TrafficController get() { return INSTANCE; }

    private static long key(int street, int avenue) {
        return (((long) street) << 32) | (avenue & 0xffffffffL);
    }

    /**
     * Try to mark a cell as occupied by a robot. Returns true if successful.
     */
    public synchronized boolean occupy(int street, int avenue, long robotId) {
        long k = key(street, avenue);
        if (occupied.containsKey(k)) return false;
        occupied.put(k, robotId);
        return true;
    }

    /**
     * Atomically move a robot token from (fs,fa) to (ts,ta) if target is free.
     * Returns true when the move is granted, false otherwise.
     */
    public synchronized boolean tryMove(int fs, int fa, int ts, int ta, long robotId) {
        long fk = key(fs, fa);
        long tk = key(ts, ta);
        // sanity: ensure caller owns from-cell (helpful while developing)
        Long owner = occupied.get(fk);
        if (owner == null || owner != robotId) return false;
        if (occupied.containsKey(tk)) return false;
        // perform the move
        occupied.remove(fk);
        occupied.put(tk, robotId);
        return true;
    }

    /**
     * Free a cell explicitly (e.g., when a robot turns off).
     */
    public synchronized void release(int street, int avenue, long robotId) {
        long k = key(street, avenue);
        Long owner = occupied.get(k);
        if (owner != null && owner == robotId) {
            occupied.remove(k);
        }
    }
 

    // ===== Subroute management =====
    public synchronized void registerSubroute(Subroute sr) {
        subroutes.put(sr.getId(), sr);
        subrouteList.add(sr);
    }

    // Busca subruta que tenga la celda (o null)
    public synchronized Subroute findContainingSubroute(int street, int avenue) {
        for (Subroute sr : subrouteList) {
            if (sr.contains(street, avenue)) return sr;
        }
        return null;
    }
    // Registrar una ruta alternativa para una celda decisoria (por ejemplo 1,11)
    public synchronized void registerAlternateRoute(int decisionStreet, int decisionAvenue, AlternateRouteSpec spec) {
        alternateRoutes.put(key(decisionStreet, decisionAvenue), spec);
    }

    // Obtener la spec (o null) para la celda dada
    public synchronized AlternateRouteSpec getAlternateForCell(int street, int avenue) {
        return alternateRoutes.get(key(street, avenue));
    }

    // Método auxiliar: consulta ocupación (lectura segura)
    public synchronized boolean isOccupied(int street, int avenue) {
        return occupied.containsKey(key(street, avenue));
    }

}
