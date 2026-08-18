"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { AccionesFila } from "@/components/listado/AccionesFila";
import { PanelListado } from "@/components/listado/PanelListado";
import { Boton } from "@/components/ui/Boton";
import { DialogoConfirmacion } from "@/components/ui/DialogoConfirmacion";
import { useNotificaciones } from "@/components/ui/Notificaciones";
import type { ColumnaTabla } from "@/components/ui/Tabla";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { ETIQUETAS_TIPO_ACUERDO } from "@/lib/etiquetas";
import { formatearClp, formatearFecha, formatearUf } from "@/lib/formato";
import { calcularEstadoVigencia } from "@/lib/vigencia";
import { useTieneAlgunRol } from "@/lib/useRoles";
import type { AcuerdoPrecio } from "@/types/acuerdoPrecio";
import { BadgeEstadoVigencia } from "./BadgeEstadoVigencia";
import { FormularioAcuerdo } from "./FormularioAcuerdo";

function formatearValor(acuerdo: AcuerdoPrecio): string {
  if (acuerdo.tipo === "DESCUENTO_PORCENTAJE") {
    return `${acuerdo.valor}%`;
  }
  return acuerdo.moneda === "UF" ? formatearUf(acuerdo.valor) : formatearClp(acuerdo.valor);
}

export interface PropiedadesListaAcuerdos {
  proyectoId: number;
}

export function ListaAcuerdos({ proyectoId }: PropiedadesListaAcuerdos) {
  const puedeGestionar = useTieneAlgunRol(["ADMINISTRADOR"]);
  const { notificar, notificarError } = useNotificaciones();

  const [acuerdos, setAcuerdos] = useState<AcuerdoPrecio[]>([]);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [version, setVersion] = useState(0);

  const [formularioAbierto, setFormularioAbierto] = useState(false);
  const [acuerdoEditando, setAcuerdoEditando] = useState<AcuerdoPrecio | null>(null);
  const [acuerdoAEliminar, setAcuerdoAEliminar] = useState<AcuerdoPrecio | null>(null);
  const [eliminando, setEliminando] = useState(false);

  const recargar = useCallback(() => setVersion((actual) => actual + 1), []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setError(null);

    clienteApiCliente
      .obtener<AcuerdoPrecio[]>(`/proyectos/${proyectoId}/acuerdos`)
      .then((acuerdosObtenidos) => {
        if (!cancelado) {
          setAcuerdos(acuerdosObtenidos);
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
  }, [proyectoId, version]);

  function abrirCreacion() {
    setAcuerdoEditando(null);
    setFormularioAbierto(true);
  }

  function abrirEdicion(acuerdo: AcuerdoPrecio) {
    setAcuerdoEditando(acuerdo);
    setFormularioAbierto(true);
  }

  async function confirmarEliminacion() {
    if (!acuerdoAEliminar) {
      return;
    }
    setEliminando(true);
    try {
      await clienteApiCliente.eliminar(`/proyectos/${proyectoId}/acuerdos/${acuerdoAEliminar.id}`);
      notificar("Acuerdo de precio eliminado.", "exito");
      setAcuerdoAEliminar(null);
      recargar();
    } catch (error) {
      notificarError(error);
    } finally {
      setEliminando(false);
    }
  }

  const acuerdosExistentesParaFormulario = useMemo(
    () => acuerdos.filter((acuerdo) => acuerdo.id !== acuerdoEditando?.id),
    [acuerdos, acuerdoEditando],
  );

  const columnas = useMemo<ColumnaTabla<AcuerdoPrecio>[]>(
    () => [
      { encabezado: "Tipo", renderizar: (acuerdo) => ETIQUETAS_TIPO_ACUERDO[acuerdo.tipo] },
      { encabezado: "Valor", renderizar: (acuerdo) => formatearValor(acuerdo) },
      {
        encabezado: "Vigencia",
        renderizar: (acuerdo) =>
          `${formatearFecha(acuerdo.fechaInicio)} a ${formatearFecha(acuerdo.fechaTermino)}`,
      },
      {
        encabezado: "Estado",
        renderizar: (acuerdo) => (
          <BadgeEstadoVigencia estado={calcularEstadoVigencia(acuerdo.fechaInicio, acuerdo.fechaTermino)} />
        ),
      },
      {
        encabezado: "",
        renderizar: (acuerdo) => (
          <AccionesFila
            puedeEditar={puedeGestionar}
            onEditar={() => abrirEdicion(acuerdo)}
            puedeEliminar={puedeGestionar}
            onEliminar={() => setAcuerdoAEliminar(acuerdo)}
          />
        ),
      },
    ],
    [puedeGestionar],
  );

  return (
    <div className="space-y-4">
      <PanelListado
        titulo="Vigencias"
        columnas={columnas}
        filas={acuerdos}
        obtenerClave={(acuerdo) => acuerdo.id}
        cargando={cargando}
        error={error}
        paginaActual={0}
        totalPaginas={1}
        onCambiarPagina={() => {}}
        mensajeVacio="Este proyecto todavía no tiene acuerdos de precio."
        accionPrincipal={
          puedeGestionar ? <Boton onClick={abrirCreacion}>+ Agregar descuento</Boton> : undefined
        }
      />

      {formularioAbierto ? (
        <FormularioAcuerdo
          proyectoId={proyectoId}
          acuerdo={acuerdoEditando}
          acuerdosExistentes={acuerdosExistentesParaFormulario}
          onCerrar={() => setFormularioAbierto(false)}
          onExito={() => {
            setFormularioAbierto(false);
            notificar(acuerdoEditando ? "Acuerdo actualizado." : "Acuerdo creado.", "exito");
            recargar();
          }}
        />
      ) : null}

      <DialogoConfirmacion
        abierto={acuerdoAEliminar !== null}
        titulo="Eliminar acuerdo de precio"
        mensaje="¿Eliminar este acuerdo de precio? Esta acción no se puede deshacer."
        etiquetaConfirmar="Eliminar"
        procesando={eliminando}
        onConfirmar={confirmarEliminacion}
        onCancelar={() => setAcuerdoAEliminar(null)}
      />
    </div>
  );
}
