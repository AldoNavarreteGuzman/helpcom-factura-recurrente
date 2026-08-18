"use client";

import Link from "next/link";
import { useCallback, useMemo, useState } from "react";
import { SelectorCliente } from "@/components/clientes/SelectorCliente";
import { AccionesFila } from "@/components/listado/AccionesFila";
import { PanelListado } from "@/components/listado/PanelListado";
import { BadgeActivo } from "@/components/ui/BadgeActivo";
import { Boton } from "@/components/ui/Boton";
import { CampoFormulario } from "@/components/ui/CampoFormulario";
import { Seleccion } from "@/components/ui/Seleccion";
import { useNotificaciones } from "@/components/ui/Notificaciones";
import type { ColumnaTabla } from "@/components/ui/Tabla";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { ETIQUETAS_PERIODICIDAD } from "@/lib/etiquetas";
import { formatearMontoEnMoneda } from "@/lib/formato";
import { construirQueryString } from "@/lib/query";
import { useListadoPaginado } from "@/lib/useListadoPaginado";
import { useTieneAlgunRol } from "@/lib/useRoles";
import type { PaginaRespuesta } from "@/types/api";
import type { Proyecto } from "@/types/proyecto";
import { FormularioProyecto } from "./FormularioProyecto";

const TAMANO_PAGINA = 20;

