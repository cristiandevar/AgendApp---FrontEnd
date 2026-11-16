# AgendApp - FrontEnd

App Android de AgendApp: la libreta del empleado y el backoffice móvil del encargado.
El backend está en el repositorio **AgendApp - BackEnd**. El plan de fases vive en `PLAN.md`.

- Paquete: `com.agendapp`
- Kotlin + Jetpack Compose + Material 3
- Room (datos locales, funciona sin conexión), Retrofit + kotlinx.serialization (API), DataStore (sesión)
- minSdk 26 · targetSdk 35 · Gradle 8.11.1 · AGP 8.7.3 · Kotlin 2.1.0

## Abrir y correr (Android Studio)

1. **File → Open** y elegir la carpeta `AgendApp - FrontEnd`.
2. Si Android Studio ofrece actualizar Gradle o el plugin de Android (*AGP Upgrade Assistant*), **no actualizar** por ahora.
3. **Gradle JDK:** en *Settings → Build, Execution, Deployment → Build Tools → Gradle*, usar el JDK **21** que trae Android Studio (*jbr-21*). Gradle 8.11 no corre con JDK 25.
4. **URL del backend:** en `local.properties` (no se versiona) agregar la IP de tu PC en la red Wi-Fi:
   ```properties
   agendapp.api.debug=http://192.168.0.154:8000/api/
   ```
   Si no se agrega, se usa la de `gradle.properties`. La URL activa se ve abajo de todo en la pantalla de login (solo en debug).
5. Levantar el backend escuchando en la red: `python manage.py runserver 0.0.0.0:8000`.
6. Conectar el celular por USB y correr la configuración **app**.

Usuarios de prueba (del `seed_agenda` del backend): `ana` / `ana12345` (empleado) y `encargado` / `encargado123`.

## Estructura

```
app/src/main/java/com/agendapp/
├── AgendAppApplication.kt, AppContainer.kt, MainActivity.kt
├── data/
│   ├── remote/   ApiService, DTOs, errores de la API, JWT (interceptor + renovación del token)
│   ├── local/    Room: catálogo (productos, parámetros, feriados, tarifas, horarios)
│   │             y movimientos del empleado (fichajes, retiros, pagos)
│   ├── session/  SessionStore (DataStore): tokens, usuario, última descarga
│   └── repo/     AuthRepository, CatalogoRepository, MovimientosRepository
├── dominio/      Kotlin puro: réplica de calculos.py y reglas.py del backend + armado de la hoja
├── ui/           tema, raíz, login, libreta (empleado), inicio (encargado, provisorio)
└── util/         formato de pesos, fechas y horas
```

## Tests

`app/src/test` tiene tests unitarios con respuestas reales del backend (errores, DTOs, formato de pesos)
y los tests del dominio (`dominio/CalculosTest.kt`), con los mismos casos que los tests del backend.

**Datos de ejemplo (solo debug):** en la libreta, menú ⋮ → *Cargar datos de ejemplo* agrega movimientos
en los 3 días anteriores para ver la hoja con contenido (entradas, salidas, retiros, un anulado, uno
fuera de horario, uno cargado por el encargado y un adelanto). *Borrar datos de ejemplo* los quita.
No se sincronizan con el servidor.
Se corren desde Android Studio (clic derecho en la carpeta `test` → *Run Tests*) o con `gradlew test`.
