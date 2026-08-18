"use client";

import { Boton, type VarianteBoton } from "./Boton";
import { Dialogo } from "./Dialogo";

export interface PropiedadesDialogoConfirmacion {
  abierto: boolean;
  titulo: string;
  mensaje: string;
  variante?: VarianteBoton;
  etiquetaConfirmar?: string;
  procesando?: boolean;
  onConfirmar: () => void;
  onCancelar: () => void;
}

/** Confirmación reutilizable para acciones destructivas o irreversibles (eliminar, dar de baja). */
export function DialogoConfirmacion({
  abierto,
  titulo,
  mensaje,
  variante = "peligro",
  etiquetaConfirmar = "Confirmar",
  procesando = false,
  onConfirmar,
  onCancelar,
}: PropiedadesDialogoConfirmacion) {
  return (
    <Dialogo abierto={abierto} titulo={titulo} onCerrar={onCancelar}>
      <p className="text-sm text-texto">{mensaje}</p>
      <div className="mt-4 flex justify-end gap-2 border-t border-linea pt-4">
        <Boton variante="secundario" onClick={onCancelar} disabled={procesando}>
          Cancelar
        </Boton>
        <Boton variante={variante} onClick={onConfirmar} cargando={procesando}>
          {etiquetaConfirmar}
        </Boton>
      </div>
    </Dialogo>
  );
}
