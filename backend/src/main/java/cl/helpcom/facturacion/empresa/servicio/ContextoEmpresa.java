package cl.helpcom.facturacion.empresa.servicio;

import org.springframework.stereotype.Component;

/**
 * Resuelve la empresa actual en un único punto del backend. Hoy retorna siempre el
 * {@code empresa_id} fijo de Helpcom. En la etapa SaaS, este componente pasará a resolver
 * la empresa desde el token OIDC o el subdominio, sin que el resto del código deba cambiar.
 */
@Component
public class ContextoEmpresa {

    private static final Long EMPRESA_ID_HELPCOM = 1L;

    public Long obtenerEmpresaId() {
        return EMPRESA_ID_HELPCOM;
    }
}
