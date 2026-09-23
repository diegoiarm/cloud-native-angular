import { CommonModule } from '@angular/common';

import {
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit
} from '@angular/core';

import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import {
  MsalBroadcastService,
  MsalService
} from '@azure/msal-angular';

import {
  AccountInfo,
  AuthenticationResult,
  InteractionStatus
} from '@azure/msal-browser';

import { Subject } from 'rxjs';

import { filter, takeUntil } from 'rxjs/operators';

import { SesionService } from './sesion.service';

@Component({
  selector: 'app-root',
  imports: [
    CommonModule,
    RouterLink,
    RouterLinkActive,
    RouterOutlet
  ],
  templateUrl: './app.html',
  styleUrl: './app.css'
})
export class App implements OnInit, OnDestroy {

  user: AccountInfo | null = null;

  rol = '';

  private readonly destroying$ = new Subject<void>();

  constructor(
    private authService: MsalService,
    private msalBroadcastService: MsalBroadcastService,
    private sesion: SesionService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    this.authService
      .handleRedirectObservable({
        navigateToLoginRequestUrl: false
      })
      .subscribe({
        next: (result: AuthenticationResult | null) => {
          if (result?.account) {
            this.authService.instance
              .setActiveAccount(result.account);
          }
        },
        error: (error) => {
          console.error('Error MSAL:', error);
        }
      });

    this.msalBroadcastService
      .inProgress$
      .pipe(
        filter((status: InteractionStatus) =>
          status === InteractionStatus.None),
        takeUntil(this.destroying$)
      )
      .subscribe(() => {
        this.user = this.sesion.getUsuario();
        this.cdr.markForCheck();

        if (this.user) {
          this.sesion.obtenerRolPrincipal().then((rol) => {
            this.rol = rol;
            this.cdr.markForCheck();
          });
        } else {
          this.rol = '';
        }
      });
  }

  logout(): void {
    this.sesion.cerrarSesion();
  }

  ngOnDestroy(): void {
    this.destroying$.next();
    this.destroying$.complete();
  }
}