"use client";

import { SessionProvider } from "next-auth/react";
import type { ReactNode } from "react";

/** Habilita `useSession()` en Client Components (Encabezado, Navegacion, clienteApiCliente). */
export function ProveedorSesion({ children }: { children: ReactNode }) {
  return <SessionProvider>{children}</SessionProvider>;
}
