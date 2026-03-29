import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { OnboardingService } from '../../../core/services/onboarding.service';

function emailsMatch(group: AbstractControl): ValidationErrors | null {
  const email = group.get('email')?.value;
  const confirm = group.get('confirmEmail')?.value;
  return email && confirm && email.toLowerCase() !== confirm.toLowerCase()
    ? { emailMismatch: true }
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
  isLoading    = false;
  submitted    = false;
  errorMessage = '';

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
      email:        ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
      confirmEmail: ['', [Validators.required, Validators.email, Validators.maxLength(150)]],
    }, { validators: emailsMatch });
  }

  get f() { return this.form.controls; }
  get mismatch() { return this.form.hasError('emailMismatch') && this.form.get('confirmEmail')?.dirty; }

  submit(): void {
    if (this.form.invalid) { this.form.markAllAsTouched(); return; }
    this.isLoading = true;
    this.errorMessage = '';

    this.onboarding.submitRequest(this.form.value).subscribe({
      next: () => {
        this.isLoading = false;
        this.submitted = true;
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.message ?? 'Une erreur est survenue. Veuillez réessayer.';
      }
    });
  }
}
