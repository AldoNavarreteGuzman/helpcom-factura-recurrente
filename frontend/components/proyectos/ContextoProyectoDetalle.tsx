"use client";

import { createContext, useContext } from "react";
import type { Proyecto } from "@/types/proyecto";

export interface ProyectoDetalleValor {
  proyecto: Proyecto;
  /** Vuelve a pedir el proyecto al backend — llamar tras editarlo con éxito. */
  recargar: () => void;
}

const Contexto = createContext<ProyectoDetalleValor | null>(null);

export const ProveedorProyectoDetalle = Contexto.Provider;

/**
 * Comparte el `proyecto` ya cargado (y su `recargar`) entre `LayoutDetalleProyecto` (que lo
 * pide una sola vez) y la pestaña "Datos" (`DatosProyecto`) — evita que, tras editar desde
 * cualquiera de los dos, el otro quede mostrando el proyecto desactualizado hasta un refresco
 * manual (docs/frontend.md §15).
 */
export function useProyectoDetalle(): ProyectoDetalleValor {
  const contexto = useContext(Contexto);
  if (!contexto) {
    throw new Error("useProyectoDetalle debe usarse dentro de <ProveedorProyectoDetalle>.");
  }
  return contexto;
}
