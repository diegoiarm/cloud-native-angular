export function mensajeError(error: any): string {
  const cuerpo = error?.error;
  if (typeof cuerpo === "string" && cuerpo.trim()) {
    return cuerpo;
  }
  // pedidos-api y catalogo-api entregan el detalle en "mensaje".
  if (cuerpo?.mensaje) {
    return cuerpo.mensaje;
  }
  if (cuerpo?.message) {
    return cuerpo.message;
  }
  switch (error?.status) {
    case 0:
      return "No se pudo contactar la API. ¿Está corriendo el servicio?";
    case 400:
      return "Los datos enviados no son válidos (400).";
    case 401:
      return "No autenticado o token inválido (401).";
    case 403:
      return "Tu rol no tiene permiso para esta operación (403).";
    case 404:
      return "No se encontró el recurso solicitado (404).";
    case 409:
      return "La operación no es posible en el estado actual del pedido (409).";
    case 503:
      return "El servicio de catálogo no está disponible (503).";
    default:
      return `Error inesperado de la API (${error?.status ?? "sin código"}).`;
  }
}
