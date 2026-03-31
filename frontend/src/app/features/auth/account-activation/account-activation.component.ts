import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, RouterModule } from '@angular/router';
import { OnboardingService } from '../../../core/services/onboarding.service';

@Component({
  selector: 'app-account-activation',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './account-activation.component.html',
  styleUrls: ['../../auth/auth.styles.scss', './account-activation.component.scss']
})
export class AccountActivationComponent implements OnInit {

  isLoading    = true;
  success      = false;
  errorMessage = '';

  constructor(
    private route: ActivatedRoute,
    private onboarding: OnboardingService
  ) {}

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');

    if (!token) {
      this.isLoading    = false;
      this.errorMessage = 'Lien d\'activation invalide ou manquant.';
      return;
    }

    this.onboarding.activateAccount({ token }).subscribe({
      next: () => {
        this.isLoading = false;
        this.success   = true;
      },
      error: err => {
        this.isLoading    = false;
        this.errorMessage = err.error?.message ?? 'Échec de l\'activation. Le lien a peut-être expiré.';
      }
    });
  }
}
