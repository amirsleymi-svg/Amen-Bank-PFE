import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterModule, Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './login.component.html',
  styleUrls: ['./login.component.scss']
})
export class LoginComponent implements OnInit {

  loginForm!: FormGroup;
  totpForm!: FormGroup;

  showTotp     = false;
  showPassword = false;
  isLoading    = false;
  errorMessage = '';
  tempToken    = '';
  totpCode     = '';

  private returnUrl = '/dashboard';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.loginForm = this.fb.group({
      identifier: ['', [Validators.required]],
      password:   ['', [Validators.required]]
    });

    this.totpForm = this.fb.group({
      code: ['', [Validators.required, Validators.pattern(/^\d{6}$/)]]
    });

    this.returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') ?? '/dashboard';

    if (this.authService.isLoggedIn()) {
      this.router.navigate([this.returnUrl]);
    }
  }

  get f() { return this.loginForm.controls; }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.isLoading    = true;
    this.errorMessage = '';

    this.authService.login({
      identifier:  this.f['identifier'].value.trim(),
      password:    this.f['password'].value,
      deviceInfo:  navigator.userAgent
    }).subscribe({
      next: res => {
        this.isLoading = false;
        const auth = res.data;

        if (auth.totpRequired && auth.tempToken) {
          this.tempToken = auth.tempToken;
          this.showTotp  = true;
        } else {
          this.router.navigate([this.returnUrl]);
        }
      },
      error: err => {
        this.isLoading    = false;
        this.errorMessage = err.error?.message ?? 'Identifiants incorrects. Veuillez réessayer.';
      }
    });
  }

  // ─── TOTP handlers ───────────────────────────────────────────────
  onTotpDigit(event: Event, index: number): void {
    const input = event.target as HTMLInputElement;
    const value = input.value.replace(/\D/g, '');
    input.value = value;

    this.updateTotpCode();

    if (value && index < 5) {
      const next = document.getElementById(`totp-${index + 1}`) as HTMLInputElement;
      next?.focus();
    }
  }

  onTotpKeydown(event: KeyboardEvent, index: number): void {
    if (event.key === 'Backspace') {
      const input = event.target as HTMLInputElement;
      if (!input.value && index > 0) {
        const prev = document.getElementById(`totp-${index - 1}`) as HTMLInputElement;
        prev?.focus();
        prev && (prev.value = '');
        this.updateTotpCode();
      }
    }
  }

  onTotpPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const text = event.clipboardData?.getData('text') ?? '';
    const digits = text.replace(/\D/g, '').slice(0, 6);

    digits.split('').forEach((d, i) => {
      const input = document.getElementById(`totp-${i}`) as HTMLInputElement;
      if (input) input.value = d;
    });
    this.updateTotpCode();

    const lastFilled = Math.min(digits.length, 5);
    (document.getElementById(`totp-${lastFilled}`) as HTMLInputElement)?.focus();
  }

  private updateTotpCode(): void {
    this.totpCode = Array.from({ length: 6 }, (_, i) => {
      const input = document.getElementById(`totp-${i}`) as HTMLInputElement;
      return input?.value ?? '';
    }).join('');
  }

  onTotpSubmit(): void {
    if (this.totpCode.length !== 6) return;

    this.isLoading    = true;
    this.errorMessage = '';

    this.authService.verifyTotp({
      tempToken: this.tempToken,
      totpCode:  this.totpCode
    }).subscribe({
      next: () => {
        this.isLoading = false;
        this.router.navigate([this.returnUrl]);
      },
      error: err => {
        this.isLoading    = false;
        this.errorMessage = err.error?.message ?? 'Code invalide. Réessayez.';
        this.clearTotpInputs();
      }
    });
  }

  cancelTotp(): void {
    this.showTotp     = false;
    this.totpCode     = '';
    this.tempToken    = '';
    this.errorMessage = '';
    this.clearTotpInputs();
  }

  private clearTotpInputs(): void {
    for (let i = 0; i < 6; i++) {
      const input = document.getElementById(`totp-${i}`) as HTMLInputElement;
      if (input) input.value = '';
    }
    this.totpCode = '';
  }
}
