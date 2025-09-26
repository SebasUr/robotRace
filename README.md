# Robot Race (KarelJRobot)

Proyecto de simulación con múltiples robots(hilos) Karel moviéndose en una grilla con control de tráfico, subrutas con capacidad y un sistema de intercambio de beepers entre dos zonas simulando el transporte de personas en una carrera con pare y siga.

## Cómo correr

Requisitos: Java (JDK 8+)

- Linux, MacOS utilizandoscript.
  1. cd Karel
  2. chmod +x run.sh
  3. ./run.sh

El mundo por defecto es `race.kwld`. El programa compila las clases en `src/` y ejecuta `karel.ControllerMain` con la librería `KarelJRobot.jar` incluida.

## Qué hace
- Lanza varios robots que siguen una ruta principal.
- Aplica rutas alternativas cuando hay congestión en ciertas celdas, utilizando unas celdas de decisión.
- Restringe la entrada a subrutas con capacidad limitada y equidad direccional, permitiendo que varios pasen en un recurso compartido en el que puede haber deadlocks.
- Intercambia pasajeros(beepers) entre dos zonas (A y B) hasta completar el transporte de los pasajeros.

## Concurrencia

La simulación usa monitores (objetos con métodos `synchronized`) para que los robots no choquen ni se bloqueen mal.

- Celdas (ocupación por casilla)
  - Clase: `TrafficController`.
  - Idea: hay un mapa `occupied` que dice qué robot tiene cada celda. Los métodos `occupy`, `tryMove(from→to)` y `release` son `synchronized`, así que sólo un hilo entra a la vez.
  - `tryMove` es atómico: verifica que el robot posee la celda origen y que la destino está libre; si sí, mueve el “token” de ocupación de origen a destino en una sola sección crítica.
  - En el robot (`RacerBot.safeMoveForward()`), se llama repetidamente a `tryMove` hasta que ceda el paso y recién entonces se hace el `move()` gráfico. Esto evita colisiones visuales.

- Subrutas (segmentos con capacidad)
  - Clase: `Subroute`.
  - Es un monitor con `tryEnterDirectional(dir, timeout)` y `exit()`.
  - Mantiene un contador `count` y un umbral `threshold`. Cuando se llena, cierra el “batch” hasta que salgan todos.
  - Usa `wait/notifyAll` para bloquear hilos que esperan turno y aplica fairness: recuerda la dirección del último batch (`lastBatchDir`) y da preferencia a la dirección opuesta si hay espera.

- Beepers (intercambio A ↔ B)
  - Clase: `BeeperExchangeManager`.
  - También es un monitor: métodos `reserve`, `refund`, `delivered` son `synchronized` y actualizan atómicamente `remainingA/B` y `carryingTotal`.
  - No bloquea con `wait`: si no hay stock, `reserve` devuelve 0 y el robot sigue más tarde. Se marca `done` cuando no queda stock y no hay beepers en tránsito.
