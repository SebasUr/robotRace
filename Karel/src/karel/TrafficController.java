package karel;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controlador de tráfico centralizado:
 * - Previene ocupación simultánea de celdas por múltiples robots
 * - Gestiona subrutas restringidas y rutas alternativas dinámicas
 * - Coordina movimientos atómicos entre celdas
 */
public final class TrafficController {
    private static final TrafficController INSTANCE = new TrafficController();

    private final Map<Long, Long> occupied = new HashMap<>();

    private final Map<String, Subroute> subroutes = new HashMap<>();
    private final List<Subroute> subrouteList = new ArrayList<>();

    private final Map<Long, AlternateRouteSpec> alternateRoutes = new HashMap<>();
    public static final class AlternateRouteSpec {
        public final List<int[]> altRoute;       // lista de {street,avenue}
        public final int rejoinStreet;
        public final int rejoinAvenue;
        public final List<int[]> triggerCells;   // celdas a comprobar (por ejemplo 1,12 .. 1,15)
        public final int requiredOccupied;       // número mínimo de celdas ocupadas (4)

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

    public synchronized boolean occupy(int street, int avenue, long robotId) {
        long k = key(street, avenue);
        if (occupied.containsKey(k)) return false;
        occupied.put(k, robotId);
        return true;
    }

    public synchronized boolean tryMove(int fs, int fa, int ts, int ta, long robotId) {
        long fk = key(fs, fa);
        long tk = key(ts, ta);
        Long owner = occupied.get(fk);
        if (owner == null || owner != robotId) return false;
        if (occupied.containsKey(tk)) return false;
        occupied.remove(fk);
        occupied.put(tk, robotId);
        return true;
    }

    public synchronized void release(int street, int avenue, long robotId) {
        long k = key(street, avenue);
        Long owner = occupied.get(k);
        if (owner != null && owner == robotId) {
            occupied.remove(k);
        }
    }
    public synchronized void registerSubroute(Subroute sr) {
        subroutes.put(sr.getId(), sr);
        subrouteList.add(sr);
    }

    public synchronized Subroute findContainingSubroute(int street, int avenue) {
        for (Subroute sr : subrouteList) {
            if (sr.contains(street, avenue)) return sr;
        }
        return null;
    }

    public synchronized void registerAlternateRoute(int decisionStreet, int decisionAvenue, AlternateRouteSpec spec) {
        alternateRoutes.put(key(decisionStreet, decisionAvenue), spec);
    }

    public synchronized AlternateRouteSpec getAlternateForCell(int street, int avenue) {
        return alternateRoutes.get(key(street, avenue));
    }

    public synchronized boolean isOccupied(int street, int avenue) {
        return occupied.containsKey(key(street, avenue));
    }

}
