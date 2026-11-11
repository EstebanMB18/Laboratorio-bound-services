# Odómetro de Tutorías — Android Bound Services

Aplicación Android para registrar la **distancia recorrida en metros** por estudiantes que se desplazan a sesiones de tutoría presencial. Utiliza un servicio enlazado (`Bound Service`), el GPS del dispositivo y una base de datos SQLite local para conservar el historial de recorridos y consultar un consolidado.

## Características

- Lectura de la ubicación GPS actual: latitud, longitud y precisión horizontal informada por el dispositivo.
- Odómetro que acumula distancias entre mediciones válidas utilizando `Location.distanceTo()`.
- Configuración desde la interfaz de la **distancia mínima de movimiento**, en metros, y del **intervalo mínimo de actualización**, en segundos.
- Inicio y finalización manual de sesiones de desplazamiento.
- Registro persistente de fecha de inicio y fin, distancia, cantidad de puntos aceptados, parámetros de actualización y coordenadas iniciales y finales.
- Historial de las 12 sesiones más recientes, número total de sesiones y suma de distancias.
- Reporte consolidado de todos los desplazamientos, exportable a un archivo CSV mediante el selector de documentos de Android.
- Manejo de permisos de ubicación en tiempo de ejecución y limpieza de solicitudes GPS al terminar.

## Funcionamiento

1. La actividad principal se conecta a `OdometerService` mediante `bindService()` y una implementación de `ServiceConnection`.
2. El servicio entrega un `OdometerBinder` local. La actividad accede a métodos como `getDistance()`, `getCoordinates()`, `startTracking()` y `stopTracking()`.
3. El estudiante introduce la precisión de movimiento (metros) y el tiempo de actualización (segundos) y comienza la sesión.
4. La aplicación solicita permiso de **ubicación precisa** si todavía no existe. Es necesario tener el GPS habilitado.
5. `LocationManager` solicita actualizaciones al proveedor GPS. El servicio almacena la primera posición confiable y acumula los segmentos posteriores con `distanceTo()`.
6. Para disminuir errores, se descartan posiciones con exactitud horizontal superior a 35 metros, variaciones menores al umbral configurado y saltos de velocidad incompatibles con un desplazamiento normal.
7. La interfaz consulta el estado del servicio cada segundo, muestra las coordenadas recibidas y el odómetro en tiempo real.
8. Al finalizar, se guarda una sesión en SQLite. Si se abandona la aplicación durante una medición, la sesión también se detiene y se guarda automáticamente.

## Componentes

| Componente | Responsabilidad |
| --- | --- |
| `MainActivity.java` | Pantalla principal, configuración, permisos, enlace/desenlace, odómetro e historial. |
| `OdometerService.java` | Servicio Android enlazado; GPS, validación de puntos, cálculo de distancia y ciclo de vida. |
| `OdometerBinder` | Binder local que permite obtener una referencia directa a `OdometerService`. |
| `TripDatabase.java` | Base SQLite de sesiones; totales, historial y generación de CSV. |
| `activity_main.xml` | Vista de configuración, ubicación, odómetro, botones y consolidado. |
| `AndroidManifest.xml` | Declara actividad, servicio privado y permisos de ubicación. |

## Arquitectura

```text
MainActivity
  ├── ServiceConnection + OdometerBinder
  │     └── OdometerService
  │           ├── LocationManager (GPS_PROVIDER)
  │           ├── LocationListener
  │           ├── Location.distanceTo()
  │           └── TripDatabase (SQLite)
  └── TripDatabase (historial, suma y CSV)
```

El servicio se enlaza al abrir la pantalla y se desenlaza al abandonarla, liberando las actualizaciones de ubicación. **Este diseño realiza seguimiento solo mientras la actividad está visible**, no es un rastreador permanente en segundo plano. La recolección de GPS ocurre en el servicio y la actividad muestra el estado que este calcula. Un servicio enlazado no implica por sí mismo ejecutar código en otro hilo.

## Requisitos técnicos

- Android Studio con Android SDK 35 y JDK 17.
- Proyecto Gradle con Android Gradle Plugin 8.9.2 y Gradle 8.11.1. El paquete incluye la configuración de Gradle, pero no el archivo binario oficial `gradle-wrapper.jar`; si se utiliza la terminal, se requiere Gradle instalado en `PATH` o generar el wrapper oficial con una instalación de Gradle.
- Android 8.0 (API 26) o superior.
- Dispositivo con GPS, ubicación activa y permiso de ubicación precisa; un emulador puede usar coordenadas simuladas.
- No requiere claves API, cuenta de usuario, conexión de red ni librerías externas adicionales.

## Instalación y ejecución

1. Abrir la carpeta raíz `Laboratorio-bound-services` como proyecto Android Studio.
2. Sincronizar Gradle, descargar las herramientas Android solicitadas y seleccionar un dispositivo o emulador.
3. Ejecutar el módulo `app`.
4. Conceder acceso a **ubicación precisa mientras se usa la aplicación**.
5. Configurar distancia mínima e intervalo; pulsar **Iniciar** y desplazarse con el dispositivo.
6. Pulsar **Finalizar** para conservar la sesión, revisar el historial y usar **Exportar reporte CSV** para seleccionar dónde guardar el archivo del consolidado.

### Configuración y límites

- **Movimiento**: de 1 a 100 metros. Valor inicial: **5 m**.
- **Actualización**: de 1 a 300 segundos. Valor inicial: **3 s**.
- Los intervalos son mínimos de solicitud al sistema, **no garantizan** una lectura puntual; dependen del proveedor GPS, el equipo y la señal.
- La precisión mostrada junto a las coordenadas es la exactitud horizontal estimada por Android, distinta del parámetro de distancia mínima de movimiento.
- La distancia acumulada es la suma de segmentos consecutivos aceptados, no la ruta en calles ni la distancia en línea recta entre origen y destino.
- El CSV contiene fechas como marcas de tiempo Unix en milisegundos, metros y coordenadas decimales; la fila `TOTAL` incluye la suma histórica.
- Los datos permanecen en el almacenamiento privado de la aplicación, sin enviarse a un servidor. Desinstalar la app elimina su base de datos.

## Pruebas de uso

- Con GPS habilitado y permiso preciso, iniciar y recorrer una distancia suficiente para superar el umbral.
- Con GPS desactivado, comprobar que la aplicación muestre un mensaje y no inicie la medición.
- Denegar el permiso y verificar que no se inicia el seguimiento.
- Finalizar dos sesiones y comprobar que el historial y la distancia total se actualizan.
- Cambiar el intervalo, iniciar una nueva sesión y verificar que los parámetros persistidos corresponden a los ingresados.
- Abandonar la aplicación durante una sesión y revisar que el registro queda guardado.

## Consideraciones de privacidad

El GPS se utiliza únicamente para las sesiones iniciadas explícitamente por el usuario. El servicio deja de solicitar ubicaciones al finalizar o abandonar la pantalla. Las coordenadas iniciales y finales se conservan localmente para el reporte. No se registra una traza completa de todos los puntos recibidos.

## Licencia

Proyecto demostrativo de Android que aplica servicios enlazados y servicios de localización.
