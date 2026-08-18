"use client";

import { useRouter } from "next/navigation";
import { useCallback, useMemo, useState, type FormEvent } from "react";
import { BadgeEstadoPropuesta } from "@/components/facturacion/propuestas/BadgeEstadoPropuesta";
import { SelectorCliente } from "@/components/clientes/SelectorCliente";
import { EncabezadoDetalle } from "@/components/detalle/EncabezadoDetalle";
import { PanelListado } from "@/components/listado/PanelListado";
import { Alerta } from "@/components/ui/Alerta";
import { AreaTexto } from "@/components/ui/AreaTexto";
import { Boton } from "@/components/ui/Boton";
import { CampoFormulario } from "@/components/ui/CampoFormulario";
import { Casilla } from "@/components/ui/Casilla";
import { Entrada } from "@/components/ui/Entrada";
import { useNotificaciones } from "@/components/ui/Notificaciones";
import { Seleccion } from "@/components/ui/Seleccion";
import type { ColumnaTabla } from "@/components/ui/Tabla";
import { ErrorApi } from "@/lib/clienteApi";
import { clienteApiCliente } from "@/lib/clienteApiCliente";
import { obtenerErrorDeCampo } from "@/lib/errores";
import { ETIQUETAS_ESTADO_PROPUESTA, NOMBRES_MES, formatearPeriodo } from "@/lib/etiquetas";
import { formatearClp } from "@/lib/formato";
import { esFacturable, formatearMontoClpOAusente } from "@/lib/propuestas";
import { construirQueryString } from "@/lib/query";
import { useFormularioApi } from "@/lib/useFormularioApi";
import { useListadoPaginado } from "@/lib/useListadoPaginado";
import { useNotificarErrorUnaVez } from "@/lib/useNotificarErrorUnaVez";
import type { PaginaRespuesta } from "@/types/api";
import type { EstadoPropuesta } from "@/types/dominio";
import type { Factura, FacturaSolicitud } from "@/types/factura";
import type { PropuestaFacturacion } from "@/types/propuestaFacturacion";

const TAMANO_PAGINA = 10;

const ESTADOS: EstadoPropuesta[] = ["PENDIENTE", "PENDIENTE_UF", "FACTURADA", "ANULADA"];

/**
 * `FACTURA_DUPLICADA` es un 409 (`ReglaNegocioException`) sin `problema.errores` — el backend
 * no lo reporta como error de VALIDACIÓN de campo, así que `obtenerErrorDeCampo` (que solo
 * mira `errores`) no lo mapea solo. Igual pertenece al campo `numeroFactura` en la UI: es el
 * único motivo por el que ese campo específico puede fallar.
 */
function errorCampoNumeroFactura(error: unknown): string | undefined {
  if (error instanceof ErrorApi && error.problema.codigo === "FACTURA_DUPLICADA") {
    return error.problema.detail ?? "Ya existe una factura con este número.";
  }
  return obtenerErrorDeCampo(error, "numeroFactura");
}

/**
 * Selección de propuestas `PENDIENTE` para crear una factura, en una ruta propia
 * (`/facturacion/facturas/nueva`) en vez de checkboxes sobre `ListaPropuestas` — mismo criterio
 * que Acuerdos (`docs/frontend.md` §4.1): la selección debe sobrevivir a cambios de filtro/
 * página (Map por id, no el estado de la página actual), y mezclar ese estado en el listado de
 * propuestas de uso general (que ya tiene su propio propósito: ver/anular) lo habría
 * complicado sin necesidad. Documentado en `docs/frontend.md` §6.
 */
