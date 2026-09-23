import { CommonModule } from '@angular/common';

import { HttpClient } from '@angular/common/http';

import {
  ChangeDetectorRef,
  Component,
  OnInit
} from '@angular/core';

import { RouterLink } from '@angular/router';

import { environment } from '../../environments/environment';

import { decodificarJwt } from '../jwt.util';

import { SesionService } from '../sesion.service';

@Component({
  selector: 'app-dashboard',
  imports: [CommonModule, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.css'
})
export class Dashboard implements OnInit {

  pedidosCount = 0;
  productosCount = 0;
  mostrarApi = false;

  roles: string[] = [];

  accessTokenPreview = '';
  accessTokenClaims: any = null;

  constructor(
    private http: HttpClient,
    private sesion: SesionService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.sesion.obtenerRoles()
      .then((roles) => {
        this.roles = roles;
        this.cdr.markForCheck();
      })
      .catch(() => {});

    this.http.get<any>(`${environment.apiBaseUrl}/api/orders`)
      .subscribe({
        next: (pedidos) => {
          this.pedidosCount = Array.isArray(pedidos) ? pedidos.length : 0;
          this.cdr.markForCheck();
        },
        error: () => {}
      });

    this.http.get<any>(`${environment.catalogoBaseUrl}/api/catalog/products`)
      .subscribe({
        next: (productos) => {
          this.productosCount = Array.isArray(productos) ? productos.length : 0;
          this.cdr.markForCheck();
        },
        error: () => {}
      });
  }

  async verToken(): Promise<void> {
    try {
      const token = await this.sesion.obtenerAccessToken();
      this.accessTokenPreview = token.substring(0, 90) + '...';
      this.accessTokenClaims = decodificarJwt(token);
      this.mostrarApi = true;
      this.cdr.markForCheck();
    } catch {
      this.accessTokenClaims = {
        error: 'No se pudo obtener el Access Token'
      };
      this.mostrarApi = true;
      this.cdr.markForCheck();
    }
  }

  get rol(): string {
    if (this.roles.includes('Admin')) {
      return 'Admin';
    }
    if (this.roles.includes('Operador')) {
      return 'Operador';
    }
    return this.roles.includes('Cliente') ? 'Cliente' : 'Sin rol';
  }
}