import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { OnboardingService, RegistrationRequestResponse, CreateAccountFromRequestDto } from '../../../../core/services/onboarding.service';
import { PageResponse } from '../../../../core/models/models';

@Component({
  selector: 'app-onboarding-requests',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule],
  templateUrl: './onboarding-requests.component.html',
  styleUrls: ['./onboarding-requests.component.scss']
})
export class OnboardingRequestsComponent implements OnInit {

  requests: RegistrationRequestResponse[] = [];
  page = 0;
  size = 10;
  totalElements = 0;
  totalPages = 0;
  isLoading = false;
  statusFilter: string = 'PENDING';

  // For approval modal/form
  selectedRequest: RegistrationRequestResponse | null = null;
  approvalForm!: FormGroup;
  isProcessing = false;

  // For rejection
  rejectionReason = '';
  showRejectionModal = false;

  constructor(
    private onboarding: OnboardingService,
    private fb: FormBuilder
  ) {}

  ngOnInit(): void {
    this.loadRequests();
    this.initApprovalForm();
  }

  initApprovalForm(): void {
    this.approvalForm = this.fb.group({
      firstName: ['', [Validators.required, Validators.maxLength(100)]],
      lastName: ['', [Validators.required, Validators.maxLength(100)]],
      phoneNumber: ['', [Validators.maxLength(20)]],
      dateOfBirth: [''],
      address: ['']
    });
  }

  loadRequests(): void {
    this.isLoading = true;
    this.onboarding.listRequests(this.page, this.size, this.statusFilter).subscribe({
      next: (res) => {
        const data = res.data;
        this.requests = data.content;
        this.totalElements = data.totalElements;
        this.totalPages = data.totalPages;
        this.isLoading = false;
      },
      error: () => this.isLoading = false
    });
  }

  onFilterChange(status: string): void {
    this.statusFilter = status;
    this.page = 0;
    this.loadRequests();
  }

  onPageChange(p: number): void {
    this.page = p;
    this.loadRequests();
  }

  openApproval(req: RegistrationRequestResponse): void {
    this.selectedRequest = req;
    this.approvalForm.reset();
    this.showRejectionModal = false;
  }

  closeApproval(): void {
    this.selectedRequest = null;
  }

  approve(): void {
    if (this.approvalForm.invalid || !this.selectedRequest) {
      this.approvalForm.markAllAsTouched();
      return;
    }

    this.isProcessing = true;
    const dto: CreateAccountFromRequestDto = {
      registrationRequestId: this.selectedRequest.id,
      ...this.approvalForm.value
    };

    this.onboarding.createAccount(dto).subscribe({
      next: () => {
        this.isProcessing = false;
        this.selectedRequest = null;
        this.loadRequests();
        alert('Compte créé avec succès ! Un email d\'activation a été envoyé.');
      },
      error: (err) => {
        this.isProcessing = false;
        alert(err.error?.message ?? 'Échec de la création du compte.');
      }
    });
  }

  openRejection(req: RegistrationRequestResponse): void {
    this.selectedRequest = req;
    this.showRejectionModal = true;
    this.rejectionReason = '';
  }

  reject(): void {
    if (!this.selectedRequest || !this.rejectionReason.trim()) return;

    this.isProcessing = true;
    this.onboarding.rejectRequest(this.selectedRequest.id, this.rejectionReason).subscribe({
      next: () => {
        this.isProcessing = false;
        this.selectedRequest = null;
        this.showRejectionModal = false;
        this.loadRequests();
        alert('Demande rejetée.');
      },
      error: (err) => {
        this.isProcessing = false;
        alert(err.error?.message ?? 'Échec du rejet.');
      }
    });
  }
}
