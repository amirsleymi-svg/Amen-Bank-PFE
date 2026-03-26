import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-forgot-password',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  template: `
<div class="auth-layout">
  <div class="auth-brand">
    <div class="brand-inner">
      <div class="brand-logo">
        <svg width="48" height="48" viewBox="0 0 48 48" fill="none"><rect width="48" height="48" rx="12" fill="white" fill-opacity="0.15"/><path d="M8 36V20L24 10L40 20V36H29V26H19V36H8Z" fill="white"/></svg>
        <span class="brand-name">Amen Bank</span>
      </div>
      <div class="brand-tagline">
        <h1>Mot de passe oublié ?</h1>
        <p>Pas d'inquiétude. Entrez votre email et nous vous enverrons un lien de réinitialisation.</p>
      </div>
    </div>
  </div>
  <div class="auth-form-panel">
    <div class="auth-form-container">
      <div class="form-header">
        <h2>Réinitialiser</h2>
        <p>Entrez l'adresse email associée à votre compte</p>
      </div>
      <div class="alert alert-success" *ngIf="sent">
        <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><circle cx="8" cy="8" r="7" stroke="currentColor" stroke-width="1.5"/><path d="M5 8l2.5 2.5L11 5" stroke="currentColor" stroke-width="1.5" stroke-linecap="round"/></svg>
        Si votre email est enregistré, vous recevrez un lien dans quelques minutes.
      </div>
      <form [formGroup]="form" (ngSubmit)="submit()" class="auth-form" *ngIf="!sent">
        <div class="field-group">
          <label>Adresse email *</label>
          <div class="input-wrapper">
            <svg class="input-icon" width="18" height="18" viewBox="0 0 24 24" fill="none"><path d="M20 4H4C2.9 4 2 4.9 2 6v12c0 1.1.9 2 2 2h16c1.1 0 2-.9 2-2V6c0-1.1-.9-2-2-2z" stroke="currentColor" stroke-width="1.5"/><path d="M22 6l-10 7L2 6" stroke="currentColor" stroke-width="1.5"/></svg>
            <input formControlName="email" type="email" placeholder="votre@email.com" class="form-input"/>
          </div>
        </div>
        <button type="submit" class="btn-primary" [disabled]="loading || form.invalid">
          <span class="btn-spinner" *ngIf="loading"></span>
          {{ loading ? 'Envoi...' : 'Envoyer le lien' }}
        </button>
      </form>
      <div class="form-footer"><p><a routerLink="/auth/login">← Retour à la connexion</a></p></div>
    </div>
  </div>
</div>`,
  styleUrls: ['../auth.styles.scss']
})
export class ForgotPasswordComponent {
  form = this.fb.group({ email: ['', [Validators.required, Validators.email]] });
  loading = false; sent = false;
  constructor(private fb: FormBuilder, private auth: AuthService) {}
  submit() {
    if (this.form.invalid) return;
    this.loading = true;
    this.auth.forgotPassword(this.form.value.email!).subscribe({
      next: () => { this.loading = false; this.sent = true; },
      error: () => { this.loading = false; this.sent = true; } // anti-enumeration
    });
  }
}
