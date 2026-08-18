import { auth } from "@/lib/auth";

export default async function PaginaInicio() {
  const sesion = await auth();
  const nombre = sesion?.user?.name ?? sesion?.user?.email ?? "usuario";
  const roles = sesion?.roles ?? [];

  return (
    <div className="space-y-4">
      <h1 className="text-2xl font-semibold text-tinta">Hola, {nombre}</h1>
      <p className="text-texto">Roles: {roles.length > 0 ? roles.join(", ") : "sin roles asignados"}</p>
      <p className="text-sutil">
        Este es el Panel — el punto de partida del sistema. El dashboard con indicadores llega en
        una etapa posterior (docs/plan-rediseno.md R9); por ahora, usa la navegación para llegar a
        cada módulo.
      </p>
    </div>
  );
}
