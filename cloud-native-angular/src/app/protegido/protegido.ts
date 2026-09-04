import { Component } from "@angular/core";
@Component({
  selector: "app-protegido",
  standalone: true,
  template: `
    <h2>Área protegida</h2>
    <p>Si puedes ver esta página, MsalGuard permitió el acceso.</p>
  `,
})
export class Protegido {}
