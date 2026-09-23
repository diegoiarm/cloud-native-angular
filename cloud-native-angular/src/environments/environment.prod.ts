// Configuración de producción (ng build). El dominio de Vercel debe estar además
// registrado como Redirect URI (SPA) en la App Registration del frontend.
export const environment = {
  production: true,
  msal: {
    clientId: "67e79135-0890-48e1-af3b-0a217b270cfa",
    tenantId: "6bf42f50-ccc9-46e4-ae46-2b6534675957",
    redirectUri: "https://TU-DOMINIO.vercel.app",

    apiScope: "api://371e0368-c37d-4768-9266-0212824e67ba/Pedidos.Read",
  },

  // Ambos microservicios se publican tras el mismo API Gateway.
  apiBaseUrl: "https://vfohqq8qme.execute-api.us-east-1.amazonaws.com",
  catalogoBaseUrl: "https://vfohqq8qme.execute-api.us-east-1.amazonaws.com",
};
