import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { OnboardingService } from '../../../core/services/onboarding.service';

function passwordsMatch(group: AbstractControl): ValidationErrors | null {
  const pass = group.get('password')?.value;
  const confirm = group.get('confirmPassword')?.value;
  return pass && confirm && pass !== confirm
    ? { passwordMismatch: true }
    : null;
}

@Component({
  selector: 'app-account-activation',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './account-activation.component.html',
  styleUrls: ['../../auth/auth.styles.scss', './account-activation.component.scss']
})
export class AccountActivationComponent implements OnInit {

  form!: FormGroup;
  token: string | null = null;
  isLoading = false;
  success = false;
  errorMessage = '';

  passwordRequirements = [
    { label: 'Au moins 8 caractères', met: false, regex: /^.{8,}$/ },
    { label: 'Une majuscule (A-Z)', met: false, regex: /[A-Z]/ },
    { label: 'Une minuscule (a-z)', met: false, regex: /[a-z]/ },
    { label: 'Un chiffre (0-9)', met: false, regex: /[0-9]/ },
    { label: 'Un caractère spécial (@$!%*?&)', met: false, regex: /[@$!%*?&]/ },
  ];

  constructor(
    private fb: FormBuilder,
    private route: ActivatedRoute,
    private router: Router,
    private onboarding: OnboardingService
  ) {}

  ngOnInit(): void {
    this.token = this.route.snapshot.queryParamMap.get('token');
    
    if (!this.token) {
      this.errorMessage = 'Lien d\'activation invalide ou manquant.';
    }

    this.form = this.fb.group({
      password: ['', [Validators.required, Validators.minLength(8)]],
      confirmPassword: ['', [Validators.required]],
    }, { validators: passwordsMatch });

    this.form.get('password')?.valueChanges.subscribe(val => {
      this.passwordRequirements.forEach(req => {
        req.met = req.regex.test(val);
      });
    });
  }

  get f() { return this.form.controls; }
  get mismatch() { return this.form.hasError('passwordMismatch') && this.form.get('confirmPassword')?.dirty; }

  activate(): void {
    if (this.form.invalid || !this.token) {
      this.form.markAllAsTouched();
      return;
    }

    this.isLoading = true;
    this.errorMessage = '';

    const payload = {
      token: this.token,
      password: this.form.get('password')?.value,
      confirmPassword: this.form.get('confirmPassword')?.value
    };

    this.onboarding.activateAccount(payload).subscribe({
      next: () => {
        this.isLoading = false;
        this.success = true;
      },
      error: err => {
        this.isLoading = false;
        this.errorMessage = err.error?.message ?? 'Échec de l\'activation. Le lien a peut-être expiré.';
      }
    });
  }
}
