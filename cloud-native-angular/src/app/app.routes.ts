import { Routes } from '@angular/router';
import { MsalGuard } from '@azure/msal-angular';
import { Login } from './login/login';
import { Dashboard } from './dashboard/dashboard';
import { Orders } from './orders/orders';
import { Catalog } from './catalog/catalog';

export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    redirectTo: 'login'
  },
  {
    path: 'login',
    component: Login
  },
  {
    path: 'dashboard',
    component: Dashboard,
    canActivate: [MsalGuard]
  },
  {
    path: 'orders',
    component: Orders,
    canActivate: [MsalGuard]
  },
  {
    path: 'catalog',
    component: Catalog,
    canActivate: [MsalGuard]
  },
  {
    path: '**',
    redirectTo: 'login'
  }
];