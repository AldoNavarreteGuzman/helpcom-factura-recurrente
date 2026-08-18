import { esCalculable } from "./propuestas";
import { NOMBRES_MES } from "./etiquetas";
import type { EstadoPropuesta } from "@/types/dominio";
import type { Proyecto } from "@/types/proyecto";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

export const SIN_CLASIFICAR = "Sin clasificar";
export const SIN_PROYECTO = "Sin proyecto";

function redondearClp(valor: number): number {
  return Math.round(valor);
}

function claveMes(anio: number, mes: number): string {
  return `${anio}-${String(mes).padStart(2, "0")}`;
}

function etiquetaMes(anio: number, mes: number): string {
  return `${NOMBRES_MES[mes - 1]!.slice(0, 3)} ${anio}`;
}

/**
 * "Lo que habría costado sin ningún acuerdo aplicado" — el precio base convertido a CLP con la
 * UF que usó la propia propuesta, o el precio base directo si ya está en CLP. `null` si la
 * propuesta necesitaba UF pero no la tiene (no debería pasar en una propuesta `esCalculable`,
 * pero se cubre por si acaso — nunca se inventa un valor).
 */
function netoSinAcuerdo(propuesta: PropuestaFacturacion): number | null {
  if (propuesta.monedaOrigen === "CLP") {
    return propuesta.precioBaseNeto;
  }
  if (propuesta.valorUf == null) {
    return null;
  }
  return redondearClp(propuesta.precioBaseNeto * propuesta.valorUf);
}

export interface KpiDescuentos {
  porcentaje: number;
  monto: number;
  totalPorcentajeYMonto: number;
  pactado: number;
}

/**
 * Descuento REALIZADO por tipo de acuerdo = precio de lista ({@link netoSinAcuerdo}) menos lo
 * que costó de verdad (`netoClp`) — la misma resta sirve para los 3 tipos de acuerdo, sin
 * reimplementar las ramas de `CalculadoraFacturacion` (backend): no hace falta reproducir CÓMO
 * se llegó al monto, solo comparar el resultado final contra el precio de lista.
 * `PRECIO_PACTADO` se devuelve APARTE — nunca se suma a `totalPorcentajeYMonto` (decisión de
 * negocio ya tomada, docs/frontend.md R9). Propuestas sin acuerdo, o no {@link esCalculable}
 * (`PENDIENTE_UF`/`ANULADA`), no aportan nada.
 */
export function calcularKpiDescuentos(propuestas: PropuestaFacturacion[]): KpiDescuentos {
  let porcentaje = 0;
  let monto = 0;
  let pactado = 0;

  for (const propuesta of propuestas) {
    if (!esCalculable(propuesta.estado) || propuesta.acuerdoTipo == null) {
      continue;
    }
    const base = netoSinAcuerdo(propuesta);
    if (base == null) {
      continue;
    }
    const descuentoRealizado = base - propuesta.netoClp;
    if (propuesta.acuerdoTipo === "DESCUENTO_PORCENTAJE") {
      porcentaje += descuentoRealizado;
    } else if (propuesta.acuerdoTipo === "DESCUENTO_MONTO") {
      monto += descuentoRealizado;
    } else {
      pactado += descuentoRealizado;
    }
  }

  return { porcentaje, monto, totalPorcentajeYMonto: porcentaje + monto, pactado };
}

export interface MesSerie {
  anio: number;
  mes: number;
  etiqueta: string;
}

export interface CategoriaSerie {
  nombre: string;
  valores: number[];
}

export interface SeriePorTipoServicio {
  meses: MesSerie[];
  categorias: CategoriaSerie[];
}

/**
 * Serie mensual de neto calculable agrupada por tipo de servicio del proyecto (join client-side
 * `propuesta.proyectoId` → `proyecto.tipoServicioId`, no hay endpoint de agregación para esto
 * hoy — ver docs/frontend.md R9). El rango de meses va del primero al último período CON datos,
 * SIN saltarse los huecos intermedios (un mes sin propuestas calculables queda en 0, visible,
 * no se omite de la serie) — así la tendencia se lee completa, con sus huecos reales.
 * `SIN_CLASIFICAR` agrupa tanto las propuestas sin proyecto (`proyectoId == null`, el caso de
 * TODA la importación CSV histórica) como un proyecto sin tipo de servicio asignado — se
 * muestra como una categoría más, nunca se omite ni se esconde.
 */
