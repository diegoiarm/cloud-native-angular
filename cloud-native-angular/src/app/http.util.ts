export function mensajeError(error: any): string {
  const cuerpo = error?.error;
  if (cuerpo) {
    if (typeof cuerpo === 'string' && cuerpo.trim()) {
      return cuerpo;
    }
    if (cuerpo.message) {
      return cuerpo.message;
    }
    if (cuerpo.error) {
      return cuerpo.error;
    }
  }
  if (error?.status === 401) {
    return 'No autenticado o token inválido (401)';
  }
  if (error?.status === 403) {
    return 'Sin permisos para esta operación (403)';
  }
  return 'Error de conexión con la API';
}