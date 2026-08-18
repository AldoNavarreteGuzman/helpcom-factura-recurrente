"use client";

import Link from "next/link";
import { useState } from "react";
import { BadgeEstadoEjecucionCiclo } from "./BadgeEstadoEjecucionCiclo";
import { Alerta } from "@/components/ui/Alerta";
import { Boton } from "@/components/ui/Boton";
import { CampoFormulario } from "@/components/ui/CampoFormulario";
import { EncabezadoDetalle } from "@/components/detalle/EncabezadoDetalle";
import { DialogoConfirmacion } from "@/components/ui/DialogoConfirmacion";
import { Entrada } from "@/components/ui/Entrada";
import { Seleccion } from "@/components/ui/Seleccion";
import { useNotificaciones } from "@/components/ui/Notificaciones";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { obtenerMensajeError } from "@/lib/errores";
import { NOMBRES_MES } from "@/lib/etiquetas";
import { obtenerAnioMesSantiago } from "@/lib/formato";
import { useTieneAlgunRol } from "@/lib/useRoles";
import type { ResultadoCiclo } from "@/types/ejecucionCiclo";

const { anio: ANIO_ACTUAL, mes: MES_ACTUAL } = obtenerAnioMesSantiago();

export function EjecutarCiclo() {
  const puedeEjecutar = useTieneAlgunRol(["ADMINISTRADOR"]);
  const { notificar, notificarError } = useNotificaciones();

  const [anio, setAnio] = useState(String(ANIO_ACTUAL));
  const [mes, setMes] = useState(String(MES_ACTUAL));
  const [confirmando, setConfirmando] = useState(false);
  const [ejecutando, setEjecutando] = useState(false);
  const [resultado, setResultado] = useState<ResultadoCiclo | null>(null);
  const [error, setError] = useState<unknown>(null);

  async function ejecutar() {
    setEjecutando(true);
    setError(null);
    setResultado(null);
    try {
      const respuesta = await clienteApiCliente.crear<ResultadoCiclo>("/ciclos/ejecutar", {
        anio: Number(anio),
        mes: Number(mes),
      });
      setResultado(respuesta);
      if (respuesta.estado === "EXITOSA") {
        notificar("Ciclo ejecutado correctamente.", "exito");
      } else if (respuesta.estado === "CON_ADVERTENCIAS") {
        notificar("El ciclo terminó con advertencias — revisa el resumen.", "info");
      } else {
        notificar("El ciclo terminó con errores.", "error");
      }
    } catch (error) {
      setError(error);
      notificarError(error);
    } finally {
      setEjecutando(false);
      setConfirmando(false);
    }
  }

  const enlacePropuestas = resultado
    ? `/facturacion?periodoAnio=${resultado.anio}&periodoMes=${resultado.mes}` +
      (resultado.cantidadPendientesUf > 0 ? "&estado=PENDIENTE_UF" : "")
    : "";

  return (
    <div className="max-w-xl space-y-6">
      <EncabezadoDetalle
        volverA="/facturacion"
        volverEtiqueta="Volver a propuestas"
        titulo="Ejecutar ciclo de facturación"
      />

      <div className="grid grid-cols-2 gap-3">
        <CampoFormulario id="anio" etiqueta="Año">
          <Entrada
            id="anio"
            type="number"
            min={2000}
            max={3000}
            value={anio}
            onChange={(evento) => setAnio(evento.target.value)}
            disabled={ejecutando}
            className="w-full"
          />
        </CampoFormulario>
        <CampoFormulario id="mes" etiqueta="Mes">
          <Seleccion
            id="mes"
            value={mes}
            onChange={(evento) => setMes(evento.target.value)}
            disabled={ejecutando}
            className="w-full"
          >
            {NOMBRES_MES.map((nombre, indice) => (
              <option key={nombre} value={indice + 1}>
                {nombre}
              </option>
            ))}
          </Seleccion>
        </CampoFormulario>
      </div>

      <div>
        <Boton
          onClick={() => setConfirmando(true)}
          disabled={!puedeEjecutar}
          cargando={ejecutando}
          title={puedeEjecutar ? undefined : "Requiere el rol ADMINISTRADOR."}
        >
          Ejecutar ciclo
        </Boton>
        {!puedeEjecutar ? (
          <p className="mt-2 text-xs text-sutil">Esta acción requiere el rol ADMINISTRADOR.</p>
        ) : null}
      </div>

      {error ? <Alerta variante="error">{obtenerMensajeError(error)}</Alerta> : null}

      {resultado ? (
        <div className="space-y-2 rounded-md border border-linea p-4">
          <BadgeEstadoEjecucionCiclo estado={resultado.estado} />
          <p className="text-sm text-texto">
            {resultado.cantidadGeneradas} propuesta{resultado.cantidadGeneradas === 1 ? "" : "s"}{" "}
            generada
            {resultado.cantidadGeneradas === 1 ? "" : "s"} para el período {resultado.mes}/
            {resultado.anio}.
          </p>
          {resultado.cantidadPendientesUf > 0 ? (
            <Alerta variante="advertencia">
              {resultado.cantidadPendientesUf} propuesta
              {resultado.cantidadPendientesUf === 1 ? "" : "s"} quedaron sin valor UF disponible
              para su fecha; revísalas y vuelve a ejecutar cuando la UF esté disponible.
            </Alerta>
          ) : null}
          {resultado.observacion ? (
            <p className="text-sm text-sutil">{resultado.observacion}</p>
          ) : null}
          {resultado.ejecucionId != null ? (
            <Link
              href={enlacePropuestas}
              className="inline-block text-sm font-medium text-marca-azul hover:text-marca-azul-700"
            >
              Ver propuestas de este período →
            </Link>
          ) : (
            <p className="text-sm text-sutil">
              Ya había una ejecución en curso para este período; no se realizó ningún cambio.
            </p>
          )}
        </div>
      ) : null}

      <DialogoConfirmacion
        abierto={confirmando}
        titulo="Ejecutar ciclo de facturación"
        mensaje={`Se van a generar las propuestas de facturación del período ${mes}/${anio} para todos los proyectos activos que correspondan. El proceso es idempotente: si ya se ejecutó para este período, no se duplican propuestas.`}
        variante="primario"
        etiquetaConfirmar="Ejecutar"
        procesando={ejecutando}
        onConfirmar={ejecutar}
        onCancelar={() => setConfirmando(false)}
      />
    </div>
  );
}
