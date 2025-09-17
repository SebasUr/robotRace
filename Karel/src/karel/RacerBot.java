package karel;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;

import kareltherobot.*;

/**
 * Robot de carrera con control de concurrencia por celda.
 * - Cada instancia corre en su propio hilo (Runnable)
 * - Antes de moverse, pide permiso al TrafficController para entrar a la celda destino
 * - Si varias rutas son posibles, elige aleatoriamente (preferencia: seguir recto)
 */
public class RacerBot extends Robot implements Runnable, Directions {
    private static final AtomicLong NEXT_ID = new AtomicLong(1);
    // Latch para sincronizar el arranque de todos los hilos
    private static final CountDownLatch START_GATE = new CountDownLatch(1);

    private final long id;
    private final Random rnd = new Random();

    // Estado local del robot (lo mantenemos para calcular la celda siguiente)
    private int street;
    private int avenue;
    private Directions.Direction dir;

    // Condición hardcodeada para elegir alternativas en rutas
    private final int condition = 0; // Cambiar a 1 para alternativa

    private int startIndex;

    // Debe ser circular y terminar en una celda 
    private final List<int[]> routePositions = new ArrayList<>();

    // Constructor: inicializar la ruta por defecto 
    public RacerBot(int street, int avenue, Directions.Direction dir, int beepers, Color color) {
        super(street, avenue, dir, beepers, color);
        this.street = street;
        this.avenue = avenue;
        this.dir = dir;
        this.id = NEXT_ID.getAndIncrement();
        // Registrar ocupación inicial (asumimos posiciones de arranque únicas)
        TrafficController.get().occupy(street, avenue, id);

        // Inicializar ruta por defecto (placeholder: usuario agrega movimientos)
        if (condition == 0) {
            // Ruta principal: sentido horario
            routePositions.add(new int[]{2,7});
            routePositions.add(new int[]{2,6});
            routePositions.add(new int[]{2,5});
            routePositions.add(new int[]{2,4});
            routePositions.add(new int[]{2,3});
            routePositions.add(new int[]{2,2});
            routePositions.add(new int[]{2,1});

            for (int a = 1; a <= 30; a++) {
                routePositions.add(new int[]{1,a});
            }

            for (int a = 1; a <= 16; a++) {
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

        } else {
            // Ruta alternativa: sentido antihorario
            routePositions.add(new int[]{1,1});
            routePositions.add(new int[]{2,1});
            routePositions.add(new int[]{3,1});
            routePositions.add(new int[]{4,1});
            routePositions.add(new int[]{4,2});
            routePositions.add(new int[]{4,3});
            routePositions.add(new int[]{4,4});
            routePositions.add(new int[]{4,5});
            routePositions.add(new int[]{4,6});
            routePositions.add(new int[]{4,7});
            routePositions.add(new int[]{3,7});
            routePositions.add(new int[]{2,7});
            routePositions.add(new int[]{1,7});
            routePositions.add(new int[]{1,6});
            routePositions.add(new int[]{1,5});
            routePositions.add(new int[]{1,4});
            routePositions.add(new int[]{1,3});
            routePositions.add(new int[]{1,2});
        }
        // Encontrar el índice de inicio basado en la posición inicial
        startIndex = 0;
        boolean onRoute = false;
        for (int i = 0; i < routePositions.size(); i++) {
            if (routePositions.get(i)[0] == street && routePositions.get(i)[1] == avenue) {
                startIndex = i;
                onRoute = true;
                break;
            }
        }
        // Si no está en la ruta, startIndex = 0, y en run se moverá a la primera
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
        while (!TrafficController.get().tryMove(street, avenue, ns, na, id)) {
            // Espera corta para no ocupar CPU
            try { Thread.sleep(2); } catch (InterruptedException ignored) {}
        }
        super.move();
        street = ns;
        avenue = na;
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
            // turn towards target
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

    @Override
    public void run() {
        // Esperar hasta que el controlador libere la salida
        try { START_GATE.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
        // bucle principal: seguir la ruta de posiciones de manera circular
        int index = startIndex;
        while (true) {
            int[] target = routePositions.get(index);
            moveTo(target[0], target[1]);
            index = (index + 1) % routePositions.size();
            // Pequeña pausa para animación y permitir intercalado de hilos
            try { Thread.sleep(10); } catch (InterruptedException ignored) {}
        }
    }

    // Método estático para liberar a todos los robots a la vez
    public static void releaseStartGate() {
        START_GATE.countDown();
    }
}