export function ListaProyectos() {
  const puedeGestionar = useTieneAlgunRol(["ADMINISTRADOR"]);
  const { notificar, notificarError } = useNotificaciones();

  const [pagina, setPagina] = useState(0);
  const [clienteId, setClienteId] = useState<number | null>(null);
  const [periodicidad, setPeriodicidad] = useState("");
  const [moneda, setMoneda] = useState("");
  const [filtroActivo, setFiltroActivo] = useState("");
  const [formularioAbierto, setFormularioAbierto] = useState(false);
  const [proyectoEditando, setProyectoEditando] = useState<Proyecto | null>(null);

  const fetcher = useCallback(() => {
    const query = construirQueryString({
      clienteId: clienteId ?? undefined,
      periodicidad: periodicidad || undefined,
      moneda: moneda || undefined,
      activo: filtroActivo === "" ? undefined : filtroActivo === "true",
      page: pagina,
      size: TAMANO_PAGINA,
    });
    return clienteApiCliente.obtener<PaginaRespuesta<Proyecto>>(`/proyectos${query}`);
  }, [clienteId, periodicidad, moneda, filtroActivo, pagina]);

  const { datos, totalPaginas, cargando, error, recargar } = useListadoPaginado(fetcher, [
    clienteId,
    periodicidad,
    moneda,
    filtroActivo,
    pagina,
  ]);

  function abrirCreacion() {
    setProyectoEditando(null);
    setFormularioAbierto(true);
  }

  function abrirEdicion(proyecto: Proyecto) {
    setProyectoEditando(proyecto);
    setFormularioAbierto(true);
  }

  const alternarActivo = useCallback(
    async (proyecto: Proyecto) => {
      try {
        await clienteApiCliente.actualizar(`/proyectos/${proyecto.id}`, {
          clienteId: proyecto.clienteId,
          tipoServicioId: proyecto.tipoServicioId,
          codigo: proyecto.codigo,
          nombre: proyecto.nombre,
          descripcion: proyecto.descripcion,
          precioBaseNeto: proyecto.precioBaseNeto,
          monedaPrecio: proyecto.monedaPrecio,
          periodicidad: proyecto.periodicidad,
          diaFacturacion: proyecto.diaFacturacion,
          fechaInicio: proyecto.fechaInicio,
          fechaTermino: proyecto.fechaTermino,
          activo: !proyecto.activo,
        });
        notificar(proyecto.activo ? "Proyecto desactivado." : "Proyecto activado.", "exito");
        recargar();
      } catch (error) {
        notificarError(error);
      }
    },
    [notificar, notificarError, recargar],
  );

  const columnas = useMemo<ColumnaTabla<Proyecto>[]>(
    () => [
      {
        encabezado: "Nombre / código",
        renderizar: (proyecto) => (
          <div>
            <Link
              href={`/proyectos/${proyecto.id}`}
              className="font-medium text-marca-azul hover:underline"
            >
              {proyecto.nombre}
            </Link>
            {proyecto.codigo ? <div className="text-xs text-sutil">{proyecto.codigo}</div> : null}
          </div>
        ),
      },
      { encabezado: "Cliente", renderizar: (proyecto) => proyecto.clienteRazonSocial },
      {
        encabezado: "Precio base",
        renderizar: (proyecto) =>
          formatearMontoEnMoneda(proyecto.precioBaseNeto, proyecto.monedaPrecio),
      },
      {
        encabezado: "Periodicidad",
        renderizar: (proyecto) => ETIQUETAS_PERIODICIDAD[proyecto.periodicidad],
      },
      { encabezado: "Día facturación", renderizar: (proyecto) => proyecto.diaFacturacion },
      {
        encabezado: "Estado",
        renderizar: (proyecto) => <BadgeActivo activo={proyecto.activo} />,
      },
      {
        encabezado: "",
        renderizar: (proyecto) => (
          <AccionesFila
            puedeEditar={puedeGestionar}
            onEditar={() => abrirEdicion(proyecto)}
            activo={proyecto.activo}
            onActivarDesactivar={() => alternarActivo(proyecto)}
          />
        ),
      },
    ],
    [puedeGestionar, alternarActivo],
  );

  return (
    <>
      <PanelListado
        titulo="Proyectos"
        columnas={columnas}
        filas={datos}
        obtenerClave={(proyecto) => proyecto.id}
        cargando={cargando}
        error={error}
        paginaActual={pagina}
        totalPaginas={totalPaginas}
        onCambiarPagina={setPagina}
        mensajeVacio="No hay proyectos que coincidan con los filtros."
        accionPrincipal={
          puedeGestionar ? <Boton onClick={abrirCreacion}>+ Nuevo proyecto</Boton> : undefined
        }
        filtros={
          <>
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
            <CampoFormulario id="filtro-periodicidad" etiqueta="Periodicidad">
              <Seleccion
                id="filtro-periodicidad"
                value={periodicidad}
                onChange={(evento) => {
                  setPeriodicidad(evento.target.value);
                  setPagina(0);
                }}
              >
                <option value="">Todas</option>
                <option value="MENSUAL">Mensual</option>
                <option value="ANUAL">Anual</option>
              </Seleccion>
            </CampoFormulario>
            <CampoFormulario id="filtro-moneda" etiqueta="Moneda">
              <Seleccion
                id="filtro-moneda"
                value={moneda}
                onChange={(evento) => {
                  setMoneda(evento.target.value);
                  setPagina(0);
                }}
              >
                <option value="">Todas</option>
                <option value="CLP">CLP</option>
                <option value="UF">UF</option>
              </Seleccion>
            </CampoFormulario>
            <CampoFormulario id="filtro-activo" etiqueta="Estado">
              <Seleccion
                id="filtro-activo"
                value={filtroActivo}
                onChange={(evento) => {
                  setFiltroActivo(evento.target.value);
                  setPagina(0);
                }}
              >
                <option value="">Todos</option>
                <option value="true">Activos</option>
                <option value="false">Inactivos</option>
              </Seleccion>
            </CampoFormulario>
          </>
        }
      />

      {formularioAbierto ? (
        <FormularioProyecto
          proyecto={proyectoEditando}
          onCerrar={() => setFormularioAbierto(false)}
          onExito={() => {
            setFormularioAbierto(false);
            notificar(proyectoEditando ? "Proyecto actualizado." : "Proyecto creado.", "exito");
            recargar();
          }}
        />
      ) : null}
    </>
  );
}
