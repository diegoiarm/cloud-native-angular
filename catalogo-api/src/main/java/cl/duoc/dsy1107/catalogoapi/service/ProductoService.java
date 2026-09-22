package cl.duoc.dsy1107.catalogoapi.service;

import cl.duoc.dsy1107.catalogoapi.dto.MovimientoStockRequest;
import cl.duoc.dsy1107.catalogoapi.dto.ProductoRequest;
import cl.duoc.dsy1107.catalogoapi.exception.ProductoNoEncontradoException;
import cl.duoc.dsy1107.catalogoapi.exception.StockInsuficienteException;
import cl.duoc.dsy1107.catalogoapi.model.Producto;
import cl.duoc.dsy1107.catalogoapi.repository.ProductoRepository;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductoService {

    private final ProductoRepository repository;

    public ProductoService(ProductoRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<Producto> listar() {
        return repository.findAllByOrderByNombreAsc();
    }

    @Transactional(readOnly = true)
    public Producto obtener(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ProductoNoEncontradoException(id));
    }

    @Transactional
    public Producto crear(ProductoRequest request) {
        return repository.save(new Producto(request.nombre(), request.precio(), request.stock()));
    }

    @Transactional
    public Producto actualizar(Long id, ProductoRequest request) {
        Producto producto = obtener(id);
        producto.setNombre(request.nombre());
        producto.setPrecio(request.precio());
        producto.setStock(request.stock());
        return producto;
    }

    /**
     * Descuenta el stock de todos los ítems o de ninguno: primero valida que
     * alcance para cada producto y recién entonces descuenta.
     */
    @Transactional
    public void descontar(MovimientoStockRequest request) {
        Map<Long, Integer> cantidades = agrupar(request);
        Map<Long, Producto> productos = bloquear(cantidades);
        cantidades.forEach((id, cantidad) -> {
            Producto producto = productos.get(id);
            if (!producto.tieneStock(cantidad)) {
                throw new StockInsuficienteException(producto.getNombre(), producto.getStock(), cantidad);
            }
        });
        cantidades.forEach((id, cantidad) -> productos.get(id).descontar(cantidad));
    }

    @Transactional
    public void reponer(MovimientoStockRequest request) {
        Map<Long, Integer> cantidades = agrupar(request);
        Map<Long, Producto> productos = bloquear(cantidades);
        cantidades.forEach((id, cantidad) -> productos.get(id).reponer(cantidad));
    }

    // Un mismo producto puede venir en varias líneas: se suman sus cantidades.
    private static Map<Long, Integer> agrupar(MovimientoStockRequest request) {
        return request.items().stream()
                .collect(Collectors.toMap(
                        MovimientoStockRequest.Item::productoId,
                        MovimientoStockRequest.Item::cantidad,
                        Integer::sum));
    }

    private Map<Long, Producto> bloquear(Map<Long, Integer> cantidades) {
        Map<Long, Producto> productos = repository.findByIdIn(cantidades.keySet()).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));
        cantidades.keySet().stream()
                .filter(id -> !productos.containsKey(id))
                .findFirst()
                .ifPresent(id -> {
                    throw new ProductoNoEncontradoException(id);
                });
        return productos;
    }
}
