# Guión de QA manual — Sistema de Facturación Recurrente

**Para quién es este documento:** cualquier persona de Helpcom con conocimiento de facturación
y clientes, **sin necesidad de saber programar**. Vas a usar el sistema como lo usaría un
administrativo o contable en el día a día: haciendo clic, llenando formularios y revisando lo
que aparece en pantalla.

Es la contraparte manual de la suite de pruebas automáticas del equipo técnico
(`docs/qa.md`) — cubre 9 de los 10 flujos de negocio de esa suite (todos menos el reproceso de
UF de una propuesta `PENDIENTE_UF`, agregado después de este guion), explicados para probarlos
tú mismo, con el mouse y el teclado. Suma además dos casos exclusivos del frontend, sin flujo
E2E de backend equivalente en `docs/qa.md`: el **Caso R** (Panel principal/dashboard, R9 de
`docs/plan-rediseno.md`) y el **Caso S** (el botón "Reprocesar UF" de Propuestas, que sí usa el
endpoint del reproceso de UF, pero desde la interfaz).

---

## 1. Introducción

Este sistema reemplaza el cálculo manual (típicamente en Excel) de "qué le vamos a facturar
este mes a cada cliente". Cada cliente tiene uno o más **proyectos** (contratos de servicio
recurrente), con un precio, una moneda (pesos chilenos o UF) y una frecuencia (mensual o
anual). El día 1 de cada mes — o cuando alguien lo ejecuta a mano — el sistema revisa todos los
proyectos activos y genera un borrador de lo que corresponde facturar a cada uno: eso es una
**propuesta de facturación**. Cada propuesta ya trae calculado el neto, el IVA (19%) y el
total.

Ese borrador no es todavía una factura real: alguien de Helpcom decide cuándo emitirla y le
asocia el número de factura y el PDF de respaldo en el sistema. También existe una vía
alternativa para cargar facturación en lote desde un archivo CSV (por ejemplo, para proyectos
que hoy todavía se calculan en Excel), y un informe que resume todo lo que se va a facturar en
un período.

Este guión te va a llevar, paso a paso, por los flujos de negocio más importantes: crear
clientes y proyectos, aplicar descuentos, ejecutar el cálculo mensual, revisar los resultados,
asociar facturas, importar un archivo CSV y ver el informe final — además de casos donde el
sistema **debe** impedir algo (fechas que se cruzan, un cliente que no existe) o mostrar un
aviso en vez de inventar un número.

---

## 2. Preparación

Antes de empezar, necesitas:

1. **La dirección del sistema** ya levantado (te la entrega el equipo técnico). En este
   documento la vamos a llamar `[dirección del sistema]` — reemplázala por la real cada vez que
   aparezca (por ejemplo, algo como `http://localhost:3000` o una URL interna de prueba).
2. **Dos usuarios de prueba** para iniciar sesión:
   - Un **usuario administrador de prueba** — con este vas a hacer casi todo el guión.
   - Un **usuario operador de prueba** — lo vas a usar solo en el Caso M, para comprobar que
     hay acciones que un operador no puede hacer.
   El equipo técnico te entrega las claves de estos dos usuarios por separado; **no están en
   este documento** por seguridad.
3. Un lector de PDF cualquiera en tu computador (para el Caso J) y, si quieres, un editor de
   texto simple (Bloc de notas o similar) para preparar el archivo del Caso K.

**Importante:** todo lo que vas a crear en este guión (clientes, proyectos, facturas) es **dato
de prueba**. Queda claramente identificado con nombres como "Contabilidad Andina SpA" o
"Auditoría Semestral Sur", que no corresponden a clientes reales de Helpcom. El equipo técnico
puede borrarlo todo después sin ningún problema — no necesitas preocuparte de "ensuciar" el
sistema real.

### Sobre las fechas de este guión

Algunos casos piden fechas relativas a "hoy" (por ejemplo, "el primer día de hace 2 meses") en
vez de una fecha fija — así el guión funciona sin importar qué día lo estés leyendo. Cuando
necesites calcular una fecha así, usa el calendario que trae el propio formulario (icono de
calendario junto al campo de fecha): es más fácil navegar meses hacia atrás/adelante ahí que
calcularlo de cabeza.

Para ejecutar el ciclo de un mes que no es el actual, la pantalla "Ejecutar ciclo" trae dos
campos editables, **Año** y **Mes** (por defecto vienen con el mes de hoy) — puedes cambiarlos
libremente antes de hacer clic en "Ejecutar ciclo". Eso es lo que vamos a usar en varios casos
para simular "el mes que viene" o "dentro de varios meses" sin tener que esperar a que ese mes
llegue de verdad.

---

## 3. Glosario mínimo

| Término | En criollo |
|---|---|
| **Proyecto** | El contrato o servicio recurrente que se le presta a un cliente: tiene un precio, una moneda (CLP o UF) y una frecuencia (mensual o anual). |
| **Propuesta de facturación** | El borrador de "esto es lo que le corresponde pagar a este cliente este mes", con neto/IVA/total ya calculados. Todavía no es una factura. |
| **Ciclo** | El proceso que revisa todos los proyectos activos y genera las propuestas del mes. Corre solo el día 1 de cada mes, o se puede ejecutar a mano para un período específico. |
| **UF (Unidad de Fomento)** | Unidad de valor reajustable, muy usada en contratos de servicios en Chile. El sistema obtiene automáticamente el valor de la UF del día exacto en que corresponde facturar cada proyecto en UF, y ese número queda guardado para siempre en esa propuesta (no cambia después, aunque la UF real siga moviéndose). |
| **Neto / IVA / Total** | Neto: el monto antes de impuestos. IVA: 19% sobre el neto. Total: neto + IVA — lo que efectivamente se cobra. |
| **Acuerdo de precio** | Un descuento o un precio especial pactado con el cliente para un proyecto, válido durante un rango de fechas. Reemplaza (o rebaja) el precio normal del proyecto mientras está vigente. |
| **Estado Pendiente** | La propuesta ya se generó y tiene un monto calculado; todavía no se le asoció una factura. |
| **Estado Pendiente UF** | La propuesta se generó, pero el sistema todavía no tiene el valor de la UF para esa fecha, así que **no inventa un monto**: queda marcada así hasta que la UF esté disponible y se pueda recalcular. |
| **Estado Facturada** | Ya tiene un número de factura (y opcionalmente un PDF) asociado. |
| **Estado Anulada** | Se descartó a propósito; no se va a facturar. |

---

## 4. Datos de prueba de este guión

Vas a crear estos clientes y proyectos a medida que avances por los casos (cada caso te dice
exactamente cuándo). Esta tabla es solo un resumen para tener a la vista — no necesitas crear
nada todavía.

**Clientes:**

| Nombre | RUT |
|---|---|
| Contabilidad Andina SpA | 76.890.123-6 |
| Soportes del Sur Ltda. | 77.456.789-5 |
| Servicios Cordillera SpA | 78.654.321-5 |

**Proyectos:**

| Nombre | Cliente | Moneda | Frecuencia | Precio base | Para qué caso |
|---|---|---|---|---|---|
| Soporte Contable Mensual | Contabilidad Andina SpA | UF | Mensual | 10 | B, C, D, E, G, J |
| Auditoría Semestral Sur | Soportes del Sur Ltda. | CLP | Mensual | $900.000 (se reemplaza por un precio pactado) | C, E, G, J |
| Marketing Digital Andina | Contabilidad Andina SpA | CLP | Mensual | $300.000 | H |
| Auditoría Anual Sur | Soportes del Sur Ltda. | CLP | Anual | $2.000.000 | I |
| Consultoría Fantasma | Servicios Cordillera SpA | UF | Mensual | 5 | F, L |
| Revisión Puntual A | Servicios Cordillera SpA | CLP | Mensual | $150.000 | L |
| Revisión Puntual B | Servicios Cordillera SpA | CLP | Mensual | $400.000 (se anula) | L |

---

## 5. Casos de prueba

---

### Caso A — Crear un cliente

