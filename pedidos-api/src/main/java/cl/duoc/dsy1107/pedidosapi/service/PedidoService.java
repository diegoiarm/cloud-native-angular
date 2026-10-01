package cl.duoc.dsy1107.pedidosapi.service;

import cl.duoc.dsy1107.pedidosapi.client.CatalogoClient;
import cl.duoc.dsy1107.pedidosapi.client.MovimientoStock;
import cl.duoc.dsy1107.pedidosapi.client.ProductoCatalogo;
import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import cl.duoc.dsy1107.pedidosapi.dto.CrearPedidoRequest;
import cl.duoc.dsy1107.pedidosapi.dto.PedidoResponse;
import cl.duoc.dsy1107.pedidosapi.exception.PedidoNoEncontradoException;
import cl.duoc.dsy1107.pedidosapi.exception.TransicionInvalidaException;
import cl.duoc.dsy1107.pedidosapi.mensajeria.PedidoEvento;
import cl.duoc.dsy1107.pedidosapi.mensajeria.PedidoEventoPublisher;
import cl.duoc.dsy1107.pedidosapi.model.EstadoPedido;
import cl.duoc.dsy1107.pedidosapi.model.ItemPedido;
import cl.duoc.dsy1107.pedidosapi.model.Pedido;
import cl.duoc.dsy1107.pedidosapi.repository.PedidoRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {

    private final PedidoRepository repository;
    private final CatalogoClient catalogo;
    private final PedidoEventoPublisher productor;

    public PedidoService(PedidoRepository repository, CatalogoClient catalogo,
            PedidoEventoPublisher productor) {
        this.repository = repository;
        this.catalogo = catalogo;
        this.productor = productor;
    }

    @Transactional
    public PedidoResponse crear(CrearPedidoRequest request, Actor actor) {
        Pedido pedido = new Pedido(actor.id(), actor.nombre());
        for (CrearPedidoRequest.Item item : request.items()) {
            // Nombre y precio vienen del catálogo, no del cliente.
            ProductoCatalogo producto = catalogo.obtenerProducto(item.productoId());
            pedido.agregarItem(new ItemPedido(
                    producto.id(), producto.nombre(), item.cantidad(), producto.precio()));
        }
        Pedido guardado = repository.save(pedido);
        // Tarea posterior: avisar al cliente. No bloquea la respuesta.
        publicar(RabbitMQConfig.PEDIDO_CREADO, guardado);
        return PedidoResponse.de(guardado);
    }

    // Cliente: solo sus pedidos. Operador y Admin: todos.
    @Transactional(readOnly = true)
    public List<PedidoResponse> listar(Actor actor, EstadoPedido estado) {
        List<Pedido> pedidos;
        if (actor.veTodos()) {
            pedidos = estado == null
                    ? repository.findAllByOrderByCreadoEnDesc()
                    : repository.findByEstadoOrderByCreadoEnDesc(estado);
        } else {
            pedidos = estado == null
                    ? repository.findByClienteIdOrderByCreadoEnDesc(actor.id())
                    : repository.findByClienteIdAndEstadoOrderByCreadoEnDesc(actor.id(), estado);
        }
        return pedidos.stream().map(PedidoResponse::de).toList();
    }

    @Transactional(readOnly = true)
    public PedidoResponse obtener(Long id, Actor actor) {
        return PedidoResponse.de(buscarVisible(id, actor));
    }

    @Transactional
    public PedidoResponse cambiarEstado(Long id, EstadoPedido nuevo) {
        Pedido pedido = repository.findById(id)
                .orElseThrow(() -> new PedidoNoEncontradoException(id));
        EstadoPedido actual = pedido.getEstado();
        if (!actual.puedeCambiarA(nuevo)) {
            throw new TransicionInvalidaException(actual, nuevo);
        }

        // Regla de negocio: al aceptar, el stock disminuye; al cancelar uno aceptado, se repone.
        // Si catálogo rechaza (409), se lanza excepción y el pedido no cambia de estado.
        if (nuevo == EstadoPedido.ACEPTADO) {
            catalogo.descontarStock(movimientos(pedido));
        } else if (nuevo == EstadoPedido.CANCELADO && actual.stockDescontado()) {
            catalogo.reponerStock(movimientos(pedido));
        }

        pedido.setEstado(nuevo);
        Pedido guardado = repository.saveAndFlush(pedido);
        // Tareas posteriores: cocina al aceptar, despacho y aviso al cliente al despachar.
        if (nuevo == EstadoPedido.ACEPTADO) {
            publicar(RabbitMQConfig.PEDIDO_ACEPTADO, guardado);
        } else if (nuevo == EstadoPedido.DESPACHADO) {
            publicar(RabbitMQConfig.PEDIDO_DESPACHADO, guardado);
        }
        return PedidoResponse.de(guardado);
    }

    // La operación principal ya quedó confirmada en la base de datos: el evento solo
    // informa que ocurrió. La consistencia entre transacción y publicación se profundiza
    // más adelante, junto con ACK/NACK, reintentos y DLQ.
    private void publicar(String routingKey, Pedido pedido) {
        productor.publicar(routingKey, new PedidoEvento(
                pedido.getId(),
                pedido.getEstado().name(),
                pedido.getClienteNombre(),
                LocalDateTime.now()));
    }

    // Un Cliente que pide un pedido ajeno recibe 404 (no se revela que existe).
    private Pedido buscarVisible(Long id, Actor actor) {
        return repository.findById(id)
                .filter(p -> actor.veTodos() || p.getClienteId().equals(actor.id()))
                .orElseThrow(() -> new PedidoNoEncontradoException(id));
    }

    private static List<MovimientoStock> movimientos(Pedido pedido) {
        return pedido.getItems().stream()
                .map(i -> new MovimientoStock(i.getProductoId(), i.getCantidad()))
                .toList();
    }
}
