import { redirect } from "next/navigation";
import { auth } from "./auth";
import { ejecutarFetch, procesarRespuesta, type OpcionesSolicitud } from "./clienteApi";

/**
 * Cliente API para Server Components y Route Handlers. `auth()` ya deja el access token al
 * día (el callback `jwt` de `lib/auth.ts` lo renueva por sí solo si está por expirar), así
 * que acá no hay que reintentar nada en el camino feliz. Si no hay sesión, o la renovación
 * ya falló antes (`RefreshAccessTokenError`), o el backend igual responde 401, se redirige a
 * `/login` — un Server Component no puede "reintentar interactivamente" como sí puede un
 * Client Component.
 */
async function solicitarDesdeServidor<T>(ruta: string, opciones: OpcionesSolicitud): Promise<T> {
  const sesion = await auth();
  if (!sesion || sesion.error === "RefreshAccessTokenError") {
    redirect("/login");
  }

  const respuesta = await ejecutarFetch(ruta, opciones, sesion.accessToken);
  if (respuesta.status === 401) {
    redirect("/login");
  }

  return procesarRespuesta<T>(respuesta);
}

export const clienteApiServidor = {
  obtener: <T>(ruta: string) => solicitarDesdeServidor<T>(ruta, { method: "GET" }),
  crear: <T>(ruta: string, body: unknown) =>
    solicitarDesdeServidor<T>(ruta, { method: "POST", body }),
  actualizar: <T>(ruta: string, body: unknown) =>
    solicitarDesdeServidor<T>(ruta, { method: "PUT", body }),
  actualizarParcial: <T>(ruta: string, body: unknown) =>
    solicitarDesdeServidor<T>(ruta, { method: "PATCH", body }),
  eliminar: <T>(ruta: string) => solicitarDesdeServidor<T>(ruta, { method: "DELETE" }),
};
