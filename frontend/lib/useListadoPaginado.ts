"use client";

import { useEffect, useState } from "react";
import type { PaginaRespuesta } from "@/types/api";

export interface ResultadoListadoPaginado<T> {
  datos: T[];
  totalPaginas: number;
  cargando: boolean;
  error: unknown;
  /** Repite la última consulta sin cambiar filtros ni página (p. ej. tras crear/editar/eliminar). */
  recargar: () => void;
}

/**
 * Orquesta la carga de un listado paginado: estados de carga/error y refetch al cambiar
 * filtros o página. Pensado para reutilizarse en toda pantalla de listado (clientes, tipos
 * de servicio, y los módulos futuros — proyectos, propuestas, facturas, importaciones,
 * informe).
 *
 * `dependencias` declara explícitamente qué dispara una recarga (típicamente
 * `[pagina, ...filtros]`); `fetcher` se llama con cada cambio pero deliberadamente NO forma
 * parte del arreglo de dependencias de React —de lo contrario, al no ser una función
 * memoizada por el llamador, dispararía una recarga en cada render—. El costo de este atajo
 * es asumir que quien llama ya declaró en `dependencias` todo lo que hace variar el
 * resultado de `fetcher`.
 */
export function useListadoPaginado<T>(
  fetcher: () => Promise<PaginaRespuesta<T>>,
  dependencias: unknown[],
): ResultadoListadoPaginado<T> {
  const [respuesta, setRespuesta] = useState<PaginaRespuesta<T> | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [version, setVersion] = useState(0);

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
  }, [...dependencias, version]);

  return {
    datos: respuesta?.contenido ?? [],
    totalPaginas: respuesta
      ? Math.max(1, Math.ceil(respuesta.total / Math.max(respuesta.tamano, 1)))
      : 1,
    cargando,
    error,
    recargar: () => setVersion((actual) => actual + 1),
  };
}
