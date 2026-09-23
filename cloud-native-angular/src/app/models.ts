export interface Producto {
  id: number;
  nombre: string;
  precio: number;
  stock: number;
}

// Corresponde a PedidoResponse.Item de pedidos-api.
export interface PedidoItem {
  productoId: number;
  nombreProducto: string;
  cantidad: number;
  precioUnitario: number;
  subtotal: number;
}

// Corresponde a PedidoResponse de pedidos-api.
export interface Pedido {
  id: number;
  cliente: string;
  estado: string;
  // El backend indica a qué estados puede pasar el pedido.
  siguientesEstados: string[];
  total: number;
  creadoEn: string;
  actualizadoEn: string;
  items: PedidoItem[];
}
