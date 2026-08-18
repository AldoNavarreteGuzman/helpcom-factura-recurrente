"use client";

import { useMemo, useState, type FormEvent } from "react";
import { FormularioDialogo } from "@/components/formularios/FormularioDialogo";
import { AreaTexto } from "@/components/ui/AreaTexto";
import { CampoFormulario } from "@/components/ui/CampoFormulario";
import { Entrada } from "@/components/ui/Entrada";
import { Seleccion } from "@/components/ui/Seleccion";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { obtenerErrorDeCampo } from "@/lib/errores";
import { formatearFecha } from "@/lib/formato";
import { calcularTerminoDesdeMeses, seSuperponen } from "@/lib/vigencia";
import { useFormularioApi } from "@/lib/useFormularioApi";
import type { AcuerdoPrecio, AcuerdoPrecioSolicitud } from "@/types/acuerdoPrecio";
import type { Moneda, TipoAcuerdo } from "@/types/dominio";

export interface PropiedadesFormularioAcuerdo {
  proyectoId: number;
  /** `null` = creando uno nuevo. */
  acuerdo: AcuerdoPrecio | null;
  /** Los demás acuerdos del proyecto (sin el propio, si se está editando) — ver §"ayuda proactiva". */
  acuerdosExistentes: AcuerdoPrecio[];
  onCerrar: () => void;
  onExito: () => void;
}

type ModoVigencia = "fecha" | "meses";

const ETIQUETA_VALOR: Record<TipoAcuerdo, string> = {
  DESCUENTO_PORCENTAJE: "Porcentaje de descuento (%)",
  DESCUENTO_MONTO: "Monto del descuento",
  PRECIO_PACTADO: "Precio pactado",
};

