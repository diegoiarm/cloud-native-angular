import { Injectable } from '@angular/core';

import { MsalService } from '@azure/msal-angular';

import { AccountInfo, AuthenticationResult } from '@azure/msal-browser';

import { firstValueFrom } from 'rxjs';

import { environment } from '../environments/environment';

import { decodificarJwt } from './jwt.util';

@Injectable({ providedIn: 'root' })
export class SesionService {

  private rolesCache: string[] | null = null;

  constructor(private authService: MsalService) {}

  getUsuario(): AccountInfo | null {
    return (
      this.authService.instance.getActiveAccount() ??
      this.authService.instance.getAllAccounts()[0] ??
      null
    );
  }

  iniciarSesion(): void {
    this.authService.loginRedirect({
      scopes: ['openid', 'profile', 'email']
    });
  }

  cerrarSesion(): void {
    this.authService.logoutRedirect({
      postLogoutRedirectUri: environment.msal.redirectUri
    });
  }

  async obtenerAccessToken(): Promise<string> {
    const account = this.getUsuario();
    if (!account) {
      throw new Error('No hay una sesión activa');
    }
    const result: AuthenticationResult =
      await firstValueFrom(
        this.authService.acquireTokenSilent({
          account,
          scopes: [environment.msal.apiScope]
        })
      );
    return result.accessToken;
  }

  async obtenerRoles(): Promise<string[]> {
    if (this.rolesCache) {
      return this.rolesCache;
    }
    const token = await this.obtenerAccessToken();
    const claims = decodificarJwt(token);
    const roles: string[] = Array.isArray(claims?.roles)
      ? claims.roles.map((r: unknown) => String(r))
      : [];
    this.rolesCache = roles;
    return roles;
  }
}