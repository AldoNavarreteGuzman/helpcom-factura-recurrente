import { redirect } from "next/navigation";
import type { ReactNode } from "react";
import { BarraInferior } from "@/components/shell/BarraInferior";
import { BarraLateral } from "@/components/shell/BarraLateral";
import { Encabezado } from "@/components/shell/Encabezado";
import { auth } from "@/lib/auth";

/**
 * Verificación de sesión de refuerzo: el middleware (`middleware.ts`) ya protege estas
 * rutas, pero volver a chequear acá con `auth()` es barato y evita depender de que el
 * middleware sea la única línea de defensa — además demuestra el uso del helper de roles en
 * un Server Component (arquitectura-tecnica.md §7).
 *
 * Shell del sistema de diseño "Confianza" (docs/plan-rediseno.md R1, §4.1): `BarraLateral`
 * (escritorio, oculta bajo `md`) y `BarraInferior` (móvil, oculta desde `md`) se alternan por
 * CSS, no por JS — cada una decide su propia visibilidad, sin lógica de breakpoint acá. `pb-20`
 * en `<main>` deja espacio para que la barra inferior fija no tape el contenido en móvil.
 */
export default async function LayoutProtegido({ children }: { children: ReactNode }) {
  const sesion = await auth();
  if (!sesion) {
    redirect("/login");
  }

  return (
    <div className="flex min-h-screen bg-fondo">
      <BarraLateral />
      <div className="flex min-h-screen flex-1 flex-col">
        <Encabezado />
        <main className="flex-1 px-4 py-6 pb-20 md:px-8 md:py-8 md:pb-8">{children}</main>
      </div>
      <BarraInferior />
    </div>
  );
}
