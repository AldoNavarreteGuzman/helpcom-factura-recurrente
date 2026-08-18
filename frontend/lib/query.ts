type ValorParametro = string | number | boolean | undefined | null;
type ParametrosQuery = Record<string, ValorParametro | ValorParametro[]>;

/**
 * Arma un query string omitiendo valores vacíos, `undefined` o `null`. Incluye el `?` inicial.
 * Un valor arreglo (p. ej. `estados: ["PENDIENTE", "FACTURADA"]`) se serializa como parámetros
 * REPETIDOS (`estados=PENDIENTE&estados=FACTURADA`) — la forma estándar en que Spring MVC
 * enlaza un `@RequestParam List<T>` sin ambigüedad, a diferencia de una lista separada por
 * comas en un solo parámetro (que también funciona en Spring, pero repetir el parámetro es
 * la forma que no depende de un converter específico).
 */
export function construirQueryString(parametros: ParametrosQuery): string {
  const query = new URLSearchParams();
  for (const [clave, valor] of Object.entries(parametros)) {
    if (Array.isArray(valor)) {
      for (const elemento of valor) {
        if (elemento !== undefined && elemento !== null && elemento !== "") {
          query.append(clave, String(elemento));
        }
      }
      continue;
    }
    if (valor !== undefined && valor !== null && valor !== "") {
      query.set(clave, String(valor));
    }
  }
  const texto = query.toString();
  return texto ? `?${texto}` : "";
}
