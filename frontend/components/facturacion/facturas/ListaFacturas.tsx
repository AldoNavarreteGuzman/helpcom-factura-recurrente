"use client";

import Link from "next/link";
import { useCallback, useMemo, useState } from "react";
import { SelectorCliente } from "@/components/clientes/SelectorCliente";
import { PanelListado } from "@/components/listado/PanelListado";
import { CampoFormulario } from "@/components/ui/CampoFormulario";
import { Entrada } from "@/components/ui/Entrada";
import type { ColumnaTabla } from "@/components/ui/Tabla";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { formatearClp, formatearFecha } from "@/lib/formato";
import { construirQueryString } from "@/lib/query";
import { useListadoPaginado } from "@/lib/useListadoPaginado";
import type { PaginaRespuesta } from "@/types/api";
import type { Factura } from "@/types/factura";

const TAMANO_PAGINA = 20;

function totalFactura(factura: Factura): number {
  return factura.propuestas.reduce((acumulado, propuesta) => acumulado + propuesta.totalClp, 0);
}

export function ListaFacturas() {
  const [pagina, setPagina] = useState(0);
  const [numero, setNumero] = useState("");
  const [fechaDesde, setFechaDesde] = useState("");
  const [fechaHasta, setFechaHasta] = useState("");
  const [clienteId, setClienteId] = useState<number | null>(null);

  const fetcher = useCallback(() => {
    const query = construirQueryString({
      numero: numero.trim() || undefined,
      fechaDesde: fechaDesde || undefined,
      fechaHasta: fechaHasta || undefined,
      clienteId: clienteId ?? undefined,
      page: pagina,
      size: TAMANO_PAGINA,
    });
    return clienteApiCliente.obtener<PaginaRespuesta<Factura>>(`/facturas${query}`);
  }, [numero, fechaDesde, fechaHasta, clienteId, pagina]);

  const { datos, totalPaginas, cargando, error } = useListadoPaginado(fetcher, [
    numero,
    fechaDesde,
    fechaHasta,
    clienteId,
    pagina,
  ]);

  const columnas = useMemo<ColumnaTabla<Factura>[]>(
    () => [
      {
        encabezado: "N° factura",
        renderizar: (factura) => (
          <Link
            href={`/facturacion/facturas/${factura.id}`}
            className="font-medium text-marca-azul hover:underline"
          >
            {factura.numeroFactura}
          </Link>
        ),
      },
      { encabezado: "Fecha", renderizar: (factura) => formatearFecha(factura.fechaFactura) },
      { encabezado: "Cliente", renderizar: (factura) => factura.clienteRazonSocial ?? "—" },
      { encabezado: "Propuestas", renderizar: (factura) => factura.propuestas.length },
      { encabezado: "Total", renderizar: (factura) => formatearClp(totalFactura(factura)) },
      {
        encabezado: "PDF",
        renderizar: (factura) => (
          <span className={factura.tienePdf ? "text-estado-facturada" : "text-sutil"}>
            {factura.tienePdf ? "Sí" : "No"}
          </span>
        ),
      },
    ],
    [],
  );

  return (
    <PanelListado
      titulo="Facturas"
      columnas={columnas}
      filas={datos}
      obtenerClave={(factura) => factura.id}
      cargando={cargando}
      error={error}
      paginaActual={pagina}
      totalPaginas={totalPaginas}
      onCambiarPagina={setPagina}
      mensajeVacio="No hay facturas que coincidan con los filtros."
      accionPrincipal={
        <div className="flex items-center gap-4">
          <Link href="/facturacion" className="text-sm text-sutil hover:text-marca-azul">
            ← Propuestas
          </Link>
          <Link
            href="/facturacion/facturas/nueva"
            className="inline-flex min-h-11 items-center justify-center gap-2 rounded bg-marca-azul px-4 py-2 text-sm font-medium text-white transition-colors hover:bg-marca-azul-700 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-marca-azul focus-visible:ring-offset-2"
          >
            + Nueva factura
          </Link>
        </div>
      }
      filtros={
        <>
          <CampoFormulario id="filtro-numero" etiqueta="N° factura">
            <Entrada
              id="filtro-numero"
              value={numero}
              onChange={(evento) => {
                setNumero(evento.target.value);
                setPagina(0);
              }}
              placeholder="Buscar por número…"
              className="w-40"
            />
          </CampoFormulario>
          <CampoFormulario id="filtro-fecha-desde" etiqueta="Desde">
            <Entrada
              id="filtro-fecha-desde"
              type="date"
              value={fechaDesde}
              onChange={(evento) => {
                setFechaDesde(evento.target.value);
                setPagina(0);
              }}
            />
          </CampoFormulario>
          <CampoFormulario id="filtro-fecha-hasta" etiqueta="Hasta">
            <Entrada
              id="filtro-fecha-hasta"
              type="date"
              value={fechaHasta}
              onChange={(evento) => {
                setFechaHasta(evento.target.value);
                setPagina(0);
              }}
            />
          </CampoFormulario>
          <CampoFormulario id="filtro-cliente" etiqueta="Cliente">
            <SelectorCliente
              id="filtro-cliente"
              valor={clienteId}
              onCambiar={(id) => {
                setClienteId(id);
                setPagina(0);
              }}
            />
          </CampoFormulario>
        </>
      }
    />
  );
}
