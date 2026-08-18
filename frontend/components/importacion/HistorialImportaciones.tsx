"use client";

import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { useCallback, useMemo, useState } from "react";
import { BadgeEstadoImportacionCsv } from "./BadgeEstadoImportacionCsv";
import { PanelListado } from "@/components/listado/PanelListado";
import type { ColumnaTabla } from "@/components/ui/Tabla";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { formatearFechaHora } from "@/lib/formato";
import { construirQueryString } from "@/lib/query";
import { useListadoPaginado } from "@/lib/useListadoPaginado";
import type { PaginaRespuesta } from "@/types/api";
import type { ImportacionCsv } from "@/types/importacionCsv";

const TAMANO_PAGINA = 20;

export function HistorialImportaciones() {
  const [pagina, setPagina] = useState(0);

  const fetcher = useCallback(() => {
    // El orden descendente por fecha ya es el orden por defecto del backend
    // (ImportacionCsvRepositorio.findByEmpresaIdOrderByFechaImportacionDesc,
    // estandares-de-codigo.md §3.8), como en el historial de ciclos — no hace falta pedirlo.
    const query = construirQueryString({
      page: pagina,
      size: TAMANO_PAGINA,
    });
    return clienteApiCliente.obtener<PaginaRespuesta<ImportacionCsv>>(`/importaciones${query}`);
  }, [pagina]);

  const { datos, totalPaginas, cargando, error } = useListadoPaginado(fetcher, [pagina]);

  const columnas = useMemo<ColumnaTabla<ImportacionCsv>[]>(
    () => [
      { encabezado: "Archivo", renderizar: (importacion) => importacion.nombreArchivo },
      {
        encabezado: "Fecha",
        renderizar: (importacion) => formatearFechaHora(importacion.fechaImportacion),
      },
      { encabezado: "Total filas", renderizar: (importacion) => importacion.totalFilas },
      { encabezado: "OK", renderizar: (importacion) => importacion.filasOk },
      { encabezado: "Error", renderizar: (importacion) => importacion.filasError },
      {
        encabezado: "Estado",
        renderizar: (importacion) => <BadgeEstadoImportacionCsv estado={importacion.estado} />,
      },
    ],
    [],
  );

  return (
    <div className="space-y-4">
      <Link
        href="/importacion"
        className="inline-flex min-h-11 items-center gap-1 text-sm text-sutil hover:text-marca-azul"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        Volver a importar
      </Link>
      <PanelListado
        titulo="Historial de importaciones"
        columnas={columnas}
        filas={datos}
        obtenerClave={(importacion) => importacion.id}
        cargando={cargando}
        error={error}
        paginaActual={pagina}
        totalPaginas={totalPaginas}
        onCambiarPagina={setPagina}
        mensajeVacio="Todavía no se ha importado ningún CSV."
      />
    </div>
  );
}
