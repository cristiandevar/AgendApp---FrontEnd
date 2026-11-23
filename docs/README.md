# Documentación de AgendApp - FrontEnd

## Para desarrolladores

1. [Arquitectura](desarrollo/arquitectura.md): capas, flujo de datos, funcionamiento sin conexión, sesión y base local.
2. [Dominio](desarrollo/dominio.md): la lógica en Kotlin puro (cálculos, reglas, armado de la hoja), réplica del backend.
3. [La libreta (UI)](desarrollo/libreta.md): pantallas, componentes, estados y cómo agregar un tipo de evento.
4. [Cómo aportar](desarrollo/como-aportar.md): entorno, tests, migraciones de Room, convenciones, ramas y commits.

## Para usuarios

- [Guía del empleado](uso/guia-del-empleado.md): ingresar, leer la libreta, fichar, retirar productos, corregir dentro del plazo. Con imágenes de referencia.

> Las imágenes de la guía son **maquetas fieles** a las pantallas: se generaron con los mismos colores,
> medidas, tipografía y textos que los Composables de `ui/`. Pueden diferir en detalles mínimos de una
> captura real (por ejemplo, la barra de estado del teléfono).

## Estado

Describe lo desarrollado hasta la **Fase 3** (libreta del empleado) del [`PLAN.md`](../PLAN.md). Lo que
todavía no existe se marca como *(Fase N)*: la sincronización con el servidor es la Fase 4 y el
backoffice del encargado en el celular, la Fase 5.

El backend y su documentación (API, reglas de negocio, guía del encargado) están en el repositorio
**AgendApp - BackEnd**.
