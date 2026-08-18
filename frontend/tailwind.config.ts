import type { Config } from "tailwindcss";

/**
 * Tokens del sistema de diseño "Confianza" de Helpcom (docs/plan-rediseno.md §3, etapa R1).
 * Los hex vienen dados por la marca (azul/celeste/estados) o son neutros derivados coherentes
 * con ellos — no son la paleta *default* de Tailwind reasignada, son valores propios.
 */
const config: Config = {
  content: [
    "./pages/**/*.{js,ts,jsx,tsx,mdx}",
    "./components/**/*.{js,ts,jsx,tsx,mdx}",
    "./app/**/*.{js,ts,jsx,tsx,mdx}",
  ],
  theme: {
    extend: {
      colors: {
        background: "var(--background)",
        foreground: "var(--foreground)",
        marca: {
          azul: "#066EE7",
          "azul-700": "#0A57C2",
          "azul-800": "#0A3AA0",
          celeste: "#06BBFF",
          "azul-50": "#EEF5FF",
          "azul-100": "#DBE9FF",
        },
        tinta: "#0F1B2D",
        texto: "#28303C",
        sutil: "#5B6472",
        tenue: "#9AA3B2",
        linea: "#E6EAF1",
        "linea-2": "#EEF1F6",
        fondo: "#F4F6F9",
        estado: {
          facturada: "#128A45",
          pendiente: "#066EE7",
          "sin-uf": "#C77700",
          anulada: "#6B7280",
          error: "#C62F42",
        },
      },
      fontFamily: {
        sans: ["var(--font-montserrat)", "ui-sans-serif", "system-ui", "sans-serif"],
      },
      borderRadius: {
        sm: "8px",
        DEFAULT: "11px",
        lg: "14px",
      },
      boxShadow: {
        // Sombras suaves con un tinte azulado (el tono de `tinta`) en vez del negro puro por
        // defecto de Tailwind — coherente con un sistema de diseño con identidad de color
        // propia hasta en los detalles neutros (docs/plan-rediseno.md §3.3).
        suave: "0 1px 3px 0 rgb(15 27 45 / 0.08), 0 1px 2px -1px rgb(15 27 45 / 0.06)",
        tarjeta: "0 1px 3px 0 rgb(15 27 45 / 0.08), 0 1px 2px -1px rgb(15 27 45 / 0.06)",
        // Sombra grande del sistema — modales y toasts, para que se despeguen del contenido
        // de fondo (docs/plan-rediseno.md R2, ajuste de profundidad de la modal).
        modal: "0 20px 40px -8px rgb(15 27 45 / 0.25), 0 8px 16px -8px rgb(15 27 45 / 0.15)",
      },
    },
  },
  plugins: [],
};
export default config;
