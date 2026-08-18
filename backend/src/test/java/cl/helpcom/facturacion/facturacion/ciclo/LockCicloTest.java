package cl.helpcom.facturacion.facturacion.ciclo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class LockCicloTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> operacionesValor;

    private LockCiclo lockCiclo;

    @BeforeEach
    void configurar() {
        lockCiclo = new LockCiclo(redisTemplate, Duration.ofMinutes(30));
    }

    @Test
    void deberiaAdquirirElLockCuandoRedisResponde() {
        when(redisTemplate.opsForValue()).thenReturn(operacionesValor);
        when(operacionesValor.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        assertThat(lockCiclo.adquirir(1L, 2026, 2)).isTrue();
    }

    @Test
    void noDeberiaAdquirirElLockCuandoYaEstaTomado() {
        when(redisTemplate.opsForValue()).thenReturn(operacionesValor);
        when(operacionesValor.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(false);

        assertThat(lockCiclo.adquirir(1L, 2026, 2)).isFalse();
    }

    @Test
    void deberiaDegradarAContinuarSinLockCuandoRedisNoEstaDisponible() {
        when(redisTemplate.opsForValue()).thenThrow(new QueryTimeoutException("Redis no responde"));

        assertThat(lockCiclo.adquirir(1L, 2026, 2)).isTrue();
    }

    @Test
    void liberarNoDeberiaLanzarExcepcionCuandoRedisNoEstaDisponible() {
        when(redisTemplate.delete(anyString())).thenThrow(new QueryTimeoutException("Redis no responde"));

        lockCiclo.liberar(1L, 2026, 2);
    }
}
