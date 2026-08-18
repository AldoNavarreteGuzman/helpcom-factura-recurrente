"use client";

import { useState, type FocusEvent, type FormEvent } from "react";
import { SelectorCliente } from "@/components/clientes/SelectorCliente";
import { SelectorTipoServicio } from "@/components/tiposServicio/SelectorTipoServicio";
import { FormularioDialogo } from "@/components/formularios/FormularioDialogo";
import { AreaTexto } from "@/components/ui/AreaTexto";
import { CampoFormulario } from "@/components/ui/CampoFormulario";
import { Entrada } from "@/components/ui/Entrada";
import { Interruptor } from "@/components/ui/Interruptor";
import { Seleccion } from "@/components/ui/Seleccion";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { obtenerErrorDeCampo } from "@/lib/errores";
import { formatearNumeroEsCl, parsearNumeroEsCl } from "@/lib/numero";
import { useFormularioApi } from "@/lib/useFormularioApi";
import type { Moneda, Periodicidad } from "@/types/dominio";
import type { Proyecto, ProyectoSolicitud } from "@/types/proyecto";

export interface PropiedadesFormularioProyecto {
  /** `null` = creando uno nuevo. */
  proyecto: Proyecto | null;
  onCerrar: () => void;
  onExito: () => void;
}

function vacioANulo(valor: string): string | null {
  const recortado = valor.trim();
  return recortado === "" ? null : recortado;
}

