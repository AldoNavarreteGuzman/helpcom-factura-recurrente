"use client";

import { useEffect, useMemo, useState } from "react";
import { Entrada } from "@/components/ui/Entrada";
import { Seleccion } from "@/components/ui/Seleccion";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { construirQueryString } from "@/lib/query";
import { formatearRut } from "@/lib/rut";
import type { PaginaRespuesta } from "@/types/api";
import type { Cliente } from "@/types/cliente";

const TAMANO_RESULTADOS = 50;

export interface ClienteResumen {
  id: number;
  razonSocial: string;
}

export interface PropiedadesSelectorCliente {
  id: string;
  valor: number | null;
  onCambiar: (clienteId: number | null) => void;
  invalida?: boolean;
  /**
   * El cliente actualmente asignado (al editar), para que su opción siga apareciendo aunque
   * no matchee la búsqueda activa o el cliente ya no esté activo. Basta con lo que ya trae
   * `ProyectoRespuestaDto` (`clienteId`/`clienteRazonSocial`) — no hace falta una consulta
   * extra.
   */
  clienteInicial?: ClienteResumen | null;
}

/**
 * Selector de clientes ACTIVOS con búsqueda (reutiliza `GET /api/v1/clientes?texto=...`).
 * Reutilizable por cualquier formulario que necesite elegir un cliente (proyectos hoy;
 * facturación/importación después).
 */
export function SelectorCliente({
  id,
  valor,
  onCambiar,
  invalida,
  clienteInicial,
}: PropiedadesSelectorCliente) {
  const [texto, setTexto] = useState("");
  const [clientes, setClientes] = useState<Cliente[]>([]);
  const [cargando, setCargando] = useState(false);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    const query = construirQueryString({
      texto: texto.trim() || undefined,
      activo: true,
      size: TAMANO_RESULTADOS,
    });
    clienteApiCliente
      .obtener<PaginaRespuesta<Cliente>>(`/clientes${query}`)
      .then((respuesta) => {
        if (!cancelado) {
          setClientes(respuesta.contenido);
        }
      })
      .catch(() => {
        if (!cancelado) {
          setClientes([]);
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
  }, [texto]);

  const opciones = useMemo(() => {
    if (clienteInicial && !clientes.some((cliente) => cliente.id === clienteInicial.id)) {
      return [
        { id: clienteInicial.id, razonSocial: clienteInicial.razonSocial, rut: "" } as Cliente,
        ...clientes,
      ];
    }
    return clientes;
  }, [clientes, clienteInicial]);

  return (
    <div className="space-y-1">
      <Entrada
        type="text"
        placeholder="Buscar por razón social o RUT…"
        value={texto}
        onChange={(evento) => setTexto(evento.target.value)}
        className="w-full"
        aria-label="Buscar cliente"
      />
      <Seleccion
        id={id}
        value={valor ?? ""}
        onChange={(evento) => onCambiar(evento.target.value ? Number(evento.target.value) : null)}
        aria-invalid={invalida}
        className="w-full"
      >
        <option value="">{cargando ? "Cargando…" : "Selecciona un cliente"}</option>
        {opciones.map((cliente) => (
          <option key={cliente.id} value={cliente.id}>
            {cliente.razonSocial}
            {cliente.rut ? ` (${formatearRut(cliente.rut)})` : ""}
          </option>
        ))}
      </Seleccion>
    </div>
  );
}
