import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { OnboardingService } from '../../../core/services/onboarding.service';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const password = group.get('password')?.value;
  const confirm  = group.get('confirmPassword')?.value;
  return password && confirm && password !== confirm
    ? { passwordMismatch: true }
    : null;
}

@Component({
  selector: 'app-onboarding-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './onboarding-register.component.html',
  styleUrls: ['../../auth/auth.styles.scss', './onboarding-register.component.scss']
})
export class OnboardingRegisterComponent implements OnInit {

  form!: FormGroup;
  isLoading     = false;
  submitted     = false;
  errorMessage  = '';
  showPassword  = false;
  showConfirm   = false;

  strengthColors = ['#E24B4A', '#F09500', '#E8D000', '#1B7A4E'];
  strengthLabels = ['Très faible', 'Faible', 'Correct', 'Fort'];

  benefits = [
    { icon: '🏦', text: 'Compte courant gratuit' },
    { icon: '⚡', text: 'Virements instantanés' },
    { icon: '🔒', text: 'Sécurité bancaire avancée' },
    { icon: '📱', text: 'Application mobile incluse' },
    { icon: '💳', text: 'Carte bancaire offerte' },
    { icon: '🌍', text: 'Transactions internationales' },
  ];

  constructor(private fb: FormBuilder, private onboarding: OnboardingService) {}

  ngOnInit(): void {
    this.form = this.fb.group({
      email:           ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
      password:        ['', [Validators.required, Validators.minLength(8),
                             Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&]).*$/)]],
      confirmPassword: ['', [Validators.required]],
    }, { validators: passwordsMatch });
  }

  get f() { return this.form.controls; }
  get mismatch() { return this.form.hasError('passwordMismatch') && this.form.get('confirmPassword')?.dirty; }

  get passwordStrength(): number {
    const pw = this.f['password'].value as string;
    if (!pw) return 0;
    let score = 0;
    if (pw.length >= 8)         score++;
    if (/[A-Z]/.test(pw))       score++;
    if (/\d/.test(pw))          score++;
    if (/[@$!%*?&]/.test(pw))   score++;
    return Math.max(1, score);
  }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.isLoading   = true;
    this.errorMessage = '';

    this.onboarding.submitRequest(this.form.value).subscribe({
      next: () => {
        this.isLoading = false;
        this.submitted = true;
      },
      error: err => {
        this.isLoading    = false;
        this.errorMessage = err.error?.message ?? 'Une erreur est survenue. Veuillez réessayer.';
      }
    });
  }
}