export function FormularioProyecto({ proyecto, onCerrar, onExito }: PropiedadesFormularioProyecto) {
  const { enviando, error, manejarEnvio } = useFormularioApi();

  const [clienteId, setClienteId] = useState<number | null>(proyecto?.clienteId ?? null);
  const [tipoServicioId, setTipoServicioId] = useState<number | null>(
    proyecto?.tipoServicioId ?? null,
  );
  const [codigo, setCodigo] = useState(proyecto?.codigo ?? "");
  const [nombre, setNombre] = useState(proyecto?.nombre ?? "");
  const [descripcion, setDescripcion] = useState(proyecto?.descripcion ?? "");
  const [precioTexto, setPrecioTexto] = useState(
    proyecto ? formatearNumeroEsCl(proyecto.precioBaseNeto) : "",
  );
  const [monedaPrecio, setMonedaPrecio] = useState<Moneda>(proyecto?.monedaPrecio ?? "CLP");
  const [periodicidad, setPeriodicidad] = useState<Periodicidad>(
    proyecto?.periodicidad ?? "MENSUAL",
  );
  const [diaFacturacion, setDiaFacturacion] = useState(String(proyecto?.diaFacturacion ?? 1));
  const [fechaInicio, setFechaInicio] = useState(proyecto?.fechaInicio ?? "");
  const [fechaTermino, setFechaTermino] = useState(proyecto?.fechaTermino ?? "");
  const [activo, setActivo] = useState(proyecto?.activo ?? true);

  const [erroresLocales, setErroresLocales] = useState<Record<string, string>>({});

  function errorDeCampo(campo: string): string | undefined {
    return erroresLocales[campo] ?? obtenerErrorDeCampo(error, campo);
  }

  function alPerderFocoPrecio(evento: FocusEvent<HTMLInputElement>) {
    const valor = parsearNumeroEsCl(evento.target.value);
    if (valor !== null) {
      setPrecioTexto(formatearNumeroEsCl(valor));
    }
  }

  function validarAntesDeEnviar(): ProyectoSolicitud | null {
    const errores: Record<string, string> = {};

    if (clienteId === null) {
      errores.clienteId = "Debes seleccionar un cliente.";
    }
    if (nombre.trim() === "") {
      errores.nombre = "El nombre es obligatorio.";
    }
    const precio = parsearNumeroEsCl(precioTexto);
    if (precio === null || precio < 0) {
      errores.precioBaseNeto = "El precio debe ser un número mayor o igual a 0.";
    }
    const dia = Number(diaFacturacion);
    if (!Number.isInteger(dia) || dia < 1 || dia > 31) {
      errores.diaFacturacion = "El día de facturación debe estar entre 1 y 31.";
    }
    if (fechaInicio.trim() === "") {
      errores.fechaInicio = "La fecha de inicio es obligatoria.";
    }
    if (fechaTermino.trim() !== "" && fechaInicio.trim() !== "" && fechaTermino < fechaInicio) {
      errores.fechaTermino = "La fecha de término debe ser igual o posterior a la fecha de inicio.";
    }

    setErroresLocales(errores);
    if (Object.keys(errores).length > 0 || clienteId === null || precio === null) {
      return null;
    }

    return {
      clienteId,
      tipoServicioId,
      codigo: vacioANulo(codigo),
      nombre: nombre.trim(),
      descripcion: vacioANulo(descripcion),
      precioBaseNeto: precio,
      monedaPrecio,
      periodicidad,
      diaFacturacion: dia,
      fechaInicio,
      fechaTermino: vacioANulo(fechaTermino),
      activo,
    };
  }

  async function alEnviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();

    const solicitud = validarAntesDeEnviar();
    if (!solicitud) {
      return;
    }

    const exito = await manejarEnvio(async () => {
      if (proyecto) {
        await clienteApiCliente.actualizar(`/proyectos/${proyecto.id}`, solicitud);
      } else {
        await clienteApiCliente.crear("/proyectos", solicitud);
      }
    });

    if (exito) {
      onExito();
    }
  }

  return (
    <FormularioDialogo
      abierto
      titulo={proyecto ? "Editar proyecto" : "Nuevo proyecto"}
      enviando={enviando}
      error={error}
      onCerrar={onCerrar}
      onEnviar={alEnviar}
    >
      <CampoFormulario
        id="clienteId"
        etiqueta="Cliente"
        requerido
        error={errorDeCampo("clienteId")}
      >
        <SelectorCliente
          id="clienteId"
          valor={clienteId}
          onCambiar={setClienteId}
          invalida={Boolean(errorDeCampo("clienteId"))}
          clienteInicial={
            proyecto ? { id: proyecto.clienteId, razonSocial: proyecto.clienteRazonSocial } : null
          }
        />
      </CampoFormulario>

      <CampoFormulario
        id="tipoServicioId"
        etiqueta="Tipo de servicio"
        error={errorDeCampo("tipoServicioId")}
      >
        <SelectorTipoServicio
          id="tipoServicioId"
          valor={tipoServicioId}
          onCambiar={setTipoServicioId}
          tipoServicioInicial={
            proyecto?.tipoServicioId
              ? { id: proyecto.tipoServicioId, nombre: proyecto.tipoServicioNombre ?? "" }
              : null
          }
        />
      </CampoFormulario>

      <CampoFormulario id="codigo" etiqueta="Código" error={errorDeCampo("codigo")}>
        <Entrada
          id="codigo"
          value={codigo}
          onChange={(evento) => setCodigo(evento.target.value)}
          maxLength={40}
          className="w-full"
        />
      </CampoFormulario>

      <CampoFormulario id="nombre" etiqueta="Nombre" requerido error={errorDeCampo("nombre")}>
        <Entrada
          id="nombre"
          value={nombre}
          onChange={(evento) => setNombre(evento.target.value)}
          invalida={Boolean(errorDeCampo("nombre"))}
          maxLength={200}
          required
          className="w-full"
        />
      </CampoFormulario>

      <CampoFormulario id="descripcion" etiqueta="Descripción" error={errorDeCampo("descripcion")}>
        <AreaTexto
          id="descripcion"
          value={descripcion}
          onChange={(evento) => setDescripcion(evento.target.value)}
          maxLength={500}
          className="w-full"
        />
      </CampoFormulario>

      <div className="grid grid-cols-2 gap-3">
        <CampoFormulario
          id="precioBaseNeto"
          etiqueta="Precio base neto"
          requerido
          error={errorDeCampo("precioBaseNeto")}
          descripcion="Neto, sin IVA, en la moneda seleccionada."
        >
          <Entrada
            id="precioBaseNeto"
            inputMode="decimal"
            value={precioTexto}
            onChange={(evento) => setPrecioTexto(evento.target.value)}
            onBlur={alPerderFocoPrecio}
            invalida={Boolean(errorDeCampo("precioBaseNeto"))}
            className="w-full"
          />
        </CampoFormulario>

        <CampoFormulario
          id="monedaPrecio"
          etiqueta="Moneda"
          requerido
          error={errorDeCampo("monedaPrecio")}
        >
          <Seleccion
            id="monedaPrecio"
            value={monedaPrecio}
            onChange={(evento) => setMonedaPrecio(evento.target.value as Moneda)}
            className="w-full"
          >
            <option value="CLP">CLP</option>
            <option value="UF">UF</option>
          </Seleccion>
        </CampoFormulario>
      </div>

      <CampoFormulario
        id="periodicidad"
        etiqueta="Periodicidad"
        requerido
        error={errorDeCampo("periodicidad")}
        descripcion={
          periodicidad === "MENSUAL"
            ? "Mensual: el primer cobro es el mes SIGUIENTE al de inicio (sin prorratear)."
            : "Anual: se factura en el mes y día del contrato, cada año."
        }
      >
        <Seleccion
          id="periodicidad"
          value={periodicidad}
          onChange={(evento) => setPeriodicidad(evento.target.value as Periodicidad)}
          className="w-full"
        >
          <option value="MENSUAL">Mensual</option>
          <option value="ANUAL">Anual</option>
        </Seleccion>
      </CampoFormulario>

      <CampoFormulario
        id="diaFacturacion"
        etiqueta="Día de facturación"
        requerido
        error={errorDeCampo("diaFacturacion")}
        descripcion="Si el mes no tiene este día (p. ej. 31 en febrero), se factura el último día del mes."
      >
        <Entrada
          id="diaFacturacion"
          type="number"
          min={1}
          max={31}
          value={diaFacturacion}
          onChange={(evento) => setDiaFacturacion(evento.target.value)}
          invalida={Boolean(errorDeCampo("diaFacturacion"))}
          required
          className="w-full"
        />
      </CampoFormulario>

      <div className="grid grid-cols-2 gap-3">
        <CampoFormulario
          id="fechaInicio"
          etiqueta="Fecha de inicio"
          requerido
          error={errorDeCampo("fechaInicio")}
        >
          <Entrada
            id="fechaInicio"
            type="date"
            value={fechaInicio}
            onChange={(evento) => setFechaInicio(evento.target.value)}
            invalida={Boolean(errorDeCampo("fechaInicio"))}
            required
            className="w-full"
          />
        </CampoFormulario>

        <CampoFormulario
          id="fechaTermino"
          etiqueta="Fecha de término"
          error={errorDeCampo("fechaTermino")}
        >
          <Entrada
            id="fechaTermino"
            type="date"
            value={fechaTermino}
            onChange={(evento) => setFechaTermino(evento.target.value)}
            invalida={Boolean(errorDeCampo("fechaTermino"))}
            className="w-full"
          />
        </CampoFormulario>
      </div>

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
