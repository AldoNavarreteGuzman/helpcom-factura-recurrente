import { Loader2 } from "lucide-react";
import type { ReactNode } from "react";
import { Alerta } from "@/components/ui/Alerta";
import { combinarClases } from "@/lib/estilos";
import { obtenerMensajeError } from "@/lib/errores";

export interface PropiedadesTarjetaDashboard {
  titulo: string;
  descripcion?: string;
  cargando: boolean;
  error: unknown;
  /** Alto reservado para el área de contenido — igual en carga/error/con-datos, para que
   * resolver el fetch no produzca layout shift. Cada tarjeta define el suyo según su contenido
   * real (una lista de 2 clientes no necesita el mismo alto que una gráfica). */
  alturaContenido: string;
  children: ReactNode;
  className?: string;
}

/**
 * Envoltorio compartido de toda tarjeta del dashboard (docs/frontend.md R9): título +
 * descripción opcional, y un área de contenido de alto fijo que muestra error, carga o el
 * contenido real — nunca cambia de alto entre esos tres estados. Todas las tarjetas del
 * dashboard comparten la misma fuente de datos (`lib/useDashboardDatos.ts`), así que si esa
 * fuente falla, cada tarjeta que la usa lo muestra en su propio lugar (este componente), sin
 * tumbar el resto de la pantalla.
 */
export function TarjetaDashboard({
  titulo,
  descripcion,
  cargando,
  error,
  alturaContenido,
  children,
  className,
}: PropiedadesTarjetaDashboard) {
  return (
    <div
      className={combinarClases(
        "space-y-3 rounded-lg border border-linea bg-white p-4 shadow-tarjeta",
        className,
      )}
    >
      <div>
        <h2 className="text-sm font-semibold text-tinta">{titulo}</h2>
        {descripcion ? <p className="text-xs text-sutil">{descripcion}</p> : null}
      </div>
      <div className={combinarClases(alturaContenido, "flex flex-col")}>
        {error ? (
          <div className="flex flex-1 items-center">
            <Alerta variante="error" className="w-full">
              {obtenerMensajeError(error)}
            </Alerta>
          </div>
        ) : cargando ? (
          <div className="flex flex-1 items-center justify-center gap-2 text-sm text-sutil">
            <Loader2 className="h-4 w-4 animate-spin" aria-hidden />
            Cargando…
          </div>
        ) : (
          children
        )}
      </div>
    </div>
  );
}
