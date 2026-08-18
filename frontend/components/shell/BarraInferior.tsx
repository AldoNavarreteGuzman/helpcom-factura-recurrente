"use client";

import { MoreHorizontal } from "lucide-react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { useState } from "react";
import { useSession } from "next-auth/react";
import { combinarClases } from "@/lib/estilos";
import { ENLACES_NAV, enlacesVisibles } from "@/lib/navegacion";

/** Cuántos enlaces caben como acceso directo antes de agruparse en "Más" (docs/plan-rediseno.md R1, §4.1). */
const CANTIDAD_PRINCIPAL = 4;

/**
 * Barra inferior de móvil — visible solo bajo el breakpoint `md` (en escritorio gobierna
 * `BarraLateral`). Muestra los primeros `CANTIDAD_PRINCIPAL` enlaces visibles según rol como
 * acceso directo; el resto queda en un menú "Más" desplegable. Toda el área de toque respeta
 * el mínimo de 44px de alto.
 */
export function BarraInferior() {
  const { data: sesion } = useSession();
  const pathname = usePathname();
  const [masAbierto, setMasAbierto] = useState(false);
  const enlaces = enlacesVisibles(ENLACES_NAV, sesion?.roles ?? []);
  const principales = enlaces.slice(0, CANTIDAD_PRINCIPAL);
  const resto = enlaces.slice(CANTIDAD_PRINCIPAL);

  return (
    <nav
      aria-label="Navegación principal"
      className="fixed inset-x-0 bottom-0 z-20 flex border-t border-linea bg-white md:hidden"
    >
      {principales.map((enlace) => {
        const activo = pathname === enlace.href;
        const Icono = enlace.icono;
        return (
          <Link
            key={enlace.href}
            href={enlace.href}
            aria-current={activo ? "page" : undefined}
            onClick={() => setMasAbierto(false)}
            className={combinarClases(
              "flex min-h-[56px] flex-1 flex-col items-center justify-center gap-0.5 px-1 py-1.5 text-[11px] font-medium",
              "focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-marca-azul",
              activo ? "text-marca-azul" : "text-sutil",
            )}
          >
            {Icono && <Icono className="h-5 w-5" aria-hidden />}
            {enlace.etiqueta}
          </Link>
        );
      })}

      {resto.length > 0 && (
        <div className="relative flex flex-1">
          {masAbierto && (
            <>
              <button
                type="button"
                aria-hidden
                tabIndex={-1}
                className="fixed inset-0 z-10"
                onClick={() => setMasAbierto(false)}
              />
              <div
                role="menu"
                aria-label="Más secciones"
                className="absolute bottom-full right-0 z-20 mb-2 w-44 overflow-hidden rounded border border-linea bg-white shadow-modal"
              >
                {resto.map((enlace) => (
                  <Link
                    key={enlace.href}
                    href={enlace.href}
                    role="menuitem"
                    onClick={() => setMasAbierto(false)}
                    className="flex min-h-11 items-center gap-2 px-4 py-2 text-sm text-texto hover:bg-fondo"
                  >
                    {enlace.icono && <enlace.icono className="h-4 w-4" aria-hidden />}
                    {enlace.etiqueta}
                  </Link>
                ))}
              </div>
            </>
          )}
          <button
            type="button"
            aria-expanded={masAbierto}
            aria-haspopup="menu"
            onClick={() => setMasAbierto((abierto) => !abierto)}
            className={combinarClases(
              "flex min-h-[56px] w-full flex-col items-center justify-center gap-0.5 px-1 py-1.5 text-[11px] font-medium",
              "focus-visible:outline focus-visible:outline-2 focus-visible:-outline-offset-2 focus-visible:outline-marca-azul",
              masAbierto ? "text-marca-azul" : "text-sutil",
            )}
          >
            <MoreHorizontal className="h-5 w-5" aria-hidden />
            Más
          </button>
        </div>
      )}
    </nav>
  );
}
