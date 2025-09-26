package karel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import kareltherobot.*;

/**
 * Robot autónomo que navega por rutas con control de tráfico:
 * - Sigue ruta principal con posibles alternativas dinámicas
 * - Control de acceso a subrutas restringidas
 * - Sistema de intercambio de beepers entre zonas designadas
 */
public class RacerBot extends Robot implements Runnable, Directions {
    private static final AtomicLong NEXT_ID = new AtomicLong(1);
    private static final CountDownLatch START_GATE = new CountDownLatch(1);

    private final long id;
    private final Random rnd = new Random();

    private int street;
    private int avenue;
    private Directions.Direction dir;

    private final int condition = 0;
    private int startIndex;

    private final List<int[]> routePositions = new ArrayList<>();

    private List<int[]> activeAlternate = null;
    private int alternateIndex = 0;
    private boolean usingAlternate = false;

    private int rejoinStreet = -1;
    private int rejoinAvenue = -1;

    private int cargoCount = 0;
    private BeeperExchangeManager.Zone cargoFrom = null;



    // Constructor: inicializar la ruta por defecto 
    public RacerBot(int street, int avenue, Directions.Direction dir, int beepers, Color color) {
        super(street, avenue, dir, beepers, color);
        this.street = street;
        this.avenue = avenue;
        this.dir = dir;
        this.id = NEXT_ID.getAndIncrement();
        TrafficController.get().occupy(street, avenue, id);
        routePositions.add(new int[]{2,7});
        routePositions.add(new int[]{2,6});
        routePositions.add(new int[]{2,5});
        routePositions.add(new int[]{2,4});
        routePositions.add(new int[]{2,3});
        routePositions.add(new int[]{2,2});
        routePositions.add(new int[]{2,1});

        for (int a = 1; a <= 10; a++) {
            routePositions.add(new int[]{1,a});
        }

        routePositions.add(new int[]{1,11}); // Acá empezaría la ruta alternativa

        for (int a = 12; a <= 30; a++) {
            routePositions.add(new int[]{1,a});
        }

        for (int a = 2; a <= 10; a++) {
            routePositions.add(new int[]{a,30});
        }

        routePositions.add(new int[]{11,30}); // La ruta alternativa termina en 10,30 y sigue acá
        
        for (int a = 12; a <= 16; a++) {
            routePositions.add(new int[]{a,30});
        }

        routePositions.add(new int[]{16,29});

        for (int a = 29; a >= 23; a--) {
            routePositions.add(new int[]{15,a});
        }

        for (int a = 23; a <= 29; a++) {
            routePositions.add(new int[]{14,a});
        }
        routePositions.add(new int[]{13,29});
        routePositions.add(new int[]{12,29});
        routePositions.add(new int[]{12,28});

        for (int a = 28; a >= 23; a--) {
            routePositions.add(new int[]{13,a});
        }

        routePositions.add(new int[]{12,23});
        routePositions.add(new int[]{11,23});

        routePositions.add(new int[]{10,23});

        for (int a = 23; a <= 30; a++) {
            routePositions.add(new int[]{10,a});
        }

        for (int a = 9; a >= 5; a--) {
            routePositions.add(new int[]{a,30});
        }

        for (int a = 5; a >= 1; a--) {
            routePositions.add(new int[]{a,29});
        }

        routePositions.add(new int[]{1,28});
        routePositions.add(new int[]{1,27});
        routePositions.add(new int[]{1,26});

        for (int a = 26; a >= 21; a--) {
            routePositions.add(new int[]{2,a});
        }

        for (int a = 21; a >= 16; a--) {
            routePositions.add(new int[]{1,a});
        }

        for (int a = 16; a >= 8; a--) {
            routePositions.add(new int[]{2,a});
        }

        routePositions.add(new int[]{3,8});
        for (int a = 8; a >= 1; a--) {
            routePositions.add(new int[]{4, a});
        }

        for (int a = 1; a <= 7; a++) {
            routePositions.add(new int[]{3, a});
        }

        startIndex = 0;
        boolean onRoute = false;
        for (int i = 0; i < routePositions.size(); i++) {
            if (routePositions.get(i)[0] == street && routePositions.get(i)[1] == avenue) {
                startIndex = i;
                onRoute = true;
                break;
            }
        }
    }

