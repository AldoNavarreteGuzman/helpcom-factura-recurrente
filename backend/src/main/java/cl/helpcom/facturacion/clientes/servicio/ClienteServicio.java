package cl.helpcom.facturacion.clientes.servicio;

import cl.helpcom.facturacion.clientes.dominio.Cliente;
import cl.helpcom.facturacion.clientes.dto.ClienteRespuestaDto;
import cl.helpcom.facturacion.clientes.dto.ClienteSolicitudDto;
import cl.helpcom.facturacion.clientes.repositorio.ClienteEspecificaciones;
import cl.helpcom.facturacion.clientes.repositorio.ClienteRepositorio;
import cl.helpcom.facturacion.comun.error.ReglaNegocioException;
import cl.helpcom.facturacion.comun.error.RecursoNoEncontradoException;
import cl.helpcom.facturacion.comun.error.SolicitudInvalidaException;
import cl.helpcom.facturacion.comun.util.RutChileno;
import cl.helpcom.facturacion.empresa.repositorio.EmpresaRepositorio;
import cl.helpcom.facturacion.empresa.servicio.ContextoEmpresa;
import cl.helpcom.facturacion.proyectos.repositorio.ProyectoRepositorio;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ClienteServicio {

    private final ClienteRepositorio clienteRepositorio;
    private final ProyectoRepositorio proyectoRepositorio;
    private final EmpresaRepositorio empresaRepositorio;
    private final ContextoEmpresa contextoEmpresa;

    public ClienteServicio(
        ClienteRepositorio clienteRepositorio,
        ProyectoRepositorio proyectoRepositorio,
        EmpresaRepositorio empresaRepositorio,
        ContextoEmpresa contextoEmpresa) {
        this.clienteRepositorio = clienteRepositorio;
        this.proyectoRepositorio = proyectoRepositorio;
        this.empresaRepositorio = empresaRepositorio;
        this.contextoEmpresa = contextoEmpresa;
    }

    @Transactional(readOnly = true)
    public Page<ClienteRespuestaDto> listar(String texto, Boolean activo, Pageable pageable) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        Specification<Cliente> especificacion = ClienteEspecificaciones.combinar(
            ClienteEspecificaciones.paraEmpresa(empresaId),
            ClienteEspecificaciones.conTexto(texto),
            ClienteEspecificaciones.conActivo(activo));

        return clienteRepositorio.findAll(especificacion, pageable).map(this::aRespuesta);
    }

    @Transactional(readOnly = true)
    public ClienteRespuestaDto obtener(Long id) {
        return aRespuesta(buscarOLanzar(id));
    }

    @Transactional
    public ClienteRespuestaDto crear(ClienteSolicitudDto solicitud) {
        Long empresaId = contextoEmpresa.obtenerEmpresaId();
        String rutNormalizado = normalizarRutOLanzar(solicitud.rut());
        validarRutNoDuplicado(empresaId, rutNormalizado, null);

        Cliente cliente = new Cliente();
        cliente.setEmpresa(empresaRepositorio.getReferenceById(empresaId));
        aplicarCampos(cliente, solicitud, rutNormalizado);

        return aRespuesta(clienteRepositorio.save(cliente));
    }

    @Transactional
    public ClienteRespuestaDto actualizar(Long id, ClienteSolicitudDto solicitud) {
        Cliente cliente = buscarOLanzar(id);
        String rutNormalizado = normalizarRutOLanzar(solicitud.rut());
        validarRutNoDuplicado(cliente.getEmpresa().getId(), rutNormalizado, id);

        aplicarCampos(cliente, solicitud, rutNormalizado);

        return aRespuesta(cliente);
    }

    /**
     * Si el cliente tiene proyectos asociados, se hace baja lógica (activo = false) para
     * no romper la referencia histórica de esos proyectos. Si no tiene proyectos, se
     * elimina físicamente.
     */
    @Transactional
    public void eliminar(Long id) {
        Cliente cliente = buscarOLanzar(id);
        Long empresaId = cliente.getEmpresa().getId();

        if (proyectoRepositorio.existsByClienteIdAndEmpresaId(id, empresaId)) {
            cliente.setActivo(false);
        } else {
            clienteRepositorio.delete(cliente);
        }
    }

    private String normalizarRutOLanzar(String rutBruto) {
        return RutChileno.normalizar(rutBruto)
            .orElseThrow(() -> new SolicitudInvalidaException(
                "El RUT '" + rutBruto + "' no es válido.", "RUT_INVALIDO"));
    }

    private void validarRutNoDuplicado(Long empresaId, String rutNormalizado, Long idExcluido) {
        boolean duplicado = idExcluido == null
            ? clienteRepositorio.existsByEmpresaIdAndRut(empresaId, rutNormalizado)
            : clienteRepositorio.existsByEmpresaIdAndRutAndIdNot(empresaId, rutNormalizado, idExcluido);
        if (duplicado) {
            throw new ReglaNegocioException(
                "Ya existe un cliente con el RUT " + rutNormalizado + ".", "CLIENTE_DUPLICADO");
        }
    }

    private void aplicarCampos(Cliente cliente, ClienteSolicitudDto solicitud, String rutNormalizado) {
        cliente.setRut(rutNormalizado);
        cliente.setRazonSocial(solicitud.razonSocial());
        cliente.setNombreFantasia(solicitud.nombreFantasia());
        cliente.setGiro(solicitud.giro());
        cliente.setEmail(solicitud.email());
        cliente.setTelefono(solicitud.telefono());
        cliente.setDireccion(solicitud.direccion());
        cliente.setActivo(solicitud.activo());
    }

    private Cliente buscarOLanzar(Long id) {
        return clienteRepositorio.findByIdAndEmpresaId(id, contextoEmpresa.obtenerEmpresaId())
            .orElseThrow(() -> new RecursoNoEncontradoException(
                "No existe un cliente con id " + id, "CLIENTE_NO_ENCONTRADO"));
    }

    private ClienteRespuestaDto aRespuesta(Cliente cliente) {
        return new ClienteRespuestaDto(
            cliente.getId(),
            cliente.getRut(),
            cliente.getRazonSocial(),
            cliente.getNombreFantasia(),
            cliente.getGiro(),
            cliente.getEmail(),
            cliente.getTelefono(),
            cliente.getDireccion(),
            cliente.isActivo());
    }
}
