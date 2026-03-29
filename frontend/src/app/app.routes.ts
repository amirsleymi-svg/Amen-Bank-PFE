import { Routes } from '@angular/router';
import { authGuard }  from './core/guards/auth.guard';
import { guestGuard } from './core/guards/guest.guard';
import { adminGuard } from './core/guards/admin.guard';

export const routes: Routes = [
  // ─── Home (Public Landing) ────────────────────────────────────
  {
    path: '',
    loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent),
    pathMatch: 'full'
  },

  // ─── Auth & Onboarding (public/guest) ─────────────────────────
  {
    path: 'auth',
    canActivate: [guestGuard],
    children: [
      { path: 'login',           loadComponent: () => import('./features/auth/login/login.component').then(m => m.LoginComponent) },
      // New Onboarding flows
      { path: 'register',        loadComponent: () => import('./features/auth/onboarding-register/onboarding-register.component').then(m => m.OnboardingRegisterComponent) },
      { path: 'activate',        loadComponent: () => import('./features/auth/account-activation/account-activation.component').then(m => m.AccountActivationComponent) },

      // Legacy/Detailed register
      { path: 'register-kyc',    loadComponent: () => import('./features/auth/register/register.component').then(m => m.RegisterComponent) },

      { path: 'forgot-password', loadComponent: () => import('./features/auth/forgot-password/forgot-password.component').then(m => m.ForgotPasswordComponent) },
      { path: 'reset-password',  loadComponent: () => import('./features/auth/reset-password/reset-password.component').then(m => m.ResetPasswordComponent) },
      { path: '', redirectTo: 'login', pathMatch: 'full' }
    ]
  },

  // ─── Client (protected) ───────────────────────────────────────
  { path: 'dashboard',           canActivate: [authGuard], loadComponent: () => import('./features/dashboard/dashboard.component').then(m => m.DashboardComponent) },
  { path: 'dashboard/transfers', canActivate: [authGuard], loadComponent: () => import('./features/transfers/transfers.component').then(m => m.TransfersComponent) },
  { path: 'dashboard/credits',   canActivate: [authGuard], loadComponent: () => import('./features/credits/credits.component').then(m => m.CreditsComponent) },
  { path: 'dashboard/profile',   canActivate: [authGuard], loadComponent: () => import('./features/profile/profile.component').then(m => m.ProfileComponent) },

  // ─── Admin (protected + admin role) ──────────────────────────
  {
    path: 'admin',
    canActivate: [authGuard, adminGuard],
    loadComponent: () => import('./features/admin/admin.component').then(m => m.AdminComponent)
  },

  // ─── Errors ───────────────────────────────────────────────────
  { path: 'forbidden', loadComponent: () => import('./features/auth/forbidden/forbidden.component').then(m => m.ForbiddenComponent) },
  { path: '**', redirectTo: '/' }
];
