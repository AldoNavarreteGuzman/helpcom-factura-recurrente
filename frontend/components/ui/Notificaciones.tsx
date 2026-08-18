"use client";

import { AlertCircle, AlertTriangle, CheckCircle2, Info } from "lucide-react";
import { createContext, useCallback, useContext, useState, type ReactNode } from "react";
import { obtenerMensajeError } from "@/lib/errores";
import { combinarClases } from "@/lib/estilos";

type TipoNotificacion = "error" | "exito" | "info" | "advertencia";

interface Notificacion {
  id: number;
  mensaje: string;
  tipo: TipoNotificacion;
}

interface ContextoNotificaciones {
  notificar: (mensaje: string, tipo?: TipoNotificacion) => void;
  /** Atajo para el caso más común: mostrar un {@link ErrorApi} (u otro error) como toast. */
  notificarError: (error: unknown) => void;
}

const DURACION_MS = 6000;

const Contexto = createContext<ContextoNotificaciones | null>(null);

const CLASES_POR_TIPO: Record<TipoNotificacion, string> = {
  error: "bg-estado-error text-white",
  exito: "bg-estado-facturada text-white",
  info: "bg-marca-azul text-white",
  advertencia: "bg-estado-sin-uf text-white",
};

const ICONO_POR_TIPO: Record<TipoNotificacion, typeof AlertCircle> = {
  error: AlertCircle,
  exito: CheckCircle2,
  info: Info,
  advertencia: AlertTriangle,
};

let secuenciaId = 0;

export function ProveedorNotificaciones({ children }: { children: ReactNode }) {
  const [notificaciones, setNotificaciones] = useState<Notificacion[]>([]);

  const notificar = useCallback((mensaje: string, tipo: TipoNotificacion = "error") => {
    const id = ++secuenciaId;
    setNotificaciones((actuales) => [...actuales, { id, mensaje, tipo }]);
    setTimeout(() => {
      setNotificaciones((actuales) => actuales.filter((notificacion) => notificacion.id !== id));
    }, DURACION_MS);
  }, []);

  const notificarError = useCallback(
    (error: unknown) => notificar(obtenerMensajeError(error), "error"),
    [notificar],
  );

  return (
    <Contexto.Provider value={{ notificar, notificarError }}>
      {children}
      <div
        className="fixed bottom-4 right-4 z-50 flex w-full max-w-sm flex-col gap-2"
        role="region"
        aria-label="Notificaciones"
      >
        {notificaciones.map((notificacion) => {
          const Icono = ICONO_POR_TIPO[notificacion.tipo];
          return (
            <div
              key={notificacion.id}
              role="alert"
              className={combinarClases(
                "flex items-start gap-2 rounded px-4 py-3 text-sm shadow-modal",
                CLASES_POR_TIPO[notificacion.tipo],
              )}
            >
              <Icono className="mt-0.5 h-4 w-4 shrink-0" aria-hidden />
              <span>{notificacion.mensaje}</span>
            </div>
          );
        })}
      </div>
    </Contexto.Provider>
  );
}

export function useNotificaciones(): ContextoNotificaciones {
  const contexto = useContext(Contexto);
  if (!contexto) {
    throw new Error("useNotificaciones debe usarse dentro de <ProveedorNotificaciones>.");
  }
  return contexto;
}
