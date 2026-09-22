package cl.duoc.dsy1107.pedidosapi.client;

import java.util.List;

/**
 * Operaciones que Pedidos necesita del módulo de Catálogo.
 *
 * Contrato HTTP esperado en catalogo-api (implementado en HttpCatalogoClient):
 *   GET  /api/catalog/products/{id}   -> 200 {id, nombre, precio, stock} | 404
 *   POST /api/catalog/stock/descontar -> body {"items":[{productoId, cantidad}]}
 *                                        200 | 404 producto | 409 stock insuficiente
 *                                        (todo o nada: si un ítem falla, no descuenta ninguno)
 *   POST /api/catalog/stock/reponer   -> mismo body, 200
 *
 * Si el catálogo queda dentro de este mismo servicio, basta con otra implementación
 * de esta interfaz que llame directo al servicio de catálogo.
 */
public interface CatalogoClient {

    ProductoCatalogo obtenerProducto(Long productoId);

    void descontarStock(List<MovimientoStock> movimientos);

    void reponerStock(List<MovimientoStock> movimientos);
}
