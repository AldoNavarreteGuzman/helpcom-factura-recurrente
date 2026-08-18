/**
 * Base compartida por `clienteApiServidor.ts` y `clienteApiCliente.ts`: la normalización de
 * errores `problem+json` (arquitectura-tecnica.md / `ManejadorGlobalErrores` del backend) y
 * el `fetch` crudo. No importa nada de Auth.js a propósito — ninguno de los dos módulos que
 * la usan puede compartir su forma de resolver el token (uno usa `auth()` de servidor, el
 * otro `getSession()` de cliente), así que esa parte vive en cada uno por separado.
 */

export const URL_BASE_API = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export interface ProblemaApi {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  codigo?: string;
  errores?: string[];
}

export class ErrorApi extends Error {
  readonly status: number;
  readonly problema: ProblemaApi;

  constructor(status: number, problema: ProblemaApi) {
    super(problema.detail ?? problema.title ?? `Error de API (status ${status})`);
    this.name = "ErrorApi";
    this.status = status;
    this.problema = problema;
  }
}

export interface OpcionesSolicitud extends Omit<RequestInit, "body"> {
  body?: unknown;
}

function esFormData(valor: unknown): valor is FormData {
  return typeof FormData !== "undefined" && valor instanceof FormData;
}

export async function ejecutarFetch(
  ruta: string,
  opciones: OpcionesSolicitud,
  token?: string | null,
): Promise<Response> {
  const encabezados = new Headers(opciones.headers);
  encabezados.set("Accept", "application/json");
  const cuerpoEsFormData = esFormData(opciones.body);
  // Si el cuerpo es FormData (subida de PDF, multipart), NO se fija Content-Type a mano: el
  // navegador arma el boundary multipart solo, y fijarlo manualmente lo rompe.
  if (opciones.body !== undefined && !cuerpoEsFormData) {
    encabezados.set("Content-Type", "application/json");
  }
  if (token) {
    encabezados.set("Authorization", `Bearer ${token}`);
  }

  return fetch(`${URL_BASE_API}${ruta}`, {
    ...opciones,
    headers: encabezados,
    body: cuerpoEsFormData
      ? (opciones.body as FormData)
      : opciones.body !== undefined
        ? JSON.stringify(opciones.body)
        : undefined,
  });
}

export async function procesarRespuesta<T>(respuesta: Response): Promise<T> {
  if (respuesta.status === 204) {
    return undefined as T;
  }

  const cuerpo = await respuesta.json().catch(() => undefined);

  if (!respuesta.ok) {
    throw new ErrorApi(respuesta.status, (cuerpo as ProblemaApi) ?? {});
  }

  return cuerpo as T;
}

export interface ArchivoDescargado {
  blob: Blob;
  nombreArchivo: string;
}

/** Extrae el nombre de archivo de un header `Content-Disposition: attachment; filename="…"`. */
function extraerNombreDeDisposicion(disposicion: string | null): string | null {
  if (!disposicion) {
    return null;
  }
  const conCodificacion = /filename\*=UTF-8''([^;]+)/i.exec(disposicion);
  if (conCodificacion) {
    return decodeURIComponent(conCodificacion[1]);
  }
  const simple = /filename="?([^";]+)"?/i.exec(disposicion);
  return simple ? simple[1] : null;
}

/**
 * Para respuestas binarias (descarga del PDF de una factura, `arquitectura-tecnica.md` §14:
 * el acceso pasa siempre por el backend, nunca una URL directa del bucket): a diferencia de
 * {@link procesarRespuesta}, el cuerpo exitoso NO se interpreta como JSON — solo el de error,
 * que el backend sigue devolviendo como `problem+json`.
 */
export async function procesarRespuestaBinaria(
  respuesta: Response,
  nombrePorDefecto: string,
): Promise<ArchivoDescargado> {
  if (!respuesta.ok) {
    const cuerpo = await respuesta.json().catch(() => undefined);
    throw new ErrorApi(respuesta.status, (cuerpo as ProblemaApi) ?? {});
  }

  const blob = await respuesta.blob();
  const nombreArchivo =
    extraerNombreDeDisposicion(respuesta.headers.get("Content-Disposition")) ?? nombrePorDefecto;
  return { blob, nombreArchivo };
}
