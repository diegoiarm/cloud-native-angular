export const environment = {
  production: false,
  msal: {
    clientId: "67e79135-0890-48e1-af3b-0a217b270cfa",
    tenantId: "6bf42f50-ccc9-46e4-ae46-2b6534675957",
    redirectUri: "http://localhost:4200",

    apiScope: "api://371e0368-c37d-4768-9266-0212824e67ba/Pedidos.Read",
  },

  // En local cada microservicio corre en su propio puerto.
  // Para probar contra AWS, reemplazar ambas por la Invoke URL del API Gateway.
  apiBaseUrl: "http://localhost:8080",
  catalogoBaseUrl: "http://localhost:8081",
};
