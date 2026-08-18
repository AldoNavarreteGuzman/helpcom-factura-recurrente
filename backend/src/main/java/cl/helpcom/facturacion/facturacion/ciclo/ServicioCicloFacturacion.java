package cl.helpcom.facturacion.facturacion.ciclo;

import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.facturacion.armado.ArmadorPropuesta;
import cl.helpcom.facturacion.facturacion.armado.EntradaArmadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.DisparoCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EjecucionCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoEjecucionCiclo;
import cl.helpcom.facturacion.facturacion.dominio.EstadoPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.OrigenPropuesta;
import cl.helpcom.facturacion.facturacion.dominio.PropuestaFacturacion;
import cl.helpcom.facturacion.facturacion.repositorio.EjecucionCicloRepositorio;
import cl.helpcom.facturacion.facturacion.repositorio.PropuestaFacturacionRepositorio;
import cl.helpcom.facturacion.proyectos.dominio.Proyecto;
import cl.helpcom.facturacion.proyectos.repositorio.ProyectoRepositorio;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.AuditorAware;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Orquesta el ciclo de facturación del día 1 (arquitectura-tecnica.md §9): recorre los
 * proyectos activos de la empresa, resuelve si corresponden al período y delega en
 * {@link ArmadorPropuesta} la construcción de la {@link PropuestaFacturacion} (acuerdo
 * vigente, valor UF, tasa IVA y el cálculo con {@code CalculadoraFacturacion}), dejando
 * trazabilidad en {@link EjecucionCiclo}.
 *
 * <p><b>Componente compartido con la importación CSV:</b> la resolución de acuerdo vigente,
 * UF, tasa IVA y el cálculo del snapshot vivían antes en esta clase; se extrajeron a
 * {@link ArmadorPropuesta} para que el ciclo y la importación CSV (que arma propuestas
 * {@code origen=CSV} a partir de filas, no de proyectos) compartan una única implementación.
 * Efecto colateral documentado del refactor: antes, si faltaba el parámetro {@code tasa_iva}
 * de la empresa, el ciclo completo abortaba de inmediato (chequeo previo a recorrer los
 * proyectos); ahora esa falta se detecta dentro de {@link ArmadorPropuesta#armar}, por
 * proyecto, y como cada proyecto ya corre en su propia transacción con captura de
 * {@code RuntimeException} (ver más abajo), el resultado es que cada proyecto queda como
 * {@code ERROR} individualmente en vez de abortar el ciclo entero — más consistente con la
 * filosofía ya documentada de "avanzar todo lo posible" del ciclo.
 *
 * <p><b>Transaccionalidad — decisión de diseño:</b> cada proyecto se procesa en su PROPIA
 * transacción ({@code REQUIRES_NEW}, vía {@link TransactionTemplate}), igual que la
 * apertura y el cierre de {@code ejecucion_ciclo}. Si un proyecto falla al persistir (p. ej.
 * una violación de integridad inesperada), su transacción se revierte SOLA — no arrastra ni
 * pierde las propuestas ya generadas por otros proyectos, ni el registro de
 * {@code ejecucion_ciclo}. Esto es intencional: envolver todo el ciclo en una única
 * transacción habría significado que un solo proyecto problemático pudiera abortar el
 * período completo (y en Postgres, además, dejar la conexión en estado "aborted" para el
 * resto del lote). El costo es no tener atomicidad de "todo o nada" a nivel de ciclo
 * completo, que de todas formas no es lo que se quiere aquí: el ciclo debe avanzar todo lo
 * posible y reportar lo que no pudo procesar.
 */
@Service
public class ServicioCicloFacturacion {

    private static final Logger log = LoggerFactory.getLogger(ServicioCicloFacturacion.class);
    private static final String USUARIO_SISTEMA = "sistema";

    private final ProyectoRepositorio proyectoRepositorio;
    private final PropuestaFacturacionRepositorio propuestaFacturacionRepositorio;
    private final EjecucionCicloRepositorio ejecucionCicloRepositorio;
    private final EmpresaRepositorio empresaRepositorio;
    private final ArmadorPropuesta armadorPropuesta;
    private final ResolutorPeriodo resolutorPeriodo;
    private final LockCiclo lockCiclo;
    private final ContextoEmpresa contextoEmpresa;
    private final AuditorAware<String> auditorAware;
    private final TransactionTemplate transaccionNueva;

    public ServicioCicloFacturacion(
        ProyectoRepositorio proyectoRepositorio,
        PropuestaFacturacionRepositorio propuestaFacturacionRepositorio,
        EjecucionCicloRepositorio ejecucionCicloRepositorio,
        EmpresaRepositorio empresaRepositorio,
        ArmadorPropuesta armadorPropuesta,
        ResolutorPeriodo resolutorPeriodo,
        LockCiclo lockCiclo,
        ContextoEmpresa contextoEmpresa,
        AuditorAware<String> auditorAware,
        PlatformTransactionManager transactionManager) {
        this.proyectoRepositorio = proyectoRepositorio;
        this.propuestaFacturacionRepositorio = propuestaFacturacionRepositorio;
        this.ejecucionCicloRepositorio = ejecucionCicloRepositorio;
        this.empresaRepositorio = empresaRepositorio;
        this.armadorPropuesta = armadorPropuesta;
        this.resolutorPeriodo = resolutorPeriodo;
        this.lockCiclo = lockCiclo;
        this.contextoEmpresa = contextoEmpresa;
        this.auditorAware = auditorAware;
        this.transaccionNueva = new TransactionTemplate(transactionManager);
        this.transaccionNueva.setPropagationBehavior(TransactionTemplate.PROPAGATION_REQUIRES_NEW);
    }

    public ResultadoCiclo ejecutarCiclo(int anio, int mes, DisparoCiclo disparo) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();

        if (!lockCiclo.adquirir(empresaId, anio, mes)) {
            log.info("Ya hay una ejecución en curso para el ciclo {}-{} de la empresa {}; se omite esta invocación.",
                anio, mes, empresaId);
            return new ResultadoCiclo(null, anio, mes, disparo, EstadoEjecucionCiclo.EXITOSA, 0, 0,
                "Ya había una ejecución en curso para este período; no se realizó ningún cambio.");
        }

        Long ejecucionId = null;
        try {
            ejecucionId = abrirEjecucion(empresaId, anio, mes, disparo);

            ContadoresCiclo contadores = procesarProyectos(empresaId, anio, mes);

            EstadoEjecucionCiclo estado = determinarEstado(contadores);
            String observacion = construirObservacion(contadores);
            cerrarEjecucion(ejecucionId, estado, contadores, observacion);

            return new ResultadoCiclo(
                ejecucionId, anio, mes, disparo, estado, contadores.generadas(), contadores.pendientesUf(), observacion);
        } catch (RuntimeException ex) {
            log.error("Error no controlado ejecutando el ciclo {}-{} para la empresa {}", anio, mes, empresaId, ex);
            if (ejecucionId != null) {
                cerrarEjecucionConError(ejecucionId, ex);
            }
            throw ex;
        } finally {
            lockCiclo.liberar(empresaId, anio, mes);
        }
    }

    private ContadoresCiclo procesarProyectos(Long empresaId, int anio, int mes) {
        YearMonth periodo = YearMonth.of(anio, mes);
        List<Long> proyectoIds = proyectoRepositorio.encontrarIdsActivosDeLaEmpresa(empresaId);

        ContadoresCiclo contadores = ContadoresCiclo.vacio();
        for (Long proyectoId : proyectoIds) {
            ResultadoItem resultado;
            try {
                resultado = transaccionNueva.execute(
                    status -> procesarProyecto(empresaId, proyectoId, anio, mes, periodo));
            } catch (RuntimeException ex) {
                log.error("Error inesperado procesando el proyecto {} en el ciclo {}-{}", proyectoId, anio, mes, ex);
                resultado = ResultadoItem.ERROR;
            }

            contadores = switch (resultado) {
                case GENERADA -> contadores.conGenerada();
                case PENDIENTE_UF -> contadores.conPendienteUf();
                case ERROR -> contadores.conErrorItem();
                case YA_EXISTIA, OMITIDA -> contadores;
            };
        }
        return contadores;
    }

    private ResultadoItem procesarProyecto(Long empresaId, Long proyectoId, int anio, int mes, YearMonth periodo) {
        Proyecto proyecto = proyectoRepositorio.findByIdAndEmpresaId(proyectoId, empresaId).orElse(null);
        if (proyecto == null) {
            return ResultadoItem.OMITIDA;
        }

        DatosProyectoPeriodo datosPeriodo = new DatosProyectoPeriodo(
            proyecto.getPeriodicidad(), proyecto.getDiaFacturacion(), proyecto.getFechaInicio(),
            proyecto.getFechaTermino(), proyecto.isActivo());
        Optional<LocalDate> fechaFacturacionResuelta = resolutorPeriodo.resolver(datosPeriodo, periodo);
        if (fechaFacturacionResuelta.isEmpty()) {
            return ResultadoItem.OMITIDA;
        }
        LocalDate fechaFacturacion = fechaFacturacionResuelta.get();

        Short periodoAnio = (short) anio;
        Short periodoMes = (short) mes;
        if (propuestaFacturacionRepositorio.existsByProyectoIdAndPeriodoAnioAndPeriodoMesAndOrigen(
                proyectoId, periodoAnio, periodoMes, OrigenPropuesta.CICLO)) {
            return ResultadoItem.YA_EXISTIA;
        }

        PropuestaFacturacion propuesta = armadorPropuesta.armar(new EntradaArmadoPropuesta(
            proyecto.getEmpresa(), proyecto.getCliente(), proyecto, OrigenPropuesta.CICLO,
            periodoAnio, periodoMes, fechaFacturacion, proyecto.getNombre(),
            proyecto.getPrecioBaseNeto(), proyecto.getMonedaPrecio()));

        try {
            propuestaFacturacionRepositorio.save(propuesta);
            propuestaFacturacionRepositorio.flush();
        } catch (DataIntegrityViolationException ex) {
            // Red de seguridad ante condiciones de carrera: otra ejecución concurrente ya
            // insertó la propuesta de este (proyecto, período) entre el existsBy... de
            // arriba y este insert. No es un error: se trata igual que "ya existía".
            return ResultadoItem.YA_EXISTIA;
        }

        return propuesta.getEstado() == EstadoPropuesta.PENDIENTE_UF ? ResultadoItem.PENDIENTE_UF : ResultadoItem.GENERADA;
    }

    private EstadoEjecucionCiclo determinarEstado(ContadoresCiclo contadores) {
        if (contadores.pendientesUf() > 0 || contadores.erroresItem() > 0) {
            return EstadoEjecucionCiclo.CON_ADVERTENCIAS;
        }
        return EstadoEjecucionCiclo.EXITOSA;
    }

    private String construirObservacion(ContadoresCiclo contadores) {
        return "Propuestas generadas: %d. Pendientes de UF: %d. Proyectos con error: %d."
            .formatted(contadores.generadas(), contadores.pendientesUf(), contadores.erroresItem());
    }

    private Long abrirEjecucion(Long empresaId, int anio, int mes, DisparoCiclo disparo) {
        return transaccionNueva.execute(status -> {
            EjecucionCiclo ejecucion = new EjecucionCiclo();
            ejecucion.setEmpresa(empresaRepositorio.getReferenceById(empresaId));
            ejecucion.setPeriodoAnio((short) anio);
            ejecucion.setPeriodoMes((short) mes);
            ejecucion.setEjecutadoEn(OffsetDateTime.now());
            ejecucion.setDisparo(disparo);
            ejecucion.setCantidadGeneradas(0);
            ejecucion.setCantidadPendientesUf(0);
            ejecucion.setEstado(EstadoEjecucionCiclo.EXITOSA);
            ejecucion.setEjecutadoPor(obtenerUsuarioActual());
            return ejecucionCicloRepositorio.save(ejecucion).getId();
        });
    }

    private void cerrarEjecucion(Long ejecucionId, EstadoEjecucionCiclo estado, ContadoresCiclo contadores, String observacion) {
        transaccionNueva.executeWithoutResult(status -> {
            EjecucionCiclo ejecucion = ejecucionCicloRepositorio.getReferenceById(ejecucionId);
            ejecucion.setEstado(estado);
            ejecucion.setCantidadGeneradas(contadores.generadas());
            ejecucion.setCantidadPendientesUf(contadores.pendientesUf());
            ejecucion.setObservacion(observacion);
        });
    }

    private void cerrarEjecucionConError(Long ejecucionId, RuntimeException ex) {
        transaccionNueva.executeWithoutResult(status -> {
            EjecucionCiclo ejecucion = ejecucionCicloRepositorio.getReferenceById(ejecucionId);
            ejecucion.setEstado(EstadoEjecucionCiclo.ERROR);
            ejecucion.setObservacion("Error no controlado: " + ex.getMessage());
        });
    }

    private String obtenerUsuarioActual() {
        return auditorAware.getCurrentAuditor().orElse(USUARIO_SISTEMA);
    }
}
