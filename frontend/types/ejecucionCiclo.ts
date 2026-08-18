import type { DisparoCiclo, EstadoEjecucionCiclo } from "./dominio";

/** Espeja `EjecucionCicloRespuestaDto` (historial, `GET /api/v1/ciclos`). */
export interface EjecucionCiclo {
  id: number;
  periodoAnio: number;
  periodoMes: number;
  /** ISO-8601 con offset (`OffsetDateTime` del backend). */
  ejecutadoEn: string;
  disparo: DisparoCiclo;
  cantidadGeneradas: number;
  cantidadPendientesUf: number;
  estado: EstadoEjecucionCiclo;
  observacion: string | null;
  ejecutadoPor: string;
}

/** Espeja `ResultadoCiclo` (respuesta directa de `POST /api/v1/ciclos/ejecutar`). */
export interface ResultadoCiclo {
  /**
   * `null` si la corrida se omitió por completo (ya había otra ejecución en curso) — pero,
   * como TODA la API (`jackson.default-property-inclusion: non_null`), un `null` se OMITE del
   * JSON en vez de viajar como `"ejecucionId":null`, así que tras `JSON.parse` vale
   * `undefined`, no `null` (docs/deuda-tecnica.md ítem 5). Comparar con `== null`/`!= null`,
   * nunca `===`/`!==` — con `!==` a secas, la rama de "ya había una ejecución en curso" nunca
   * se mostraba, bug real corregido en `EjecutarCiclo.tsx`.
   */
  ejecucionId: number | null | undefined;
  anio: number;
  mes: number;
  disparo: DisparoCiclo;
  estado: EstadoEjecucionCiclo;
  cantidadGeneradas: number;
  cantidadPendientesUf: number;
  observacion: string | null;
}

/** Espeja `EjecutarCicloSolicitudDto`. Ambos campos opcionales: si se omiten, el backend usa el mes actual. */
export interface EjecutarCicloSolicitud {
  anio?: number;
  mes?: number;
}
