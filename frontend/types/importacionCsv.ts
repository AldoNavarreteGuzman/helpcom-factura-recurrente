import type { EstadoFilaCsv, EstadoImportacionCsv } from "./dominio";

/**
 * Espeja `ImportacionPreviewFilaDto`. Los campos parseados del CSV (`rutCliente`, `periodo`,
 * `fechaFacturacion`, `moneda`, `montoNeto`) llegan como texto crudo, tal como venían en el
 * archivo (sin normalizar) — `fechaFacturacion` en particular sigue en `DD-MM-AAAA`, NO en
 * ISO, así que no debe pasarse a `formatearFecha`. `netoClp`/`ivaClp`/`totalClp` son `null`
 * únicamente en filas `ERROR` — pero, como TODA la API (`jackson.default-property-inclusion:
 * non_null`, `application.yml`), un campo `null` no viaja como `"campo":null` en el JSON, se
 * OMITE por completo, así que después de `JSON.parse` vale `undefined`, no `null`. Por eso
 * estos tres campos son `| undefined` además de `| null` — un chequeo `=== null` a secas NO
 * detecta la ausencia real (bug real, corregido en `TablaPreviewImportacion.tsx`: usar
 * `== null`, que sí cubre ambos casos).
 */
export interface ImportacionPreviewFila {
  numeroFila: number;
  estado: EstadoFilaCsv;
  mensajes: string[];
  rutCliente: string | null;
  codigoProyecto: string | null;
  descripcion: string | null;
  /** AAAA-MM, tal como vino en el CSV. */
  periodo: string | null;
  /** DD-MM-AAAA, tal como vino en el CSV (no ISO-8601). */
  fechaFacturacion: string | null;
  moneda: string | null;
  montoNeto: string | null;
  observacion: string | null;
  netoClp: number | null | undefined;
  ivaClp: number | null | undefined;
  totalClp: number | null | undefined;
}

/** Espeja `ImportacionPreviewResumenDto`. */
export interface ImportacionPreviewResumen {
  totalFilas: number;
  filasOk: number;
  filasAdvertencia: number;
  filasError: number;
}

/** Espeja `ImportacionPreviewRespuestaDto` (respuesta de `POST /importaciones/previsualizar`). */
export interface ImportacionPreview {
  resumen: ImportacionPreviewResumen;
  filas: ImportacionPreviewFila[];
}

/**
 * Espeja `ImportacionCsvRespuestaDto`: tanto el resultado de `POST /importaciones/confirmar`
 * como cada fila del historial (`GET /importaciones`). `filasOk` cuenta las filas realmente
 * importadas (`OK` + `ADVERTENCIA` de la previsualización, es decir `PENDIENTE` +
 * `PENDIENTE_UF`); `cantidadPendienteUf` es el subconjunto REAL de `filasOk` que quedó en
 * `PENDIENTE_UF` — se lee como `filasOk = (filasOk - cantidadPendienteUf) con valor +
 * cantidadPendienteUf sin UF`; `filasError` queda aparte, sin solaparse con ninguno de los
 * dos.
 *
 * `cantidadPendienteUf` es `null` en las filas del historial (el backend no lo persiste, ver
 * `docs/deuda-tecnica.md`) — solo viene con un número real en la respuesta de `confirmar`.
 */
export interface ImportacionCsv {
  id: number;
  nombreArchivo: string;
  /** Instante ISO-8601 con offset (`OffsetDateTime` del backend). */
  fechaImportacion: string;
  totalFilas: number;
  filasOk: number;
  /** Real (no estimado): contado por el backend al confirmar. `null` en filas del historial. */
  cantidadPendienteUf: number | null;
  filasError: number;
  estado: EstadoImportacionCsv;
}
