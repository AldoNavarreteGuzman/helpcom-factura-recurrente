"use client";

import { useEffect, useRef, type FormEvent, type ReactNode } from "react";
import { Alerta } from "@/components/ui/Alerta";
import { Boton } from "@/components/ui/Boton";
import { Dialogo } from "@/components/ui/Dialogo";
import { useNotificaciones } from "@/components/ui/Notificaciones";
import { obtenerMensajeError } from "@/lib/errores";

export interface PropiedadesFormularioDialogo {
  abierto: boolean;
  titulo: string;
  enviando: boolean;
  /** El error tal como lo dejó `useFormularioApi` (un `ErrorApi`, u otro error, o `null`). */
  error?: unknown;
  onCerrar: () => void;
  onEnviar: (evento: FormEvent<HTMLFormElement>) => void;
  children: ReactNode;
  etiquetaEnviar?: string;
}

/**
 * Envoltorio reutilizable para formularios de creación/edición dentro de un modal
 * (`components/ui/Dialogo.tsx`): botones Cancelar/Guardar con estado de envío, y el mensaje
 * general del error (`problema.detail`) mostrado DOS veces a propósito — como notificación
 * (para que no se pierda si el modal se cierra) y como banner dentro del formulario (para
 * errores que no mapean a ningún campo, como un 409 de duplicado). Cada campo específico
 * muestra el suyo por separado vía `CampoFormulario` + `lib/errores.ts::obtenerErrorDeCampo`.
 */
export function FormularioDialogo({
  abierto,
  titulo,
  enviando,
  error,
  onCerrar,
  onEnviar,
  children,
  etiquetaEnviar = "Guardar",
}: PropiedadesFormularioDialogo) {
  const { notificarError } = useNotificaciones();
  const errorNotificado = useRef<unknown>(null);

  useEffect(() => {
    if (error && error !== errorNotificado.current) {
      notificarError(error);
    }
    errorNotificado.current = error;
  }, [error, notificarError]);

  return (
    <Dialogo abierto={abierto} titulo={titulo} onCerrar={onCerrar}>
      <form onSubmit={onEnviar} className="space-y-4" noValidate>
        {error ? <Alerta variante="error">{obtenerMensajeError(error)}</Alerta> : null}
        {children}
        <div className="flex justify-end gap-2 border-t border-linea pt-4">
          <Boton type="button" variante="secundario" onClick={onCerrar} disabled={enviando}>
            Cancelar
          </Boton>
          <Boton type="submit" variante="primario" cargando={enviando}>
            {etiquetaEnviar}
          </Boton>
        </div>
      </form>
    </Dialogo>
  );
}
