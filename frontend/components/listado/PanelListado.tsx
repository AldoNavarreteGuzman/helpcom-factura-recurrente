import { Loader2 } from "lucide-react";
import type { ReactNode } from "react";
import { Alerta } from "@/components/ui/Alerta";
import { Paginacion } from "@/components/ui/Paginacion";
import { Tabla, type ColumnaTabla } from "@/components/ui/Tabla";
import { obtenerMensajeError } from "@/lib/errores";

export interface PropiedadesPanelListado<T> {
  titulo: string;
  columnas: ColumnaTabla<T>[];
  filas: T[];
  obtenerClave: (fila: T) => string | number;
  cargando: boolean;
  error?: unknown;
  paginaActual: number;
  totalPaginas: number;
  onCambiarPagina: (pagina: number) => void;
  /** Controles de filtro específicos de la entidad (texto, selects de estado, etc.). */
  filtros?: ReactNode;
  /** Típicamente el botón "+ Nuevo …", ya gateado por rol por quien arma la pantalla. */
  accionPrincipal?: ReactNode;
  mensajeVacio?: string;
}

/**
 * Vista de listado genérica: filtros + tabla + paginación + estados de carga/vacío/error.
 * Pensada para reutilizarse en toda pantalla de listado (clientes, tipos de servicio, y los
 * módulos futuros — proyectos, propuestas, facturas, importaciones, informe). Las columnas
 * (incluida una de "Acciones" con botones por fila, ver `AccionesFila`) las define cada
 * pantalla; el estado vacío lo resuelve `Tabla` internamente.
 */
export function PanelListado<T>({
  titulo,
  columnas,
  filas,
  obtenerClave,
  cargando,
  error,
  paginaActual,
  totalPaginas,
  onCambiarPagina,
  filtros,
  accionPrincipal,
  mensajeVacio,
}: PropiedadesPanelListado<T>) {
  return (
    <section className="space-y-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold text-tinta">{titulo}</h1>
        {accionPrincipal}
      </div>

      {filtros ? (
        <div className="flex flex-col items-stretch gap-3 sm:flex-row sm:flex-wrap sm:items-end">
          {filtros}
        </div>
      ) : null}

      {error ? (
        <Alerta variante="error">{obtenerMensajeError(error)}</Alerta>
      ) : cargando ? (
        <div className="flex items-center justify-center gap-2 py-10 text-sm text-sutil">
          <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
          Cargando…
        </div>
      ) : (
        <>
          <Tabla
            columnas={columnas}
            filas={filas}
            obtenerClave={obtenerClave}
            mensajeVacio={mensajeVacio}
          />
          <Paginacion
            paginaActual={paginaActual}
            totalPaginas={totalPaginas}
            onCambiarPagina={onCambiarPagina}
          />
        </>
      )}
    </section>
  );
}
