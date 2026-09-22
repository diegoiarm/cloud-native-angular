package cl.duoc.dsy1107.pedidosapi.service;

import cl.duoc.dsy1107.pedidosapi.client.CatalogoClient;
import cl.duoc.dsy1107.pedidosapi.client.MovimientoStock;
import cl.duoc.dsy1107.pedidosapi.client.ProductoCatalogo;
import cl.duoc.dsy1107.pedidosapi.dto.CrearPedidoRequest;
import cl.duoc.dsy1107.pedidosapi.dto.PedidoResponse;
import cl.duoc.dsy1107.pedidosapi.exception.PedidoNoEncontradoException;
import cl.duoc.dsy1107.pedidosapi.exception.TransicionInvalidaException;
import cl.duoc.dsy1107.pedidosapi.model.EstadoPedido;
import cl.duoc.dsy1107.pedidosapi.model.ItemPedido;
import cl.duoc.dsy1107.pedidosapi.model.Pedido;
import cl.duoc.dsy1107.pedidosapi.repository.PedidoRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PedidoService {

    private final PedidoRepository repository;
    private final CatalogoClient catalogo;

    public PedidoService(PedidoRepository repository, CatalogoClient catalogo) {
        this.repository = repository;
        this.catalogo = catalogo;
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
        return PedidoResponse.de(repository.save(pedido));
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
        return PedidoResponse.de(repository.saveAndFlush(pedido));
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