    // Utilidades de giro y actualización de dirección local
    private void physicalTurnLeft() {
        super.turnLeft();
    }

    private void physicalTurnRight() {
        super.turnLeft();
        super.turnLeft();
        super.turnLeft();
    }

    private void turnRight() {
        physicalTurnRight();
        dir = rotateLeft(rotateLeft(rotateLeft(dir)));
    }

    private void turnLeftAndUpdate() {
        physicalTurnLeft();
        dir = rotateLeft(dir);
    }

    private Directions.Direction rotateLeft(Directions.Direction d) {
        if (d == North) return West;
        if (d == West)  return South;
        if (d == South) return East;
        return North; // East -> North
    }

    private int nextStreet() {
        if (dir == North) return street + 1;
        if (dir == South) return street - 1;
        return street;
    }

    private int nextAvenue() {
        if (dir == East) return avenue + 1;
        if (dir == West) return avenue - 1;
        return avenue;
    }

    // Movimiento seguro: pide el paso y solo si lo obtiene, ejecuta move()
    private void safeMoveForward() {
        int ns = nextStreet();
        int na = nextAvenue();

        TrafficController tc = TrafficController.get();

        // Subruta destino y subruta actual (si estamos dentro de una)
        Subroute entering = tc.findContainingSubroute(ns, na);
        Subroute currentSubroute = tc.findContainingSubroute(street, avenue);

        // Entrando a una subruta nueva => batch tryEnter
        if (entering != null && entering != currentSubroute) {
            try {
                Subroute.FlowDir fd = null;
                if (dir == North) fd = Subroute.FlowDir.NORTH;
                else if (dir == South) fd = Subroute.FlowDir.SOUTH;
                else if (dir == East) fd = Subroute.FlowDir.EAST;
                else if (dir == West) fd = Subroute.FlowDir.WEST;
                boolean got = entering.tryEnterDirectional(fd, 5000);
                if (!got) {
                    try { Thread.sleep(10); } catch (InterruptedException ignored) {}
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        // Intentar hacer el movimiento por celda (token por celda)
        int prevStreet = street;
        int prevAvenue = avenue;
        while (!tc.tryMove(prevStreet, prevAvenue, ns, na, id)) {
            try { Thread.sleep(2); } catch (InterruptedException ignored) {}
        }

        super.move();

        // actualizar coordenadas locales
        street = ns;
        avenue = na;

        // Si salimos de una subruta (estábamos en una y ya no la contiene la nueva celda) => exit()
        if (currentSubroute != null && currentSubroute != entering && !currentSubroute.contains(street, avenue)) {
            currentSubroute.exit();
        }
    }



    // Sondeos de intersección conservando orientación
    private boolean clearLeft() {
        // girar temporalmente a la izquierda, chequear, y volver
        turnLeftAndUpdate();
        boolean ok = frontIsClear();
        // volver a la orientación original (girar derecha)
        turnRight();
        return ok;
    }

    private boolean clearRight() {
        // girar temporalmente a la derecha
        turnRight();
        boolean ok = frontIsClear();
        // volver a la orientación original (girar izquierda)
        turnLeftAndUpdate();
        return ok;
    }

    private void moveTo(int ts, int ta) {
        while (street != ts || avenue != ta) {
            Directions.Direction targetDir = null;
            if (street < ts) {
                targetDir = North;
            } else if (street > ts) {
                targetDir = South;
            } else if (avenue < ta) {
                targetDir = East;
            } else if (avenue > ta) {
                targetDir = West;
            }
            if (targetDir != null && dir != targetDir) {
                turnTo(targetDir);
            }
            if (frontIsClear()) {
                safeMoveForward();
            } else {
                // stuck, break
                break;
            }
        }
    }

    private void turnTo(Directions.Direction targetDir) {
        while (dir != targetDir) {
            turnLeftAndUpdate();
        }
    }

        // buscar índice de (s,a) en una lista de puntos
    private int indexOf(List<int[]> list, int s, int a) {
        if (list == null) return -1;
        for (int i = 0; i < list.size(); i++) {
            int[] p = list.get(i);
            if (p[0] == s && p[1] == a) return i;
        }
        return -1;
    }


    @Override
    public void run() {
        // Esperar hasta que el controlador libere la salida
        try { START_GATE.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        // bucle principal: seguir la ruta de posiciones de manera circular
        int index = startIndex;
        while (true) {
            // Terminar si el intercambio ya completó
            if (BeeperExchangeManager.get().isDone()) {
                try { Thread.sleep(5); } catch (InterruptedException ignored) {}
                turnOff();
                break;
            }
            // elegir target según si estamos en alternativa o en la ruta principal
            int[] target;
            if (usingAlternate && activeAlternate != null && !activeAlternate.isEmpty()) {
                if (alternateIndex >= activeAlternate.size()) alternateIndex = 0;
                target = activeAlternate.get(alternateIndex);
            } else {
                if (index >= routePositions.size()) index = 0;
                target = routePositions.get(index);
            }

            // mover al target (moveTo actualizará street/avenue)
            moveTo(target[0], target[1]);

            // Lógica de intercambio de beepers al estar en una celda
            maybeHandleBeeperExchange();

            // --- importante: ahora estamos en target; evaluar switch global ---
            // solo los robots que estén en la celda decisoria consultarán la spec (checkAndMaybeSwitchRoute hace eso)
            checkAndMaybeSwitchRoute();

            // si estamos en alternativa, comprobar rejoin: si alcanzamos la celda de rejoin del spec,
            // necesitamos volver a la ruta principal (buscar la celda en routePositions y continuar desde ahí).
            if (usingAlternate && activeAlternate != null) {
                // obtener la spec para esta alternativa (si fue activada desde spec, rejoin está en spec)
                // pero la spec está en TrafficController, buscaremos el rejoin comprobando si la celda actual
                // es igual a ANY rejoin de alguna spec. Para simplicidad consultamos todas las specs:
                // (más eficiente sería almacenar rejoin en el robot al activar; aquí vamos a recuperarlo)
                // -> mejor: cuando activamos guardamos rejoin en variables: ve abajo (modifica el activation para guardar)
            }

            // AVANCE de índices
            if (usingAlternate && activeAlternate != null && !activeAlternate.isEmpty()) {
                // reencaje **solo** si alcanzamos la celda explícita de rejoin
                if (rejoinStreet >= 0 && street == rejoinStreet && avenue == rejoinAvenue) {
                    // reencajamos: volver a ruta principal en el índice donde estamos
                    int mainIdx = indexOf(routePositions, street, avenue);
                    System.out.println("Robot " + id + " rejoined main route at " + street + "," + avenue + " (mainIdx=" + mainIdx + ")");
                    usingAlternate = false;
                    activeAlternate = null;
                    alternateIndex = 0;
                    // limpiar rejoin
                    rejoinStreet = -1;
                    rejoinAvenue = -1;
                    // si encontramos el índice en la ruta principal, continuar desde ahí
                    if (mainIdx >= 0) {
                        index = mainIdx;
                    } else {
                        // si por alguna razón la celda de rejoin no está en mainRoute, reiniciamos al siguiente
                        index = (index + 1) % routePositions.size();
                    }
                } else {
                    // seguir avanzando por la alternativa
                    alternateIndex = (alternateIndex + 1) % activeAlternate.size();
                }
            } else {
                // seguir avanzando por la ruta principal
                index = (index + 1) % routePositions.size();
            }


            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }

    }

    // Reglas de intercambio de beepers:
    // - En el origen A(1,9) o B(11,23), si no llevo carga, reservo hasta 4 y pickeo hasta cubrir la reserva o quedarme sin beepers en el piso.
    // - En el destino de esa carga, dejo todos y notifico; si dejé exactamente 4, espero 3s.
    private void maybeHandleBeeperExchange() {
        BeeperExchangeManager mgr = BeeperExchangeManager.get();
        if (mgr.isDone()) return;

        // Si no llevo nada: intentar cargar en origen si estoy parado allí
        if (cargoCount == 0) {
            BeeperExchangeManager.Zone z = mgr.originAt(street, avenue);
            if (z != null) {
                int reserve = mgr.reserve(z, 4);
                if (reserve > 0) {
                    int picked = 0;
                    while (picked < reserve && nextToABeeper()) {
                        pickBeeper();
                        picked++;
                    }
                    if (picked > 0) {
                        cargoFrom = z;
                        cargoCount = picked;
                    }
                    if (picked < reserve) {
                        mgr.refund(z, reserve - picked);
                    }
                }
            }
        }

        // Si llevo algo y estoy en el destino correspondiente: descargar
        if (cargoCount > 0 && cargoFrom != null && mgr.isDropPointFor(cargoFrom, street, avenue)) {
            int delivered = cargoCount;
            for (int i = 0; i < delivered; i++) {
                putBeeper();
            }
            cargoCount = 0;
            mgr.delivered(cargoFrom, delivered);
            cargoFrom = null;
            if (delivered == 4) {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        }
    }

    // Método estático para liberar a todos los robots a la vez
    public static void releaseStartGate() {
        START_GATE.countDown();
    }

        /**
     * Consulta al TrafficController si para la celda actual hay una AlternateRouteSpec.
     * Si existe y se cumple el requisito (requiredOccupied), activa la ruta alternativa.
     * Este método debe llamarse DESPUÉS de moverse a la celda actual (cuando street/avenue están actualizados).
     */
    private void checkAndMaybeSwitchRoute() {
        if (usingAlternate) return; // ya en alterna, nada que hacer

        TrafficController tc = TrafficController.get();
        TrafficController.AlternateRouteSpec spec = tc.getAlternateForCell(street, avenue);
        if (spec == null) return;

        // contar cuántas triggerCells están ocupadas en este instante
        int occupiedCount = 0;
        StringBuilder sb = new StringBuilder();
        sb.append("Robot ").append(id).append(" checking alternate at ").append(street).append(",").append(avenue).append(": ");
        for (int[] cell : spec.triggerCells) {
            boolean occ = tc.isOccupied(cell[0], cell[1]);
            sb.append("[").append(cell[0]).append(",").append(cell[1]).append("=").append(occ).append("] ");
            if (occ) occupiedCount++;
        }
        sb.append(" -> occupiedCount=").append(occupiedCount).append("/").append(spec.requiredOccupied);
        System.out.println(sb.toString()); // logging de diagnóstico

        if (occupiedCount >= spec.requiredOccupied) {
            // activar la alternativa
            this.usingAlternate = true;
            this.activeAlternate = spec.altRoute;
            // posicionar alternateIndex en la posición correspondiente si existe
            int idx = indexOf(activeAlternate, street, avenue);
            this.alternateIndex = (idx >= 0) ? idx : 0;
            // guardar rejoin explícito (solo reencajar cuando se alcance esta celda)
            this.rejoinStreet = spec.rejoinStreet;
            this.rejoinAvenue = spec.rejoinAvenue;
            System.out.println("Robot " + id + " switching to GLOBAL alternate route. rejoin=" 
                               + rejoinStreet + "," + rejoinAvenue);
        }
    }

}
