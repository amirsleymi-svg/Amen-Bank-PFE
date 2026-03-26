import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, AbstractControl, ValidationErrors } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

function passwordMatch(group: AbstractControl): ValidationErrors | null {
  const pw  = group.get('password')?.value;
  const cpw = group.get('confirmPassword')?.value;
  return pw && cpw && pw !== cpw ? { mismatch: true } : null;
}

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './register.component.html',
  styleUrls: ['../auth.styles.scss', './register.component.scss']
})
export class RegisterComponent implements OnInit {

  step1Form!: FormGroup;
  step2Form!: FormGroup;

  currentStep     = 1;
  isLoading       = false;
  showPassword    = false;
  errorMessage    = '';
  successMessage  = '';

  brandFeatures = [
    'Compte courant gratuit sans frais cachés',
    'Virements illimités 24h/24',
    'Double authentification (2FA) incluse',
    'Support client disponible 7j/7',
  ];

  strengthColors = ['#E24B4A', '#F09500', '#E8D000', '#1B7A4E'];
  strengthLabels = ['Très faible', 'Faible', 'Correct', 'Fort'];

  constructor(private fb: FormBuilder, private authService: AuthService) {}

  ngOnInit(): void {
    this.step1Form = this.fb.group({
      firstName:    ['', [Validators.required, Validators.maxLength(100)]],
      lastName:     ['', [Validators.required, Validators.maxLength(100)]],
      idCardNumber: ['', [Validators.required, Validators.maxLength(30)]],
      dateOfBirth:  [''],
      phoneNumber:  ['', [Validators.maxLength(20)]],
    });

    this.step2Form = this.fb.group({
      username: ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50),
                      Validators.pattern(/^[a-zA-Z0-9_.-]+$/)]],
      email:    ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(8),
                      Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&]).*$/)]],
      confirmPassword: ['', Validators.required],
      acceptTerms: [false, Validators.requiredTrue],
    }, { validators: passwordMatch });
  }

  get s1() { return this.step1Form.controls; }
  get s2() { return this.step2Form.controls; }

  get passwordStrength(): number {
    const pw = this.s2['password'].value as string;
    if (!pw) return 0;
    let score = 0;
    if (pw.length >= 8)  score++;
    if (/[A-Z]/.test(pw)) score++;
    if (/\d/.test(pw))  score++;
    if (/[@$!%*?&]/.test(pw)) score++;
    return Math.max(1, score);
  }

  nextStep(): void {
    if (this.currentStep === 1) {
      if (this.step1Form.invalid) { this.step1Form.markAllAsTouched(); return; }
      this.currentStep = 2;
      this.errorMessage = '';
    } else if (this.currentStep === 2) {
      if (this.step2Form.invalid) { this.step2Form.markAllAsTouched(); return; }
      this.submit();
    }
  }

  submit(): void {
    this.isLoading    = true;
    this.errorMessage = '';

    const payload = {
      ...this.step1Form.value,
      ...this.step2Form.value,
    };
    delete payload.confirmPassword;
    delete payload.acceptTerms;

    this.authService.register(payload).subscribe({
      next: () => {
        this.isLoading   = false;
        this.currentStep = 3;
      },
      error: err => {
        this.isLoading    = false;
        this.errorMessage = err.error?.message ?? 'Une erreur est survenue. Veuillez réessayer.';
      }
    });
  }
}
