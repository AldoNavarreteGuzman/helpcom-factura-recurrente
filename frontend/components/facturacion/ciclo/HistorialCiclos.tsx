"use client";

import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { useCallback, useMemo, useState } from "react";
import { BadgeEstadoEjecucionCiclo } from "./BadgeEstadoEjecucionCiclo";
import { PanelListado } from "@/components/listado/PanelListado";
import type { ColumnaTabla } from "@/components/ui/Tabla";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { ETIQUETAS_DISPARO_CICLO, formatearPeriodo } from "@/lib/etiquetas";
import { formatearFechaHora } from "@/lib/formato";
import { construirQueryString } from "@/lib/query";
import { useListadoPaginado } from "@/lib/useListadoPaginado";
import type { PaginaRespuesta } from "@/types/api";
import type { EjecucionCiclo } from "@/types/ejecucionCiclo";

const TAMANO_PAGINA = 20;

export function HistorialCiclos() {
  const [pagina, setPagina] = useState(0);

  const fetcher = useCallback(() => {
    const query = construirQueryString({ page: pagina, size: TAMANO_PAGINA });
    // El backend ya ordena por fecha de ejecución descendente
    // (EjecucionCicloRepositorio.findByEmpresaIdOrderByEjecutadoEnDesc); no hace falta pedir orden acá.
    return clienteApiCliente.obtener<PaginaRespuesta<EjecucionCiclo>>(`/ciclos${query}`);
  }, [pagina]);

  const { datos, totalPaginas, cargando, error } = useListadoPaginado(fetcher, [pagina]);

  const columnas = useMemo<ColumnaTabla<EjecucionCiclo>[]>(
    () => [
      { encabezado: "Período", renderizar: (e) => formatearPeriodo(e.periodoAnio, e.periodoMes) },
      { encabezado: "Ejecutado", renderizar: (e) => formatearFechaHora(e.ejecutadoEn) },
      { encabezado: "Disparo", renderizar: (e) => ETIQUETAS_DISPARO_CICLO[e.disparo] },
      { encabezado: "Generadas", renderizar: (e) => e.cantidadGeneradas },
      { encabezado: "Pendientes UF", renderizar: (e) => e.cantidadPendientesUf },
      { encabezado: "Estado", renderizar: (e) => <BadgeEstadoEjecucionCiclo estado={e.estado} /> },
      { encabezado: "Observación", renderizar: (e) => e.observacion ?? "—" },
    ],
    [],
  );

  return (
    <div className="space-y-4">
      <Link
        href="/facturacion"
        className="inline-flex min-h-11 items-center gap-1 text-sm text-sutil hover:text-marca-azul"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        Volver a propuestas
      </Link>
      <PanelListado
        titulo="Historial de ciclos"
        columnas={columnas}
        filas={datos}
        obtenerClave={(e) => e.id}
        cargando={cargando}
        error={error}
        paginaActual={pagina}
        totalPaginas={totalPaginas}
        onCambiarPagina={setPagina}
        mensajeVacio="Todavía no se ha ejecutado el ciclo."
      />
    </div>
  );
}
