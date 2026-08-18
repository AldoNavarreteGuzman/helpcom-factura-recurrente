"use client";

import Image from "next/image";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { signOut, useSession } from "next-auth/react";
import { combinarClases } from "@/lib/estilos";
import { ENLACES_NAV, enlacesVisibles } from "@/lib/navegacion";

/**
 * Sidebar de escritorio (docs/plan-rediseno.md R1, §4.1) — fondo azul de marca pleno, logo
 * blanco arriba, ítems con ícono + etiqueta filtrados por rol (misma fuente de datos y misma
 * lógica que antes tenía `Navegacion.tsx`, ver `lib/navegacion.ts`), y el usuario autenticado +
 * cerrar sesión al final. Oculto bajo el breakpoint `md` — ahí gobierna `BarraInferior`.
 */
export function BarraLateral() {
  const { data: sesion } = useSession();
  const pathname = usePathname();
  const enlaces = enlacesVisibles(ENLACES_NAV, sesion?.roles ?? []);
  const nombreOCorreo = sesion?.user?.name ?? sesion?.user?.email ?? "";

  return (
    <aside className="hidden md:flex md:w-64 md:shrink-0 md:flex-col md:bg-marca-azul">
      <div className="flex h-16 items-center justify-center px-6">
        <Image
          src="/logo-helpcom-blanco.png"
          alt="Helpcom"
          width={140}
          height={62}
          className="h-8 w-auto"
          priority
        />
      </div>

      <nav aria-label="Navegación principal" className="flex-1 space-y-1 px-3 py-2">
        {enlaces.map((enlace) => {
          const activo = pathname === enlace.href;
          const Icono = enlace.icono;
          return (
            <Link
              key={enlace.href}
              href={enlace.href}
              aria-current={activo ? "page" : undefined}
              className={combinarClases(
                "flex min-h-11 items-center gap-3 rounded-sm border-l-4 px-3 py-2 text-sm font-medium transition-colors",
                "focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca-celeste",
                activo
                  ? "border-marca-celeste bg-white/10 text-white"
                  : "border-transparent text-marca-azul-50/80 hover:bg-white/5 hover:text-white",
              )}
            >
              {Icono && <Icono className="h-5 w-5 shrink-0" aria-hidden />}
              {enlace.etiqueta}
            </Link>
          );
        })}
      </nav>

      <div className="border-t border-white/10 px-4 py-4">
        {nombreOCorreo && (
          <p className="mb-2 truncate text-xs text-marca-azul-50/80" title={nombreOCorreo}>
            {nombreOCorreo}
          </p>
        )}
        <button
          type="button"
          onClick={() => signOut({ redirectTo: "/login" })}
          className={combinarClases(
            "min-h-11 w-full rounded-sm px-3 py-2 text-left text-sm font-medium text-white/90 transition-colors",
            "hover:bg-white/10 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca-celeste",
          )}
        >
          Cerrar sesión
        </button>
      </div>
    </aside>
  );
}
