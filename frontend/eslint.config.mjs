import path from "node:path";
import { fileURLToPath } from "node:url";
import { defineConfig } from "eslint/config";
import { FlatCompat } from "@eslint/eslintrc";
import js from "@eslint/js";
import nextCoreWebVitals from "eslint-config-next/core-web-vitals";
import nextTypescript from "eslint-config-next/typescript";

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const compat = new FlatCompat({
  baseDirectory: __dirname,
  recommendedConfig: js.configs.recommended,
  allConfig: js.configs.all,
});

export default defineConfig([
  {
    extends: [...nextCoreWebVitals, ...nextTypescript, ...compat.extends("prettier")],
    rules: {
      // `eslint-config-next@16` (vía `eslint-plugin-react-hooks@7`) agregó esta regla, que
      // marca como error el patrón `setCargando(true); setError(null);` al inicio de un
      // `useEffect` de fetching — usado hoy en 6 sitios (lib/useListadoPaginado.ts,
      // lib/useInformeFacturacion.ts, LayoutDetalleProyecto.tsx, ListaAcuerdos.tsx,
      // DetalleFactura.tsx, SelectorCliente.tsx). Se relaja a "warn" A PROPÓSITO al subir a
      // Next 16 (docs/deuda-tecnica.md ítem 1): el refactor de esos 6 sitios es una tarea
      // aparte, deliberadamente NO mezclada con el bump de versión — no tocar código de
      // lógica en el mismo cambio que sube dependencias. Volver a "error" cuando se resuelva
      // esa deuda.
      "react-hooks/set-state-in-effect": "warn",
    },
  },
]);
