import type { Metadata } from "next";
import { Montserrat } from "next/font/google";
import { ProveedorSesion } from "@/components/proveedores/ProveedorSesion";
import { ProveedorNotificaciones } from "@/components/ui/Notificaciones";
import "./globals.css";

/**
 * Tipografía del sistema de diseño "Confianza" (docs/plan-rediseno.md §3.2). `next/font/google`
 * auto-hospeda el archivo (se descarga en build time y se sirve desde el propio dominio) — sin
 * llamada a Google Fonts en runtime.
 */
const montserrat = Montserrat({
  subsets: ["latin"],
  variable: "--font-montserrat",
  weight: ["400", "500", "600", "700", "800"],
});

export const metadata: Metadata = {
  title: "Facturación Recurrente — Helpcom",
  description: "Sistema de facturación recurrente de proyectos de Helpcom Ltda.",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="es-CL">
      <body className={`${montserrat.variable} font-sans antialiased`}>
        <ProveedorSesion>
          <ProveedorNotificaciones>{children}</ProveedorNotificaciones>
        </ProveedorSesion>
      </body>
    </html>
  );
}
