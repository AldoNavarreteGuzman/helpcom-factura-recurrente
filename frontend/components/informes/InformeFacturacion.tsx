"use client";

import { useCallback, useMemo, useState } from "react";
import { ResumenInforme } from "./ResumenInforme";
import { BadgeEstadoPropuesta } from "@/components/facturacion/propuestas/BadgeEstadoPropuesta";
import { SelectorCliente } from "@/components/clientes/SelectorCliente";
import { PanelListado } from "@/components/listado/PanelListado";
import { Boton } from "@/components/ui/Boton";
import { CampoFormulario } from "@/components/ui/CampoFormulario";
import { Casilla } from "@/components/ui/Casilla";
import { Entrada } from "@/components/ui/Entrada";
import { useNotificaciones } from "@/components/ui/Notificaciones";
import { Seleccion } from "@/components/ui/Seleccion";
import type { ColumnaTabla } from "@/components/ui/Tabla";
import { descargarArchivoEnNavegador } from "@/lib/archivos";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import {
  ETIQUETAS_ESTADO_PROPUESTA,
  ETIQUETAS_ORIGEN_PROPUESTA,
  NOMBRES_MES,
  formatearPeriodo,
} from "@/lib/etiquetas";
import { formatearFecha, formatearMontoEnMoneda } from "@/lib/formato";
import { formatearMontoClpOAusente } from "@/lib/propuestas";
import { construirQueryString } from "@/lib/query";
import { useInformeFacturacion } from "@/lib/useInformeFacturacion";
import type { EstadoPropuesta, OrigenPropuesta } from "@/types/dominio";
import type {
  InformeFacturacionDetalleFila,
  InformeFacturacionRespuesta,
} from "@/types/informeFacturacion";

const TAMANO_PAGINA = 20;
const ESTADOS: EstadoPropuesta[] = ["PENDIENTE", "PENDIENTE_UF", "FACTURADA", "ANULADA"];
const ORIGENES: OrigenPropuesta[] = ["CICLO", "CSV"];

/** `"AAAA-MM"` (valor nativo de `<input type="month">`) → `AAAAMM` numérico que espera el backend. */
function codificarAnioMes(valor: string): number | undefined {
  if (!valor) {
    return undefined;
  }
  const [anio, mes] = valor.split("-");
  if (!anio || !mes) {
    return undefined;
  }
  return Number(anio + mes);
}

/**
 * Informe de facturación — "lo que se va a facturar" (arquitectura-tecnica.md §11). Filtros +
 * resumen + detalle paginado, todos con los MISMOS filtros (el backend los devuelve juntos en
 * una sola respuesta, `docs/frontend.md` §8.1).
 */
