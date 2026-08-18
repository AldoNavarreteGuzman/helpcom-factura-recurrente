export type EstadoVigencia = "VIGENTE" | "FUTURO" | "PASADO";

/**
 * Vigente/futuro/pasado respecto de hoy — cálculo orientativo en el cliente (el backend no
 * lo provee). Compara strings ISO `AAAA-MM-DD` lexicográficamente, válido porque ese formato
 * ordena igual que las fechas que representa.
 */
export function calcularEstadoVigencia(
  fechaInicio: string,
  fechaTermino: string,
  hoyIso: string = new Date().toISOString().slice(0, 10),
): EstadoVigencia {
  if (hoyIso < fechaInicio) {
    return "FUTURO";
  }
  if (hoyIso > fechaTermino) {
    return "PASADO";
  }
  return "VIGENTE";
}

/**
 * `true` si los rangos `[aInicio, aTermino]` y `[bInicio, bTermino]` se superponen (bordes
 * inclusive, igual que la exclusion constraint del backend). Solo orientativo: el backend es
 * la única autoridad real sobre el solape (arquitectura-tecnica.md — condiciones de carrera).
 */
export function seSuperponen(
  aInicio: string,
  aTermino: string,
  bInicio: string,
  bTermino: string,
): boolean {
  return aInicio <= bTermino && aTermino >= bInicio;
}

/**
 * Último día de la vigencia de N meses pactados a partir de `fechaInicioIso`: replica
 * `fechaInicio.plusMonths(meses).minusDays(1)` de `AcuerdoPrecioServicio` (backend), incluido
 * el "clamp" de `LocalDate.plusMonths` al último día del mes cuando el día de inicio no
 * existe en el mes resultante (p. ej. 31 de enero + 1 mes → 28 o 29 de febrero, no marzo).
 * Solo se usa como vista previa: lo que se envía es `mesesPactados`, y el backend calcula la
 * fecha real — esto nunca es la fuente de verdad.
 */
export function calcularTerminoDesdeMeses(fechaInicioIso: string, meses: number): string {
  const [anioTexto, mesTexto, diaTexto] = fechaInicioIso.split("-");
  const anio = Number(anioTexto);
  const mesIndice0 = Number(mesTexto) - 1;
  const dia = Number(diaTexto);

  const totalMeses = anio * 12 + mesIndice0 + meses;
  const anioResultado = Math.floor(totalMeses / 12);
  const mesResultado0 = totalMeses % 12;
  const ultimoDiaDelMesResultado = new Date(anioResultado, mesResultado0 + 1, 0).getDate();

  const fechaMasMeses = new Date(
    anioResultado,
    mesResultado0,
    Math.min(dia, ultimoDiaDelMesResultado),
  );
  fechaMasMeses.setDate(fechaMasMeses.getDate() - 1);

  const rellenar = (valor: number) => String(valor).padStart(2, "0");
  return `${fechaMasMeses.getFullYear()}-${rellenar(fechaMasMeses.getMonth() + 1)}-${rellenar(fechaMasMeses.getDate())}`;
}
