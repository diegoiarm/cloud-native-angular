import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { ChangeDetectorRef, Component, OnInit } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { environment } from "../../environments/environment";
import { mensajeError } from "../http.util";
import { Pedido, Producto } from "../models";
import { SesionService } from "../sesion.service";

interface LineaForm {
  productoId: number;
  cantidad: number;
  nombre: string;
}

@Component({
  selector: "app-orders",
  imports: [CommonModule, FormsModule],
  templateUrl: "./orders.html",
  styleUrl: "./orders.css",
})
export class Orders implements OnInit {
  pedidos: Pedido[] = [];
  productos: Producto[] = [];
  roles: string[] = [];

  nuevoProducto = 0;
  nuevoCantidad = 1;
  lineas: LineaForm[] = [];

  objetivo: Record<number, string> = {};
  detalleId: number | null = null;

  error = "";
  mensaje = "";

  constructor(
    private http: HttpClient,
    private sesion: SesionService,
    private cdr: ChangeDetectorRef,
  ) {}

  ngOnInit(): void {
    this.sesion
      .obtenerRoles()
      .then((roles) => {
        this.roles = roles;
        this.cargar();
        this.cdr.markForCheck();
      })
      .catch(() => this.cargar());

    this.http
      .get<Producto[]>(`${environment.catalogoBaseUrl}/api/catalog/products`)
      .subscribe({
        next: (productos) => {
          this.productos = productos;
          this.cdr.markForCheck();
        },
        error: () => {},
      });
  }

  // Operador y Admin ven todos los pedidos y gestionan estados.
  get esGestor(): boolean {
    return this.roles.includes("Operador") || this.roles.includes("Admin");
  }

  // Según la matriz de actores, el Admin supervisa pero no crea pedidos.
  get puedeCrear(): boolean {
    return this.roles.includes("Cliente") || this.roles.includes("Operador");
  }

  cargar(): void {
    this.http.get<Pedido[]>(`${environment.apiBaseUrl}/api/orders`).subscribe({
      next: (pedidos) => {
        this.pedidos = pedidos;
        this.error = "";
        this.cdr.markForCheck();
      },
      error: (err) => {
        this.error = mensajeError(err);
        this.cdr.markForCheck();
      },
    });
  }

  agregarLinea(): void {
    const producto = this.productos.find(
      (p) => p.id === Number(this.nuevoProducto),
    );
    if (!producto) {
      return;
    }
    this.lineas.push({
      productoId: producto.id,
      cantidad: this.nuevoCantidad,
      nombre: producto.nombre,
    });
    this.nuevoProducto = 0;
    this.nuevoCantidad = 1;
    this.cdr.markForCheck();
  }

  quitarLinea(i: number): void {
    this.lineas.splice(i, 1);
    this.cdr.markForCheck();
  }

  crear(): void {
    if (this.lineas.length === 0) {
      this.error = "Agrega al menos un producto al pedido.";
      return;
    }
    // El precio lo pone el backend desde el catálogo; aquí solo van producto y cantidad.
    const body = {
      items: this.lineas.map((l) => ({
        productoId: l.productoId,
        cantidad: l.cantidad,
      })),
    };
    this.http
      .post<Pedido>(`${environment.apiBaseUrl}/api/orders`, body)
      .subscribe({
        next: (pedido) => {
          this.lineas = [];
          this.mensaje = `Pedido #${pedido.id} creado correctamente.`;
          this.error = "";
          this.cargar();
          this.cdr.markForCheck();
        },
        error: (err) => {
          this.error = mensajeError(err);
          this.mensaje = "";
          this.cdr.markForCheck();
        },
      });
  }

  cambiarEstado(pedido: Pedido): void {
    const estado = this.objetivo[pedido.id];
    if (!estado) {
      return;
    }
    this.http
      .put<Pedido>(`${environment.apiBaseUrl}/api/orders/${pedido.id}/status`, {
        estado,
      })
      .subscribe({
        next: () => {
          this.mensaje = `Pedido #${pedido.id} ahora está en ${estado}.`;
          this.error = "";
          this.cargar();
          this.cdr.markForCheck();
        },
        error: (err) => {
          // 409 = transición inválida o stock insuficiente.
          this.error = mensajeError(err);
          this.mensaje = "";
          this.cdr.markForCheck();
        },
      });
  }

  toggleDetalle(id: number): void {
    this.detalleId = this.detalleId === id ? null : id;
  }
}
