"use client";

import { useEffect, useMemo, useState } from "react";
import { Seleccion } from "@/components/ui/Seleccion";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { construirQueryString } from "@/lib/query";
import type { PaginaRespuesta } from "@/types/api";
import type { TipoServicio } from "@/types/tipoServicio";

const TAMANO_RESULTADOS = 200;

export interface TipoServicioResumen {
  id: number;
  nombre: string;
}

export interface PropiedadesSelectorTipoServicio {
  id: string;
  valor: number | null;
  onCambiar: (tipoServicioId: number | null) => void;
  /** El tipo de servicio actualmente asignado (al editar), igual que `SelectorCliente`. */
  tipoServicioInicial?: TipoServicioResumen | null;
}

/** Selector OPCIONAL de tipos de servicio activos (`GET /api/v1/tipos-servicio?activo=true`). */
export function SelectorTipoServicio({
  id,
  valor,
  onCambiar,
  tipoServicioInicial,
}: PropiedadesSelectorTipoServicio) {
  const [tipos, setTipos] = useState<TipoServicio[]>([]);

  useEffect(() => {
    let cancelado = false;
    const query = construirQueryString({ activo: true, size: TAMANO_RESULTADOS });
    clienteApiCliente
      .obtener<PaginaRespuesta<TipoServicio>>(`/tipos-servicio${query}`)
      .then((respuesta) => {
        if (!cancelado) {
          setTipos(respuesta.contenido);
        }
      })
      .catch(() => {
        if (!cancelado) {
          setTipos([]);
        }
      });
    return () => {
      cancelado = true;
    };
  }, []);

  const opciones = useMemo(() => {
    if (tipoServicioInicial && !tipos.some((tipo) => tipo.id === tipoServicioInicial.id)) {
      return [
        { id: tipoServicioInicial.id, nombre: tipoServicioInicial.nombre, activo: true },
        ...tipos,
      ];
    }
    return tipos;
  }, [tipos, tipoServicioInicial]);

  return (
    <Seleccion
      id={id}
      value={valor ?? ""}
      onChange={(evento) => onCambiar(evento.target.value ? Number(evento.target.value) : null)}
      className="w-full"
    >
      <option value="">Sin tipo de servicio</option>
      {opciones.map((tipo) => (
        <option key={tipo.id} value={tipo.id}>
          {tipo.nombre}
        </option>
      ))}
    </Seleccion>
  );
}
