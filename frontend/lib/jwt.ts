/**
 * Decodifica el payload de un JWT SIN verificar la firma. Es intencional: el token llega
 * directamente de Keycloak por HTTPS (respuesta del endpoint de token / refresh), así que ya
 * es confiable; esto solo lee el claim `realm_access.roles` para armar la sesión. No usar
 * para validar tokens de origen no confiable.
 *
 * Implementado a mano (sin `jsonwebtoken` ni `jose`) con `atob`, disponible tanto en el
 * runtime Edge (usado por el middleware) como en Node — evita depender de `Buffer`, que no
 * existe en Edge.
 */
export function decodificarPayloadJwt(jwt: string): Record<string, unknown> {
  try {
    const partes = jwt.split(".");
    if (partes.length !== 3) {
      return {};
    }
    const base64Normalizado = partes[1].replace(/-/g, "+").replace(/_/g, "/");
    const decodificado = decodeURIComponent(
      atob(base64Normalizado)
        .split("")
        .map((caracter) => "%" + caracter.charCodeAt(0).toString(16).padStart(2, "0"))
        .join(""),
    );
    return JSON.parse(decodificado) as Record<string, unknown>;
  } catch {
    return {};
  }
}
