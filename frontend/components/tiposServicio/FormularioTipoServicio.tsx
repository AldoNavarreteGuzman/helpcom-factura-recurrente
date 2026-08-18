"use client";

import { useState, type FormEvent } from "react";
import { FormularioDialogo } from "@/components/formularios/FormularioDialogo";
import { CampoFormulario } from "@/components/ui/CampoFormulario";
import { Entrada } from "@/components/ui/Entrada";
import { Interruptor } from "@/components/ui/Interruptor";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { obtenerErrorDeCampo } from "@/lib/errores";
import { useFormularioApi } from "@/lib/useFormularioApi";
import type { TipoServicio, TipoServicioSolicitud } from "@/types/tipoServicio";

export interface PropiedadesFormularioTipoServicio {
  /** `null` = creando uno nuevo. */
  tipoServicio: TipoServicio | null;
  onCerrar: () => void;
  onExito: () => void;
}

export function FormularioTipoServicio({
  tipoServicio,
  onCerrar,
  onExito,
}: PropiedadesFormularioTipoServicio) {
  const { enviando, error, manejarEnvio } = useFormularioApi();
  const [nombre, setNombre] = useState(tipoServicio?.nombre ?? "");
  const [activo, setActivo] = useState(tipoServicio?.activo ?? true);

  async function alEnviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();

    const solicitud: TipoServicioSolicitud = { nombre: nombre.trim(), activo };
    const exito = await manejarEnvio(async () => {
      if (tipoServicio) {
        await clienteApiCliente.actualizar(`/tipos-servicio/${tipoServicio.id}`, solicitud);
      } else {
        await clienteApiCliente.crear("/tipos-servicio", solicitud);
      }
    });

    if (exito) {
      onExito();
    }
  }

  return (
    <FormularioDialogo
      abierto
      titulo={tipoServicio ? "Editar tipo de servicio" : "Nuevo tipo de servicio"}
      enviando={enviando}
      error={error}
      onCerrar={onCerrar}
      onEnviar={alEnviar}
    >
      <CampoFormulario
        id="nombre"
        etiqueta="Nombre"
        requerido
        error={obtenerErrorDeCampo(error, "nombre")}
      >
        <Entrada
          id="nombre"
          value={nombre}
          onChange={(evento) => setNombre(evento.target.value)}
          invalida={Boolean(obtenerErrorDeCampo(error, "nombre"))}
          maxLength={120}
          required
          className="w-full"
        />
      </CampoFormulario>

      <CampoFormulario id="activo" etiqueta="Estado">
        <Interruptor
          id="activo"
          etiqueta="Activo"
          checked={activo}
          onChange={(evento) => setActivo(evento.target.checked)}
        />
      </CampoFormulario>
    </FormularioDialogo>
  );
}
