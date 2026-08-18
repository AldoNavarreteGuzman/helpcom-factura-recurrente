import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import type { ReactNode } from "react";

export interface PropiedadesEncabezadoDetalle {
  /** Ruta del listado del que se viene — el enlace "Volver". */
  volverA: string;
  volverEtiqueta?: string;
  titulo: string;
  /** Típicamente un {@link import("../ui/BadgeActivo").BadgeActivo} u otro badge de estado. */
  subtitulo?: ReactNode;
  /** Típicamente un botón "Editar" (docs/frontend.md §14). */
  acciones?: ReactNode;
}

/**
 * Cabecera del patrón "detalle de entidad" (docs/frontend.md §14): enlace de vuelta al listado,
 * título + un badge/estado junto a él, y las acciones de la entidad (editar, etc.). Se usa sola
 * en una pantalla de detalle sin subsecciones, o junto a {@link PestanasDetalle} cuando las hay
 * — R4 la usará así para el detalle de Proyecto (pestañas "Datos"/"Descuentos").
 */
export function EncabezadoDetalle({
  volverA,
  volverEtiqueta = "Volver",
  titulo,
  subtitulo,
  acciones,
}: PropiedadesEncabezadoDetalle) {
  return (
    <div className="space-y-3">
      <Link
        href={volverA}
        className="inline-flex min-h-11 items-center gap-1 text-sm text-sutil hover:text-marca-azul"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        {volverEtiqueta}
      </Link>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-3">
          <h1 className="text-2xl font-semibold text-tinta">{titulo}</h1>
          {subtitulo}
        </div>
        {acciones}
      </div>
    </div>
  );
}
