import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterModule, ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-reset-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  template: `
<div class="auth-layout">
  <div class="auth-brand">
    <div class="brand-inner">
      <div class="brand-logo"><svg width="48" height="48" viewBox="0 0 48 48" fill="none"><rect width="48" height="48" rx="12" fill="white" fill-opacity="0.15"/><path d="M8 36V20L24 10L40 20V36H29V26H19V36H8Z" fill="white"/></svg><span class="brand-name">Amen Bank</span></div>
      <div class="brand-tagline"><h1>Nouveau mot de passe</h1><p>Choisissez un mot de passe fort pour sécuriser votre compte.</p></div>
    </div>
  </div>
  <div class="auth-form-panel">
    <div class="auth-form-container">
      <div class="form-header"><h2>Réinitialiser</h2><p>Saisissez votre nouveau mot de passe</p></div>
      <div class="alert alert-error" *ngIf="error">{{ error }}</div>
      <div class="alert alert-success" *ngIf="done">Mot de passe modifié ! <a routerLink="/auth/login">Se connecter →</a></div>
      <form [formGroup]="form" (ngSubmit)="submit()" class="auth-form" *ngIf="!done">
        <div class="field-group">
          <label>Nouveau mot de passe *</label>
          <div class="input-wrapper">
            <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none"><rect x="3" y="11" width="18" height="11" rx="2" stroke="currentColor" stroke-width="1.5"/><path d="M7 11V7a5 5 0 0 1 10 0v4" stroke="currentColor" stroke-width="1.5"/></svg>
            <input formControlName="newPassword" type="password" placeholder="••••••••" class="form-input"/>
          </div>
        </div>
        <button type="submit" class="btn-primary" [disabled]="loading || form.invalid">
          <span class="btn-spinner" *ngIf="loading"></span>{{ loading ? 'En cours...' : 'Confirmer' }}
        </button>
      </form>
    </div>
  </div>
</div>`,
  styleUrls: ['../auth.styles.scss']
})
export class ResetPasswordComponent implements OnInit {
  form = this.fb.group({ newPassword: ['', [Validators.required, Validators.minLength(8), Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&]).{8,}$/)]] });
  loading = false; done = false; error = ''; token = '';
  constructor(private fb: FormBuilder, private auth: AuthService, private route: ActivatedRoute, private router: Router) {}
  ngOnInit() { this.token = this.route.snapshot.queryParamMap.get('token') ?? ''; }
  submit() {
    if (this.form.invalid || !this.token) return;
    this.loading = true;
    this.auth.resetPassword(this.token, this.form.value.newPassword!).subscribe({
      next: () => { this.loading = false; this.done = true; },
      error: err => { this.loading = false; this.error = err.error?.message ?? 'Lien invalide ou expiré.'; }
    });
  }
}