export function NuevaFactura() {
  const router = useRouter();
  const { notificar } = useNotificaciones();
  const { enviando, error, manejarEnvio } = useFormularioApi();

  // Mismo criterio que `FormularioDialogo` (docs/frontend.md §3.2): el error general se
  // muestra DOS veces a propósito — notificación + banner en el formulario. Esta pantalla no
  // usa `FormularioDialogo` (no es un modal), así que replica el efecto vía
  // `useNotificarErrorUnaVez` (docs/frontend.md §7.2).
  useNotificarErrorUnaVez(error);

  const [pagina, setPagina] = useState(0);
  const [periodoAnio, setPeriodoAnio] = useState("");
  const [periodoMes, setPeriodoMes] = useState("");
  const [clienteFiltro, setClienteFiltro] = useState<number | null>(null);
  const [estadoFiltro, setEstadoFiltro] = useState("");

  const [seleccionadas, setSeleccionadas] = useState<Map<number, PropuestaFacturacion>>(new Map());

  const [numeroFactura, setNumeroFactura] = useState("");
  const [fechaFactura, setFechaFactura] = useState("");
  const [observacion, setObservacion] = useState("");
  const [erroresLocales, setErroresLocales] = useState<Record<string, string>>({});

  const fetcher = useCallback(() => {
    const query = construirQueryString({
      periodoAnio: periodoAnio || undefined,
      periodoMes: periodoMes || undefined,
      clienteId: clienteFiltro ?? undefined,
      estado: estadoFiltro || undefined,
      page: pagina,
      size: TAMANO_PAGINA,
    });
    return clienteApiCliente.obtener<PaginaRespuesta<PropuestaFacturacion>>(`/propuestas${query}`);
  }, [periodoAnio, periodoMes, clienteFiltro, estadoFiltro, pagina]);

  const {
    datos,
    totalPaginas,
    cargando,
    error: errorListado,
    recargar,
  } = useListadoPaginado(fetcher, [periodoAnio, periodoMes, clienteFiltro, estadoFiltro, pagina]);

  const clienteSeleccionado = useMemo(() => {
    const primera = seleccionadas.values().next();
    return primera.done ? null : primera.value.clienteId;
  }, [seleccionadas]);

  const subtotal = useMemo(
    () => Array.from(seleccionadas.values()).reduce((acumulado, p) => acumulado + p.totalClp, 0),
    [seleccionadas],
  );

  function motivoNoSeleccionable(propuesta: PropuestaFacturacion): string | null {
    if (!esFacturable(propuesta.estado)) {
      return "Solo se pueden facturar propuestas en estado Pendiente.";
    }
    if (clienteSeleccionado !== null && propuesta.clienteId !== clienteSeleccionado) {
      return "Todas las propuestas de una factura deben ser del mismo cliente.";
    }
    return null;
  }

  function alternarSeleccion(propuesta: PropuestaFacturacion) {
    setSeleccionadas((actuales) => {
      const siguiente = new Map(actuales);
      if (siguiente.has(propuesta.id)) {
        siguiente.delete(propuesta.id);
      } else {
        siguiente.set(propuesta.id, propuesta);
      }
      return siguiente;
    });
  }

  function quitarSeleccion(propuestaId: number) {
    setSeleccionadas((actuales) => {
      const siguiente = new Map(actuales);
      siguiente.delete(propuestaId);
      return siguiente;
    });
  }

  const columnas = useMemo<ColumnaTabla<PropuestaFacturacion>[]>(
    () => [
      {
        encabezado: "",
        renderizar: (propuesta) => {
          const motivo = motivoNoSeleccionable(propuesta);
          const yaSeleccionada = seleccionadas.has(propuesta.id);
          return (
            <Casilla
              aria-label={`Seleccionar propuesta ${propuesta.descripcion}`}
              checked={yaSeleccionada}
              disabled={motivo !== null && !yaSeleccionada}
              title={motivo ?? undefined}
              onChange={() => alternarSeleccion(propuesta)}
            />
          );
        },
      },
      { encabezado: "Cliente", renderizar: (p) => p.clienteRazonSocial },
      { encabezado: "Descripción", renderizar: (p) => p.descripcion },
      { encabezado: "Período", renderizar: (p) => formatearPeriodo(p.periodoAnio, p.periodoMes) },
      { encabezado: "Total", renderizar: (p) => formatearMontoClpOAusente(p.totalClp, p.estado) },
      { encabezado: "Estado", renderizar: (p) => <BadgeEstadoPropuesta estado={p.estado} /> },
    ],
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [seleccionadas, clienteSeleccionado],
  );

  function validarAntesDeEnviar(): FacturaSolicitud | null {
    const errores: Record<string, string> = {};
    if (numeroFactura.trim() === "") {
      errores.numeroFactura = "El número de factura es obligatorio.";
    }
    if (fechaFactura.trim() === "") {
      errores.fechaFactura = "La fecha de la factura es obligatoria.";
    }
    if (seleccionadas.size === 0) {
      errores.propuestas = "Selecciona al menos una propuesta Pendiente.";
    }

    setErroresLocales(errores);
    if (Object.keys(errores).length > 0) {
      return null;
    }

    return {
      numeroFactura: numeroFactura.trim(),
      fechaFactura,
      observacion: observacion.trim() === "" ? null : observacion.trim(),
      propuestaIds: Array.from(seleccionadas.keys()),
    };
  }

  async function alEnviar(evento: FormEvent<HTMLFormElement>) {
    evento.preventDefault();
    const solicitud = validarAntesDeEnviar();
    if (!solicitud) {
      return;
    }

    await manejarEnvio(async () => {
      const creada = await clienteApiCliente.crear<Factura>("/facturas", solicitud);
      notificar("Factura creada.", "exito");
      router.push(`/facturacion/facturas/${creada.id}`);
    });
  }

  function errorDeCampo(campo: string): string | undefined {
    return erroresLocales[campo] ?? obtenerErrorDeCampo(error, campo);
  }

  const errorNumeroFactura = erroresLocales.numeroFactura ?? errorCampoNumeroFactura(error);

  const errorPropuestaNoFacturable =
    error instanceof ErrorApi && error.problema.codigo === "PROPUESTA_NO_FACTURABLE";

  return (
    <div className="max-w-4xl space-y-6">
      <EncabezadoDetalle
        volverA="/facturacion/facturas"
        volverEtiqueta="Volver a facturas"
        titulo="Nueva factura"
      />

      {clienteSeleccionado !== null ? (
        <Alerta variante="info">
          Selección restringida al cliente de la primera propuesta elegida — todas las propuestas de
          una factura deben ser del mismo cliente.
        </Alerta>
      ) : null}

      <PanelListado
        titulo="Elige las propuestas a facturar"
        columnas={columnas}
        filas={datos}
        obtenerClave={(p) => p.id}
        cargando={cargando}
        error={errorListado}
        paginaActual={pagina}
        totalPaginas={totalPaginas}
        onCambiarPagina={setPagina}
        mensajeVacio="No hay propuestas que coincidan con los filtros."
        filtros={
          <>
            <CampoFormulario id="filtro-periodo-anio" etiqueta="Año">
              <Entrada
                id="filtro-periodo-anio"
                type="number"
                min={2000}
                max={3000}
                value={periodoAnio}
                onChange={(evento) => {
                  setPeriodoAnio(evento.target.value);
                  setPagina(0);
                }}
                className="w-24"
              />
            </CampoFormulario>
            <CampoFormulario id="filtro-periodo-mes" etiqueta="Mes">
              <Seleccion
                id="filtro-periodo-mes"
                value={periodoMes}
                onChange={(evento) => {
                  setPeriodoMes(evento.target.value);
                  setPagina(0);
                }}
              >
                <option value="">Todos</option>
                {NOMBRES_MES.map((nombre, indice) => (
                  <option key={nombre} value={indice + 1}>
                    {nombre}
                  </option>
                ))}
              </Seleccion>
            </CampoFormulario>
            <CampoFormulario id="filtro-cliente" etiqueta="Cliente">
              <SelectorCliente
                id="filtro-cliente"
                valor={clienteFiltro}
                onCambiar={setClienteFiltro}
              />
            </CampoFormulario>
            <CampoFormulario id="filtro-estado" etiqueta="Estado">
              <Seleccion
                id="filtro-estado"
                value={estadoFiltro}
                onChange={(evento) => {
                  setEstadoFiltro(evento.target.value);
                  setPagina(0);
                }}
              >
                <option value="">Todos</option>
                {ESTADOS.map((valor) => (
                  <option key={valor} value={valor}>
                    {ETIQUETAS_ESTADO_PROPUESTA[valor]}
                  </option>
                ))}
              </Seleccion>
            </CampoFormulario>
          </>
        }
      />

      <div className="space-y-3 rounded-md border border-linea p-4">
        <h2 className="text-sm font-semibold text-tinta">
          Propuestas seleccionadas ({seleccionadas.size})
        </h2>
        {seleccionadas.size === 0 ? (
          <p className="text-sm text-sutil">Ninguna propuesta seleccionada todavía.</p>
        ) : (
          <ul className="divide-y divide-linea-2">
            {Array.from(seleccionadas.values()).map((propuesta) => (
              <li key={propuesta.id} className="flex items-center justify-between py-2 text-sm">
                <span className="text-texto">
                  {propuesta.clienteRazonSocial} — {propuesta.descripcion} —{" "}
                  {formatearPeriodo(propuesta.periodoAnio, propuesta.periodoMes)} —{" "}
                  {formatearClp(propuesta.totalClp)}
                </span>
                <button
                  type="button"
                  onClick={() => quitarSeleccion(propuesta.id)}
                  className="text-sm font-medium text-estado-error hover:text-estado-error/80"
                >
                  Quitar
                </button>
              </li>
            ))}
          </ul>
        )}
        {errorDeCampo("propuestas") ? (
          <p role="alert" className="text-xs text-estado-error">
            {errorDeCampo("propuestas")}
          </p>
        ) : null}
        <p className="text-sm font-semibold text-tinta">Subtotal: {formatearClp(subtotal)}</p>
      </div>

      <form onSubmit={alEnviar} className="space-y-4 rounded-md border border-linea p-4" noValidate>
        <h2 className="text-sm font-semibold text-tinta">Datos de la factura</h2>

        {error ? (
          <>
            <Alerta variante="error">
              {error instanceof ErrorApi
                ? (error.problema.detail ?? error.message)
                : "Ocurrió un error inesperado."}
            </Alerta>
            {errorPropuestaNoFacturable ? (
              <div className="-mt-2">
                <Boton type="button" variante="secundario" onClick={recargar}>
                  Refrescar listado
                </Boton>
                <p className="mt-1 text-xs text-estado-error">
                  Alguna propuesta seleccionada pudo cambiar de estado; refresca el listado de
                  arriba y revisa tu selección.
                </p>
              </div>
            ) : null}
          </>
        ) : null}

        <div className="grid grid-cols-2 gap-3">
          <CampoFormulario
            id="numeroFactura"
            etiqueta="N° factura"
            requerido
            error={errorNumeroFactura}
          >
            <Entrada
              id="numeroFactura"
              value={numeroFactura}
              onChange={(evento) => setNumeroFactura(evento.target.value)}
              invalida={Boolean(errorNumeroFactura)}
              maxLength={40}
              required
              className="w-full"
            />
          </CampoFormulario>
          <CampoFormulario
            id="fechaFactura"
            etiqueta="Fecha de factura"
            requerido
            error={errorDeCampo("fechaFactura")}
          >
            <Entrada
              id="fechaFactura"
              type="date"
              value={fechaFactura}
              onChange={(evento) => setFechaFactura(evento.target.value)}
              invalida={Boolean(errorDeCampo("fechaFactura"))}
              required
              className="w-full"
            />
          </CampoFormulario>
        </div>

        <CampoFormulario
          id="observacion"
          etiqueta="Observación"
          error={errorDeCampo("observacion")}
        >
          <AreaTexto
            id="observacion"
            value={observacion}
            onChange={(evento) => setObservacion(evento.target.value)}
            maxLength={300}
            className="w-full"
          />
        </CampoFormulario>

        <div className="flex justify-end">
          <Boton type="submit" cargando={enviando}>
            Crear factura
          </Boton>
        </div>
      </form>
    </div>
  );
}