export function calcularSeriePorTipoServicio(
  propuestas: PropuestaFacturacion[],
  proyectos: Proyecto[],
): SeriePorTipoServicio {
  const tipoServicioPorProyecto = new Map<number, string>();
  for (const proyecto of proyectos) {
    tipoServicioPorProyecto.set(proyecto.id, proyecto.tipoServicioNombre ?? SIN_CLASIFICAR);
  }

  const calculables = propuestas.filter((propuesta) => esCalculable(propuesta.estado));
  if (calculables.length === 0) {
    return { meses: [], categorias: [] };
  }

  const clavesConDatos = Array.from(
    new Set(calculables.map((propuesta) => claveMes(propuesta.periodoAnio, propuesta.periodoMes))),
  ).sort();
  const [primerAnio, primerMes] = clavesConDatos[0]!.split("-").map(Number) as [number, number];
  const [ultimoAnio, ultimoMes] = clavesConDatos[clavesConDatos.length - 1]!.split("-").map(
    Number,
  ) as [number, number];

  const meses: MesSerie[] = [];
  let anio = primerAnio;
  let mes = primerMes;
  while (anio < ultimoAnio || (anio === ultimoAnio && mes <= ultimoMes)) {
    meses.push({ anio, mes, etiqueta: etiquetaMes(anio, mes) });
    mes += 1;
    if (mes > 12) {
      mes = 1;
      anio += 1;
    }
  }
  const indicePorClave = new Map(meses.map((m, indice) => [claveMes(m.anio, m.mes), indice]));

  const seriesPorCategoria = new Map<string, number[]>();
  function obtenerSerie(nombre: string): number[] {
    let serie = seriesPorCategoria.get(nombre);
    if (!serie) {
      serie = new Array(meses.length).fill(0) as number[];
      seriesPorCategoria.set(nombre, serie);
    }
    return serie;
  }

  for (const propuesta of calculables) {
    const nombreCategoria =
      propuesta.proyectoId == null
        ? SIN_CLASIFICAR
        : (tipoServicioPorProyecto.get(propuesta.proyectoId) ?? SIN_CLASIFICAR);
    const indice = indicePorClave.get(claveMes(propuesta.periodoAnio, propuesta.periodoMes));
    if (indice == null) {
      continue;
    }
    obtenerSerie(nombreCategoria)[indice] += propuesta.netoClp;
  }

  const categorias = Array.from(seriesPorCategoria.entries())
    .map(([nombre, valores]) => ({ nombre, valores }))
    .sort((a, b) => {
      if (a.nombre === SIN_CLASIFICAR) {
        return 1;
      }
      if (b.nombre === SIN_CLASIFICAR) {
        return -1;
      }
      return a.nombre.localeCompare(b.nombre, "es");
    });

  return { meses, categorias };
}

export interface FilaProyecto {
  proyectoId: number | null;
  nombre: string;
  netoClp: number;
  cantidad: number;
}

/** Agrupa el neto calculable por proyecto — `proyectoId == null` (toda la importación CSV
 * histórica) queda como su propia fila "Sin proyecto", visible y ordenada como cualquier otra,
 * nunca escondida al fondo ni omitida. */
export function calcularPorProyecto(propuestas: PropuestaFacturacion[]): FilaProyecto[] {
  const filas = new Map<string, FilaProyecto>();
  for (const propuesta of propuestas) {
    if (!esCalculable(propuesta.estado)) {
      continue;
    }
    const clave = propuesta.proyectoId == null ? "sin-proyecto" : String(propuesta.proyectoId);
    let fila = filas.get(clave);
    if (!fila) {
      fila = {
        proyectoId: propuesta.proyectoId,
        nombre:
          propuesta.proyectoId == null ? SIN_PROYECTO : (propuesta.proyectoNombre ?? SIN_PROYECTO),
        netoClp: 0,
        cantidad: 0,
      };
      filas.set(clave, fila);
    }
    fila.netoClp += propuesta.netoClp;
    fila.cantidad += 1;
  }
  return Array.from(filas.values()).sort((a, b) => b.netoClp - a.netoClp);
}

export interface FilaCliente {
  clienteId: number;
  nombre: string;
  netoClp: number;
  cantidad: number;
  cantidadPorEstado: Record<EstadoPropuesta, number>;
}

/** Agrupa TODAS las propuestas por cliente (cantidad por estado incluye `PENDIENTE_UF`/
 * `ANULADA` para contexto), pero `netoClp` solo suma las {@link esCalculable}. */
export function calcularPorCliente(propuestas: PropuestaFacturacion[]): FilaCliente[] {
  const filas = new Map<number, FilaCliente>();
  for (const propuesta of propuestas) {
    let fila = filas.get(propuesta.clienteId);
    if (!fila) {
      fila = {
        clienteId: propuesta.clienteId,
        nombre: propuesta.clienteRazonSocial,
        netoClp: 0,
        cantidad: 0,
        cantidadPorEstado: { PENDIENTE: 0, PENDIENTE_UF: 0, FACTURADA: 0, ANULADA: 0 },
      };
      filas.set(propuesta.clienteId, fila);
    }
    fila.cantidad += 1;
    fila.cantidadPorEstado[propuesta.estado] += 1;
    if (esCalculable(propuesta.estado)) {
      fila.netoClp += propuesta.netoClp;
    }
  }
  return Array.from(filas.values()).sort((a, b) => b.netoClp - a.netoClp);
}

