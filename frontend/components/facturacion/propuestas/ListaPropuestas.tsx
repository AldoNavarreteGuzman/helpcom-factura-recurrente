"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import { useCallback, useMemo, useState } from "react";
import { BadgeEstadoPropuesta } from "./BadgeEstadoPropuesta";
import { DialogoDetallePropuesta } from "./DialogoDetallePropuesta";
import { SelectorCliente } from "@/components/clientes/SelectorCliente";
import { PanelListado } from "@/components/listado/PanelListado";
import { CampoFormulario } from "@/components/ui/CampoFormulario";
import { DialogoConfirmacion } from "@/components/ui/DialogoConfirmacion";
import { Entrada } from "@/components/ui/Entrada";
import { useNotificaciones } from "@/components/ui/Notificaciones";
import { Seleccion } from "@/components/ui/Seleccion";
import type { ColumnaTabla } from "@/components/ui/Tabla";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import {
  ETIQUETAS_ESTADO_PROPUESTA,
  ETIQUETAS_ORIGEN_PROPUESTA,
  NOMBRES_MES,
  formatearPeriodo,
} from "@/lib/etiquetas";
import { formatearFecha, formatearMontoEnMoneda } from "@/lib/formato";
import { esAnulable, esReprocesableUf, formatearMontoClpOAusente } from "@/lib/propuestas";
import { construirQueryString } from "@/lib/query";
import { useListadoPaginado } from "@/lib/useListadoPaginado";
import { useTieneAlgunRol } from "@/lib/useRoles";
import type { PaginaRespuesta } from "@/types/api";
import type { EstadoPropuesta, OrigenPropuesta } from "@/types/dominio";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

const TAMANO_PAGINA = 20;

const ESTADOS: EstadoPropuesta[] = ["PENDIENTE", "PENDIENTE_UF", "FACTURADA", "ANULADA"];
const ORIGENES: OrigenPropuesta[] = ["CICLO", "CSV"];

