"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { combinarClases } from "@/lib/estilos";

export interface PestanaDetalle {
  href: string;
  etiqueta: string;
}

export interface PropiedadesPestanasDetalle {
  pestanas: PestanaDetalle[];
}

/**
 * Barra de pestañas del patrón "cabecera de entidad + subsecciones" (docs/frontend.md §14,
 * docs/plan-rediseno.md §4.2) — la pestaña activa se resuelve por la ruta actual
 * (`usePathname`), sin estado propio, igual que ya hace `BarraLateral` para el nav principal.
 * Pensada para R4: el detalle de un proyecto usará `<PestanasDetalle pestanas={[{href:
 * "/proyectos/{id}", etiqueta: "Datos"}, {href: "/proyectos/{id}/acuerdos", etiqueta:
 * "Descuentos"}]} />` bajo `EncabezadoDetalle`, resolviendo el acceso a Descuentos que hoy no
 * es visible desde el listado de Proyectos.
 */
export function PestanasDetalle({ pestanas }: PropiedadesPestanasDetalle) {
  const pathname = usePathname();

  return (
    <nav aria-label="Secciones" className="flex gap-6 border-b border-linea">
      {pestanas.map((pestana) => {
        const activa = pathname === pestana.href;
        return (
          <Link
            key={pestana.href}
            href={pestana.href}
            aria-current={activa ? "page" : undefined}
            className={combinarClases(
              "flex min-h-11 items-center border-b-2 px-1 text-sm font-medium transition-colors",
              "focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-marca-azul focus-visible:ring-offset-2",
              activa ? "border-marca-azul text-marca-azul" : "border-transparent text-sutil hover:text-texto",
            )}
          >
            {pestana.etiqueta}
          </Link>
        );
      })}
    </nav>
  );
}
