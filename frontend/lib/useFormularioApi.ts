"use client";

import { useCallback, useState } from "react";

export interface ResultadoFormularioApi {
  enviando: boolean;
  error: unknown;
  /** Ejecuta `accion` (la llamada al cliente API); retorna `true` si tuvo éxito. */
  manejarEnvio: (accion: () => Promise<void>) => Promise<boolean>;
}

/**
 * Estado de envío + captura de error para un formulario que llama al cliente API —
 * pensado para usarse junto a `components/formularios/FormularioDialogo.tsx`, que toma
 * `enviando`/`error` y se encarga de mostrar el mensaje general (notificación) mientras cada
 * `CampoFormulario` muestra el suyo vía `lib/errores.ts::obtenerErrorDeCampo`.
 */
export function useFormularioApi(): ResultadoFormularioApi {
  const [enviando, setEnviando] = useState(false);
  const [error, setError] = useState<unknown>(null);

  const manejarEnvio = useCallback(async (accion: () => Promise<void>): Promise<boolean> => {
    setEnviando(true);
    setError(null);
    try {
      await accion();
      return true;
    } catch (error) {
      setError(error);
      return false;
    } finally {
      setEnviando(false);
    }
  }, []);

  return { enviando, error, manejarEnvio };
}
