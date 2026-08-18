import type { ArchivoDescargado } from "./clienteApi";

/**
 * Dispara la descarga en el navegador de un archivo ya obtenido vía
 * `clienteApiCliente.descargarArchivo` (fetch autenticado → blob). No hay otra forma de
 * "guardar" un blob en el navegador salvo simular el click de un enlace efímero.
 */
export function descargarArchivoEnNavegador({ blob, nombreArchivo }: ArchivoDescargado): void {
  const url = URL.createObjectURL(blob);
  const enlace = document.createElement("a");
  enlace.href = url;
  enlace.download = nombreArchivo;
  document.body.appendChild(enlace);
  enlace.click();
  document.body.removeChild(enlace);
  URL.revokeObjectURL(url);
}
