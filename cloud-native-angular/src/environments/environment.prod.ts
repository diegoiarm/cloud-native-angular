// Configuración de producción (ng build). Reemplazar los valores TU-...
// por el dominio real del frontend y la URL pública de AWS API Gateway.
export const environment = {
  production: true,
  msal: {
    clientId: "67e79135-0890-48e1-af3b-0a217b270cfa",
    tenantId: "6bf42f50-ccc9-46e4-ae46-2b6534675957",
    redirectUri: "https://TU-DOMINIO.vercel.app",

    apiScope: "api://371e0368-c37d-4768-9266-0212824e67ba/Pedidos.Read",
  },

  // En producción ambos microservicios se publican tras el mismo API Gateway.
  apiBaseUrl: "https://TU-API-ID.execute-api.us-east-1.amazonaws.com/prod",
  catalogoBaseUrl: "https://TU-API-ID.execute-api.us-east-1.amazonaws.com/prod",
};