export function ListaPropuestas() {
  const parametrosBusqueda = useSearchParams();
  // Mismo rol gatea anular y reprocesar UF — ambas mutan el estado de una propuesta y son
  // solo-ADMINISTRADOR (estandares-de-codigo.md §3.11, backend).
  const esAdministrador = useTieneAlgunRol(["ADMINISTRADOR"]);
  const { notificar, notificarError } = useNotificaciones();

  const [pagina, setPagina] = useState(0);
  const [periodoAnio, setPeriodoAnio] = useState(() => parametrosBusqueda.get("periodoAnio") ?? "");
  const [periodoMes, setPeriodoMes] = useState(() => parametrosBusqueda.get("periodoMes") ?? "");
  const [clienteId, setClienteId] = useState<number | null>(() => {
    const valor = parametrosBusqueda.get("clienteId");
    return valor ? Number(valor) : null;
  });
  const [estado, setEstado] = useState(() => parametrosBusqueda.get("estado") ?? "");
  const [origen, setOrigen] = useState(() => parametrosBusqueda.get("origen") ?? "");

  const [propuestaDetalle, setPropuestaDetalle] = useState<PropuestaFacturacion | null>(null);
  const [propuestaAAnular, setPropuestaAAnular] = useState<PropuestaFacturacion | null>(null);
  const [anulando, setAnulando] = useState(false);
  // Por id, no un booleano global: puede haber varias filas PENDIENTE_UF a la vista y cada
  // botón debe deshabilitarse solo mientras SU propio PATCH está en vuelo.
  const [reprocesandoId, setReprocesandoId] = useState<number | null>(null);

  const fetcher = useCallback(() => {
    const query = construirQueryString({
      periodoAnio: periodoAnio || undefined,
      periodoMes: periodoMes || undefined,
      clienteId: clienteId ?? undefined,
      estado: estado || undefined,
      origen: origen || undefined,
      page: pagina,
      size: TAMANO_PAGINA,
    });
    return clienteApiCliente.obtener<PaginaRespuesta<PropuestaFacturacion>>(`/propuestas${query}`);
  }, [periodoAnio, periodoMes, clienteId, estado, origen, pagina]);

  const { datos, totalPaginas, cargando, error, recargar } = useListadoPaginado(fetcher, [
    periodoAnio,
    periodoMes,
    clienteId,
    estado,
    origen,
    pagina,
  ]);

  async function confirmarAnulacion() {
    if (!propuestaAAnular) {
      return;
    }
    setAnulando(true);
    try {
      await clienteApiCliente.actualizarParcial(
        `/propuestas/${propuestaAAnular.id}/anular`,
        undefined,
      );
      notificar("Propuesta anulada.", "exito");
      setPropuestaAAnular(null);
      recargar();
    } catch (error) {
      notificarError(error);
    } finally {
      setAnulando(false);
    }
  }

  /**
   * Reprocesar UF (deuda-tecnica.md ítem 8/9): el endpoint es seguro de reintentar cuantas
   * veces haga falta y SIEMPRE responde 200 con la propuesta (nunca falla por "seguir sin UF"
   * — eso no es un error, es un resultado legítimo). Por eso el resultado se decide mirando el
   * `estado` de la respuesta, no el éxito/fracaso de la llamada: si sigue `PENDIENTE_UF` es un
   * aviso informativo (ámbar, ni error ni éxito falso); si pasó a `PENDIENTE` es éxito real, con
   * el neto ya calculado. Sin diálogo de confirmación — a diferencia de anular, no es
   * destructivo ni irreversible, así que pedir "¿estás seguro?" no aportaría nada.
   */
  const reprocesarUf = useCallback(
    async (propuesta: PropuestaFacturacion) => {
      setReprocesandoId(propuesta.id);
      try {
        const actualizada = await clienteApiCliente.actualizarParcial<PropuestaFacturacion>(
          `/propuestas/${propuesta.id}/reprocesar-uf`,
          undefined,
        );
        if (actualizada.estado === "PENDIENTE_UF") {
          notificar(
            "No se pudo obtener la UF de esta fecha (puede que aún no esté publicada). La propuesta sigue pendiente.",
            "advertencia",
          );
        } else {
          notificar("Propuesta reprocesada: ya tiene su neto calculado.", "exito");
        }
        recargar();
      } catch (error) {
        notificarError(error);
      } finally {
        setReprocesandoId(null);
      }
    },
    [notificar, notificarError, recargar],
  );

  const columnas = useMemo<ColumnaTabla<PropuestaFacturacion>[]>(
    () => [
      { encabezado: "Cliente", renderizar: (p) => p.clienteRazonSocial },
      { encabezado: "Proyecto", renderizar: (p) => p.proyectoNombre ?? "—" },
      { encabezado: "Descripción", renderizar: (p) => p.descripcion },
      { encabezado: "Período", renderizar: (p) => formatearPeriodo(p.periodoAnio, p.periodoMes) },
      { encabezado: "Fecha facturación", renderizar: (p) => formatearFecha(p.fechaFacturacion) },
      { encabezado: "Moneda", renderizar: (p) => p.monedaOrigen },
      {
        encabezado: "Valor UF",
        renderizar: (p) =>
          p.valorUf != null ? formatearMontoEnMoneda(p.valorUf, "UF") : "— (sin UF)",
      },
      {
        encabezado: "Neto",
        renderizar: (p) => formatearMontoClpOAusente(p.netoClp, p.estado),
        alineacion: "derecha",
      },
      {
        encabezado: "IVA",
        renderizar: (p) => formatearMontoClpOAusente(p.ivaClp, p.estado),
        alineacion: "derecha",
      },
      {
        encabezado: "Total",
        renderizar: (p) => formatearMontoClpOAusente(p.totalClp, p.estado),
        alineacion: "derecha",
      },
      { encabezado: "Estado", renderizar: (p) => <BadgeEstadoPropuesta estado={p.estado} /> },
      { encabezado: "Origen", renderizar: (p) => ETIQUETAS_ORIGEN_PROPUESTA[p.origen] },
      { encabezado: "N° factura", renderizar: (p) => p.numeroFactura ?? "—" },
      {
        encabezado: "",
        renderizar: (p) => (
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={() => setPropuestaDetalle(p)}
              className="text-sm font-medium text-sutil hover:text-marca-azul"
            >
              Ver detalle
            </button>
            {esReprocesableUf(p.estado) ? (
              <button
                type="button"
                onClick={() => reprocesarUf(p)}
                disabled={!esAdministrador || reprocesandoId === p.id}
                title={esAdministrador ? undefined : "Requiere el rol ADMINISTRADOR."}
                className="text-sm font-medium text-marca-azul hover:text-marca-azul-700 disabled:cursor-not-allowed disabled:opacity-40"
              >
                {reprocesandoId === p.id ? "Reprocesando…" : "Reprocesar UF"}
              </button>
            ) : null}
            {esAnulable(p.estado) ? (
              <button
                type="button"
                onClick={() => setPropuestaAAnular(p)}
                disabled={!esAdministrador}
                title={esAdministrador ? undefined : "Requiere el rol ADMINISTRADOR."}
                className="text-sm font-medium text-estado-error hover:text-estado-error/80 disabled:cursor-not-allowed disabled:opacity-40"
              >
                Anular
              </button>
            ) : null}
          </div>
        ),
      },
    ],
    [esAdministrador, reprocesandoId, reprocesarUf],
  );

  return (
    <>
      <PanelListado
        titulo="Propuestas de facturación"
        columnas={columnas}
        filas={datos}
        obtenerClave={(p) => p.id}
        cargando={cargando}
        error={error}
        paginaActual={pagina}
        totalPaginas={totalPaginas}
        onCambiarPagina={setPagina}
        mensajeVacio="No hay propuestas que coincidan con los filtros."
        accionPrincipal={
          <div className="flex items-center gap-4">
            <Link href="/facturacion/facturas" className="text-sm text-sutil hover:text-marca-azul">
              Facturas
            </Link>
            <Link
              href="/facturacion/ciclo/historial"
              className="text-sm text-sutil hover:text-marca-azul"
            >
              Historial de ciclos
            </Link>
            <Link
              href="/facturacion/ciclo"
              className="text-sm font-medium text-marca-azul hover:text-marca-azul-700"
            >
              Ejecutar ciclo →
            </Link>
          </div>
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
            <CampoFormulario id="filtro-estado" etiqueta="Estado">
              <Seleccion
                id="filtro-estado"
                value={estado}
                onChange={(evento) => {
                  setEstado(evento.target.value);
                  setPagina(0);
                }}
              >
                <option value="">Todos</option>
                {ESTADOS.map((valor) => (
                  <option key={valor} value={valor}>
                    {ETIQUETAS_ESTADO_PROPUESTA[valor]}
                  </option>
                ))}
              </Seleccion>
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
          </>
        }
      />

      <DialogoDetallePropuesta
        propuesta={propuestaDetalle}
        onCerrar={() => setPropuestaDetalle(null)}
      />

      <DialogoConfirmacion
        abierto={propuestaAAnular !== null}
        titulo="Anular propuesta"
        mensaje={`¿Anular la propuesta de "${propuestaAAnular?.clienteRazonSocial}" (${propuestaAAnular ? formatearPeriodo(propuestaAAnular.periodoAnio, propuestaAAnular.periodoMes) : ""})? Esta acción no se puede deshacer.`}
        etiquetaConfirmar="Anular"
        procesando={anulando}
        onConfirmar={confirmarAnulacion}
        onCancelar={() => setPropuestaAAnular(null)}
      />
    </>
  );
}
