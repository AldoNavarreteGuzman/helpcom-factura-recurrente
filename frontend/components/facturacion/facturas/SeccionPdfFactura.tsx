"use client";

import { useRef, useState, type ChangeEvent, type FormEvent } from "react";
import { Alerta } from "@/components/ui/Alerta";
import { Boton } from "@/components/ui/Boton";
import { useNotificaciones } from "@/components/ui/Notificaciones";
import { descargarArchivoEnNavegador } from "@/lib/archivos";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { obtenerMensajeError } from "@/lib/errores";
import { useFormularioApi } from "@/lib/useFormularioApi";
import type { Factura } from "@/types/factura";

const TIPO_CONTENIDO_PDF = "application/pdf";

export interface PropiedadesSeccionPdfFactura {
  factura: Factura;
  onActualizada: (factura: Factura) => void;
}

/**
 * Subida/descarga/reemplazo del PDF de respaldo de una factura (arquitectura-tecnica.md §14:
 * el acceso siempre pasa por el backend, nunca una URL directa del bucket). "Descargar" usa
 * `clienteApiCliente.descargarArchivo` (fetch autenticado → blob → `lib/archivos.ts`), NUNCA
 * un `<a href>` directo al endpoint: no llevaría el header `Authorization`.
 *
 * El tamaño máximo del archivo no está expuesto al frontend (vive en
 * `PropiedadesAlmacenamiento` del backend, configuración de servidor) — se valida en cliente
 * solo el TIPO (`application/pdf`); el tamaño lo impone el backend
 * (`ARCHIVO_DEMASIADO_GRANDE`), y su mensaje se muestra tal cual si se excede.
 */
export function SeccionPdfFactura({ factura, onActualizada }: PropiedadesSeccionPdfFactura) {
  const { notificar, notificarError } = useNotificaciones();
  const { enviando, error, manejarEnvio } = useFormularioApi();

  const [archivo, setArchivo] = useState<File | null>(null);
  const [errorTipo, setErrorTipo] = useState<string | null>(null);
  const [reemplazando, setReemplazando] = useState(false);
  const [descargando, setDescargando] = useState(false);
  const referenciaInput = useRef<HTMLInputElement>(null);

  function alElegirArchivo(evento: ChangeEvent<HTMLInputElement>) {
    const elegido = evento.target.files?.[0] ?? null;
    if (elegido && elegido.type !== TIPO_CONTENIDO_PDF) {
      setErrorTipo("Solo se aceptan archivos PDF (application/pdf).");
      setArchivo(null);
      return;
    }
    setErrorTipo(null);
    setArchivo(elegido);
  }

  async function alSubir(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    if (!archivo) {
      setErrorTipo("Selecciona un archivo PDF.");
      return;
    }
    if (archivo.type !== TIPO_CONTENIDO_PDF) {
      setErrorTipo("Solo se aceptan archivos PDF (application/pdf).");
      return;
    }

    const eraReemplazo = reemplazando;
    const exito = await manejarEnvio(async () => {
      const actualizada = await clienteApiCliente.subirArchivo<Factura>(
        `/facturas/${factura.id}/pdf`,
        "archivo",
        archivo,
      );
      onActualizada(actualizada);
      notificar(eraReemplazo ? "PDF reemplazado." : "PDF subido.", "exito");
    });

    if (exito) {
      setArchivo(null);
      setReemplazando(false);
      if (referenciaInput.current) {
        referenciaInput.current.value = "";
      }
    }
  }

  async function alDescargar() {
    setDescargando(true);
    try {
      const descarga = await clienteApiCliente.descargarArchivo(
        `/facturas/${factura.id}/pdf`,
        factura.nombreArchivoPdf ?? `${factura.numeroFactura}.pdf`,
      );
      descargarArchivoEnNavegador(descarga);
    } catch (error) {
      notificarError(error);
    } finally {
      setDescargando(false);
    }
  }

  const mostrarFormulario = !factura.tienePdf || reemplazando;

  return (
    <div className="space-y-3 rounded-md border border-linea p-4">
      <h2 className="text-sm font-semibold text-tinta">PDF de respaldo</h2>

      {factura.tienePdf && !reemplazando ? (
        <div className="flex items-center justify-between">
          <span className="text-sm text-texto">{factura.nombreArchivoPdf}</span>
          <div className="flex items-center gap-3">
            <Boton variante="secundario" onClick={alDescargar} cargando={descargando}>
              Descargar
            </Boton>
            <Boton variante="secundario" onClick={() => setReemplazando(true)}>
              Reemplazar
            </Boton>
          </div>
        </div>
      ) : null}

      {mostrarFormulario ? (
        <form onSubmit={alSubir} className="space-y-3" noValidate>
          {error ? <Alerta variante="error">{obtenerMensajeError(error)}</Alerta> : null}
          <div className="flex items-center gap-3">
            <input
              ref={referenciaInput}
              type="file"
              accept="application/pdf"
              aria-label="Archivo PDF"
              onChange={alElegirArchivo}
              className="text-sm text-texto"
            />
            <Boton type="submit" cargando={enviando}>
              {factura.tienePdf ? "Reemplazar PDF" : "Subir PDF"}
            </Boton>
            {reemplazando ? (
              <button
                type="button"
                onClick={() => {
                  setReemplazando(false);
                  setArchivo(null);
                  setErrorTipo(null);
                }}
                className="text-sm text-sutil hover:text-marca-azul"
              >
                Cancelar
              </button>
            ) : null}
          </div>
          {errorTipo ? (
            <p role="alert" className="text-xs text-estado-error">
              {errorTipo}
            </p>
          ) : null}
        </form>
      ) : null}
    </div>
  );
}
