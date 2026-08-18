import Link from "next/link";
import { BadgeEstadoImportacionCsv } from "./BadgeEstadoImportacionCsv";
import { Alerta } from "@/components/ui/Alerta";
import { periodoUnicoImportable } from "@/lib/importaciones";
import type { ImportacionCsv, ImportacionPreview } from "@/types/importacionCsv";

export interface PropiedadesResultadoImportacion {
  resultado: ImportacionCsv;
  /**
   * La previsualización que llevó a este resultado — solo para armar el enlace a Propuestas
   * con período (el resultado de confirmar no trae el período, ver `docs/frontend.md` §7.4).
   * El contador de `PENDIENTE_UF` YA NO se estima desde acá: `resultado.cantidadPendienteUf`
   * es el valor REAL que confirma el backend, que puede diferir del estimado que mostró la
   * previsualización (p. ej. si la UF se volvió disponible mientras tanto). Si `preview` es
   * `null` (no debería pasar en el flujo normal, pero el tipo lo permite), el enlace queda sin
   * acotar por período.
   */
  preview: ImportacionPreview | null;
}

/** Resultado de `POST /importaciones/confirmar`, mostrado con honestidad: PARCIAL y RECHAZADA no se pintan como éxito. */
export function ResultadoImportacion({ resultado, preview }: PropiedadesResultadoImportacion) {
  const periodo = preview ? periodoUnicoImportable(preview.filas) : null;
  const enlacePropuestas =
    "/facturacion?origen=CSV" +
    (periodo ? `&periodoAnio=${periodo.anio}&periodoMes=${periodo.mes}` : "");

  return (
    <div className="space-y-3 rounded-md border border-linea p-4">
      <div className="flex items-center gap-3">
        <BadgeEstadoImportacionCsv estado={resultado.estado} />
        <h2 className="text-sm font-semibold text-tinta">Resultado de la importación</h2>
      </div>

      {resultado.estado !== "PROCESADA" ? (
        <Alerta variante="advertencia">
          {resultado.estado === "RECHAZADA"
            ? "No se importó ninguna fila: todas tenían error."
            : "Solo se importó una parte del archivo — revisa las filas con error antes de reintentar el resto."}
        </Alerta>
      ) : null}

      <p className="text-sm text-texto">
        {resultado.filasOk} de {resultado.totalFilas} fila{resultado.totalFilas === 1 ? "" : "s"}{" "}
        importada{resultado.filasOk === 1 ? "" : "s"}
        {resultado.filasError > 0
          ? `; ${resultado.filasError} fila${resultado.filasError === 1 ? "" : "s"} con error.`
          : "."}
      </p>

      {resultado.cantidadPendienteUf !== null && resultado.cantidadPendienteUf > 0 ? (
        <Alerta variante="advertencia">
          {resultado.cantidadPendienteUf} de las importadas quedaron en estado Pendiente UF (sin
          valor UF disponible para su fecha) — revísalas en el listado de propuestas.
        </Alerta>
      ) : null}

      <Link
        href={enlacePropuestas}
        className="inline-block text-sm font-medium text-marca-azul hover:text-marca-azul-700"
      >
        Ver propuestas importadas (origen CSV) →
      </Link>
    </div>
  );
}
