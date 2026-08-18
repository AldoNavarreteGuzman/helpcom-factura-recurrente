"use client";

import Link from "next/link";
import { useState, type ChangeEvent } from "react";
import { AyudaFormatoCsv } from "./AyudaFormatoCsv";
import { ResultadoImportacion } from "./ResultadoImportacion";
import { TablaPreviewImportacion } from "./TablaPreviewImportacion";
import { Alerta } from "@/components/ui/Alerta";
import { Boton } from "@/components/ui/Boton";
import { DialogoConfirmacion } from "@/components/ui/DialogoConfirmacion";
import { useNotificaciones } from "@/components/ui/Notificaciones";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { obtenerMensajeError } from "@/lib/errores";
import { esSinUf } from "@/lib/importaciones";
import { useFormularioApi } from "@/lib/useFormularioApi";
import { useNotificarErrorUnaVez } from "@/lib/useNotificarErrorUnaVez";
import type { ImportacionCsv, ImportacionPreview } from "@/types/importacionCsv";

function esArchivoCsv(archivo: File): boolean {
  return archivo.name.toLowerCase().endsWith(".csv");
}

/**
 * Importación CSV en dos fases (arquitectura-tecnica.md §10): previsualizar → confirmar,
 * reenviando el MISMO `File` (estrategia (a) del backend: no hay id temporal, confirmar
 * vuelve a parsear y validar el archivo completo). El `File` se conserva en estado de React
 * mientras dure la sesión de la pantalla — no se sube a ningún lado hasta que el usuario pide
 * previsualizar o confirmar explícitamente.
 */
export function ImportarCsv() {
  const { notificar } = useNotificaciones();
  const {
    enviando: previsualizando,
    error: errorPreview,
    manejarEnvio: manejarPrevisualizar,
  } = useFormularioApi();
  const {
    enviando: confirmando,
    error: errorConfirmar,
    manejarEnvio: manejarConfirmar,
  } = useFormularioApi();

  useNotificarErrorUnaVez(errorPreview);
  useNotificarErrorUnaVez(errorConfirmar);

  const [archivo, setArchivo] = useState<File | null>(null);
  const [errorArchivo, setErrorArchivo] = useState<string | null>(null);
  const [preview, setPreview] = useState<ImportacionPreview | null>(null);
  const [resultado, setResultado] = useState<ImportacionCsv | null>(null);
  const [mostrarConfirmacion, setMostrarConfirmacion] = useState(false);

  function alElegirArchivo(evento: ChangeEvent<HTMLInputElement>) {
    const elegido = evento.target.files?.[0] ?? null;
    setPreview(null);
    setResultado(null);
    if (elegido && !esArchivoCsv(elegido)) {
      setErrorArchivo("Selecciona un archivo .csv.");
      setArchivo(null);
      return;
    }
    setErrorArchivo(null);
    setArchivo(elegido);
  }

  async function previsualizar() {
    if (!archivo) {
      return;
    }
    await manejarPrevisualizar(async () => {
      const respuesta = await clienteApiCliente.subirArchivo<ImportacionPreview>(
        "/importaciones/previsualizar",
        "archivo",
        archivo,
      );
      setPreview(respuesta);
      setResultado(null);
    });
  }

  async function confirmar() {
    if (!archivo) {
      return;
    }
    await manejarConfirmar(async () => {
      const respuesta = await clienteApiCliente.subirArchivo<ImportacionCsv>(
        "/importaciones/confirmar",
        "archivo",
        archivo,
      );
      setResultado(respuesta);
      if (respuesta.estado === "PROCESADA") {
        notificar("Importación procesada correctamente.", "exito");
      } else if (respuesta.estado === "PARCIAL") {
        notificar("La importación terminó parcial — revisa las filas con error.", "info");
      } else {
        notificar("La importación fue rechazada: ninguna fila se importó.", "error");
      }
    });
    setMostrarConfirmacion(false);
  }

  const filasImportables = preview ? preview.resumen.filasOk + preview.resumen.filasAdvertencia : 0;
  const pendientesUfEstimadas = preview
    ? preview.filas.filter((fila) => fila.estado === "ADVERTENCIA" && esSinUf(fila)).length
    : 0;

  const mensajeConfirmacion = preview
    ? `Se importarán ${filasImportables} fila${filasImportables === 1 ? "" : "s"} ` +
      `(${preview.resumen.filasOk} OK + ${preview.resumen.filasAdvertencia} con advertencia); ` +
      `${preview.resumen.filasError} fila${preview.resumen.filasError === 1 ? "" : "s"} con error NO ` +
      `se ${preview.resumen.filasError === 1 ? "importará" : "importarán"}. ` +
      "Las filas con advertencia SÍ se importan" +
      (pendientesUfEstimadas > 0
        ? `, de las cuales ~${pendientesUfEstimadas} (estimado según esta previsualización) ` +
          "podrían quedar en estado Pendiente UF (sin monto calculado hasta que haya un valor " +
          "UF disponible) — el número real se confirma al procesar."
        : ".") +
      " Confirmar vuelve a validar el archivo completo: el resultado puede diferir de esta " +
      "previsualización si los datos de base (clientes, proyectos, UF) cambiaron mientras tanto."
    : "";

  return (
    <div className="max-w-5xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-tinta">Importación CSV</h1>
        <Link
          href="/importacion/historial"
          className="text-sm font-medium text-marca-azul hover:text-marca-azul-700"
        >
          Ver historial de importaciones →
        </Link>
      </div>

      <AyudaFormatoCsv />

      <div className="space-y-3 rounded-md border border-linea p-4">
        <h2 className="text-sm font-semibold text-tinta">Archivo</h2>
        <div className="flex flex-wrap items-center gap-3">
          <input
            type="file"
            accept=".csv,text/csv"
            aria-label="Archivo CSV"
            onChange={alElegirArchivo}
            className="text-sm text-texto"
          />
          <Boton onClick={previsualizar} disabled={!archivo} cargando={previsualizando}>
            Previsualizar
          </Boton>
        </div>
        {errorArchivo ? (
          <p role="alert" className="text-xs text-estado-error">
            {errorArchivo}
          </p>
        ) : null}
        {errorPreview ? <Alerta variante="error">{obtenerMensajeError(errorPreview)}</Alerta> : null}
      </div>

      {preview ? (
        <>
          <TablaPreviewImportacion preview={preview} />

          <div className="space-y-2">
            <Boton
              onClick={() => setMostrarConfirmacion(true)}
              disabled={filasImportables === 0}
              title={
                filasImportables === 0
                  ? "Todas las filas tienen error; no hay nada para importar."
                  : undefined
              }
            >
              Confirmar importación
            </Boton>
            {filasImportables === 0 ? (
              <p className="text-xs text-sutil">
                Todas las filas tienen error; no hay nada para importar.
              </p>
            ) : null}
            {errorConfirmar ? (
              <Alerta variante="error">{obtenerMensajeError(errorConfirmar)}</Alerta>
            ) : null}
          </div>
        </>
      ) : null}

      {resultado ? <ResultadoImportacion resultado={resultado} preview={preview} /> : null}

      <DialogoConfirmacion
        abierto={mostrarConfirmacion}
        titulo="Confirmar importación"
        mensaje={mensajeConfirmacion}
        variante="primario"
        etiquetaConfirmar="Confirmar"
        procesando={confirmando}
        onConfirmar={confirmar}
        onCancelar={() => setMostrarConfirmacion(false)}
      />
    </div>
  );
}
