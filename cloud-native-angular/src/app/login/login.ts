import { CommonModule } from '@angular/common';

import {
  ChangeDetectorRef,
  Component,
  OnDestroy,
  OnInit
} from '@angular/core';

import { RouterLink } from '@angular/router';

import {
  MsalBroadcastService,
  MsalService
} from '@azure/msal-angular';

import {
  AccountInfo,
  InteractionStatus
} from '@azure/msal-browser';

import { Subject } from 'rxjs';

import { filter, takeUntil } from 'rxjs/operators';

import { SesionService } from '../sesion.service';

@Component({
  selector: 'app-login',
  imports: [CommonModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login implements OnInit, OnDestroy {

  user: AccountInfo | null = null;

  private readonly destroying$ = new Subject<void>();

  constructor(
    private authService: MsalService,
    private msalBroadcastService: MsalBroadcastService,
    private sesion: SesionService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
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
      });
  }

  login(): void {
    this.sesion.iniciarSesion();
  }

  logout(): void {
    this.sesion.cerrarSesion();
  }

  ngOnDestroy(): void {
    this.destroying$.next();
    this.destroying$.complete();
  }
}