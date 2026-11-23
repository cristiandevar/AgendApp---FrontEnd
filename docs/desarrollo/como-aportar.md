# Cómo aportar

## Entorno

- **Android Studio** reciente, con el **JDK 21** que trae (*jbr-21*) como Gradle JDK: *Settings → Build,
  Execution, Deployment → Build Tools → Gradle*. Gradle 8.11 no corre con JDK 25.
- Si Android Studio ofrece actualizar Gradle o el plugin de Android, **no actualizar** sin acordarlo:
  las versiones están fijadas en `gradle/libs.versions.toml` (AGP 8.7.3, Kotlin 2.1.0, Compose BOM
  2024.12.01, Room 2.6.1…).
- Un celular con Android 8.0 o superior (minSdk 26) y **Google Play Services** (para el escáner), por
  USB con depuración activada.
- El **backend corriendo** en la PC (ver el README del BackEnd), escuchando en la red:
  `python manage.py runserver 0.0.0.0:8000`.

### URL del backend

En `local.properties` (no se versiona), con la IP de la PC en la red Wi-Fi:

```properties
agendapp.api.debug=http://192.168.0.154:8000/api/
```

Si no está, se usa la de `gradle.properties`. En debug, la URL activa se ve abajo de todo en el login.

### Datos para probar

- Usuarios del `seed_agenda`: `ana` / `ana12345` (empleado) y `encargado` / `encargado123`.
- En la libreta, menú ⋮ → **Cargar datos de ejemplo** (solo debug): tres días anteriores con entradas,
  salidas, retiros, un anulado, uno fuera de horario, uno del encargado y un adelanto. Sus ids empiezan
  con `demo-` y **Borrar datos de ejemplo** los quita.
- Escáner: los productos del seed tienen los códigos `AGENDAPP-0001` a `AGENDAPP-0008`. Se pueden
  generar el QR o el código de barras con ese texto y escanearlos desde la pantalla de la PC.
- Para ver la base del teléfono: *App Inspection → Database Inspector* en Android Studio.

## Tests

```bash
gradlew test          # o en Android Studio: clic derecho en app/src/test → Run Tests
```

Son tests de JVM (no hace falta el teléfono). El dominio es Kotlin puro: cualquier regla nueva se testea
ahí, con el mismo caso que en el backend (ver [Dominio](dominio.md#tests)).

## Dónde poner cada cosa

- ¿Es una cuenta o una regla? → `dominio/`, sin imports de Android, con test.
- ¿Lee o escribe datos? → un repositorio de `data/repo/`. La UI no usa DAOs ni la API directamente.
- ¿Es una acción del usuario? → el ViewModel: relee de la base, controla con `Reglas` y guarda. Las
  reglas también se evalúan en la UI, pero solo para mostrar cosas (el motivo, el tope); el control que
  vale es el del ViewModel.
- ¿Es visual? → `ui/`. Colores del tema (`Tinta`, `Margen`, `Papel`…), nunca valores sueltos.

## Cambios en la base local

Room está en la **versión 2** y los esquemas se exportan a `app/schemas/` (se versionan).

**Desde la v2, toda migración tiene que ser real**: en los teléfonos hay fichajes y retiros que todavía no
llegaron al servidor, y una migración destructiva los borraría. Para cambiar una tabla:

1. Modificá la entidad y subí `version` en `AppDatabase`.
2. Escribí la `Migration(2, 3)` con el SQL y agregala con `.addMigrations(...)`.
3. Compilá: Room genera `schemas/…/3.json`, que va en el mismo commit.
4. Probá instalando **encima** de la versión anterior con datos cargados, no con una instalación limpia.

Agregar consultas a un DAO no cambia el esquema ni necesita migración.

## Reglas compartidas con el backend

Las reglas de `dominio/` son copia de `reglas.py` y `calculos.py`. Si una cambia:

1. Se cambia y testea en el backend.
2. Se replica en `dominio/` con el **mismo caso de test** y el mismo mensaje.
3. Los PR de los dos repos se mencionan entre sí.

## Forma de trabajo

- El trabajo se organiza en **fases** y **entregas** ([`PLAN.md`](../../PLAN.md)). No se avanza de
  fase con errores pendientes.
- **Ramas** `AAAAMMDD_descripcion` desde `main` (por ejemplo `20261116_fase_3_libreta`).
- **Commits** en castellano, uno por entrega probada en el celular: `Fase 3b: fichajes desde la libreta`,
  con el detalle en el cuerpo. Se commitea recién cuando las pruebas dan lo esperado.
- **Un PR por fase** hacia `main`. Al cerrar la fase se actualiza `PLAN.md` y se copia al repositorio.
- `versionName` sigue la fase (0.3.0 = Fase 3).
- No se versionan `local.properties`, `build/` ni claves.

## Checklist antes de un PR

- [ ] `gradlew test` pasa.
- [ ] Se probó en un celular real, incluido **sin conexión** (modo avión).
- [ ] Si cambió una entidad: hay migración real, esquema exportado y se probó actualizando sobre datos existentes.
- [ ] Si cambió una regla: está igual en el backend, con el mismo test.
- [ ] Los textos se entienden sin conocer el sistema y dicen qué hacer.
- [ ] `docs/` refleja el cambio (guía del empleado incluida).