export function InformeFacturacion() {
  const { notificarError } = useNotificaciones();

  const [periodoAnio, setPeriodoAnio] = useState("");
  const [periodoMes, setPeriodoMes] = useState("");
  const [anioMesDesde, setAnioMesDesde] = useState("");
  const [anioMesHasta, setAnioMesHasta] = useState("");
  const [clienteId, setClienteId] = useState<number | null>(null);
  const [estados, setEstados] = useState<EstadoPropuesta[]>([]);
  const [origen, setOrigen] = useState("");
  const [facturada, setFacturada] = useState("");
  const [pagina, setPagina] = useState(0);
  const [exportando, setExportando] = useState(false);

  function alternarEstado(estado: EstadoPropuesta) {
    setEstados((actuales) =>
      actuales.includes(estado) ? actuales.filter((e) => e !== estado) : [...actuales, estado],
    );
    setPagina(0);
  }

  const construirFiltros = useCallback(
    () => ({
      periodoAnio: periodoAnio || undefined,
      periodoMes: periodoMes || undefined,
      anioMesDesde: codificarAnioMes(anioMesDesde),
      anioMesHasta: codificarAnioMes(anioMesHasta),
      clienteId: clienteId ?? undefined,
      estados,
      origen: origen || undefined,
      facturada: facturada || undefined,
    }),
    [periodoAnio, periodoMes, anioMesDesde, anioMesHasta, clienteId, estados, origen, facturada],
  );

  const fetcher = useCallback(() => {
    const query = construirQueryString({
      ...construirFiltros(),
      page: pagina,
      size: TAMANO_PAGINA,
    });
    return clienteApiCliente.obtener<InformeFacturacionRespuesta>(`/informes/facturacion${query}`);
  }, [construirFiltros, pagina]);

  const { resumen, detalle, cargando, error } = useInformeFacturacion(fetcher, [
    periodoAnio,
    periodoMes,
    anioMesDesde,
    anioMesHasta,
    clienteId,
    estados,
    origen,
    facturada,
    pagina,
  ]);

  const totalPaginas = detalle
    ? Math.max(1, Math.ceil(detalle.total / Math.max(detalle.tamano, 1)))
    : 1;

  async function exportarCsv() {
    setExportando(true);
    try {
      const query = construirQueryString(construirFiltros());
      // El nombre viene del Content-Disposition del backend (ya arma uno descriptivo según
      // el período/rango filtrado); "informe-facturacion.csv" solo aplica si el header
      // faltara o no se pudiera interpretar.
      const descarga = await clienteApiCliente.descargarArchivo(
        `/informes/facturacion/export${query}`,
        "informe-facturacion.csv",
      );
      descargarArchivoEnNavegador({
        blob: descarga.blob,
        nombreArchivo: descarga.nombreArchivo,
      });
    } catch (error) {
      notificarError(error);
    } finally {
      setExportando(false);
    }
  }

  const columnas = useMemo<ColumnaTabla<InformeFacturacionDetalleFila>[]>(
    () => [
      { encabezado: "Cliente", renderizar: (fila) => fila.clienteRazonSocial },
      { encabezado: "Proyecto", renderizar: (fila) => fila.proyectoNombre ?? "—" },
      { encabezado: "Descripción", renderizar: (fila) => fila.descripcion },
      {
        encabezado: "Período",
        renderizar: (fila) => formatearPeriodo(fila.periodoAnio, fila.periodoMes),
      },
      {
        encabezado: "Fecha facturación",
        renderizar: (fila) => formatearFecha(fila.fechaFacturacion),
      },
      { encabezado: "Moneda", renderizar: (fila) => fila.monedaOrigen },
      {
        encabezado: "Valor UF",
        renderizar: (fila) =>
          fila.valorUf != null ? formatearMontoEnMoneda(fila.valorUf, "UF") : "—",
      },
      {
        encabezado: "Neto",
        renderizar: (fila) => formatearMontoClpOAusente(fila.netoClp, fila.estado),
      },
      {
        encabezado: "IVA",
        renderizar: (fila) => formatearMontoClpOAusente(fila.ivaClp, fila.estado),
      },
      {
        encabezado: "Total",
        renderizar: (fila) => formatearMontoClpOAusente(fila.totalClp, fila.estado),
      },
      { encabezado: "Estado", renderizar: (fila) => <BadgeEstadoPropuesta estado={fila.estado} /> },
      { encabezado: "Origen", renderizar: (fila) => ETIQUETAS_ORIGEN_PROPUESTA[fila.origen] },
      { encabezado: "N° factura", renderizar: (fila) => fila.numeroFactura ?? "—" },
    ],
    [],
  );

  return (
    <div className="space-y-6">
      <h1 className="text-2xl font-semibold text-tinta">Informe de facturación</h1>

      {resumen ? (
        <ResumenInforme
          resumen={resumen}
          filtrosParaPropuestas={{
            periodoAnio: periodoAnio ? Number(periodoAnio) : undefined,
            periodoMes: periodoMes ? Number(periodoMes) : undefined,
            clienteId,
            origen: origen || undefined,
          }}
        />
      ) : null}

      <PanelListado
        titulo="Detalle"
        columnas={columnas}
        filas={detalle?.contenido ?? []}
        obtenerClave={(fila) => fila.id}
        cargando={cargando}
        error={error}
        paginaActual={pagina}
        totalPaginas={totalPaginas}
        onCambiarPagina={setPagina}
        mensajeVacio="No hay propuestas que coincidan con los filtros."
        accionPrincipal={
          <Boton variante="secundario" onClick={exportarCsv} cargando={exportando}>
            Exportar CSV
          </Boton>
        }
        filtros={
          <>
            <CampoFormulario id="filtro-periodo-anio" etiqueta="Año">
              <Entrada
                id="filtro-periodo-anio"
                type="number"
                min={2000}
                max={3000}
                value={periodoAnio}
                onChange={(evento) => {
                  setPeriodoAnio(evento.target.value);
                  setPagina(0);
                }}
                className="w-24"
              />
            </CampoFormulario>
            <CampoFormulario id="filtro-periodo-mes" etiqueta="Mes">
              <Seleccion
                id="filtro-periodo-mes"
                value={periodoMes}
                onChange={(evento) => {
                  setPeriodoMes(evento.target.value);
                  setPagina(0);
                }}
              >
                <option value="">Todos</option>
                {NOMBRES_MES.map((nombre, indice) => (
                  <option key={nombre} value={indice + 1}>
                    {nombre}
                  </option>
                ))}
              </Seleccion>
            </CampoFormulario>
            <CampoFormulario
              id="filtro-anio-mes-desde"
              etiqueta="Desde"
              descripcion="Rango de períodos (se intersecta con Año/Mes si ambos se usan)."
            >
              <Entrada
                id="filtro-anio-mes-desde"
                type="month"
                value={anioMesDesde}
                onChange={(evento) => {
                  setAnioMesDesde(evento.target.value);
                  setPagina(0);
                }}
              />
            </CampoFormulario>
            <CampoFormulario id="filtro-anio-mes-hasta" etiqueta="Hasta">
              <Entrada
                id="filtro-anio-mes-hasta"
                type="month"
                value={anioMesHasta}
                onChange={(evento) => {
                  setAnioMesHasta(evento.target.value);
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
            <CampoFormulario id="filtro-origen" etiqueta="Origen">
              <Seleccion
                id="filtro-origen"
                value={origen}
                onChange={(evento) => {
                  setOrigen(evento.target.value);
                  setPagina(0);
                }}
              >
                <option value="">Todos</option>
                {ORIGENES.map((valor) => (
                  <option key={valor} value={valor}>
                    {ETIQUETAS_ORIGEN_PROPUESTA[valor]}
                  </option>
                ))}
              </Seleccion>
            </CampoFormulario>
            <CampoFormulario id="filtro-facturada" etiqueta="Facturación">
              <Seleccion
                id="filtro-facturada"
                value={facturada}
                onChange={(evento) => {
                  setFacturada(evento.target.value);
                  setPagina(0);
                }}
              >
                <option value="">Todas</option>
                <option value="true">Facturadas</option>
                <option value="false">No facturadas</option>
              </Seleccion>
            </CampoFormulario>
            <CampoFormulario id="filtro-estados" etiqueta="Estado">
              <div className="flex flex-wrap gap-3 pt-2">
                {ESTADOS.map((estado) => (
                  <Casilla
                    key={estado}
                    etiqueta={ETIQUETAS_ESTADO_PROPUESTA[estado]}
                    checked={estados.includes(estado)}
                    onChange={() => alternarEstado(estado)}
                  />
                ))}
              </div>
            </CampoFormulario>
          </>
        }
      />
    </div>
  );
}
