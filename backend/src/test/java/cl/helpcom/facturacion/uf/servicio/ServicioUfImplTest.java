package cl.helpcom.facturacion.uf.servicio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.helpcom.facturacion.uf.cache.CacheUf;
import cl.helpcom.facturacion.uf.dominio.ValorUf;
import cl.helpcom.facturacion.uf.dominio.ValorUfNoDisponibleException;
import cl.helpcom.facturacion.uf.fuente.FuenteUf;
import cl.helpcom.facturacion.uf.repositorio.ValorUfRepositorio;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ServicioUfImplTest {

    @Mock
    private CacheUf cacheUf;

    @Mock
    private ValorUfRepositorio valorUfRepositorio;

    @Mock
    private FuenteUf fuenteUf;

    private ServicioUfImpl servicioUf;

    @BeforeEach
    void configurar() {
        servicioUf = new ServicioUfImpl(cacheUf, valorUfRepositorio, fuenteUf);
    }

    @Test
    void deberiaRetornarElValorDesdeElCacheSinConsultarPersistenciaNiFuenteExterna() {
        LocalDate fecha = LocalDate.of(2026, 1, 15);
        when(cacheUf.obtener(fecha)).thenReturn(Optional.of(new BigDecimal("38935.9000")));

        BigDecimal valor = servicioUf.obtenerValorUf(fecha);

        assertThat(valor).isEqualByComparingTo("38935.9000");
        verify(valorUfRepositorio, never()).findById(any());
        verify(fuenteUf, never()).consultarUf(any());
    }

    @Test
    void deberiaServirUnaFechaPasadaDesdeLaPersistenciaYCachearlaSinConsultarLaFuenteExterna() {
        LocalDate fechaPasada = LocalDate.of(2020, 3, 10);
        ValorUf registrado = new ValorUf();
        registrado.setFecha(fechaPasada);
        registrado.setValor(new BigDecimal("28897.4500"));

        when(cacheUf.obtener(fechaPasada)).thenReturn(Optional.empty());
        when(valorUfRepositorio.findById(fechaPasada)).thenReturn(Optional.of(registrado));

        BigDecimal valor = servicioUf.obtenerValorUf(fechaPasada);

        assertThat(valor).isEqualByComparingTo("28897.4500");
        verify(cacheUf).guardar(eq(fechaPasada), eq(new BigDecimal("28897.4500")));
        verify(fuenteUf, never()).consultarUf(any());
    }

    @Test
    void deberiaConsultarLaFuenteExternaPersistirYCachearCuandoNoEstaNiEnCacheNiEnPersistencia() {
        LocalDate fecha = LocalDate.of(2026, 6, 1);
        when(cacheUf.obtener(fecha)).thenReturn(Optional.empty());
        when(valorUfRepositorio.findById(fecha)).thenReturn(Optional.empty());
        when(fuenteUf.consultarUf(fecha)).thenReturn(Optional.of(new BigDecimal("39500.1200")));

        BigDecimal valor = servicioUf.obtenerValorUf(fecha);

        assertThat(valor).isEqualByComparingTo("39500.1200");

        ArgumentCaptor<ValorUf> capturador = ArgumentCaptor.forClass(ValorUf.class);
        verify(valorUfRepositorio, times(1)).save(capturador.capture());
        ValorUf guardado = capturador.getValue();
        assertThat(guardado.getFecha()).isEqualTo(fecha);
        assertThat(guardado.getValor()).isEqualByComparingTo("39500.1200");
        assertThat(guardado.getFuente()).isEqualTo("mindicador.cl");
        assertThat(guardado.getRegistradoEn()).isNotNull();

        verify(cacheUf).guardar(eq(fecha), eq(new BigDecimal("39500.1200")));
    }

    @Test
    void deberiaLanzarValorUfNoDisponibleExceptionCuandoNingunaViaEntregaElValor() {
        LocalDate fecha = LocalDate.of(2026, 7, 4);
        when(cacheUf.obtener(fecha)).thenReturn(Optional.empty());
        when(valorUfRepositorio.findById(fecha)).thenReturn(Optional.empty());
        when(fuenteUf.consultarUf(fecha)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> servicioUf.obtenerValorUf(fecha))
            .isInstanceOf(ValorUfNoDisponibleException.class)
            .hasMessageContaining(fecha.toString());

        verify(valorUfRepositorio, never()).save(any());
        verify(cacheUf, never()).guardar(any(), any());
    }
}
