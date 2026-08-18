"use client";

import { Settings2 } from "lucide-react";
import Link from "next/link";
import { useCallback, useMemo, useState } from "react";
import { AccionesFila } from "@/components/listado/AccionesFila";
import { PanelListado } from "@/components/listado/PanelListado";
import { BadgeActivo } from "@/components/ui/BadgeActivo";
import { Boton } from "@/components/ui/Boton";
import { CampoFormulario } from "@/components/ui/CampoFormulario";
import { DialogoConfirmacion } from "@/components/ui/DialogoConfirmacion";
import { Entrada } from "@/components/ui/Entrada";
import { useNotificaciones } from "@/components/ui/Notificaciones";
import { Seleccion } from "@/components/ui/Seleccion";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { construirQueryString } from "@/lib/query";
import { formatearRut } from "@/lib/rut";
import { useListadoPaginado } from "@/lib/useListadoPaginado";
import { useTieneAlgunRol } from "@/lib/useRoles";
import type { ColumnaTabla } from "@/components/ui/Tabla";
import type { PaginaRespuesta } from "@/types/api";
import type { Cliente } from "@/types/cliente";
import { FormularioCliente } from "./FormularioCliente";

const TAMANO_PAGINA = 20;

export function ListaClientes() {
  const puedeGestionar = useTieneAlgunRol(["ADMINISTRADOR"]);
  const { notificar, notificarError } = useNotificaciones();

  const [pagina, setPagina] = useState(0);
  const [texto, setTexto] = useState("");
  const [filtroActivo, setFiltroActivo] = useState("");
  const [formularioAbierto, setFormularioAbierto] = useState(false);
  const [clienteEditando, setClienteEditando] = useState<Cliente | null>(null);
  const [clienteAEliminar, setClienteAEliminar] = useState<Cliente | null>(null);
  const [eliminando, setEliminando] = useState(false);

  const fetcher = useCallback(() => {
    const query = construirQueryString({
      texto: texto.trim() || undefined,
      activo: filtroActivo === "" ? undefined : filtroActivo === "true",
      page: pagina,
      size: TAMANO_PAGINA,
    });
    return clienteApiCliente.obtener<PaginaRespuesta<Cliente>>(`/clientes${query}`);
  }, [texto, filtroActivo, pagina]);

  const { datos, totalPaginas, cargando, error, recargar } = useListadoPaginado(fetcher, [
    texto,
    filtroActivo,
    pagina,
  ]);

  function abrirCreacion() {
    setClienteEditando(null);
    setFormularioAbierto(true);
  }

  function abrirEdicion(cliente: Cliente) {
    setClienteEditando(cliente);
    setFormularioAbierto(true);
  }

  const alternarActivo = useCallback(
    async (cliente: Cliente) => {
      try {
        await clienteApiCliente.actualizar(`/clientes/${cliente.id}`, {
          rut: cliente.rut,
          razonSocial: cliente.razonSocial,
          nombreFantasia: cliente.nombreFantasia,
          giro: cliente.giro,
          email: cliente.email,
          telefono: cliente.telefono,
          direccion: cliente.direccion,
          activo: !cliente.activo,
        });
        notificar(cliente.activo ? "Cliente desactivado." : "Cliente activado.", "exito");
        recargar();
      } catch (error) {
        notificarError(error);
      }
    },
    [notificar, notificarError, recargar],
  );

  async function confirmarEliminacion() {
    if (!clienteAEliminar) {
      return;
    }
    setEliminando(true);
    try {
      await clienteApiCliente.eliminar(`/clientes/${clienteAEliminar.id}`);
      notificar("Cliente eliminado.", "exito");
      setClienteAEliminar(null);
      recargar();
    } catch (error) {
      notificarError(error);
    } finally {
      setEliminando(false);
    }
  }

  const columnas = useMemo<ColumnaTabla<Cliente>[]>(
    () => [
      { encabezado: "Razón social", renderizar: (cliente) => cliente.razonSocial },
      { encabezado: "RUT", renderizar: (cliente) => formatearRut(cliente.rut) },
      { encabezado: "Nombre de fantasía", renderizar: (cliente) => cliente.nombreFantasia ?? "—" },
      {
        encabezado: "Estado",
        renderizar: (cliente) => <BadgeActivo activo={cliente.activo} />,
      },
      {
        encabezado: "",
        renderizar: (cliente) => (
          <AccionesFila
            activo={cliente.activo}
            puedeEditar={puedeGestionar}
            puedeEliminar={puedeGestionar}
            onEditar={() => abrirEdicion(cliente)}
            onActivarDesactivar={() => alternarActivo(cliente)}
            onEliminar={() => setClienteAEliminar(cliente)}
          />
        ),
      },
    ],
    [puedeGestionar, alternarActivo],
  );

  return (
    <>
      <PanelListado
        titulo="Clientes"
        columnas={columnas}
        filas={datos}
        obtenerClave={(cliente) => cliente.id}
        cargando={cargando}
        error={error}
        paginaActual={pagina}
        totalPaginas={totalPaginas}
        onCambiarPagina={setPagina}
        mensajeVacio="No hay clientes que coincidan con los filtros."
        accionPrincipal={
          <div className="flex items-center gap-3">
            <Link
              href="/clientes/tipos-servicio"
              className="inline-flex min-h-11 items-center gap-2 rounded border border-marca-azul px-4 py-2 text-sm font-medium text-marca-azul transition-colors hover:bg-marca-azul-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-marca-azul focus-visible:ring-offset-2"
            >
              <Settings2 className="h-4 w-4" aria-hidden />
              Tipos de servicio
            </Link>
            {puedeGestionar ? <Boton onClick={abrirCreacion}>+ Nuevo cliente</Boton> : null}
          </div>
        }
        filtros={
          <>
            <CampoFormulario id="filtro-texto" etiqueta="Razón social o RUT">
              <Entrada
                id="filtro-texto"
                value={texto}
                onChange={(evento) => {
                  setTexto(evento.target.value);
                  setPagina(0);
                }}
                placeholder="Buscar…"
              />
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
        <FormularioCliente
          cliente={clienteEditando}
          onCerrar={() => setFormularioAbierto(false)}
          onExito={() => {
            setFormularioAbierto(false);
            notificar(clienteEditando ? "Cliente actualizado." : "Cliente creado.", "exito");
            recargar();
          }}
        />
      ) : null}

      <DialogoConfirmacion
        abierto={clienteAEliminar !== null}
        titulo="Eliminar cliente"
        mensaje={`¿Eliminar "${clienteAEliminar?.razonSocial}"? Si tiene proyectos asociados, se desactivará en su lugar en vez de eliminarse.`}
        etiquetaConfirmar="Eliminar"
        procesando={eliminando}
        onConfirmar={confirmarEliminacion}
        onCancelar={() => setClienteAEliminar(null)}
      />
    </>
  );
}
