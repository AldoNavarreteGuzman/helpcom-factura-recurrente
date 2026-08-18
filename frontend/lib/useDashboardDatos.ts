"use client";

import { useEffect, useState } from "react";
import { clienteApiCliente } from "./clienteApiCliente";
import type { PaginaRespuesta } from "@/types/api";
import type { Proyecto } from "@/types/proyecto";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

const TAMANO_PAGINA_COMPLETA = 500;

export interface DatosDashboard {
  propuestas: PropuestaFacturacion[] | null;
  proyectos: Proyecto[] | null;
  cargando: boolean;
  error: unknown;
}

/**
 * Fuente de datos ÚNICA de todo el dashboard (`components/dashboard/`, docs/frontend.md R9):
 * todas las tarjetas se derivan de estas dos listas completas (`lib/dashboardCalculos.ts`), no
 * hay un endpoint de agregación por tarjeta — ver la justificación completa en R9. `size=500`
 * trae las 67 propuestas/4 proyectos reales en una sola página cada uno (muy por debajo del
 * `max-page-size` de Spring Data, sin configurar en este backend); si el volumen crece mucho
 * más, este patrón deja de ser el correcto y hace falta un endpoint de agregación real —
 * anotado como tal, no resuelto acá (mismo criterio que "tendencia mensual",
 * docs/plan-rediseno.md §6.2).
 *
 * Al compartir una sola fuente, si esta falla TODAS las tarjetas que dependen de ella muestran
 * su propio estado de error (cada una en su lugar, sin tumbar el resto de la pantalla) — no hay
 * tarjetas con una fuente de datos genuinamente independiente en este dashboard hoy.
 */
export function useDashboardDatos(): DatosDashboard {
  const [propuestas, setPropuestas] = useState<PropuestaFacturacion[] | null>(null);
  const [proyectos, setProyectos] = useState<Proyecto[] | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<unknown>(null);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setError(null);

    Promise.all([
      clienteApiCliente.obtener<PaginaRespuesta<PropuestaFacturacion>>(
        `/propuestas?size=${TAMANO_PAGINA_COMPLETA}`,
      ),
      clienteApiCliente.obtener<PaginaRespuesta<Proyecto>>(
        `/proyectos?size=${TAMANO_PAGINA_COMPLETA}`,
      ),
    ])
      .then(([paginaPropuestas, paginaProyectos]) => {
        if (!cancelado) {
          setPropuestas(paginaPropuestas.contenido);
          setProyectos(paginaProyectos.contenido);
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
  }, []);

  return { propuestas, proyectos, cargando, error };
}