export type ResultadoMoM =
  | {
      disponible: true;
      anioActual: number;
      mesActual: number;
      anioAnterior: number;
      mesAnterior: number;
      totalActual: number;
      totalAnterior: number;
      variacionPorcentaje: number;
    }
  | { disponible: false };

function totalesPorMesCalculables(propuestas: PropuestaFacturacion[]): Map<string, number> {
  const totales = new Map<string, number>();
  for (const propuesta of propuestas) {
    if (!esCalculable(propuesta.estado)) {
      continue;
    }
    const clave = claveMes(propuesta.periodoAnio, propuesta.periodoMes);
    totales.set(clave, (totales.get(clave) ?? 0) + propuesta.netoClp);
  }
  return totales;
}

/**
 * Variación mes contra mes — SOLO entre el último mes con datos y el mes calendario
 * INMEDIATAMENTE anterior, y solo si ese mes anterior TAMBIÉN tiene datos. Deliberadamente no
 * compara contra "el período anterior en la lista" (que podría estar varios meses atrás, un
 * hueco real) — con los datos reales de este sistema, enero concentra 58 propuestas CSV y no
 * vuelve a haber datos hasta mayo: comparar mayo contra enero se leería como una caída de
 * -100%, cuando en realidad es un hueco de 4 meses, no una tendencia. `disponible: false` es la
 * respuesta honesta en ese caso, no un porcentaje inventado.
 */
export function calcularMoM(propuestas: PropuestaFacturacion[]): ResultadoMoM {
  const totales = totalesPorMesCalculables(propuestas);
  if (totales.size === 0) {
    return { disponible: false };
  }
  const claves = Array.from(totales.keys()).sort();
  const ultimaClave = claves[claves.length - 1]!;
  const [anioActual, mesActual] = ultimaClave.split("-").map(Number) as [number, number];

  let anioAnterior = anioActual;
  let mesAnterior = mesActual - 1;
  if (mesAnterior < 1) {
    mesAnterior = 12;
    anioAnterior -= 1;
  }
  const totalAnterior = totales.get(claveMes(anioAnterior, mesAnterior));
  if (totalAnterior == null) {
    return { disponible: false };
  }
  const totalActual = totales.get(ultimaClave)!;
  const variacionPorcentaje =
    totalAnterior === 0 ? 0 : ((totalActual - totalAnterior) / totalAnterior) * 100;

  return {
    disponible: true,
    anioActual,
    mesActual,
    anioAnterior,
    mesAnterior,
    totalActual,
    totalAnterior,
    variacionPorcentaje,
  };
}

export type ResultadoYoY =
  | {
      disponible: true;
      anioActual: number;
      mes: number;
      totalActual: number;
      totalAnterior: number;
      variacionPorcentaje: number;
    }
  | { disponible: false; anioActual: number; mes: number; totalActual: number };

/**
 * Variación interanual: último mes con datos contra el MISMO mes del año anterior. Con datos
 * reales de 2026 y ningún dato de 2025, esto nace `disponible: false` a propósito — es el
 * comportamiento correcto también en producción durante el primer año del sistema, no un caso
 * sin manejar. Nunca se muestra "-100%": la ausencia total de un período anterior no es una
 * caída, es la ausencia de un punto de comparación.
 */
export function calcularYoY(propuestas: PropuestaFacturacion[]): ResultadoYoY {
  const totales = totalesPorMesCalculables(propuestas);
  if (totales.size === 0) {
    return { disponible: false, anioActual: 0, mes: 0, totalActual: 0 };
  }
  const claves = Array.from(totales.keys()).sort();
  const ultimaClave = claves[claves.length - 1]!;
  const [anioActual, mes] = ultimaClave.split("-").map(Number) as [number, number];
  const totalActual = totales.get(ultimaClave)!;

  const totalAnterior = totales.get(claveMes(anioActual - 1, mes));
  if (totalAnterior == null) {
    return { disponible: false, anioActual, mes, totalActual };
  }
  const variacionPorcentaje =
    totalAnterior === 0 ? 0 : ((totalActual - totalAnterior) / totalAnterior) * 100;
  return { disponible: true, anioActual, mes, totalActual, totalAnterior, variacionPorcentaje };
}

export function contarPendienteUf(propuestas: PropuestaFacturacion[]): number {
  return propuestas.filter((propuesta) => propuesta.estado === "PENDIENTE_UF").length;
}
