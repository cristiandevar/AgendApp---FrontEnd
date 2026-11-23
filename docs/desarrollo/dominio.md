# Dominio (Kotlin puro)

`app/src/main/java/com/agendapp/dominio/` no depende de Android: se puede compilar y testear con la JVM
sola. Es la **réplica en el teléfono** de `calculos.py` y `reglas.py` del backend, para que la libreta
calcule y controle sin conexión. Las reglas en sí están explicadas en *Reglas de negocio* del BackEnd;
acá se cuenta cómo están implementadas en la app.

## Archivos

| Archivo | Contenido |
|---|---|
| `Modelo.kt` | Datos del dominio: `Fichaje`, `Retiro`, `Pago`, `Tarifa`, `Feriado`, `Horario`, `ProductoDisponible` y `DatosEmpleado` (todo lo que necesita la libreta, más `toleranciaMinutos` y `plazoMinutos`) |
| `Calculos.kt` | Tramos, ganado, tarifa vigente, multiplicador de feriado, cuenta de un día, saldo arrastrado, ganado por salida |
| `Reglas.kt` | Secuencia de fichajes, tolerancia, plazo, qué se puede anular o cambiar, controles de un fichaje y un retiro nuevos, máximo diario |
| `Hoja.kt` | `ArmadoHoja.armar()`: arma los renglones de la hoja de un día |
| `Busqueda.kt` | Búsqueda incremental de productos |

## Cálculos (`Calculos`)

| Función | Equivale a (backend) | Qué hace |
|---|---|---|
| `armarTramos(fichajesDelDia)` | `armar_tramos` | Pares entrada→salida de los fichajes vigentes; `abiertoDesde` = entrada sin salida |
| `calcularGanado(min, tarifa, mult)` | `calcular_ganado` | `min / 60 × tarifa × mult` |
| `tarifaVigente(tarifas, fecha)` | contexto de `calculos` | La de `vigenteDesde` más reciente ≤ fecha |
| `multiplicador(feriados, fecha)` | | El del feriado, o 1 |
| `dia(datos, fecha)` | `datos_por_dia` | Minutos, ganado, retiros, pagos y si hay turno abierto |
| `saldoHasta(datos, fecha)` | `saldo_hasta` | Saldo acumulado al cierre de esa fecha |
| `ganadoPorSalida(datos, fecha)` | — | Monto de cada salida, como diferencia acumulada (las salidas suman exacto el total del día) |

### Redondeo idéntico al de Python

Python hace `Decimal(minutos) / Decimal(60) * tarifa * multiplicador` con el contexto por defecto
(28 dígitos, `ROUND_HALF_EVEN`) y al final `quantize(0.01, ROUND_HALF_UP)`. En Kotlin:

```kotlin
BigDecimal(minutos).divide(SESENTA, PYTHON)      // PYTHON = MathContext(28, RoundingMode.HALF_EVEN)
    .multiply(tarifa, PYTHON)
    .multiply(multiplicador, PYTHON)
    .setScale(2, RoundingMode.HALF_UP)
```

Así un día de 237 minutos a $2.500 da exactamente lo mismo en el teléfono y en el servidor ($9.875,00).

## Reglas (`Reglas`)

| Función | Para qué |
|---|---|
| `ultimoFichajeVigente`, `siguienteTipo` | El próximo fichaje es entrada o salida según el último vigente |
| `evaluarTolerancia(horarios, tipo, hora, tol)` | `Tolerancia(fuera, referencia)`: hora esperada más cercana y si quedó fuera |
| `errorDeSecuencia(marcas)` | Réplica de `validar_secuencia` (mismo mensaje) |
| `controlarFichaje(…)` | Control completo al tocar Aceptar → `Valido`, `FaltaMotivo` o `Rechazado(mensaje)`. Registra la hora **al minuto** y exige que sea **posterior** al último fichaje vigente |
| `dentroDelPlazo(registradoEn, anulado, ahora, plazo)` | Cuenta desde `registradoEn` con precisión de milisegundos |
| `limiteDelPlazo`, `segundosRestantes`, `formatoCuentaRegresiva` | Para mostrar "hasta las 22:48:45 (quedan 3:12)" |
| `impedimentoParaAnular(fichaje, …)` | `null` si se puede anular; si no, el motivo (ya anulado, cargado por el encargado, pasó el plazo, no es el último del día) |
| `impedimentoParaCambiarRetiro(retiro, …)` | Lo mismo para modificar o anular un retiro |
| `restanteDiario`, `yaRetirado` | Cuánto queda del máximo diario (excluyendo el retiro que se modifica) |
| `controlarRetiro(…)` | Control de un retiro nuevo o modificado → `Valido(producto, cantidad, precioCosto)`, `SinCambios` o `Rechazado`. Decide el **precio congelado** |

Los `impedimento…` devuelven un texto en lugar de un booleano para que la UI pueda **explicar** por qué no
se puede (y los tests verifiquen el mensaje).

## La hoja (`ArmadoHoja`)

`armar(datos, fecha, hoy)` devuelve una `Hoja` con:

- encabezado: fecha, si es hoy, feriado, horarios del día y desde cuándo está trabajando (turno abierto);
- `renglones`: `SaldoAnterior`, los `Evento` del día y `SaldoALaFecha` (con el `ResumenDia`);
- cada `Evento` lleva hora, `TipoRenglon` (Entrada, Retiro, Salida, Recibido), detalle, monto con signo,
  marcas (anulado, corregido, pendiente) y el fichaje, retiro o pago original.

Los eventos se ordenan por hora y, a igual hora, Entrada → Retiro → Salida → Recibido (igual que el
backend). Los montos llevan el signo **del estado de cuenta**: retiros y recibidos en negativo, salidas en
positivo. Los popups muestran los montos en positivo.

## Búsqueda (`Busqueda`)

`filtrar(productos, texto)`: sin importar mayúsculas ni acentos (`Normalizer` NFD), todas las palabras
escritas en cualquier orden ("miga sand" encuentra "Sándwich de miga"), o el **código exacto** tipeado.

## Tests

En `app/src/test/java/com/agendapp/dominio/`:

| Archivo | Tests | Qué cubre |
|---|---|---|
| `CalculosTest.kt` | 13 | Mismos casos que `CalculosTests`/`ReglasTests` del backend: tramos, redondeo, feriados, tarifas, saldo, tolerancia |
| `FichajeTest.kt` | 11 | Control de fichajes nuevos, secuencia, impedimentos para anular |
| `RetiroTest.kt` | 12 | Máximo diario, precio congelado al modificar, plazo con segundos, búsqueda |

Más `ApiErrorTest`, `DtosTest` y `FormatoTest` (respuestas reales de la API y formato de pesos):
**47 tests** en total.

Cuando se agrega o cambia una regla, el caso de test se escribe **igual en los dos repositorios** (mismos
datos, mismo resultado esperado).
