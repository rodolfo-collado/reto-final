# Sedes · Planificador de trayectos

Aplicación de escritorio JavaFX para administrar un mapa dirigido de sedes ficticias y encontrar el trayecto de menor costo con Dijkstra. Permite añadir y eliminar sedes, crear, actualizar y eliminar conexiones, visualizar flechas y costos, resaltar una ruta, importar y exportar mapas y consultar cálculos anteriores.

## Ejecutar

Requiere **JDK 17 o superior** con `JAVA_HOME` configurado y un escritorio gráfico. El proyecto incluye Maven Wrapper **3.8.5**; no hace falta instalar Maven. La primera ejecución descarga dependencias gratuitas de Maven Central. JavaFX **21.0.6**, JUnit **5.12.1** y las versiones de plugins se declaran en `pom.xml`.

Desde la raíz del proyecto:

```bash
./mvnw javafx:run
```

En Windows: `mvnw.cmd javafx:run`. En Linux se requieren las bibliotecas gráficas de GTK 3 y una sesión X11/Wayland. Las pruebas del dominio no necesitan pantalla.

## Pruebas y compilación

```bash
./mvnw test
./mvnw clean verify
```

Hay **29 pruebas JUnit**: ruta óptima, sede aislada, sentido dirigido, validación de nombres y extremos, pesos negativos, origen igual a destino, costos cero, ciclos, lazos, empates, entradas obsoletas de la cola, costos mayores que `Integer.MAX_VALUE`, importaciones inválidas, Unicode, recarga, fallos de escritura e historial. Usan directorios temporales y no modifican el estado del usuario. El flujo de GitHub Actions ejecuta la suite con Java 17 y 21.

## Demostración de los criterios obligatorios

1. Abre la aplicación, pulsa **Importar mapa**, selecciona `examples/mapa-demo.tsv` y confirma el reemplazo.
2. Selecciona origen **A**, destino **B** y pulsa **Calcular ruta**: debe mostrar **costo 3** y **A → C → B**, resaltados en verde.
3. Selecciona **A → D**: aparece **SIN_RUTA**; D es una sede aislada.
4. Intenta guardar **B → D** con costo **-1**: aparece un mensaje controlado y la conexión no se añade. También puedes importar `examples/mapa-invalido.tsv`: la importación se rechaza y el mapa anterior se conserva.
5. Calcula **D → D**: costo **0**, ruta **D**.
6. Cierra y vuelve a ejecutar. El mapa se recupera automáticamente y **Historial** muestra los cálculos previos, incluidos los resultados sin ruta.
7. Exporta el mapa, modifica una conexión e importa el archivo exportado para restaurarlo.

## Persistencia e intercambio

- `data/mapa.tsv`: estado del mapa, guardado automáticamente tras cada edición o importación válida.
- `data/mapa.tsv.rutas.tsv`: entradas y resultados de los cálculos, con fecha UTC, origen, destino, estado, costo `long` y secuencia de sedes. Se carga al arrancar y se consulta mediante **Historial**.
- Los archivos locales se excluyen de Git. Los datos de `examples/` son ficticios y sí se versionan.
- Cada archivo se escribe en un temporal del mismo directorio y se reemplaza con un movimiento atómico cuando el sistema de archivos lo admite; se utiliza reemplazo normal como alternativa. Si la escritura falla, el cambio no se publica en memoria.
- Una importación se valida completamente antes de reemplazar el mapa. Un archivo dañado al arrancar genera un aviso y detiene la apertura; se conserva para su corrección.
- El historial conserva resultados del mapa que existía al calcularlos. Editar o importar otro mapa no reinterpreta ni elimina esos resultados. Exportar un mapa exporta sus sedes y conexiones; el historial permanece local.

Para elegir otro almacenamiento, puedes usar la variable estándar de la JVM antes de arrancar:

```bash
JAVA_TOOL_OPTIONS='-Dsedes.data=/ruta/absoluta/mapa.tsv' ./mvnw javafx:run
```

### Formato del mapa

