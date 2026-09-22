package cl.duoc.dsy1107.pedidosapi.client;

import java.math.BigDecimal;

// Datos mínimos que Pedidos necesita de un producto del catálogo.
public record ProductoCatalogo(Long id, String nombre, BigDecimal precio, Integer stock) {
}
