import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-forbidden',
  standalone: true,
  imports: [CommonModule, RouterModule],
  template: `
<div style="min-height:100vh;display:flex;align-items:center;justify-content:center;background:#F0F3F8;font-family:Inter,sans-serif">
  <div style="text-align:center;max-width:400px;padding:2rem">
    <div style="width:80px;height:80px;background:#FEF0EF;border-radius:50%;display:flex;align-items:center;justify-content:center;margin:0 auto 1.5rem">
      <svg width="40" height="40" viewBox="0 0 24 24" fill="none"><circle cx="12" cy="12" r="10" stroke="#C5372A" stroke-width="1.75"/><path d="M12 7v6M12 16h.01" stroke="#C5372A" stroke-width="1.75" stroke-linecap="round"/></svg>
    </div>
    <h1 style="font-size:2.5rem;font-weight:800;color:#C5372A;letter-spacing:-.03em">403</h1>
    <h2 style="font-size:1.2rem;font-weight:700;color:#1A2540;margin:.5rem 0">Accès refusé</h2>
    <p style="color:#6B7A99;margin-bottom:2rem;line-height:1.6">Vous n'avez pas les autorisations nécessaires pour accéder à cette page.</p>
    <a routerLink="/dashboard" style="display:inline-flex;align-items:center;gap:8px;height:44px;padding:0 1.5rem;background:#0A3F6B;color:white;border-radius:10px;font-weight:600;text-decoration:none">← Retour au tableau de bord</a>
  </div>
</div>`
})
export class ForbiddenComponent {}