Texto UTF-8, con **tabuladores reales** como separadores y encabezado `MAPA_SEDES` + tabulador + `1`. Ejemplo representado con columnas separadas por tabuladores:

```text
MAPA_SEDES	1
SEDE	A
SEDE	B
SEDE	C
SEDE	D
CONEXION	A	B	4
CONEXION	A	C	1
CONEXION	C	B	2
```

Las sedes pueden incluir espacios internos y acentos; tienen entre 1 y 60 caracteres, sin caracteres de control ni espacios en los extremos. Se distinguen mayúsculas y minúsculas. Los costos son enteros entre 0 y 2147483647. Se rechazan versiones desconocidas, registros mal formados, sedes duplicadas, conexiones duplicadas en archivos, extremos inexistentes y costos inválidos. Los registros de conexión pueden preceder a las sedes. Se admiten mapas vacíos y líneas en blanco.

## Diseño

| Capa | Responsabilidad |
| --- | --- |
| `domain` | `CampusMap`: lista de adyacencia dirigida y validaciones; `Connection`, `RouteResult`, `RouteCalculation`: valores del dominio. |
| `service` | `DijkstraService`: algoritmo independiente de JavaFX. `MapService`: coordina cambios, importación, exportación, cálculo, guardado y recuperación. |
| `persistence` | `MapRepository` y `TextMapRepository`: archivo del mapa. `RouteHistoryRepository`: archivo de resultados. |
| Interfaz | `MapApplication`: formularios, dibujo del grafo y mensajes controlados. `Launcher`: entrada de la aplicación. |

Hay una conexión por par ordenado: guardarla otra vez desde la interfaz actualiza el costo. Los lazos y costos cero son válidos. Eliminar una sede elimina también todas sus conexiones entrantes y salientes. Una conexión inversa debe añadirse explícitamente.

Dijkstra usa `PriorityQueue` y distancias `long`. Cada mejora añade una entrada; al extraer, se ignoran las entradas obsoletas. La relajación es estricta, por lo que un ciclo de costo cero no crea un ciclo en los predecesores. Se reconstruye la ruta desde el destino. Para sedes válidas con origen igual a destino se devuelve costo cero. Un extremo inexistente se rechaza antes del cálculo; un destino inaccesible devuelve `SIN_RUTA` con ruta vacía y sin costo.

### Complejidad

Para V sedes y E conexiones, la variante con entradas diferidas en la cola tarda **O((V+E) log(V+E))** y requiere **O(V+E)** de memoria auxiliar en el peor caso. En este grafo simple, el tiempo también suele expresarse como **O((V+E) log V)** (para V ≥ 2). Reconstruir la ruta cuesta O(V). El mapa ocupa O(V+E). Copiar, importar o exportar un mapa cuesta O(V+E), sin contar la longitud del texto; el guardado de un cálculo reescribe el historial completo.

## Limitaciones conocidas

- Aplicación local de un solo proceso; las operaciones se ejecutan en el hilo de JavaFX. No hay requisito de concurrencia ni pruebas concurrentes. No se debe compartir el mismo archivo entre varias instancias.
- El diseño circular es esquemático, sin coordenadas geográficas. Muchas sedes o conexiones pueden solaparse; los nombres largos se abrevian en el dibujo y se muestran completos al pasar el cursor o en las listas.
- El historial no tiene límite ni paginación. Para mapas o historiales muy grandes convendría añadir trabajo en segundo plano y una base de datos local.
- La exportación no crea un instalador autónomo: se ejecuta mediante Maven y el JDK. Las pruebas automatizadas verifican dominio, servicios y persistencia; la interacción visual se comprueba por separado.

## Entrega por el líder

Remoto público configurado: [rodolfo-collado/reto-final](https://github.com/rodolfo-collado/reto-final). El líder debe revisar los cambios, subirlos al remoto y compartir ese enlace para evaluación. No se incluyen credenciales ni se necesitan servicios de pago.

```bash
git add pom.xml .gitignore README.md src examples .github
git commit -m "Implementar planificador de sedes con JavaFX y Dijkstra"
git push origin HEAD
```
