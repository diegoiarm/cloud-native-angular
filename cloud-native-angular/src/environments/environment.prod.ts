// Configuración de producción (ng build). Reemplazar los valores TU-...
// por el dominio real del frontend y la URL pública de AWS API Gateway.
export const environment = {
  production: true,
  msal: {
    clientId: "198c0fac-457a-43c6-a8a8-f3b90ba14dc1",
    tenantId: "413875c7-b2d6-4bc6-93ee-071e4f43a25e",
    redirectUri: "https://TU-DOMINIO.vercel.app",

    apiScope: "api://fe49fbff-96c3-4146-b54e-a81b1d2a2379/Pedidos.Read",
  },

  apiBaseUrl: "https://TU-API-ID.execute-api.us-east-1.amazonaws.com/prod",
};
