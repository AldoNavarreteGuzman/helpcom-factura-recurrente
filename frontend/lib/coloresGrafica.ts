/**
 * Hex tomados 1:1 de `tailwind.config.ts` (`colors.marca`/`colors.estado`) — Recharts pinta SVG
 * directo (`fill`/`stroke`), no puede leer clases de Tailwind, así que estos valores deben
 * mantenerse sincronizados a mano si la paleta de marca cambia (`docs/frontend.md` R9).
 */
export const COLOR_MARCA_AZUL = "#066EE7";
export const COLOR_MARCA_AZUL_700 = "#0A57C2";
export const COLOR_MARCA_CELESTE = "#06BBFF";
export const COLOR_TENUE = "#9AA3B2";
export const COLOR_SUTIL = "#5B6472";
export const COLOR_LINEA = "#E6EAF1";
export const COLOR_ESTADO_FACTURADA = "#128A45";
export const COLOR_ESTADO_SIN_UF = "#C77700";
export const COLOR_ESTADO_ERROR = "#C62F42";

/** Paleta cíclica para categorías reales — nunca se usa para el bucket "Sin clasificar"/"Sin
 * proyecto", que siempre pinta {@link COLOR_TENUE} (deliberadamente neutro/apagado: es un
 * residuo de clasificación, no una categoría de negocio real). */
const PALETA_CATEGORIAS = [COLOR_MARCA_AZUL, COLOR_MARCA_CELESTE, COLOR_MARCA_AZUL_700];

export function colorParaCategoria(
  nombre: string,
  indice: number,
  nombreSinClasificar: string,
): string {
  if (nombre === nombreSinClasificar) {
    return COLOR_TENUE;
  }
  return PALETA_CATEGORIAS[indice % PALETA_CATEGORIAS.length]!;
}
