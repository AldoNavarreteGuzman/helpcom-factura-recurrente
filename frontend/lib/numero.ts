const LOCALE = "es-CL";

const formateador = new Intl.NumberFormat(LOCALE, { maximumFractionDigits: 4 });

/** Formato es-CL de solo lectura: punto de miles, coma decimal — usar al perder el foco. */
export function formatearNumeroEsCl(valor: number): string {
  return formateador.format(valor);
}

/**
 * Interpreta un número escrito en es-CL (punto de miles, coma decimal) o en formato "crudo"
 * (solo dígitos y un punto decimal, como lo tipearía alguien sin pensar en el separador).
 * `null` si no es un número válido.
 */
export function parsearNumeroEsCl(texto: string): number | null {
  const limpio = texto.trim();
  if (limpio === "") {
    return null;
  }
  const normalizado = limpio.includes(",") ? limpio.replace(/\./g, "").replace(",", ".") : limpio;
  const valor = Number(normalizado);
  return Number.isFinite(valor) ? valor : null;
}
