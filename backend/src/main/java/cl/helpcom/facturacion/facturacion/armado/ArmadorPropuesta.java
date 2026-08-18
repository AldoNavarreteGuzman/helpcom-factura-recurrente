package cl.helpcom.facturacion.facturacion.armado;

import cl.helpcom.facturacion.empresa.repositorio.ParametroSistemaRepositorio;
import cl.helpcom.facturacion.facturacion.calculo.AcuerdoAplicado;
import cl.helpcom.facturacion.facturacion.calculo.CalculadoraFacturacion;
import cl.helpcom.facturacion.facturacion.calculo.DatosCalculo;
import cl.helpcom.facturacion.facturacion.calculo.ResultadoCalculo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.proyectos.dominio.AcuerdoPrecio;
import cl.helpcom.facturacion.proyectos.repositorio.AcuerdoPrecioRepositorio;
import cl.helpcom.facturacion.uf.dominio.ValorUfNoDisponibleException;
import cl.helpcom.facturacion.uf.servicio.ServicioUf;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

/**
 * Única implementación de "armar una propuesta de facturación a partir de una fuente de datos
 * (proyecto del ciclo o fila del CSV) más una fecha de facturación". Antes de existir este
 * componente, {@code ServicioCicloFacturacion} tenía esta lógica in-line; se extrajo aquí para
 * que la importación CSV la reutilice EXACTAMENTE igual en vez de reimplementarla (mandato de
 * arquitectura-tecnica.md §10): resuelve el acuerdo de precio vigente, el valor UF (con la
 * misma degradación a {@code PENDIENTE_UF} si no está disponible), la tasa de IVA vigente, y
 * llama a {@link CalculadoraFacturacion} — la única fuente de verdad del cálculo.
 *
 * <p>Deliberadamente NO persiste: retorna la {@link PropuestaFacturacion} armada (con su
 * snapshot completo) para que el llamador decida cómo guardarla — el ciclo y la importación
 * tienen políticas distintas ante condiciones de carrera al insertar (ver cada llamador).
 */
@Component
public class ArmadorPropuesta {

    private static final String CLAVE_TASA_IVA = "tasa_iva";

    private final AcuerdoPrecioRepositorio acuerdoPrecioRepositorio;
    private final ParametroSistemaRepositorio parametroSistemaRepositorio;
    private final ServicioUf servicioUf;
    private final CalculadoraFacturacion calculadora;

    public ArmadorPropuesta(
        AcuerdoPrecioRepositorio acuerdoPrecioRepositorio,
        ParametroSistemaRepositorio parametroSistemaRepositorio,
        ServicioUf servicioUf,
        CalculadoraFacturacion calculadora) {
        this.acuerdoPrecioRepositorio = acuerdoPrecioRepositorio;
        this.parametroSistemaRepositorio = parametroSistemaRepositorio;
        this.servicioUf = servicioUf;
        this.calculadora = calculadora;
    }

    public PropuestaFacturacion armar(EntradaArmadoPropuesta entrada) {
        AcuerdoPrecio acuerdoVigente = entrada.proyecto() == null
            ? null
            : acuerdoPrecioRepositorio.buscarVigente(entrada.proyecto().getId(), entrada.fechaFacturacion())
                .orElse(null);
        AcuerdoAplicado acuerdoAplicado = aAcuerdoAplicado(acuerdoVigente);
        BigDecimal tasaIva = obtenerTasaIva(entrada.empresa().getId());

        boolean necesitaUf = calculadora.requiereUf(entrada.monedaPrecio(), acuerdoAplicado);
        BigDecimal valorUf = null;
        LocalDate fechaValorUf = null;
        boolean pendienteUf = false;

        if (necesitaUf) {
            try {
                valorUf = servicioUf.obtenerValorUf(entrada.fechaFacturacion());
                fechaValorUf = entrada.fechaFacturacion();
            } catch (ValorUfNoDisponibleException ex) {
                pendienteUf = true;
            }
        }

        PropuestaFacturacion propuesta = construirBase(entrada, acuerdoVigente, acuerdoAplicado, tasaIva);

        if (pendienteUf) {
            // Decisión documentada (misma que el ciclo): no se inventan cifras. neto/iva/total
            // quedan en 0 y el estado queda PENDIENTE_UF hasta un recálculo posterior.
            propuesta.setValorUf(null);
            propuesta.setFechaValorUf(null);
            propuesta.setNetoClp(BigDecimal.ZERO);
            propuesta.setIvaClp(BigDecimal.ZERO);
            propuesta.setTotalClp(BigDecimal.ZERO);
            propuesta.setEstado(EstadoPropuesta.PENDIENTE_UF);
        } else {
            ResultadoCalculo resultado = calculadora.calcular(new DatosCalculo(
                entrada.precioBaseNeto(), entrada.monedaPrecio(), acuerdoAplicado, valorUf, tasaIva));
            propuesta.setValorUf(valorUf);
            propuesta.setFechaValorUf(fechaValorUf);
            propuesta.setNetoClp(resultado.netoClp());
            propuesta.setIvaClp(resultado.ivaClp());
            propuesta.setTotalClp(resultado.totalClp());
            propuesta.setEstado(EstadoPropuesta.PENDIENTE);
        }

        return propuesta;
    }

    private PropuestaFacturacion construirBase(
        EntradaArmadoPropuesta entrada, AcuerdoPrecio acuerdoVigente, AcuerdoAplicado acuerdoAplicado,
        BigDecimal tasaIva) {
        PropuestaFacturacion propuesta = new PropuestaFacturacion();
        propuesta.setEmpresa(entrada.empresa());
        propuesta.setCliente(entrada.cliente());
        propuesta.setProyecto(entrada.proyecto());
        propuesta.setOrigen(entrada.origen());
        propuesta.setPeriodoAnio(entrada.periodoAnio());
        propuesta.setPeriodoMes(entrada.periodoMes());
        propuesta.setFechaFacturacion(entrada.fechaFacturacion());
        propuesta.setDescripcion(entrada.descripcion());
        propuesta.setMonedaOrigen(entrada.monedaPrecio());
        propuesta.setPrecioBaseNeto(entrada.precioBaseNeto());
        propuesta.setAcuerdo(acuerdoVigente);
        if (acuerdoAplicado != null) {
            propuesta.setAcuerdoTipo(acuerdoAplicado.tipo());
            propuesta.setAcuerdoValor(acuerdoAplicado.valor());
            propuesta.setAcuerdoMoneda(acuerdoAplicado.moneda());
        }
        propuesta.setTasaIva(tasaIva);
        return propuesta;
    }

    private AcuerdoAplicado aAcuerdoAplicado(AcuerdoPrecio acuerdo) {
        if (acuerdo == null) {
            return null;
        }
        return new AcuerdoAplicado(acuerdo.getTipo(), acuerdo.getValor(), acuerdo.getMoneda());
    }

    private BigDecimal obtenerTasaIva(Long empresaId) {
        return parametroSistemaRepositorio.findByEmpresaIdAndClave(empresaId, CLAVE_TASA_IVA)
            .map(p -> new BigDecimal(p.getValor()))
            .orElseThrow(() -> new IllegalStateException(
                "No está configurado el parámetro 'tasa_iva' para la empresa " + empresaId + "."));
    }
}
