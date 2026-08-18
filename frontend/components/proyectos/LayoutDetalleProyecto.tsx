"use client";

import { useCallback, useEffect, useState, type ReactNode } from "react";
import { EncabezadoDetalle } from "@/components/detalle/EncabezadoDetalle";
import { PestanasDetalle } from "@/components/detalle/PestanasDetalle";
import { Alerta } from "@/components/ui/Alerta";
import { BadgeActivo } from "@/components/ui/BadgeActivo";
import { Boton } from "@/components/ui/Boton";
import { useNotificaciones } from "@/components/ui/Notificaciones";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { obtenerMensajeError } from "@/lib/errores";
import { ETIQUETAS_PERIODICIDAD } from "@/lib/etiquetas";
import { formatearMontoEnMoneda } from "@/lib/formato";
import { useTieneAlgunRol } from "@/lib/useRoles";
import type { Proyecto } from "@/types/proyecto";
import { ProveedorProyectoDetalle } from "./ContextoProyectoDetalle";
import { FormularioProyecto } from "./FormularioProyecto";

export interface PropiedadesLayoutDetalleProyecto {
  proyectoId: number;
  children: ReactNode;
}

/**
 * Cabecera + pestañas del detalle de un proyecto (docs/frontend.md §15, patrón definido en R3 —
 * docs/frontend.md §14.2): resuelve el acceso a Descuentos que antes no era visible desde
 * `/proyectos`. `EncabezadoDetalle`/`PestanasDetalle` son los componentes compartidos que R3
 * dejó listos para esto. El proyecto se pide una sola vez acá y se comparte con la pestaña
 * "Datos" vía `ProveedorProyectoDetalle` — evita una segunda llamada redundante y, sobre todo,
 * que editar desde un lado deje al otro con datos viejos.
 */
export function LayoutDetalleProyecto({ proyectoId, children }: PropiedadesLayoutDetalleProyecto) {
  const puedeGestionar = useTieneAlgunRol(["ADMINISTRADOR"]);
  const { notificar } = useNotificaciones();

  const [proyecto, setProyecto] = useState<Proyecto | null>(null);
  const [cargando, setCargando] = useState(true);
  const [error, setError] = useState<unknown>(null);
  const [version, setVersion] = useState(0);
  const [formularioAbierto, setFormularioAbierto] = useState(false);

  const recargar = useCallback(() => setVersion((actual) => actual + 1), []);

  useEffect(() => {
    let cancelado = false;
    setCargando(true);
    setError(null);
    clienteApiCliente
      .obtener<Proyecto>(`/proyectos/${proyectoId}`)
      .then((obtenido) => {
        if (!cancelado) {
          setProyecto(obtenido);
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

  if (cargando) {
    return <p className="py-10 text-center text-sm text-sutil">Cargando…</p>;
  }

  if (error || !proyecto) {
    return <Alerta variante="error">{obtenerMensajeError(error)}</Alerta>;
  }

  return (
    <div className="space-y-4">
      <EncabezadoDetalle
        volverA="/proyectos"
        volverEtiqueta="Proyectos"
        titulo={proyecto.nombre}
        subtitulo={<BadgeActivo activo={proyecto.activo} />}
        acciones={
          puedeGestionar ? (
            <Boton variante="secundario" onClick={() => setFormularioAbierto(true)}>
              Editar
            </Boton>
          ) : undefined
        }
      />
      <p className="-mt-2 text-sm text-sutil">
        {proyecto.clienteRazonSocial} · {formatearMontoEnMoneda(proyecto.precioBaseNeto, proyecto.monedaPrecio)}{" "}
        · {ETIQUETAS_PERIODICIDAD[proyecto.periodicidad]}
      </p>

      <PestanasDetalle
        pestanas={[
          { href: `/proyectos/${proyectoId}`, etiqueta: "Datos" },
          { href: `/proyectos/${proyectoId}/acuerdos`, etiqueta: "Descuentos" },
        ]}
      />

      <ProveedorProyectoDetalle value={{ proyecto, recargar }}>
        <div className="pt-2">{children}</div>
      </ProveedorProyectoDetalle>

      {formularioAbierto ? (
        <FormularioProyecto
          proyecto={proyecto}
          onCerrar={() => setFormularioAbierto(false)}
          onExito={() => {
            setFormularioAbierto(false);
            notificar("Proyecto actualizado.", "exito");
            recargar();
          }}
        />
      ) : null}
    </div>
  );
}
