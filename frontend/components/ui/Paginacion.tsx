"use client";

import { Boton } from "./Boton";

export interface PropiedadesPaginacion {
  /** 0-indexado, igual que `Pageable` del backend (ver `PaginaRespuestaDto`). */
  paginaActual: number;
  totalPaginas: number;
  onCambiarPagina: (pagina: number) => void;
}

export function Paginacion({ paginaActual, totalPaginas, onCambiarPagina }: PropiedadesPaginacion) {
  if (totalPaginas <= 1) {
    return null;
  }

  return (
    <nav className="flex items-center justify-between gap-4 py-2" aria-label="Paginación">
      <Boton
        variante="secundario"
        disabled={paginaActual === 0}
        onClick={() => onCambiarPagina(paginaActual - 1)}
      >
        Anterior
      </Boton>
      <span className="text-sm text-slate-600">
        Página {paginaActual + 1} de {totalPaginas}
      </span>
      <Boton
        variante="secundario"
        disabled={paginaActual >= totalPaginas - 1}
        onClick={() => onCambiarPagina(paginaActual + 1)}
      >
        Siguiente
      </Boton>
    </nav>
  );
}
