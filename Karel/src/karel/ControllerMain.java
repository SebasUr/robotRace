package karel;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import kareltherobot.*;

/**
 * Clase principal para iniciar la simulación de robots de carrera.
 * - Carga el mundo (pista de carreras) por defecto si no se especifica otro
 * - Crea múltiples instancias de RacerBot en una grilla (7x4) con colores únicos
 * - Cada robot corre en su propio hilo y sigue una ruta predefinida
 */
public class ControllerMain implements Directions
{
    public static void main(String[] args)
    {
        UrRobot karel = null;
        int street = 1;
        int avenue = 1;
        Direction direction = North;
        int beepers = 0;
        String world = "";
        int red = 0;
        int green = 0;
        int blue = 0;
        Color color = null;
        try
        {
            street = Integer.parseInt(args[0]);
            avenue = Integer.parseInt(args[1]);
            String which = args[2];
            if(which.equalsIgnoreCase("South")) direction = South;
            else if(which.equalsIgnoreCase("East")) direction = East;
            else if(which.equalsIgnoreCase("West")) direction = West;
            if(args[3].equalsIgnoreCase("infinity"))beepers = infinity;
            else beepers = Integer.parseInt(args[3]);
            world = args[4];        
            if(world != null && world != "")kareltherobot.World.readWorld(world);
            red = Integer.parseInt(args[5]);
            green = Integer.parseInt(args[6]);
            blue = Integer.parseInt(args[7]);
            color = new Color(red, green, blue);
        }
        catch (Throwable e)
        {
            System.out.println("Using some default arguments");
        }
        kareltherobot.World.asObject().setDelay(0);
        kareltherobot.World.asObject().setVisible(true);

        // Cargar por defecto la pista de carreras y crear varios robots
        if (world == null || world.equals("")) {
            kareltherobot.World.readWorld("race.kwld");
        }
        kareltherobot.World.asObject().setDelay(5);

        // Bloqueo de la avenida 30
        Subroute s1 = new Subroute("S1", 4);
        for (int a = 10; a >= 5; a--) {
            s1.addCell(a, 30); 
        }
        TrafficController.get().registerSubroute(s1);
        
        //Bloqueo de la calle 1, 1 (derecha a izquierda)
        Subroute s2 = new Subroute("S2", 4);
        for (int a = 26; a <= 29; a++) {
            s2.addCell(1, a);
        }
        for (int a = 16; a <= 21; a++) {
            s2.addCell(1, a);
        }
        TrafficController.get().registerSubroute(s2);

        // Bloqueo de la calle 1, 2 (derecha a izquierda)
        // Subroute s3 = new Subroute("S3", 4);
        // for (int a = 16; a <= 21; a++) {
        //     s3.addCell(1, a);
        // }
        // TrafficController.get().registerSubroute(s3);

        // Subroute s4 = new Subroute("S4", 4);
        // for (int a = 5; a >= 2; a--) {
        //     s4.addCell(a, 29);
        // }
        // TrafficController.get().registerSubroute(s4);

        // RUTA ALTERNA AZUL ----------------------------------------------------------
        //  al llegar a 1,11, evaluar 1,12..1,15
        List<int[]> alt = new ArrayList<>();
        // ruta alternativa (usa la tuya)
        for (int a = 2; a <= 11; a++) {
            alt.add(new int[]{a,11});
        }

        alt.add(new int[]{11,10});
        alt.add(new int[]{11,9});

        for (int a =11; a <= 13 ;a++){
            alt.add(new int[]{a,8});
        }

        for (int a = 8; a <= 16; a++) {
            alt.add(new int[]{14,a});
        }

        alt.add(new int[]{13,16});
        alt.add(new int[]{12,16});
        alt.add(new int[]{11,16});
        alt.add(new int[]{10,16});
        alt.add(new int[]{10,15});
        alt.add(new int[]{10,14});

        for (int a = 10; a >= 5; a--) {
            alt.add(new int[]{a,13});
        }

        for (int a = 14; a <= 20; a++) {
            alt.add(new int[]{5,a});
        }
        
        for (int a = 6; a <= 10; a++) {
            alt.add(new int[]{a,20});
        }
            
        for (int a = 21; a <= 30; a++) {
            alt.add(new int[]{10,a});
        }
        alt.add(new int[]{10,30});
        alt.add(new int[]{11,30});
        alt.add(new int[]{12,30});
        alt.add(new int[]{13,30});
        alt.add(new int[]{14,30});
        alt.add(new int[]{15,30});


        // triggers: las cuatro celdas a comprobar (1,12..1,15)
        List<int[]> triggers = new ArrayList<>();
        triggers.add(new int[]{1,12});
        triggers.add(new int[]{1,13});
        triggers.add(new int[]{1,14});
        triggers.add(new int[]{1,15});

        // requiredOccupied = 4 -> sólo cambiar si las 4 están ocupadas en el instante
        TrafficController.AlternateRouteSpec spec = new TrafficController.AlternateRouteSpec(alt, 15, 30, triggers, 4);
        TrafficController.get().registerAlternateRoute(1, 11, spec);
        // FIN RUTA ALTERNATIVA VERDE. ----------------------------------------------------------------------------

        // ----------------------------------------------------------------------------------------------------------------------------------|
        // # RUTA ALTERNATIVA MORADA. ------------------------------------------------------------------------------|
        List<int[]> alt2 = new ArrayList<>();
        alt2.add(new int[]{11,22});
        alt2.add(new int[]{11,21});
        
        for (int a=11;a<=19; a++){
            alt2.add(new int[]{a,20});
        }

        alt2.add(new int[]{19,19});

        for (int a=19;a>=15; a--){
            alt2.add(new int[]{a,18});
        }

        for (int a=17;a>=1; a--){
            alt2.add(new int[]{15,a});
        }

        for (int a=14;a<=10; a--){
            alt2.add(new int[]{a,1});
        }

        for (int a=2;a<=10; a++){
            alt2.add(new int[]{10,a});
        }

        for (int a=9;a>=2; a--){
            alt2.add(new int[]{a,10});
        }

        List<int[]> triggers2 = new ArrayList<>();
        triggers2.add(new int[]{10,24});
        triggers2.add(new int[]{10,25});
        triggers2.add(new int[]{10,26});
        triggers2.add(new int[]{10,27});
        triggers2.add(new int[]{10,28});
        triggers2.add(new int[]{10,29});


        // requiredOccupied = 4 -> sólo cambiar si las 4 están ocupadas en el instante
        TrafficController.AlternateRouteSpec spec2 = new TrafficController.AlternateRouteSpec(alt2,2, 10, triggers2, 6);
        TrafficController.get().registerAlternateRoute(11, 23, spec2);
    // ----------------------------------------------------------------------------------------------------------------------
    
        int totalAvenues = 7; // columnas
        int totalStreets = 4; // filas
        int count = totalAvenues * totalStreets; // 28 robots

        int robotNumber = 1;
        for (int avenuex = 1; avenuex <= totalAvenues; avenuex++) {
            for (int streetx = 1; streetx <= totalStreets; streetx++) {
                // Color único para cada robot
                Color c = new Color((50 * robotNumber) % 256, (80 * robotNumber) % 256, (120 * robotNumber) % 256);

                // Crear robot en (street, avenue)
                RacerBot bot = new RacerBot(streetx, avenuex, Directions.East, Directions.infinity, c);

                // Crear y lanzar el hilo
                Thread t = new Thread(bot, "Racer-" + robotNumber);
                t.start();

                robotNumber++;
            }
        }

        int startAvenue = 23; 
        int endAvenue   = 30; 
        int[] streetsLineaRoja = {13, 14, 15}; // filas (streets) de la línea roja

        for (int streetx : streetsLineaRoja) {
            for (int avenuex = startAvenue; avenuex <= endAvenue; avenuex++) {
                // Color distinto para estos robots también
                Color c = new Color((50 * robotNumber) % 256,
                                    (80 * robotNumber) % 256,
                                    (120 * robotNumber) % 256);

                RacerBot bot = new RacerBot(streetx, avenuex, Directions.East,
                                            Directions.infinity, c);

                Thread t = new Thread(bot, "Racer-" + robotNumber);
                t.start();

                robotNumber++;
            }
        }

    // Pausa  para visualizar posiciones iniciales antes de arrancar
    try { Thread.sleep(1000); } catch (InterruptedException ignored) {}
    // Liberar la salida: todos los hilos comienzan a seguir su ruta
    RacerBot.releaseStartGate();

    //testing
        // count = 2;
        // for (int i = 0; i < count; i++) {
        //     Color c = new Color((50 * i) % 256, (80 * i) % 256, (120 * i) % 256);

        //     RacerBot bot = new RacerBot(2, i + 1, Directions.East, Directions.infinity, c);

        //     Thread t = new Thread(bot, "Racer-" + i);
        //     t.start();
        // }

    }

}
