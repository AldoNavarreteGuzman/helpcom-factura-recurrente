import { ErrorApi } from "./clienteApi";

/** Mensaje legible para mostrar en una notificación (toast) ante cualquier error. */
export function obtenerMensajeError(error: unknown): string {
  if (error instanceof ErrorApi) {
    return error.problema.detail ?? error.problema.title ?? error.message;
  }
  if (error instanceof Error) {
    return error.message;
  }
  return "Ocurrió un error inesperado.";
}

/**
 * El backend reporta errores de validación (400) como `problema.errores`: una lista con los
 * NOMBRES de los campos inválidos (no un mensaje por campo — ver
 * `ManejadorGlobalErrores.manejarValidacionArgumentos` en el backend). Por eso el mensaje que
 * se muestra junto al campo es el `detail` general de la respuesta, no uno específico: es lo
 * único que el backend entrega hoy.
 */
export function obtenerErrorDeCampo(error: unknown, campo: string): string | undefined {
  if (error instanceof ErrorApi && error.problema.errores?.includes(campo)) {
    return error.problema.detail ?? "Este campo no es válido.";
  }
  return undefined;
}
