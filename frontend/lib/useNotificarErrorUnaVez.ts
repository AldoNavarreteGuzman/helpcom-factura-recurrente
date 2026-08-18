"use client";

import { useEffect, useRef } from "react";
import { useNotificaciones } from "@/components/ui/Notificaciones";

/**
 * Notifica un error como toast la primera vez que aparece (no en cada render) — para
 * pantallas full-page que no usan `FormularioDialogo` pero replican su mismo criterio de
 * mostrar el error general dos veces: notificación (no se pierde de vista) + banner propio de
 * la pantalla (ver docs/frontend.md §3.2). `FormularioDialogo` ya trae este efecto integrado;
 * este hook es el equivalente para todo lo que arma su propio layout de error a mano.
 */
export function useNotificarErrorUnaVez(error: unknown): void {
  const { notificarError } = useNotificaciones();
  const notificado = useRef<unknown>(null);

  useEffect(() => {
    if (error && error !== notificado.current) {
      notificarError(error);
    }
    notificado.current = error;
  }, [error, notificarError]);
}
