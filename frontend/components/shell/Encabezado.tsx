"use client";

import { signOut, useSession } from "next-auth/react";
import { Boton } from "@/components/ui/Boton";

/**
 * Barra superior delgada (docs/plan-rediseno.md R1, §4.3) — con la marca ahora en
 * `BarraLateral` (escritorio), esta barra queda para el título de la app y el acceso a
 * usuario/cerrar sesión, visible en TODOS los anchos: en escritorio complementa al sidebar
 * (que ya repite el bloque de usuario en su pie), en móvil es la única vía para cerrar sesión
 * (la barra inferior solo lleva navegación).
 */
export function Encabezado() {
  const { data: sesion } = useSession();
  const nombreOCorreo = sesion?.user?.name ?? sesion?.user?.email ?? "";

  return (
    <header className="flex min-h-14 items-center justify-between border-b border-linea bg-white px-4 py-2 md:px-8">
      <p className="text-sm font-semibold text-tinta">Facturación Recurrente</p>
      <div className="flex items-center gap-3">
        {nombreOCorreo && <span className="hidden text-sm text-sutil sm:inline">{nombreOCorreo}</span>}
        <Boton variante="secundario" onClick={() => signOut({ redirectTo: "/login" })}>
          Cerrar sesión
        </Boton>
      </div>
    </header>
  );
}
