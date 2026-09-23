export function decodificarJwt(token: string): any {
  const part = token.split('.')[1];
  if (!part) {
    return null;
  }
  const base64 = part.replace(/-/g, '+').replace(/_/g, '/');
  const padded =
    base64 + '='.repeat((4 - (base64.length % 4)) % 4);
  return JSON.parse(
    decodeURIComponent(
      atob(padded)
        .split('')
        .map((c) => '%' + c.charCodeAt(0).toString(16).padStart(2, '0'))
        .join('')
    )
  );
}