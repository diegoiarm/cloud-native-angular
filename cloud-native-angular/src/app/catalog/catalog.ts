import { CommonModule } from '@angular/common';

import { HttpClient } from '@angular/common/http';

import {
  ChangeDetectorRef,
  Component,
  OnInit
} from '@angular/core';

import { FormsModule } from '@angular/forms';

import { environment } from '../../environments/environment';

import { mensajeError } from '../http.util';

import { Producto } from '../models';

import { SesionService } from '../sesion.service';

@Component({
  selector: 'app-catalog',
  imports: [CommonModule, FormsModule],
  templateUrl: './catalog.html',
  styleUrl: './catalog.css'
})
export class Catalog implements OnInit {

  productos: Producto[] = [];
  roles: string[] = [];

  form = {
    id: null as number | null,
    nombre: '',
    precio: 0,
    stock: 0
  };

  error = '';
  mensaje = '';
  cargando = true;
  guardando = false;

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

    this.cargar();
  }

  get esAdmin(): boolean {
    return this.roles.includes('Admin');
  }

  cargar(): void {
    this.cargando = true;
    this.http.get<Producto[]>(`${environment.catalogoBaseUrl}/api/catalog/products`)
      .subscribe({
        next: (productos) => {
          this.productos = productos;
          this.cargando = false;
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.error = mensajeError(err);
          this.cargando = false;
          this.cdr.markForCheck();
        }
      });
  }

  nuevo(): void {
    this.form = { id: null, nombre: '', precio: 0, stock: 0 };
    this.error = '';
  }

  editar(producto: Producto): void {
    this.form = {
      id: producto.id,
      nombre: producto.nombre,
      precio: producto.precio,
      stock: producto.stock
    };
    this.error = '';
    this.cdr.markForCheck();
  }

  guardar(): void {
    const body = {
      nombre: this.form.nombre,
      precio: this.form.precio,
      stock: this.form.stock
    };
    const peticion = this.form.id
      ? this.http.put<Producto>(
          `${environment.catalogoBaseUrl}/api/catalog/products/${this.form.id}`,
          body
        )
      : this.http.post<Producto>(
          `${environment.catalogoBaseUrl}/api/catalog/products`,
          body
        );

    this.guardando = true;
    peticion.subscribe({
      next: () => {
        this.guardando = false;
        this.mensaje = 'Producto guardado correctamente.';
        this.error = '';
        this.nuevo();
        this.cargar();
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.guardando = false;
        this.error = mensajeError(err);
        this.cdr.markForCheck();
      }
    });
  }
}