**Objetivo:** comprobar que se puede registrar un cliente nuevo, y que el sistema rechaza un
RUT inválido con un aviso claro.

**Pasos:**

1. Inicia sesión en `[dirección del sistema]` con el **usuario administrador de prueba**.
2. En el menú, entra a **Clientes**.
3. Haz clic en **"+ Nuevo cliente"**.
4. En el campo **RUT**, escribe `76.890.123-4` (a propósito, un dígito verificador incorrecto)
   y haz clic fuera del campo (o presiona Tab).
   - **Resultado esperado:** bajo el campo aparece, en rojo, el mensaje **"El RUT no es
     válido."** — el sistema no te deja seguir con ese RUT.
5. Corrige el campo **RUT** a `76.890.123-6` (el correcto) y complétalo con:
   - **Razón social:** `Contabilidad Andina SpA`
   - (El resto de los campos — nombre de fantasía, giro, email, teléfono, dirección — son
     opcionales; puedes dejarlos en blanco.)
   - **Estado:** Activo (viene marcado por defecto).
6. Haz clic en **Guardar**.
7. Repite los pasos 3, 5 y 6 (sin repetir la prueba del RUT inválido) dos veces más, para
   crear:
   - **Razón social:** `Soportes del Sur Ltda.` — **RUT:** `77.456.789-5`
   - **Razón social:** `Servicios Cordillera SpA` — **RUT:** `78.654.321-5`

**Resultado esperado:** el RUT `76.890.123-4` fue rechazado con el aviso en rojo antes de
guardar. Los tres clientes con RUT correcto (`76.890.123-6`, `77.456.789-5`, `78.654.321-5`)
quedan creados y aparecen en el listado de Clientes.

☐ OK &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso B — Crear un proyecto en UF

**Objetivo:** registrar un proyecto que se factura en UF, de forma mensual.

**Pasos:**

1. En el menú, entra a **Proyectos**.
2. Haz clic en **"+ Nuevo proyecto"**.
3. Completa:
   - **Cliente:** `Contabilidad Andina SpA`
   - **Nombre:** `Soporte Contable Mensual`
   - **Precio base neto:** `10`
   - **Moneda:** `UF`
   - **Periodicidad:** `Mensual`
   - **Día de facturación:** `10`
   - **Fecha de inicio:** el día 1 del mes, dos meses atrás desde hoy (por ejemplo, si hoy
     estamos en agosto, usa el 1 de junio).
   - **Fecha de término:** déjala en blanco (sin fecha de término).
   - **Estado:** Activo.
4. Haz clic en **Guardar**.

**Resultado esperado:** el proyecto "Soporte Contable Mensual" aparece en el listado de
Proyectos, asociado a "Contabilidad Andina SpA", con moneda UF y periodicidad Mensual.

> **Nota sobre la UF:** el sistema obtiene el valor de la UF automáticamente (de una fuente
> oficial) para el día exacto en que corresponde facturar cada proyecto, y lo deja fijo en esa
> propuesta para siempre. Como ese número real cambia día a día, este guión no puede
> fijarlo de antemano — en el Caso E te mostramos la fórmula y usamos también, en paralelo, un
> proyecto en pesos para tener un ejemplo con números 100% garantizados de antemano.

☐ OK &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso C — Aplicar un descuento (acuerdo de precio)

**Objetivo:** comprobar que un descuento porcentual y un precio pactado se guardan
correctamente, para verificar su efecto en el Caso E.

**Escenario 1 — descuento porcentual (sobre "Soporte Contable Mensual"):**

