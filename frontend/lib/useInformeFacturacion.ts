"use client";

import { useEffect, useState } from "react";
import type { InformeFacturacionRespuesta } from "@/types/informeFacturacion";

export interface ResultadoInformeFacturacion {
  resumen: InformeFacturacionRespuesta["resumen"] | null;
  detalle: InformeFacturacionRespuesta["detalle"] | null;
  cargando: boolean;
  error: unknown;
}

/**
 * Mismo criterio que `useListadoPaginado` (§3.1): dependencias explícitas, `fetcher`
 * deliberadamente fuera del arreglo de dependencias del `useEffect` (no está memoizado por
 * quien llama). No se pudo reutilizar `useListadoPaginado` tal cual porque ese hook asume que
 * el `fetcher` retorna directamente una `PaginaRespuesta<T>` — el informe devuelve resumen +
 * detalle paginado en UNA sola respuesta (comparten los mismos filtros,
 * `arquitectura-tecnica.md` §11), así que hace falta un hook que retenga ambas partes en vez
 * de descartar el resumen.
 */
export function useInformeFacturacion(
  fetcher: () => Promise<InformeFacturacionRespuesta>,
  dependencias: unknown[],
): ResultadoInformeFacturacion {
  const [respuesta, setRespuesta] = useState<InformeFacturacionRespuesta | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setError(null);

    fetcher()
      .then((datos) => {
        if (!cancelado) {
          setRespuesta(datos);
        }
      })
      .catch((error: unknown) => {
        if (!cancelado) {
          setError(error);
        }
      })
      .finally(() => {
        if (!cancelado) {
          setCargando(false);
        }
      });

    return () => {
      cancelado = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, dependencias);

  return {
    resumen: respuesta?.resumen ?? null,
    detalle: respuesta?.detalle ?? null,
    cargando,
    error,
  };
}
