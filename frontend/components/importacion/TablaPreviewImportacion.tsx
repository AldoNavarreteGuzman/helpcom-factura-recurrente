"use client";

import { useMemo, useState } from "react";
import { BadgeEstadoFilaCsv } from "./BadgeEstadoFilaCsv";
import { PanelListado } from "@/components/listado/PanelListado";
import type { ColumnaTabla } from "@/components/ui/Tabla";
import { TarjetaEstadistica } from "@/components/ui/TarjetaEstadistica";
import { formatearMontoFilaCsv } from "@/lib/importaciones";
import type { ImportacionPreview, ImportacionPreviewFila } from "@/types/importacionCsv";

const TAMANO_PAGINA = 50;

export interface PropiedadesTablaPreviewImportacion {
  preview: ImportacionPreview;
}

/**
 * Resumen + tabla de la previsualización. `POST /importaciones/previsualizar` devuelve TODAS
 * las filas del CSV en una sola respuesta (no hay paginación de servidor para esto: es el
 * cálculo completo de un archivo ya subido, no un listado independiente) — para que un CSV de
 * cientos de filas no reviente el DOM con una tabla gigante, la paginación se hace 100% en
 * cliente sobre el arreglo ya cargado (`.slice()`), reutilizando `PanelListado`/`Paginacion`
 * tal cual: a esos componentes no les importa si la "página" viene de una nueva consulta al
 * servidor o de datos que ya están en memoria.
 */
export function TablaPreviewImportacion({ preview }: PropiedadesTablaPreviewImportacion) {
  const [pagina, setPagina] = useState(0);

  const totalPaginas = Math.max(1, Math.ceil(preview.filas.length / TAMANO_PAGINA));
  const filasPagina = preview.filas.slice(pagina * TAMANO_PAGINA, (pagina + 1) * TAMANO_PAGINA);

  const columnas = useMemo<ColumnaTabla<ImportacionPreviewFila>[]>(
    () => [
      { encabezado: "Fila", renderizar: (fila) => fila.numeroFila },
      { encabezado: "RUT cliente", renderizar: (fila) => fila.rutCliente ?? "—" },
      { encabezado: "Proyecto", renderizar: (fila) => fila.codigoProyecto ?? "—" },
      { encabezado: "Descripción", renderizar: (fila) => fila.descripcion ?? "—" },
      { encabezado: "Período", renderizar: (fila) => fila.periodo ?? "—" },
      { encabezado: "Fecha", renderizar: (fila) => fila.fechaFacturacion ?? "—" },
      { encabezado: "Moneda", renderizar: (fila) => fila.moneda ?? "—" },
      { encabezado: "Monto neto", renderizar: (fila) => fila.montoNeto ?? "—" },
      { encabezado: "Estado", renderizar: (fila) => <BadgeEstadoFilaCsv estado={fila.estado} /> },
      {
        encabezado: "Mensajes",
        renderizar: (fila) =>
          fila.mensajes.length > 0 ? (
            <ul className="list-inside list-disc space-y-0.5">
              {fila.mensajes.map((mensaje, indice) => (
                <li
                  key={indice}
                  className={fila.estado === "ERROR" ? "text-estado-error" : "text-estado-sin-uf"}
                >
                  {mensaje}
                </li>
              ))}
            </ul>
          ) : (
            "—"
          ),
      },
      {
        encabezado: "Neto",
        renderizar: (fila) =>
          fila.netoClp == null ? "—" : formatearMontoFilaCsv(fila.netoClp, fila),
      },
      {
        encabezado: "IVA",
        renderizar: (fila) =>
          fila.ivaClp == null ? "—" : formatearMontoFilaCsv(fila.ivaClp, fila),
      },
      {
        encabezado: "Total",
        renderizar: (fila) =>
          fila.totalClp == null ? "—" : formatearMontoFilaCsv(fila.totalClp, fila),
      },
    ],
    [],
  );

  return (
    <div className="space-y-3">
      <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
        <TarjetaEstadistica
          etiqueta="Total filas"
          valor={preview.resumen.totalFilas}
          className="text-tinta"
        />
        <TarjetaEstadistica
          etiqueta="OK"
          valor={preview.resumen.filasOk}
          className="text-estado-facturada"
        />
        <TarjetaEstadistica
          etiqueta="Advertencia"
          valor={preview.resumen.filasAdvertencia}
          className="text-estado-sin-uf"
        />
        <TarjetaEstadistica
          etiqueta="Error"
          valor={preview.resumen.filasError}
          className="text-estado-error"
        />
      </div>

      <PanelListado
        titulo="Vista previa"
        columnas={columnas}
        filas={filasPagina}
        obtenerClave={(fila) => fila.numeroFila}
        cargando={false}
        paginaActual={pagina}
        totalPaginas={totalPaginas}
        onCambiarPagina={setPagina}
        mensajeVacio="El archivo no tiene filas."
      />
    </div>
  );
}