export function FormularioAcuerdo({
  proyectoId,
  acuerdo,
  acuerdosExistentes,
  onCerrar,
  onExito,
}: PropiedadesFormularioAcuerdo) {
  const { enviando, error, manejarEnvio } = useFormularioApi();

  const [tipo, setTipo] = useState<TipoAcuerdo>(acuerdo?.tipo ?? "DESCUENTO_PORCENTAJE");
  const [valor, setValor] = useState(acuerdo ? String(acuerdo.valor) : "");
  const [moneda, setMoneda] = useState<Moneda>(acuerdo?.moneda ?? "CLP");
  const [modo, setModo] = useState<ModoVigencia>(
    acuerdo?.mesesPactados != null ? "meses" : "fecha",
  );
  const [fechaInicio, setFechaInicio] = useState(acuerdo?.fechaInicio ?? "");
  const [fechaTermino, setFechaTermino] = useState(acuerdo?.fechaTermino ?? "");
  const [mesesPactados, setMesesPactados] = useState(
    acuerdo?.mesesPactados ? String(acuerdo.mesesPactados) : "",
  );
  const [observacion, setObservacion] = useState(acuerdo?.observacion ?? "");

  const [erroresLocales, setErroresLocales] = useState<Record<string, string>>({});

  const requiereMoneda = tipo !== "DESCUENTO_PORCENTAJE";

  const terminoCalculado = useMemo(() => {
    if (modo !== "meses" || fechaInicio.trim() === "") {
      return null;
    }
    const meses = Number(mesesPactados);
    if (!Number.isInteger(meses) || meses < 1) {
      return null;
    }
    return calcularTerminoDesdeMeses(fechaInicio, meses);
  }, [modo, fechaInicio, mesesPactados]);

  const terminoEfectivo = modo === "fecha" ? fechaTermino : terminoCalculado;

  const avisoSolape = useMemo(() => {
    if (fechaInicio.trim() === "" || !terminoEfectivo) {
      return null;
    }
    const conflicto = acuerdosExistentes.find((existente) =>
      seSuperponen(fechaInicio, terminoEfectivo, existente.fechaInicio, existente.fechaTermino),
    );
    if (!conflicto) {
      return null;
    }
    return `Este rango se superpone con el acuerdo del ${formatearFecha(conflicto.fechaInicio)} al ${formatearFecha(conflicto.fechaTermino)}. El backend rechazará el guardado si efectivamente se traslapan.`;
  }, [fechaInicio, terminoEfectivo, acuerdosExistentes]);

  function errorDeCampo(campo: string): string | undefined {
    return erroresLocales[campo] ?? obtenerErrorDeCampo(error, campo);
  }

  function validarAntesDeEnviar(): AcuerdoPrecioSolicitud | null {
    const errores: Record<string, string> = {};

    const valorNumerico = Number(valor.replace(",", "."));
    if (valor.trim() === "" || !Number.isFinite(valorNumerico) || valorNumerico <= 0) {
      errores.valor = "El valor debe ser un número mayor que 0.";
    } else if (tipo === "DESCUENTO_PORCENTAJE" && valorNumerico > 100) {
      errores.valor = "El porcentaje debe ser mayor que 0 y menor o igual a 100.";
    }

    if (fechaInicio.trim() === "") {
      errores.fechaInicio = "La fecha de inicio es obligatoria.";
    }

    let fechaTerminoFinal: string | null = null;
    let mesesFinal: number | null = null;
    if (modo === "fecha") {
      if (fechaTermino.trim() === "") {
        errores.fechaTermino = "La fecha de término es obligatoria.";
      } else if (fechaInicio.trim() !== "" && fechaTermino < fechaInicio) {
        errores.fechaTermino =
          "La fecha de término debe ser igual o posterior a la fecha de inicio.";
      } else {
        fechaTerminoFinal = fechaTermino;
      }
    } else {
      const meses = Number(mesesPactados);
      if (!Number.isInteger(meses) || meses < 1) {
        errores.mesesPactados = "Los meses pactados deben ser un entero mayor o igual a 1.";
      } else {
        mesesFinal = meses;
      }
    }

    setErroresLocales(errores);
    if (Object.keys(errores).length > 0) {
      return null;
    }

    return {
      tipo,
      valor: valorNumerico,
      moneda: requiereMoneda ? moneda : null,
      fechaInicio,
      fechaTermino: fechaTerminoFinal,
      mesesPactados: mesesFinal,
      observacion: observacion.trim() === "" ? null : observacion.trim(),
    };
  }

  async function alEnviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();

    const solicitud = validarAntesDeEnviar();
    if (!solicitud) {
      return;
    }

    const exito = await manejarEnvio(async () => {
      if (acuerdo) {
        await clienteApiCliente.actualizar(
          `/proyectos/${proyectoId}/acuerdos/${acuerdo.id}`,
          solicitud,
        );
      } else {
        await clienteApiCliente.crear(`/proyectos/${proyectoId}/acuerdos`, solicitud);
      }
    });

    if (exito) {
      onExito();
    }
  }

  return (
    <FormularioDialogo
      abierto
      titulo={acuerdo ? "Editar acuerdo de precio" : "Nuevo acuerdo de precio"}
      enviando={enviando}
      error={error}
      onCerrar={onCerrar}
      onEnviar={alEnviar}
    >
      {acuerdosExistentes.length > 0 ? (
        <div className="rounded border border-linea bg-fondo px-3 py-2 text-xs text-sutil">
          <p className="mb-1 font-medium text-texto">Vigencias ya ocupadas en este proyecto:</p>
          <ul className="list-inside list-disc space-y-0.5">
            {acuerdosExistentes.map((existente) => (
              <li key={existente.id}>
                {formatearFecha(existente.fechaInicio)} a {formatearFecha(existente.fechaTermino)}
              </li>
            ))}
          </ul>
        </div>
      ) : null}

      <CampoFormulario id="tipo" etiqueta="Tipo de acuerdo" requerido error={errorDeCampo("tipo")}>
        <Seleccion
          id="tipo"
          value={tipo}
          onChange={(evento) => setTipo(evento.target.value as TipoAcuerdo)}
          className="w-full"
        >
          <option value="DESCUENTO_PORCENTAJE">Descuento porcentual</option>
          <option value="DESCUENTO_MONTO">Descuento por monto</option>
          <option value="PRECIO_PACTADO">Precio pactado</option>
        </Seleccion>
      </CampoFormulario>

      {tipo === "PRECIO_PACTADO" ? (
        <p className="text-xs text-sutil">
          El precio pactado REEMPLAZA al precio base del proyecto durante toda la vigencia de este
          acuerdo.
        </p>
      ) : null}

      <div className={requiereMoneda ? "grid grid-cols-2 gap-3" : ""}>
        <CampoFormulario
          id="valor"
          etiqueta={ETIQUETA_VALOR[tipo]}
          requerido
          error={errorDeCampo("valor")}
        >
          <Entrada
            id="valor"
            inputMode="decimal"
            value={valor}
            onChange={(evento) => setValor(evento.target.value)}
            invalida={Boolean(errorDeCampo("valor"))}
            className="w-full"
          />
        </CampoFormulario>

        {requiereMoneda ? (
          <CampoFormulario id="moneda" etiqueta="Moneda" requerido error={errorDeCampo("moneda")}>
            <Seleccion
              id="moneda"
              value={moneda}
              onChange={(evento) => setMoneda(evento.target.value as Moneda)}
              className="w-full"
            >
              <option value="CLP">CLP</option>
              <option value="UF">UF</option>
            </Seleccion>
          </CampoFormulario>
        ) : null}
      </div>

      <CampoFormulario id="modoVigencia" etiqueta="Vigencia">
        <Seleccion
          id="modoVigencia"
          value={modo}
          onChange={(evento) => setModo(evento.target.value as ModoVigencia)}
          className="w-full"
        >
          <option value="fecha">Fecha de inicio y término</option>
          <option value="meses">Fecha de inicio y meses pactados</option>
        </Seleccion>
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

        {modo === "fecha" ? (
          <CampoFormulario
            id="fechaTermino"
            etiqueta="Fecha de término"
            requerido
            error={errorDeCampo("fechaTermino")}
          >
            <Entrada
              id="fechaTermino"
              type="date"
              value={fechaTermino}
              onChange={(evento) => setFechaTermino(evento.target.value)}
              invalida={Boolean(errorDeCampo("fechaTermino"))}
              required
              className="w-full"
            />
          </CampoFormulario>
        ) : (
          <CampoFormulario
            id="mesesPactados"
            etiqueta="Meses pactados"
            requerido
            error={errorDeCampo("mesesPactados")}
            descripcion={
              terminoCalculado
                ? `Término calculado: ${formatearFecha(terminoCalculado)}`
                : undefined
            }
          >
            <Entrada
              id="mesesPactados"
              type="number"
              min={1}
              value={mesesPactados}
              onChange={(evento) => setMesesPactados(evento.target.value)}
              invalida={Boolean(errorDeCampo("mesesPactados"))}
              required
              className="w-full"
            />
          </CampoFormulario>
        )}
      </div>

      {avisoSolape ? (
        <p role="status" className="rounded border border-estado-sin-uf/30 bg-estado-sin-uf/10 px-3 py-2 text-xs text-estado-sin-uf">
          {avisoSolape}
        </p>
      ) : null}

      <CampoFormulario id="observacion" etiqueta="Observación" error={errorDeCampo("observacion")}>
        <AreaTexto
          id="observacion"
          value={observacion ?? ""}
          onChange={(evento) => setObservacion(evento.target.value)}
          maxLength={300}
          className="w-full"
        />
      </CampoFormulario>
    </FormularioDialogo>
  );
}
