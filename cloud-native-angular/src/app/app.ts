import { CommonModule } from "@angular/common";
import { HttpClient } from "@angular/common/http";
import { ChangeDetectorRef, Component, OnDestroy, OnInit } from "@angular/core";
import { RouterLink, RouterOutlet } from "@angular/router";
import { MsalBroadcastService, MsalService } from "@azure/msal-angular";
import {
  AccountInfo,
  AuthenticationResult,
  InteractionStatus,
} from "@azure/msal-browser";
import { Subject } from "rxjs";
import { filter, takeUntil } from "rxjs/operators";
import { environment } from "../environments/environment";
@Component({
  selector: "app-root",
  imports: [CommonModule, RouterLink, RouterOutlet],
  templateUrl: "./app.html",
  styleUrl: "./app.css",
})
export class App implements OnInit, OnDestroy {
  user: AccountInfo | null = null;
  accessTokenPreview = "";
  respuestaApi: any = null;
  private readonly destroying$ = new Subject<void>();
  constructor(
    private authService: MsalService,
    private msalBroadcastService: MsalBroadcastService,
    private cdr: ChangeDetectorRef,
    private http: HttpClient,
  ) {}
  ngOnInit(): void {
    this.authService
      .handleRedirectObservable({
        navigateToLoginRequestUrl: false,
      })
      .subscribe({
        next: (result: AuthenticationResult | null) => {
          if (result?.account) {
            this.authService.instance.setActiveAccount(result.account);
          }
        },
        error: (error) => {
          console.error("Error MSAL:", error);
        },
      });
    this.msalBroadcastService.inProgress$
      .pipe(
        filter(
          (status: InteractionStatus) => status === InteractionStatus.None,
        ),
        takeUntil(this.destroying$),
      )
      .subscribe(() => {
        this.actualizarUsuario();
      });
  }
  private actualizarUsuario(): void {
    let activeAccount = this.authService.instance.getActiveAccount();
    const accounts = this.authService.instance.getAllAccounts();
    if (!activeAccount && accounts.length > 0) {
      activeAccount = accounts[0];
      this.authService.instance.setActiveAccount(activeAccount);
    }
    this.user = activeAccount ?? null;
    this.cdr.markForCheck();
  }
  login(): void {
    this.authService.loginRedirect({
      scopes: ["openid", "profile", "email"],
    });
  }
  obtenerAccessToken(): void {
    const account = this.authService.instance.getActiveAccount();
    if (!account) {
      return;
    }
    this.authService
      .acquireTokenSilent({
        account,
        scopes: [environment.msal.apiScope],
      })
      .subscribe({
        next: (result) => {
          this.accessTokenPreview = result.accessToken.substring(0, 90) + "...";
          this.cdr.markForCheck();
        },
        error: () => {
          this.authService.acquireTokenRedirect({
            scopes: [environment.msal.apiScope],
          });
        },
      });
  }
  consultarPedidos(): void {
    this.respuestaApi = null;
    this.http.get(`${environment.apiBaseUrl}/api/pedidos`).subscribe({
      next: (respuesta) => {
        this.respuestaApi = respuesta;
        this.cdr.markForCheck();
      },
      error: (error) => {
        this.respuestaApi = {
          status: error.status,
          mensaje: "Solicitud rechazada",
        };
        this.cdr.markForCheck();
      },
    });
  }
  logout(): void {
    this.authService.logoutRedirect({
      postLogoutRedirectUri: "http://localhost:4200",
    });
  }
  ngOnDestroy(): void {
    this.destroying$.next();
    this.destroying$.complete();
  }
}
