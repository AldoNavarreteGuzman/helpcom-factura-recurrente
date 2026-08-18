"use client";

import { X } from "lucide-react";
import { useEffect, useRef, type MouseEvent, type ReactNode } from "react";

export interface PropiedadesDialogo {
  abierto: boolean;
  titulo: string;
  onCerrar: () => void;
  children: ReactNode;
}

/**
 * Modal construido sobre el elemento nativo `<dialog>`: el navegador ya resuelve el foco
 * atrapado dentro del diálogo, el cierre con ESC y el `::backdrop` — evita sumar una
 * dependencia (Radix/shadcn) solo para esto (ver decisión documentada en docs/frontend.md).
 */
export function Dialogo({ abierto, titulo, onCerrar, children }: PropiedadesDialogo) {
  const referencia = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    const elemento = referencia.current;
    if (!elemento) {
      return;
    }
    if (abierto && !elemento.open) {
      elemento.showModal();
    } else if (!abierto && elemento.open) {
      elemento.close();
    }
  }, [abierto]);

  function alHacerClicEnElFondo(evento: MouseEvent<HTMLDialogElement>) {
    if (evento.target === referencia.current) {
      onCerrar();
    }
  }

  return (
    <dialog
      ref={referencia}
      onClose={onCerrar}
      onClick={alHacerClicEnElFondo}
      className="w-full max-w-md rounded-lg p-0 shadow-modal backdrop:bg-tinta/60"
    >
      <div className="flex items-center justify-between rounded-t-lg border-b border-linea bg-marca-azul-50 px-4 py-3">
        <h2 className="text-base font-semibold text-marca-azul">{titulo}</h2>
        <button
          type="button"
          onClick={onCerrar}
          aria-label="Cerrar"
          className="rounded p-1 text-marca-azul/70 transition-colors hover:bg-marca-azul-100 hover:text-marca-azul focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-marca-azul"
        >
          <X className="h-4 w-4" aria-hidden />
        </button>
      </div>
      <div className="rounded-b-lg bg-white px-4 py-4">{children}</div>
    </dialog>
  );
}
