import { Dashboard } from "@/components/dashboard/Dashboard";
import { auth } from "@/lib/auth";

export default async function PaginaInicio() {
  const sesion = await auth();
  const nombre = sesion?.user?.name ?? sesion?.user?.email ?? "usuario";

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold text-tinta">Hola, {nombre}</h1>
      <Dashboard />
    </div>
  );
}
