"use client";

import { ArrowLeft } from "lucide-react";
import Link from "next/link";
import { useCallback, useMemo, useState } from "react";
import { AccionesFila } from "@/components/listado/AccionesFila";
import { PanelListado } from "@/components/listado/PanelListado";
import { BadgeActivo } from "@/components/ui/BadgeActivo";
import { Boton } from "@/components/ui/Boton";
import { CampoFormulario } from "@/components/ui/CampoFormulario";
import { DialogoConfirmacion } from "@/components/ui/DialogoConfirmacion";
import { useNotificaciones } from "@/components/ui/Notificaciones";
import { Seleccion } from "@/components/ui/Seleccion";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { construirQueryString } from "@/lib/query";
import { useListadoPaginado } from "@/lib/useListadoPaginado";
import { useTieneAlgunRol } from "@/lib/useRoles";
import type { ColumnaTabla } from "@/components/ui/Tabla";
import type { PaginaRespuesta } from "@/types/api";
import type { TipoServicio } from "@/types/tipoServicio";
import { FormularioTipoServicio } from "./FormularioTipoServicio";

const TAMANO_PAGINA = 20;

export function ListaTiposServicio() {
  const puedeGestionar = useTieneAlgunRol(["ADMINISTRADOR"]);
  const { notificar, notificarError } = useNotificaciones();

  const [pagina, setPagina] = useState(0);
  const [filtroActivo, setFiltroActivo] = useState("");
  const [formularioAbierto, setFormularioAbierto] = useState(false);
  const [tipoServicioEditando, setTipoServicioEditando] = useState<TipoServicio | null>(null);
  const [tipoServicioAEliminar, setTipoServicioAEliminar] = useState<TipoServicio | null>(null);
  const [eliminando, setEliminando] = useState(false);

  const fetcher = useCallback(() => {
    const query = construirQueryString({
      activo: filtroActivo === "" ? undefined : filtroActivo === "true",
      page: pagina,
      size: TAMANO_PAGINA,
    });
    return clienteApiCliente.obtener<PaginaRespuesta<TipoServicio>>(`/tipos-servicio${query}`);
  }, [filtroActivo, pagina]);

  const { datos, totalPaginas, cargando, error, recargar } = useListadoPaginado(fetcher, [
    filtroActivo,
    pagina,
  ]);

  function abrirCreacion() {
    setTipoServicioEditando(null);
    setFormularioAbierto(true);
  }

  function abrirEdicion(tipoServicio: TipoServicio) {
    setTipoServicioEditando(tipoServicio);
    setFormularioAbierto(true);
  }

  const alternarActivo = useCallback(
    async (tipoServicio: TipoServicio) => {
      try {
        await clienteApiCliente.actualizar(`/tipos-servicio/${tipoServicio.id}`, {
          nombre: tipoServicio.nombre,
          activo: !tipoServicio.activo,
        });
        notificar(
          tipoServicio.activo ? "Tipo de servicio desactivado." : "Tipo de servicio activado.",
          "exito",
        );
        recargar();
      } catch (error) {
        notificarError(error);
      }
    },
    [notificar, notificarError, recargar],
  );

  async function confirmarEliminacion() {
    if (!tipoServicioAEliminar) {
      return;
    }
    setEliminando(true);
    try {
      await clienteApiCliente.eliminar(`/tipos-servicio/${tipoServicioAEliminar.id}`);
      notificar("Tipo de servicio eliminado.", "exito");
      setTipoServicioAEliminar(null);
      recargar();
    } catch (error) {
      notificarError(error);
    } finally {
      setEliminando(false);
    }
  }

  const columnas = useMemo<ColumnaTabla<TipoServicio>[]>(
    () => [
      { encabezado: "Nombre", renderizar: (tipo) => tipo.nombre },
      {
        encabezado: "Estado",
        renderizar: (tipo) => <BadgeActivo activo={tipo.activo} />,
      },
      {
        encabezado: "",
        renderizar: (tipo) => (
          <AccionesFila
            activo={tipo.activo}
            puedeEditar={puedeGestionar}
            puedeEliminar={puedeGestionar}
            onEditar={() => abrirEdicion(tipo)}
            onActivarDesactivar={() => alternarActivo(tipo)}
            onEliminar={() => setTipoServicioAEliminar(tipo)}
          />
        ),
      },
    ],
    [puedeGestionar, alternarActivo],
  );

  return (
    <>
      <Link
        href="/clientes"
        className="mb-4 inline-flex min-h-11 items-center gap-1 text-sm text-sutil hover:text-marca-azul"
      >
        <ArrowLeft className="h-4 w-4" aria-hidden />
        Clientes
      </Link>

      <PanelListado
        titulo="Tipos de servicio"
        columnas={columnas}
        filas={datos}
        obtenerClave={(tipo) => tipo.id}
        cargando={cargando}
        error={error}
        paginaActual={pagina}
        totalPaginas={totalPaginas}
        onCambiarPagina={setPagina}
        mensajeVacio="No hay tipos de servicio registrados."
        accionPrincipal={
          puedeGestionar ? (
            <Boton onClick={abrirCreacion}>+ Nuevo tipo de servicio</Boton>
          ) : undefined
        }
        filtros={
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
        }
      />

      {formularioAbierto ? (
        <FormularioTipoServicio
          tipoServicio={tipoServicioEditando}
          onCerrar={() => setFormularioAbierto(false)}
          onExito={() => {
            setFormularioAbierto(false);
            notificar(
              tipoServicioEditando ? "Tipo de servicio actualizado." : "Tipo de servicio creado.",
              "exito",
            );
            recargar();
          }}
        />
      ) : null}

      <DialogoConfirmacion
        abierto={tipoServicioAEliminar !== null}
        titulo="Eliminar tipo de servicio"
        mensaje={`¿Eliminar "${tipoServicioAEliminar?.nombre}"? Si está en uso por algún proyecto, se desactivará en su lugar en vez de eliminarse.`}
        etiquetaConfirmar="Eliminar"
        procesando={eliminando}
        onConfirmar={confirmarEliminacion}
        onCancelar={() => setTipoServicioAEliminar(null)}
      />
    </>
  );
}
