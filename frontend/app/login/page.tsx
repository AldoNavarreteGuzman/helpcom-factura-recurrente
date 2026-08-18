import Image from "next/image";
import { signIn } from "@/lib/auth";

interface PropiedadesPaginaLogin {
  searchParams: Promise<{ callbackUrl?: string }>;
}

/**
 * Server Component sin JavaScript de cliente: el botón dispara una server action que llama
 * a `signIn("keycloak")` directamente, sin necesitar `"use client"` ni `next-auth/react`
 * acá. `callbackUrl` lo agrega el middleware cuando redirige aquí desde una ruta protegida.
 *
 * Logo full-color sobre fondo claro (docs/plan-rediseno.md R1, §5) — a diferencia del sidebar,
 * acá no hace falta la variante blanca.
 */
export default async function PaginaLogin({ searchParams }: PropiedadesPaginaLogin) {
  const destino = (await searchParams).callbackUrl ?? "/";

  return (
    <div className="flex min-h-screen items-center justify-center bg-fondo px-4">
      <div className="w-full max-w-sm rounded-lg border border-linea bg-white p-8 text-center shadow-tarjeta">
        <Image
          src="/Logo_Helpcom.png"
          alt="Helpcom"
          width={220}
          height={97}
          className="mx-auto h-12 w-auto"
          priority
        />
        <p className="mt-3 text-sm text-sutil">Facturación Recurrente</p>
        <form
          className="mt-6"
          action={async () => {
            "use server";
            await signIn("keycloak", { redirectTo: destino });
          }}
        >
          <button
            type="submit"
            className="w-full min-h-11 rounded bg-marca-azul px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-marca-azul-700 focus-visible:outline focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-marca-azul"
          >
            Continuar con Keycloak
          </button>
        </form>
      </div>
    </div>
  );
}