1. En **Proyectos**, entra al proyecto **"Soporte Contable Mensual"**.
2. Ve a su sección de **Acuerdos de precio** y haz clic en **"+ Nuevo acuerdo"** (o similar).
3. Completa:
   - **Tipo de acuerdo:** `Descuento porcentual`
   - **Porcentaje de descuento (%):** `10`
   - **Vigencia:** deja la opción "Fecha de inicio y término".
   - **Fecha de inicio:** la misma fecha de inicio del proyecto (Caso B).
   - **Fecha de término:** `31-12-2099` (una fecha bien a futuro — en la práctica, "sin
     vencimiento" para efectos de este guión).
4. Haz clic en **Guardar**.

**Escenario 2 — precio pactado (proyecto nuevo):**

5. Crea un segundo proyecto (**Proyectos → "+ Nuevo proyecto"**):
   - **Cliente:** `Soportes del Sur Ltda.`
   - **Nombre:** `Auditoría Semestral Sur`
   - **Precio base neto:** `900000`
   - **Moneda:** `CLP`
   - **Periodicidad:** `Mensual`
   - **Día de facturación:** `12`
   - **Fecha de inicio:** el mismo primer día de hace 2 meses que usaste en el Caso B.
   - Guarda.
6. Entra a este nuevo proyecto → **Acuerdos de precio** → **"+ Nuevo acuerdo"**:
   - **Tipo de acuerdo:** `Precio pactado`
   - **Precio pactado:** `600000`
   - **Moneda:** `CLP`
   - **Fecha de inicio:** la misma fecha de inicio del proyecto.
   - **Fecha de término:** `31-12-2099`
7. Haz clic en **Guardar**.

**Resultado esperado:** ambos acuerdos quedan guardados y aparecen en la lista de acuerdos de
cada proyecto respectivo. Un **precio pactado reemplaza por completo** el precio base del
proyecto mientras esté vigente — por eso no importa que "Auditoría Semestral Sur" tenga un
precio base de $900.000: mientras este acuerdo esté vigente, lo que se factura es el precio
pactado ($600.000), no el precio base. Vas a comprobar el efecto exacto en el Caso E.

☐ OK &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso D — Dos acuerdos que se cruzan en fechas

**Objetivo:** comprobar que el sistema impide tener dos acuerdos de precio vigentes al mismo
tiempo en el mismo proyecto.

**Pasos:**

1. En **Proyectos**, entra a **"Soporte Contable Mensual"** (el que ya tiene un acuerdo vigente
   desde el Caso C, hasta el 31-12-2099).
2. Ve a **Acuerdos de precio** → **"+ Nuevo acuerdo"**.
3. Completa:
   - **Tipo de acuerdo:** `Descuento porcentual`
   - **Porcentaje de descuento (%):** `20`
   - **Fecha de inicio:** un mes después de la fecha de inicio del proyecto (cualquier fecha
     que caiga dentro del rango del acuerdo ya existente).
   - **Fecha de término:** `31-12-2099`
4. Antes de hacer clic en Guardar, fíjate si aparece un **aviso amarillo** advirtiendo que el
   rango se superpone con el acuerdo ya existente.
5. Haz clic en **Guardar** de todas formas.

**Resultado esperado:** el sistema **rechaza** el guardado y muestra un mensaje de error
indicando que las fechas se traslapan con el acuerdo que ya existe para ese proyecto. El nuevo
acuerdo **no** queda creado — si vuelves a la lista de acuerdos del proyecto, sigue habiendo
solo uno.

> **Por qué:** un proyecto solo puede tener **un** acuerdo de precio vigente a la vez. Si
> pudieran superponerse, el sistema no sabría cuál de los dos aplicar en el cálculo.

☐ OK &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso E — Ejecutar el ciclo del mes y revisar los montos

**Objetivo:** ejecutar el cálculo mensual y comprobar, con números concretos, que el neto, el
IVA (19%) y el total de la propuesta generada son los correctos.

**Pasos:**

1. En el menú, entra a **Facturación → Ciclo** (o el enlace "Ejecutar ciclo" dentro de
   Facturación).
2. Deja los campos **Año** y **Mes** tal como aparecen por defecto (el mes actual).
3. Haz clic en **"Ejecutar ciclo"** y confirma en el cuadro que aparece.

**Resultado esperado en pantalla:** un resumen indicando cuántas propuestas se generaron, con
el estado general "Exitosa" (o "Con advertencias" si aparece alguna propuesta sin UF — no
debería, todavía no llegamos a ese caso).

4. Haz clic en **"Ver propuestas de este período →"** (o entra a **Facturación** y filtra por
   el período que acabas de ejecutar).
5. Busca la propuesta de **"Auditoría Semestral Sur"** (cliente Soportes del Sur Ltda.) y
   revisa sus montos:

   **Resultado esperado — Auditoría Semestral Sur (100% verificable, sin depender de la UF):**
   - **Neto:** `$600.000` — es el **precio pactado** del acuerdo (Caso C), no el precio base
     de $900.000: el precio pactado lo reemplaza por completo.
   - **IVA (19%):** `$600.000 × 0,19 = $114.000`
   - **Total:** `$600.000 + $114.000 = $714.000`

6. Busca la propuesta de **"Soporte Contable Mensual"** (cliente Contabilidad Andina SpA) y
   anota el **"Valor UF"** que muestra (la propuesta lo deja visible en su detalle).

   **Resultado esperado — Soporte Contable Mensual (ejemplo con la fórmula):**
   - El proyecto vale 10 UF, con un descuento del 10% (Caso C) → neto = `10 × 0,90 × [Valor UF
     que anotaste]`.
   - *Ejemplo ilustrativo:* si el Valor UF fuera $39.000, el neto sería
     `10 × 0,90 × 39.000 = $351.000`; IVA = `$351.000 × 0,19 = $66.690`; total = `$417.690`.
     Reemplaza $39.000 por el valor real que viste en tu pantalla y confirma que el neto, el
     IVA y el total calzan con la misma fórmula.

**Resultado esperado general:** ambas propuestas quedan en estado **Pendiente**, con sus
montos visibles (ninguna debería decir "sin UF" en este paso).

☐ OK &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso F — Una propuesta sin valor UF disponible

**Objetivo:** ver cómo se comporta una propuesta cuando el sistema todavía no tiene el valor
de la UF para su fecha, y confirmar que no se puede facturar así.

Este caso usa una fecha **bien a futuro** a propósito: el valor de la UF se publica con muy
poca anticipación (no más de un mes), así que pedir el cálculo para una fecha muy lejana
garantiza que el sistema todavía no la tenga — sin importar qué día ejecutes tú esta prueba.

**Pasos:**

1. Crea un nuevo proyecto (**Proyectos → "+ Nuevo proyecto"**):
   - **Cliente:** `Servicios Cordillera SpA`
   - **Nombre:** `Consultoría Fantasma`
   - **Precio base neto:** `5`
   - **Moneda:** `UF`
   - **Periodicidad:** `Mensual`
   - **Día de facturación:** `1`
   - **Fecha de inicio:** el primer día de hace 6 meses.
   - Guarda. (Sin acuerdo de precio para este proyecto.)
2. Ve a **Facturación → Ciclo**.
3. En **Año** y **Mes**, ingresa **13 meses hacia adelante** desde hoy (mismo mes que hoy, un
   año después y un mes más — por ejemplo, si hoy es agosto de 2026, usa **septiembre de
   2027**). **Anota este Año/Mes** — lo vas a necesitar de nuevo en el Caso L. Este período lo
   vamos a llamar "el Período Futuro" en el resto del guión.
4. Ejecuta el ciclo.

**Resultado esperado:**
- El resumen indica que el ciclo terminó **"Con advertencias"**, y menciona 1 (o más) propuesta
  sin valor UF disponible.
- Al entrar a **Facturación** y filtrar por ese período, la propuesta de "Consultoría Fantasma"
  aparece con el estado **"Pendiente UF"** (no "Pendiente").
- En la columna de monto (Total), **no aparece "$0"** — aparece el texto **"— (sin UF)"**. Esto
  es a propósito: el sistema nunca inventa un número; si no sabe cuánto vale la UF ese día, deja
  claro que el monto todavía no se conoce, en vez de mostrar un cero que se leería como "esto va
  a costar cero pesos".
- Intenta facturarla: entra a **Facturación → Facturas → "+ Nueva factura"**, filtra por este
  período y busca esta propuesta en la lista. **No vas a poder marcar su casilla de
  selección** — solo se pueden facturar propuestas en estado Pendiente, y esta está en
  Pendiente UF hasta que se pueda calcular su monto real.

☐ OK &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso G — Volver a ejecutar el mismo ciclo

**Objetivo:** comprobar que ejecutar el cálculo del mismo mes más de una vez no duplica nada.

**Pasos:**

1. Ve a **Facturación → Ciclo**.
2. Deja **Año** y **Mes** en el mismo período que usaste en el **Caso E** (el mes actual, la
   primera vez que ejecutaste el ciclo).
3. Ejecuta el ciclo de nuevo.

**Resultado esperado:**
- El resumen indica que se generaron **0 propuestas nuevas** para ese período (puede decir
  también que el proceso es idempotente o similar).
- Al entrar a **Facturación** y filtrar por ese mismo período y cliente, **"Soporte Contable
  Mensual"** y **"Auditoría Semestral Sur"** siguen apareciendo **una sola vez cada uno** — no
  hay filas repetidas.

> **Por qué:** el sistema identifica cada propuesta del ciclo por su proyecto y su período. Si
> ya existe una para ese proyecto y ese mes, no la vuelve a crear — puedes ejecutar el ciclo
> las veces que quieras sin miedo a duplicar nada.

☐ OK &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso H — Un proyecto que parte a mitad de mes

**Objetivo:** comprobar que un proyecto mensual que empieza a mitad de mes **no** se factura
ese mismo mes, sino el siguiente.

**Pasos:**

1. Crea un nuevo proyecto:
   - **Cliente:** `Contabilidad Andina SpA`
   - **Nombre:** `Marketing Digital Andina`
   - **Precio base neto:** `300000`
   - **Moneda:** `CLP`
   - **Periodicidad:** `Mensual`
   - **Día de facturación:** `20`
   - **Fecha de inicio:** el día **15 del mes actual**.
   - Guarda. (Sin acuerdo de precio.)
2. Ve a **Facturación → Ciclo**, deja **Año/Mes** en el mes actual, y ejecuta el ciclo.
3. Entra a **Facturación** y filtra por este proyecto/cliente y el mes actual.

**Resultado esperado (primera ejecución):** **no** aparece ninguna propuesta nueva de
"Marketing Digital Andina" para el mes actual.

> **Por qué:** un proyecto mensual siempre se factura el mes **siguiente** al de su inicio —
> nunca el mismo mes en que partió, y sin prorratear los días que faltan. Si un proyecto
> arranca a mitad de mes, ese primer mes parcial no se cobra.

4. Vuelve a **Facturación → Ciclo**. Esta vez, cambia **Mes** al **mes siguiente** al actual
   (si estás en Año actual y diciembre, sube el Año en 1 y usa Enero).
5. Ejecuta el ciclo.
6. Filtra **Facturación** por este proyecto y el mes siguiente.

**Resultado esperado (segunda ejecución):** ahora sí aparece una propuesta de "Marketing
Digital Andina", con fecha de facturación el **día 20** de ese mes siguiente, y montos:
- **Neto:** `$300.000`
- **IVA:** `$300.000 × 0,19 = $57.000`
- **Total:** `$357.000`

☐ OK &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso I — Un proyecto anual

**Objetivo:** comprobar que un proyecto anual solo se factura en su mes de aniversario, no en
otros meses.

**Pasos:**

1. Crea un nuevo proyecto:
   - **Cliente:** `Soportes del Sur Ltda.`
   - **Nombre:** `Auditoría Anual Sur`
   - **Precio base neto:** `2000000`
   - **Moneda:** `CLP`
   - **Periodicidad:** `Anual`
   - **Día de facturación:** el día de hoy (por ejemplo, si hoy es 8, usa `8`).
   - **Fecha de inicio:** el mismo día y mes de hoy, pero del **año pasado** (por ejemplo, si
     hoy es 8 de agosto de 2026, usa 08-08-2025).
   - Guarda. (Sin acuerdo de precio.)
2. Ve a **Facturación → Ciclo**, deja **Año/Mes** en el mes actual, y ejecuta.
3. Filtra **Facturación** por este proyecto y el mes actual.

**Resultado esperado:** aparece una propuesta de "Auditoría Anual Sur" — es su mes de
aniversario. Montos:
- **Neto:** `$2.000.000`
- **IVA:** `$2.000.000 × 0,19 = $380.000`
- **Total:** `$2.380.000`

4. Vuelve a **Facturación → Ciclo**, cambia **Mes** al mes siguiente al actual (mismo Año, o
   suma 1 si corresponde), y ejecuta.
5. Filtra **Facturación** por este proyecto y ese mes siguiente.

**Resultado esperado:** **no** aparece ninguna propuesta nueva de "Auditoría Anual Sur" — no es
su mes de aniversario.

> **Por qué:** un proyecto anual se factura una sola vez al año, en el mismo mes y día del
> contrato — a diferencia del mensual, que se repite todos los meses.

☐ OK &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso J — Asociar una factura y subir el PDF

**Objetivo:** tomar una propuesta Pendiente, asociarle un número de factura y un PDF de
respaldo, y confirmar que se puede descargar después.

**Pasos:**

1. Ve a **Facturación → Facturas → "+ Nueva factura"**.
2. Filtra por el período del **Caso E** (el mes actual en que ejecutaste el ciclo por primera
   vez) y por cliente **"Soportes del Sur Ltda."**.
3. Marca la casilla de la propuesta de **"Auditoría Semestral Sur"** (debe seguir en estado
   Pendiente, con total $714.000).
4. Completa:
   - **Número de factura:** `F-QA-0001`
   - **Fecha de la factura:** hoy.
5. Haz clic en **Guardar** (o "Crear factura").

**Resultado esperado:** la factura queda creada; si vuelves a **Facturación** y buscas esa
propuesta, ahora su estado es **Facturada** (ya no Pendiente).

6. Entra al detalle de la factura recién creada (desde el listado de Facturas, `F-QA-0001`).
7. En la sección **"PDF de respaldo"**, elige cualquier archivo PDF pequeño que tengas a mano
   (o crea uno de prueba) y haz clic en **"Subir PDF"**.

**Resultado esperado:** el nombre del archivo aparece junto a los botones **"Descargar"** y
**"Reemplazar"**.

8. Haz clic en **"Descargar"**.

**Resultado esperado:** el navegador descarga el mismo archivo que subiste, y se abre
correctamente con tu lector de PDF habitual.

☐ OK &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso K — Importar un archivo CSV

**Objetivo:** cargar facturación en lote desde un archivo, revisando fila por fila lo que el
sistema entiende antes de confirmar.

**Paso 1 — prepara el archivo.** Abre un editor de texto simple (Bloc de notas o similar), pega
exactamente este contenido y guárdalo como `prueba-qa.csv` (elige "Todos los archivos" al
guardar, para que no le agregue ".txt"):

```
rut_cliente;codigo_proyecto;descripcion;periodo;fecha_facturacion;moneda;monto_neto;observacion
76890123-6;;Revisión contable mayo;2026-05;20-05-2026;CLP;250000;Caso QA fila correcta
77456789-5;;Consultoría con fecha a revisar;2026-05;10-06-2026;CLP;180000;Caso QA fila con advertencia
78321654-K;;Cliente que no existe en el sistema;2026-05;15-05-2026;CLP;100000;Caso QA fila con error
```

(El separador entre columnas es punto y coma `;`, no coma — respeta el formato exacto. También
puedes partir de la **"Descargar plantilla CSV"** que ofrece la pantalla de importación y
editarla, si prefieres no escribir el archivo a mano.)

Las tres filas, explicadas:
- **Fila 1:** el RUT `76.890.123-6` es "Contabilidad Andina SpA" (Caso A) — un cliente real del
  sistema, con todos los datos correctos. Debería importarse sin problema.
- **Fila 2:** el RUT `77.456.789-5` es "Soportes del Sur Ltda." — correcto, pero el período
  dice mayo (`2026-05`) mientras que la fecha de facturación cae en junio
  (`10-06-2026`) — a propósito, para que el sistema avise de la inconsistencia.
- **Fila 3:** el RUT `78.321.654-K` tiene un formato válido, pero **no corresponde a ningún
  cliente creado en el sistema** — a propósito, para ver el error.

**Paso 2 — previsualiza:**

1. Ve a **Importación**.
2. Elige el archivo `prueba-qa.csv` y haz clic en **"Previsualizar"**.

**Resultado esperado en la previsualización:**
- **Fila 1:** estado **OK**. Montos: neto `$250.000`, IVA `$47.500` (`250.000 × 0,19`), total
  `$297.500`.
- **Fila 2:** estado **Advertencia**, con un mensaje indicando que la fecha de facturación no
  coincide con el período — pero **igual calcula** el monto: neto `$180.000`, IVA `$34.200`,
  total `$214.200`. Las filas con advertencia se importan igual, con ese mismo monto.
- **Fila 3:** estado **Error**, con el mensaje "No existe un cliente con RUT 78.321.654-K" (o
  similar) — **sin** montos calculados.

**Paso 3 — confirma:**

3. Haz clic en **"Confirmar importación"** y confirma en el cuadro que aparece.

**Resultado esperado:**
- El resumen de la importación indica: **3 filas en total, 2 importadas** (la OK y la de
  advertencia), **1 con error**, y el estado general **"Parcial"** (ni todo bien, ni todo mal).
- Si entras a **Facturación** y filtras por período mayo 2026 y origen **"Importación CSV"**,
  aparecen exactamente **2 propuestas nuevas** (las de las filas 1 y 2, con los montos ya
  calculados) — la fila 3 **no** generó ninguna propuesta.

☐ OK &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso L — Ver el informe de facturación

**Objetivo:** comprobar que el informe suma solo lo que efectivamente se va a facturar, y que
las propuestas sin UF y las anuladas se ven aparte, sin sumar al total.

Este caso usa el **Período Futuro** que definiste en el **Caso F** (13 meses adelante de hoy).
Antes de revisar el informe, necesitas terminar de preparar los datos de ese período:

**Paso 1 — prepara dos proyectos más:**

1. Crea un nuevo proyecto:
   - **Cliente:** `Servicios Cordillera SpA`
   - **Nombre:** `Revisión Puntual A`
   - **Precio base neto:** `150000`
   - **Moneda:** `CLP`
   - **Periodicidad:** `Mensual`
   - **Día de facturación:** `10`
   - **Fecha de inicio:** el primer día, **12 meses** hacia adelante desde hoy (un mes antes
     del Período Futuro).
   - Guarda. Sin acuerdo de precio.
2. Repite, creando un segundo proyecto igual pero llamado **`Revisión Puntual B`**, con
   **Precio base neto:** `400000` (mismos demás datos: mismo cliente, CLP, Mensual, día 10,
   misma fecha de inicio).
3. Ve a **Facturación → Ciclo**, ingresa el **Período Futuro** (el mismo Año/Mes que anotaste
   en el Caso F) y ejecuta el ciclo.
4. Entra a **Facturación**, filtra por cliente **"Servicios Cordillera SpA"** y el Período
   Futuro. Deberías ver tres propuestas: "Consultoría Fantasma" (Pendiente UF, del Caso F),
   "Revisión Puntual A" (Pendiente, $150.000/$28.500/$178.500) y "Revisión Puntual B"
   (Pendiente, $400.000/$76.000/$476.000).
5. **Anula** la propuesta de "Revisión Puntual B": marca su acción "Anular" y confirma.

**Resultado esperado del paso 5:** su estado cambia a **Anulada**.

**Paso 2 — revisa el informe:**

6. Ve a **Informes**.
7. Filtra por **Cliente:** `Servicios Cordillera SpA` y por el **Período Futuro** (Año/Mes).

**Resultado esperado:**
- La sección **Totales** (Neto / IVA / Total) muestra exactamente los montos de "Revisión
  Puntual A": **Neto $150.000, IVA $28.500, Total $178.500** — nota que dice explícitamente
  "Solo Pendiente + Facturada — excluye Pendiente UF y Anulada".
- La sección **"Cantidad de propuestas por estado"** muestra: Pendiente = 1, Pendiente UF = 1,
  Facturada = 0, Anulada = 1 (3 en total) — porque filtraste por el cliente **Servicios
  Cordillera SpA**, así que solo cuentan las tres propuestas de sus proyectos ("Consultoría
  Fantasma", "Revisión Puntual A" y "Revisión Puntual B"), aunque el ciclo del Período Futuro
  también haya generado propuestas para otros clientes.
- Aparece un aviso ámbar del tipo "1 propuesta sin valor UF, no incluida en los totales...".
- Si abres el **detalle** del informe (la tabla de filas), "Revisión Puntual B" **aparece
  listada** con su estado Anulada y su monto original ($400.000/$476.000) — se ve, pero **no**
  está sumada en los Totales de arriba.

> **Por qué:** el informe existe para responder "¿cuánto voy a facturar realmente?" — sumar una
> propuesta sin UF (que todavía no tiene un monto real) o una anulada (que no se va a facturar)
> daría un total engañoso. Por eso ambas quedan fuera de la suma, pero siguen siendo visibles y
> contables, para que nada se pierda de vista.

☐ OK &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso M — Diferencia de permisos entre Operador y Administrador

**Objetivo:** comprobar que un usuario Operador no puede ejecutar el ciclo ni anular
propuestas, mientras que un Administrador sí puede.

**Pasos:**

1. Cierra sesión y vuelve a entrar con el **usuario operador de prueba**.
2. Ve a **Facturación → Ciclo**.

**Resultado esperado:** el botón **"Ejecutar ciclo"** aparece **deshabilitado** (atenuado, no
se puede hacer clic) — puede mostrar un mensaje del tipo "Requiere el rol ADMINISTRADOR." al
pasar el mouse por encima o debajo del botón.

3. Ve a **Facturación** (listado de propuestas) y busca la propuesta de **"Marketing Digital
   Andina"** generada en el **Caso H** (la del mes siguiente, todavía Pendiente).

**Resultado esperado:** el botón **"Anular"** de esa fila también aparece **deshabilitado**
para este usuario.

4. Cierra sesión y vuelve a entrar con el **usuario administrador de prueba**.
5. Ve a **Facturación → Ciclo**.

**Resultado esperado:** el botón **"Ejecutar ciclo"** está habilitado y funciona con
normalidad.

6. Ve a **Facturación**, busca la misma propuesta de "Marketing Digital Andina" y haz clic en
   **"Anular"**, confirmando en el cuadro que aparece.

**Resultado esperado:** el botón está habilitado, la acción se completa sin error y la
propuesta queda en estado **Anulada**.

> **Por qué:** ejecutar el ciclo y anular una propuesta son acciones sensibles (afectan lo que
> se le va a cobrar a un cliente), así que están restringidas al rol Administrador. Un
> Operador puede ver todo, crear clientes/proyectos según corresponda, asociar facturas y subir
> PDFs, pero no estas dos acciones puntuales.

☐ OK &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso N — Navegar el detalle de un proyecto y agregar un descuento que se cruza (escritorio y móvil)

**Objetivo:** comprobar que se puede llegar al detalle de un proyecto y a sus descuentos desde
el listado, en un computador y en un celular, y que el aviso de fechas cruzadas se ve
correctamente en ambos anchos de pantalla.

**Pasos — en un computador (pantalla ancha):**

1. Inicia sesión en `[dirección del sistema]` con el **usuario administrador de prueba**.
2. Entra a **Proyectos** y haz clic en el **nombre** de "Soporte Contable Mensual" (el mismo
   del Caso B — a esta altura ya tiene un descuento vigente, del Caso C).
3. Verifica que se abre el **detalle del proyecto**, con dos pestañas arriba: **"Datos"** y
   **"Descuentos"**, y un botón **"Editar"** en la cabecera.
4. En la pestaña **"Datos"** (la que abre por defecto), confirma que se ven el cliente, el
   precio base, la moneda, la periodicidad y las fechas del proyecto — solo para leer, sin
   campos para escribir (el botón "Editar" de la cabecera es la única forma de modificarlos).
5. Haz clic en la pestaña **"Descuentos"**: aparece el listado de acuerdos de precio del
   proyecto (el del Caso C), con su estado ("Vigente" en verde).
6. Haz clic en **"+ Agregar descuento"**.
7. Completa un descuento porcentual con una fecha de inicio que caiga dentro del rango del
   acuerdo ya existente (igual que en el Caso D).
8. Antes de guardar, fíjate en el **aviso** que aparece cerca del campo de fechas advirtiendo
   que se cruza con el acuerdo vigente — este aviso aparece mientras completas el formulario,
   no interrumpe lo que estás escribiendo.
9. Haz clic en **Guardar**.

**Resultado esperado (computador):** el sistema rechaza el guardado con el mismo mensaje de
traslape del Caso D; la pestaña "Descuentos" sigue mostrando un solo acuerdo.

**Pasos — en un celular (o achicando la ventana del navegador hasta un ancho angosto):**

10. Repite los pasos 2 a 9 con la pantalla angosta (celular real, o la ventana del navegador
    achicada a un ancho de celular).

**Resultado esperado (celular):** la navegación se ve igual de completa que en el computador,
adaptada al espacio angosto — el menú principal pasa a una barra en la parte de abajo de la
pantalla (en vez del panel lateral del computador), el listado de acuerdos se ve como tarjetas
apiladas en vez de una tabla ancha, y el aviso de fechas cruzadas sigue siendo legible sin
tener que desplazar la pantalla hacia los costados.

> **Por qué:** este es el mismo sistema visto en dos tamaños de pantalla distintos — un
> administrativo de Helpcom puede necesitar revisar un descuento desde el celular fuera de la
> oficina, y el sistema tiene que seguir siendo usable ahí, no solo en el computador.

☐ OK (computador) &nbsp;&nbsp;☐ Falla (computador)
☐ OK (celular) &nbsp;&nbsp;☐ Falla (celular)

Observaciones: ________________________________________________

---

### Caso O — Recorrer Propuestas y Ciclo de facturación con ambos roles (escritorio y móvil)

**Objetivo:** comprobar que el listado de propuestas, su detalle, "Ejecutar ciclo" y el
historial se ven y se usan correctamente, tanto con el usuario administrador como con el
operador, en un computador y en un celular.

**Pasos — con el usuario administrador de prueba:**

1. Inicia sesión en `[dirección del sistema]` y entra a **Facturación**.
2. Filtra por **Año**, **Mes**, **Cliente**, **Estado** y **Origen** (uno por uno, o
   combinados) y confirma que el listado se actualiza según lo elegido.
3. Haz clic en **"Ver detalle"** de cualquier fila: se abre una ventana con todos los datos
   de esa propuesta (cliente, período, montos, acuerdo aplicado si tiene) — solo para leer,
   sin nada para modificar ahí. Ciérrala.
4. Si hay alguna propuesta en estado **Pendiente** (por ejemplo, del Caso E), haz clic en
   **"Anular"**, confirma en el cuadro que aparece, y verifica que su estado pasa a
   **Anulada** en el listado.
5. Ve a **"Ejecutar ciclo"** (enlace arriba del listado) y ejecútalo para el mes actual (o
   cualquier período). Confirma que el resultado muestra el estado con su color (verde si
   salió todo bien, ámbar si quedó con advertencias) y, si corresponde, el aviso de
   propuestas sin UF.
6. Ve a **"Historial de ciclos"** y confirma que aparecen todas las ejecuciones anteriores,
   cada una con su estado coloreado igual que en el paso 5.

**Pasos — con el usuario operador de prueba:**

7. Cierra sesión y vuelve a entrar con el **usuario operador de prueba**.
8. Ve a **Facturación** y confirma que el botón **"Anular"** de cualquier propuesta
   **Pendiente** se ve atenuado (deshabilitado) — igual que en el Caso M.
9. Ve a **"Ejecutar ciclo"** y confirma que el botón **"Ejecutar ciclo"** también se ve
   atenuado.

**Pasos — repetir en un celular (o la ventana del navegador achicada a un ancho angosto):**

10. Repite los pasos 1 a 9 con la pantalla angosta.

**Resultado esperado:** en computador y en celular, los tres colores de estado (verde para
Facturada/Exitosa, ámbar para Pendiente UF/Con advertencias, gris para Anulada, rojo si
alguna vez aparece un error) se distinguen claramente entre sí; los botones deshabilitados
para el operador se ven atenuados y no reaccionan al clic, con el mismo criterio ya visto en
el Caso M; en el celular, el listado se ve como tarjetas apiladas y los filtros/botones
siguen siendo fáciles de tocar con el dedo.

> **Por qué:** este caso no repite la lógica de negocio de los Casos E/G/M (eso ya está
> cubierto ahí) — revisa que la MISMA lógica se vea y se use bien con la apariencia nueva del
> sistema, con ambos roles y en los dos tamaños de pantalla.

☐ OK (computador, administrador) &nbsp;&nbsp;☐ Falla
☐ OK (computador, operador) &nbsp;&nbsp;☐ Falla
☐ OK (celular, administrador) &nbsp;&nbsp;☐ Falla
☐ OK (celular, operador) &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso P — Recorrer Facturas con ambos roles (escritorio y móvil)

**Objetivo:** comprobar que listar facturas, crear una nueva (selección de propuestas +
subtotal), ver el detalle y subir/descargar el PDF se ven y se usan correctamente con la
apariencia nueva del sistema — con ambos roles y en los dos tamaños de pantalla.

**Datos de este caso:** si tu ambiente todavía tiene la factura **F-2026-0001** (de pruebas
anteriores, ya con un PDF subido) y una propuesta **Pendiente** disponible en el listado de
Propuestas, úsalas directamente en los pasos de abajo. Si no existen, cualquier factura con
PDF y cualquier propuesta en estado Pendiente sirven igual — el caso no depende de esos datos
puntuales, solo de que existan equivalentes.

**Pasos — con el usuario administrador de prueba:**

1. Inicia sesión en `[dirección del sistema]` y entra a **Facturación → Facturas**.
2. Confirma que el listado se ve bien y que la columna "PDF" distingue con claridad las
   facturas que ya tienen uno de las que no.
3. Abre el detalle de la factura **F-2026-0001** (o la que tengas con PDF). Confirma que se
   ve el número, la fecha, el cliente, las propuestas asociadas y el total. Haz clic en
   **"Descargar"** y confirma que el PDF se descarga correctamente.
4. **Verificación puntual (cambio reciente):** en la barra de direcciones, cambia el número
   final de la URL del detalle por uno que no exista (por ejemplo, si estabas en
   `.../facturas/1`, cambia a `.../facturas/99999`). Confirma que aparece el mensaje de error
   ("No existe una factura...") **solo con el mensaje**, sin ningún enlace "Volver a
   facturas" arriba — antes si aparecía. Vuelve a **Facturas** desde el menú (no hay enlace
   directo en esta pantalla de error, es lo que se está confirmando).
5. Ve a **"+ Nueva factura"**. Elige una propuesta en estado **Pendiente** del listado (por
   ejemplo, la que tenga el id 5, si sigue disponible) y confirma que aparece en el resumen
   de "Propuestas seleccionadas" de abajo, con el **subtotal** actualizado correctamente.
   **No completes el formulario todavía.**
6. **Verificación puntual (cambio reciente) — sin cerrar esta pestaña:** abre una **segunda
   pestaña** en el mismo navegador, con la misma sesión, y ve a **Facturación** (el listado
   de propuestas). Busca esa MISMA propuesta que elegiste en el paso 5 y haz clic en
   **"Anular"**, confirmando en el cuadro que aparece.
7. Vuelve a la primera pestaña (la de "Nueva factura", que sigue con la propuesta ya anulada
   todavía seleccionada). Completa **N° de factura** y **Fecha** con cualquier valor y haz
   clic en **"Crear factura"**.
8. Confirma que aparece un mensaje de error explicando que esa propuesta ya no se puede
   facturar, y fíjate en dónde queda el botón **"Refrescar listado"**: debe verse **debajo y
   fuera** del recuadro rojo del mensaje, no encajonado dentro de él — antes quedaba adentro.
   Haz clic en **"Refrescar listado"** y confirma que el listado de arriba se actualiza (la
   propuesta anulada ya no aparece seleccionable).

**Pasos — con el usuario operador de prueba:**

9. Cierra sesión y vuelve a entrar con el **usuario operador de prueba**.
10. Repite el paso 5 (elegir una propuesta Pendiente y ver el subtotal) y crea la factura
    completa esta vez (con una propuesta que sigas teniendo disponible) — confirma que el
    operador puede crear facturas y subir/descargar PDF sin restricciones (a diferencia de
    Ciclo, Facturas está permitida para ambos roles por igual, Caso M no aplica acá).
11. Sube un PDF a la factura recién creada y confirma que queda guardado (columna "PDF" pasa
    a "Sí" en el listado).

**Pasos — repetir en un celular (o la ventana del navegador achicada a un ancho angosto):**

12. Repite los pasos 1 a 3 con la pantalla angosta: confirma que el listado se ve como
    tarjetas apiladas y que el detalle de la factura sigue siendo legible y usable.

**Resultado esperado:** en computador y en celular, con ambos roles: el listado, el detalle y
el flujo de crear factura se ven con la misma apariencia de marca que el resto del sistema; el
mensaje de error de una factura inexistente aparece **solo**, sin el enlace "Volver" (paso 4);
el botón "Refrescar listado" ante una propuesta que dejó de ser facturable aparece **fuera**
del recuadro de error, no adentro (paso 8); subir y descargar el PDF funciona igual para
ambos roles.

> **Por qué:** los pasos 4 y 8 no son solo estética — verifican dos cambios de comportamiento
> reales que trajo el rediseño de esta pantalla (`docs/frontend.md` §17): la cabecera del
> detalle de factura ahora sigue el mismo patrón que el detalle de Proyecto (no muestra el
> enlace "Volver" mientras carga o si hay un error), y el aviso de "Refrescar listado" se
> reubicó para no quedar encajonado dentro del recuadro de error. Ninguno de los dos cambia
> qué pasa al hacer clic — solo dónde y cómo se ve — pero conviene dejarlos vigilados
> explícitamente, igual que se hizo con la navegación de Proyectos (Caso N) y de Propuestas/
> Ciclo (Caso O).

☐ OK (computador, administrador) &nbsp;&nbsp;☐ Falla
☐ OK (computador, operador) &nbsp;&nbsp;☐ Falla
☐ OK (celular, administrador) &nbsp;&nbsp;☐ Falla
☐ OK (celular, operador) &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso Q — Recorrer Importación CSV con ambos roles (escritorio y móvil)

**Objetivo:** comprobar que el flujo de importar un CSV (elegir archivo → previsualizar →
confirmar) y el historial de importaciones se ven y se usan correctamente con la apariencia
nueva del sistema, con especial atención a algo propio de esta pantalla: la **paginación de la
previsualización se arma en el propio navegador**, no pidiéndole página por página al
servidor como en el resto de los listados — por eso conviene revisarla cruzando de página
explícitamente, no solo mirando la primera.

**Datos de este caso:** necesitas un archivo CSV de prueba con el formato de
`modelo-de-datos.md` §6 (encabezado con los nombres de columna exactos, separador `;`, decimal
con `.`) y, del orden de **60 filas**, de modo que quepan en 2 páginas (la previsualización
muestra 50 filas por página):
- la mayoría (unas 55) con datos válidos y RUTs de clientes que **sí existan** en tu ambiente,
  para que queden en estado **OK**;
- algunas (2 o 3) con una fecha que **no calce con el período** de esas filas, para que queden
  en estado **ADVERTENCIA**;
- algunas (1 o 2) con un RUT con dígito verificador válido pero que **no exista** como cliente,
  para que queden en estado **ERROR**.

Si ya tienes un archivo así de una revisión anterior, úsalo directamente. Si no, arma uno
nuevo con esa forma — el caso no depende de un archivo puntual, solo de que el mix de filas
produzca los tres estados y cruce a una segunda página.

**Pasos — con el usuario administrador de prueba:**

1. Inicia sesión en `[dirección del sistema]` y entra a **Importación**.
2. Elige el archivo CSV de prueba y haz clic en **"Previsualizar"**. Confirma que aparece la
   tabla de previsualización con las 4 tarjetas de resumen arriba (total, OK, advertencia,
   error) con los colores nuevos (verde/ámbar/rojo).
3. Revisa la **primera página** de la tabla: confirma que las filas OK, ADVERTENCIA y ERROR se
   distinguen con claridad con la paleta nueva (mensaje de fila en verde/ámbar/rojo según
   corresponda).
4. **Paso distintivo de esta pantalla:** en el pie de la tabla, haz clic en el control de
   paginación para pasar de la **página 1 a la página 2**. Confirma que el control se ve y se
   usa igual que la paginación del resto de los listados (mismo componente), aunque acá no
   dispara ninguna llamada nueva al servidor — la tabla completa ya se cargó de una vez.
   Confirma que en la página 2 también se ven filas con los tres estados (o los que
   correspondan según cómo armaste el archivo) con la paleta correcta.
5. Haz clic en **"Confirmar importación"**. Confirma que el cuadro de confirmación explica
   cuántas filas se importarán y avisa que confirmar **vuelve a validar el archivo completo**
   (el resultado puede diferir de la previsualización si algo cambió mientras tanto). Confirma
   la importación.
6. Confirma que aparece el resumen de resultado (filas importadas, filas con error) con la
   apariencia nueva, y que el enlace "Ver propuestas importadas" funciona.
7. Ve a **"Ver historial de importaciones"**. Confirma que el enlace "Volver a importar" usa el
   mismo patrón (flecha + texto) que el resto de las pantallas con listado anidado, y que la
   importación recién hecha aparece en el listado con su estado.

**Pasos — con el usuario operador de prueba:**

8. Cierra sesión y vuelve a entrar con el **usuario operador de prueba**. Repite los pasos 2 a
   4 (previsualizar y cruzar de página 1 a 2) y confirma que el operador puede hacerlo sin
   restricciones.

**Pasos — repetir en un celular (o la ventana del navegador achicada a un ancho angosto):**

9. Repite los pasos 2 a 4 con la pantalla angosta: confirma que la tabla de previsualización se
   ve como tarjetas apiladas, que los tres estados se distinguen igual de bien, y que cruzar de
   página 1 a 2 funciona igual.

**Resultado esperado:** en computador y en celular, con ambos roles: el flujo completo de
importación se ve con la misma apariencia de marca que el resto del sistema; las filas OK,
ADVERTENCIA y ERROR se distinguen con la paleta nueva en ambas páginas de la previsualización;
el control de paginación de la previsualización es el mismo componente que el resto de los
listados, aunque pagina en el propio navegador y no contra el servidor; el diálogo de
confirmación avisa la re-validación; el resumen de resultado y el historial se ven correctos.

> **Por qué:** la previsualización de Importación CSV es la única pantalla del sistema donde la
> paginación no pide una página nueva al servidor — la tabla completa ya llegó con la respuesta
> de "previsualizar" y el recorte en páginas se hace en el navegador (`docs/frontend.md` §7.3).
> Visualmente usa el mismo componente `Paginacion` que los listados paginados por API, así que
> a simple vista no se nota la diferencia — por eso el caso pide cruzar de página
> explícitamente: es la única forma de confirmar que las filas de la página 2 (que nunca
> pasaron por una llamada nueva) también se ven con la paleta nueva.

☐ OK (computador, administrador) &nbsp;&nbsp;☐ Falla
☐ OK (computador, operador) &nbsp;&nbsp;☐ Falla
☐ OK (celular, administrador) &nbsp;&nbsp;☐ Falla
☐ OK (celular, operador) &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso R — Recorrer el Panel principal (dashboard) con ambos roles (escritorio y móvil)

**Objetivo:** comprobar que el Panel principal (la primera pantalla que se ve al iniciar
sesión) muestra sus tarjetas con datos reales y con la apariencia nueva, con especial atención a
algo propio de esta pantalla: varias tarjetas están calculadas a partir de datos que, en la
práctica, casi nunca están parejos — hay que confirmar que lo que se ve es **honesto** con esa
realidad, no que se vea "bonito" escondiendo lo que sobra o falta.

**Datos de este caso:** no depende de un archivo ni de datos que armes vos — usa lo que ya haya
cargado en el sistema (propuestas de ciclo e importaciones CSV, de casos anteriores). Sí conviene
que haya:
- al menos una propuesta con un acuerdo de **descuento porcentual**, una con **descuento por
  monto** y, si es posible, una con **precio pactado** (para ver los 3 tipos en la tarjeta de
  descuentos);
- al menos una propuesta **sin proyecto asociado** (las que vienen de una importación CSV sin
  código de proyecto cuentan) y al menos una **con** proyecto — para ver el bloque "sin
  clasificar"/"sin proyecto" junto a los reales;
- al menos una propuesta en estado **Pendiente UF** (sin valor UF todavía) en el sistema.

Si tu ambiente no tiene alguno de estos, no es un problema del caso — anota qué faltaba y seguí
igual con lo que sí haya.

**Pasos — con el usuario administrador de prueba:**

1. Inicia sesión en `[dirección del sistema]`. Confirma que caes directo en el **Panel**
   (la pantalla de inicio) y no en una pantalla en blanco ni en un saludo sin nada más.
2. Espera a que las tarjetas terminen de cargar (un instante breve — cada una tiene su propio
   indicador de "Cargando…"). Confirma que ninguna tarjeta "salta" de tamaño de golpe al
   terminar de cargar (el espacio ya estaba reservado desde antes).
3. Revisa la tarjeta **"Descuentos realizados"**: confirma que muestra un monto de descuento
   porcentual, uno de descuento por monto, y un **total** que es la suma de esos dos — y que el
   monto de **precio pactado** aparece en una línea aparte, en un tono ámbar distinto, **sin**
   estar sumado al total de arriba (si no hay ninguna propuesta con precio pactado, esa línea
   puede mostrar $0 — igual debe verse separada del total).
4. Revisa la tarjeta **"Por tipo de servicio, por mes"**: confirma que el gráfico muestra una
   categoría **"Sin clasificar"** junto a los tipos de servicio reales — en un tono gris/neutro,
   distinto de los colores de marca de las categorías reales. **Paso distintivo:** si la mayoría
   de las propuestas del sistema no tienen proyecto asociado (algo esperable si hubo una
   importación CSV grande), "Sin clasificar" va a ser, con toda razón, la porción más grande del
   gráfico — confirma que se ve así de grande y **no** que esté recortada, escondida al fondo, o
   ausente del todo.
5. Revisa la tarjeta **"Por proyecto"**: mismo chequeo que el paso anterior, pero con la barra
   **"Sin proyecto"** en vez de "Sin clasificar".
6. Revisa la tarjeta **"Comparación de períodos"**: confirma que "Mes contra mes" muestra un
   porcentaje (positivo en verde o negativo en rojo) **solo si** el sistema tiene datos de dos
   meses calendario consecutivos; si no los tiene, confirma que en su lugar aparece un mensaje
   explicando que no hay dos meses seguidos para comparar — **nunca** debe aparecer un
   "-100%" (esa cifra, si aparece, sería un indicio de que se está comparando contra un mes sin
   datos en vez de contra el mes anterior real).
7. En la misma tarjeta, confirma que "Año contra año" muestra el mensaje de que no hay datos del
   año anterior (es lo esperable si el sistema no tiene todavía un año completo de historia) —
   tampoco acá debería verse nunca un "-100%".
8. Revisa la tarjeta **"Por cliente"**: confirma que aparece cada cliente con propuestas, su
   monto total y la cantidad de propuestas por estado (con los mismos colores de estado que ya
   viste en Propuestas).
9. Si el sistema tiene alguna propuesta **Pendiente UF**, confirma que aparece un aviso ámbar
   arriba de las tarjetas indicando cuántas hay, con un enlace a verlas en Propuestas — y que esa
   cantidad **no** está incluida en ninguno de los montos de las tarjetas de arriba (ni en
   Descuentos, ni en Por tipo de servicio, ni en Por proyecto, ni en Por cliente). Si no hay
   ninguna, el aviso simplemente no debe aparecer.

**Pasos — con el usuario operador de prueba:**

10. Cierra sesión y vuelve a entrar con el **usuario operador de prueba**. Repite los pasos 2 a 9
    y confirma que el operador ve exactamente lo mismo (el Panel no distingue por rol).

**Pasos — repetir en un celular (o la ventana del navegador achicada a un ancho angosto):**

11. Repite los pasos 2 a 9 con la pantalla angosta: confirma que las tarjetas se apilan una debajo
    de otra (no una al lado de la otra como en escritorio) y que los gráficos se achican al ancho
    de la pantalla sin desbordar ni cortarse.

**Resultado esperado:** en computador y en celular, con ambos roles: las 6 tarjetas del Panel
cargan sin saltos de tamaño; los descuentos muestran sus 3 tipos con el pactado siempre aparte;
"Sin clasificar"/"Sin proyecto" se ven como una porción más, del tamaño real que les corresponda,
nunca escondidas; la comparación de períodos nunca inventa un "-100%" cuando no hay un período
real con el que comparar; y los Pendiente UF quedan siempre contados aparte, nunca sumados.

> **Por qué:** el Panel es la única pantalla del sistema donde varios números salen de calcular
> — no de mostrar tal cual — datos que le llegan al navegador (`docs/frontend.md` §21). Con un
> dato de sistema real y desparejo (una importación CSV grande sin proyecto, meses sin actividad
> entre uno y otro), es fácil que un cálculo mal pensado dé un resultado que se vea "raro" sin
> ser, técnicamente, un error — por eso este caso pide mirar la FORMA de lo que se muestra
> (¿el bloque residual está visible?, ¿el mensaje es honesto cuando falta un dato?), no un
> número puntual que va a cambiar con el tiempo.

☐ OK (computador, administrador) &nbsp;&nbsp;☐ Falla
☐ OK (computador, operador) &nbsp;&nbsp;☐ Falla
☐ OK (celular, administrador) &nbsp;&nbsp;☐ Falla
☐ OK (celular, operador) &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

### Caso S — Reprocesar UF de una propuesta Pendiente UF (ambos roles)

**Objetivo:** comprobar que el botón "Reprocesar UF" de Propuestas funciona correctamente en
sus dos resultados posibles, y que nunca deja a la fila en un estado confuso o a mitad de
camino.

**Datos de este caso:** necesitas al menos una propuesta en estado **Pendiente UF** — si no
tienes ninguna a mano, el Caso O explica cómo llegar a una (un proyecto en UF sin que la UF de
su fecha esté disponible). **Importante — este botón depende de un servicio externo real (el
valor de la UF publicado por el Banco Central), así que su resultado NO es predecible de
antemano:** puede que la UF ya esté disponible (la propuesta se completa) o que todavía no (la
propuesta sigue igual) — **ambos son resultados correctos**, este caso no falla por cuál de los
dos ocurra, solo por que la pantalla no reaccione como se describe abajo para el que sí ocurra.

**Pasos — con el usuario administrador de prueba:**

1. Inicia sesión y entra a **Facturación**. Si hace falta, usa el filtro **Estado → Pendiente
   UF** para encontrar más fácil una fila así.
2. Confirma que la fila **Pendiente UF** tiene un botón **"Reprocesar UF"** — y que una fila en
   cualquier otro estado (Pendiente, Facturada, Anulada) **no** lo tiene.
3. Haz clic en **"Reprocesar UF"**. Mientras la acción está en curso, confirma que el botón
   cambia a **"Reprocesando…"** y no reacciona a más clics.
4. Cuando termina, revisa cuál de los dos resultados ocurrió:
   - **Si la UF se consiguió:** aparece un aviso de éxito, y la fila ahora muestra un monto
     real en Neto/IVA/Total (ya no "— (sin UF)") con el estado cambiado a **Pendiente**.
   - **Si la UF todavía no está disponible:** aparece un aviso — en un tono ámbar, ni de éxito
     ni de error grave — que explica que no se pudo obtener la UF de esa fecha y que la
     propuesta sigue pendiente. La fila queda exactamente igual que antes (sigue Pendiente UF,
     sigue con "— (sin UF)"). **Este NO es un error del sistema** — es la respuesta honesta
     cuando la UF de verdad no está publicada todavía.
5. Cualquiera de los dos resultados que haya salido, confirma que puedes repetir el paso 3 las
   veces que quieras sobre la misma fila (u otra) sin que nada se rompa ni quede duplicado.

**Pasos — con el usuario operador de prueba:**

6. Cierra sesión y vuelve a entrar con el **usuario operador de prueba**. Ve a **Facturación** y
   confirma que el botón **"Reprocesar UF"** de cualquier propuesta **Pendiente UF** se ve
   atenuado (deshabilitado) — igual que "Anular" en el Caso O.

**Pasos — repetir en un celular (o la ventana del navegador achicada a un ancho angosto):**

7. Repite los pasos 2 a 4 con la pantalla angosta: confirma que el botón y los avisos se ven
   igual de claros en la vista de tarjetas apiladas.

**Resultado esperado:** en computador y en celular: el botón aparece solo en filas Pendiente UF;
habilitado para el administrador, atenuado para el operador; el resultado de la acción siempre
se comunica con claridad — éxito real cuando la UF se completó, un aviso honesto (no un error ni
un éxito falso) cuando sigue sin estar disponible — y la fila nunca queda en un estado a medias.

> **Por qué:** a diferencia de "Anular" (una acción que siempre tiene el mismo resultado si se
> permite), "Reprocesar UF" depende de un dato externo real que puede o no estar disponible en
> el momento — el sistema no puede prometer que va a funcionar, solo prometer que va a decir la
> verdad sobre lo que pasó. Este caso existe para confirmar eso: que un resultado "no pasó nada"
> se distingue con claridad de un error real, y de un éxito real (`docs/frontend.md` §5.8).

☐ OK (computador, administrador) &nbsp;&nbsp;☐ Falla
☐ OK (computador, operador) &nbsp;&nbsp;☐ Falla
☐ OK (celular, administrador) &nbsp;&nbsp;☐ Falla
☐ OK (celular, operador) &nbsp;&nbsp;☐ Falla

Observaciones: ________________________________________________

---

## 6. Qué hacer si algo falla

Si en algún caso el resultado no coincide con lo esperado, anota lo siguiente para que el
equipo técnico pueda reproducirlo (entre más detalle, más rápido se soluciona):

1. **Qué caso y qué paso** — por ejemplo, "Caso E, paso 5".
2. **En qué pantalla estabas** — el nombre de la sección (por ejemplo, "Facturación → Ciclo") y,
   si puedes, la dirección que aparece en la barra del navegador.
3. **Qué datos usaste exactamente** — copia los valores que ingresaste, tal como los escribiste.
4. **Qué esperabas que pasara** — según este guión.
5. **Qué pasó realmente** — describe lo que viste. Si apareció un mensaje de error, **copia el
   texto completo del mensaje** (no hace falta que lo entiendas, solo que lo transcribas tal
   cual).
6. **Una captura de pantalla**, si puedes tomarla — ayuda muchísimo, sobre todo si el problema
   es algo visual o un mensaje de error.
7. **Con qué usuario** estabas conectado (administrador u operador de prueba).

No hace falta que entiendas la causa del problema ni que uses términos técnicos — con esta
información el equipo técnico puede reproducir el caso exacto y revisarlo.

---

*Guión construido sobre 9 de los 10 flujos de la suite automática (`docs/qa.md`) — falta
traducir el reproceso de UF, agregado después — más el Caso R (Panel/dashboard, R9) y el Caso S
(botón "Reprocesar UF" en Propuestas), ninguno de los dos con flujo E2E de backend equivalente.
Si el sistema cambia (nuevas pantallas, campos o reglas de negocio),
este documento debe actualizarse junto con `docs/qa.md` y los documentos técnicos de `docs/`.*
