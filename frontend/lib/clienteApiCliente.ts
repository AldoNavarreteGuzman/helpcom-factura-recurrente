"use client";

import { getSession, signIn } from "next-auth/react";
import {
  ErrorApi,
  ejecutarFetch,
  procesarRespuesta,
  procesarRespuestaBinaria,
  type ArchivoDescargado,
  type OpcionesSolicitud,
} from "./clienteApi";

const MENSAJE_SESION_EXPIRADA = "La sesión expiró; inicia sesión nuevamente.";

/**
 * Cliente API para Client Components. `getSession()` re-evalúa el callback `jwt` de
 * `lib/auth.ts` (renovando el access token si ya venció), así que llamarlo antes de cada
 * solicitud ya cubre el caso normal. Si el backend igual responde 401 (p. ej. el token se
 * invalidó del lado de Keycloak entre medio), se vuelve a pedir la sesión UNA vez más y se
 * reintenta; si eso tampoco alcanza — o la sesión ya venía con `RefreshAccessTokenError` —
 * se fuerza el re-login con `signIn("keycloak")` en vez de seguir usando un token inválido.
 */
async function solicitarDesdeCliente<T>(ruta: string, opciones: OpcionesSolicitud): Promise<T> {
  const sesion = await getSession();
  if (sesion?.error === "RefreshAccessTokenError") {
    await signIn("keycloak");
    throw new ErrorApi(401, { detail: MENSAJE_SESION_EXPIRADA });
  }

  let respuesta = await ejecutarFetch(ruta, opciones, sesion?.accessToken);

  if (respuesta.status === 401) {
    const sesionRenovada = await getSession();
    if (!sesionRenovada?.accessToken || sesionRenovada.error) {
      await signIn("keycloak");
      throw new ErrorApi(401, { detail: MENSAJE_SESION_EXPIRADA });
    }
    respuesta = await ejecutarFetch(ruta, opciones, sesionRenovada.accessToken);
  }

  return procesarRespuesta<T>(respuesta);
}

/**
 * Descarga binaria autenticada (p. ej. el PDF de una factura, `arquitectura-tecnica.md` §14).
 * Mismo patrón de reintento ante 401 que {@link solicitarDesdeCliente}, pero sin interpretar
 * el cuerpo exitoso como JSON — por eso no comparte la misma función.
 */
async function solicitarBinarioDesdeCliente(
  ruta: string,
  nombrePorDefecto: string,
): Promise<ArchivoDescargado> {
  const sesion = await getSession();
  if (sesion?.error === "RefreshAccessTokenError") {
    await signIn("keycloak");
    throw new ErrorApi(401, { detail: MENSAJE_SESION_EXPIRADA });
  }

  let respuesta = await ejecutarFetch(ruta, { method: "GET" }, sesion?.accessToken);

  if (respuesta.status === 401) {
    const sesionRenovada = await getSession();
    if (!sesionRenovada?.accessToken || sesionRenovada.error) {
      await signIn("keycloak");
      throw new ErrorApi(401, { detail: MENSAJE_SESION_EXPIRADA });
    }
    respuesta = await ejecutarFetch(ruta, { method: "GET" }, sesionRenovada.accessToken);
  }

  return procesarRespuestaBinaria(respuesta, nombrePorDefecto);
}

export const clienteApiCliente = {
  obtener: <T>(ruta: string) => solicitarDesdeCliente<T>(ruta, { method: "GET" }),
  crear: <T>(ruta: string, body: unknown) =>
    solicitarDesdeCliente<T>(ruta, { method: "POST", body }),
  actualizar: <T>(ruta: string, body: unknown) =>
    solicitarDesdeCliente<T>(ruta, { method: "PUT", body }),
  actualizarParcial: <T>(ruta: string, body: unknown) =>
    solicitarDesdeCliente<T>(ruta, { method: "PATCH", body }),
  eliminar: <T>(ruta: string) => solicitarDesdeCliente<T>(ruta, { method: "DELETE" }),
  /** Sube un archivo multipart (campo `nombreCampo`) — p. ej. el PDF de una factura. */
  subirArchivo: <T>(ruta: string, nombreCampo: string, archivo: File) => {
    const formData = new FormData();
    formData.append(nombreCampo, archivo);
    return solicitarDesdeCliente<T>(ruta, { method: "POST", body: formData });
  },
  /**
   * Descarga autenticada de un archivo binario. NUNCA uses un `<a href>` directo al endpoint
   * del backend para esto: no llevaría el header `Authorization`, y `arquitectura-tecnica.md`
   * §14 exige que la descarga pase siempre por el backend.
   */
  descargarArchivo: (ruta: string, nombrePorDefecto: string) =>
    solicitarBinarioDesdeCliente(ruta, nombrePorDefecto),
};
