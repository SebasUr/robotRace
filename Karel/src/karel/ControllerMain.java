package karel;
import java.awt.Color;
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
