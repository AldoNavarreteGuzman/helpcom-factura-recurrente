import { AlertCircle } from "lucide-react";
import type { ReactNode } from "react";

export interface PropiedadesCampoFormulario {
  id: string;
  etiqueta: string;
  error?: string;
  descripcion?: string;
  requerido?: boolean;
  children: ReactNode;
}

/**
 * Envuelve un control de formulario (p. ej. {@link Entrada}) con su etiqueta y el mensaje de
 * error correspondiente — pensado para mostrar los errores de campo que llegan en
 * `problema.errores` (ver `lib/errores.ts`).
 */
export function CampoFormulario({
  id,
  etiqueta,
  error,
  descripcion,
  requerido,
  children,
}: PropiedadesCampoFormulario) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-texto">
        {etiqueta}
        {requerido && (
          <span className="text-estado-error" aria-hidden>
            {" "}
            *
          </span>
        )}
      </label>
      {children}
      {error ? (
        <p id={`${id}-error`} role="alert" className="flex items-center gap-1 text-xs text-estado-error">
          <AlertCircle className="h-3.5 w-3.5 shrink-0" aria-hidden />
          {error}
        </p>
      ) : (
        descripcion && <p className="text-xs text-sutil">{descripcion}</p>
      )}
    </div>
  );
}
