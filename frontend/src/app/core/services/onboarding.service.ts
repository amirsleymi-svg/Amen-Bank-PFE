import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ApiResponse, PageResponse } from '../models/models';

export interface RegistrationRequestDto {
  email: string;
  confirmEmail: string;
}

export interface ActivateAccountRequest {
  token: string;
  password: string;
  confirmPassword: string;
}

export interface CreateAccountFromRequestDto {
  registrationRequestId: number;
  firstName: string;
  lastName: string;
  phoneNumber?: string;
  dateOfBirth?: string;
  address?: string;
}

export interface RegistrationRequestResponse {
  id: number;
  email: string;
  status: 'PENDING' | 'APPROVED' | 'REJECTED';
  rejectionReason?: string;
  reviewedBy?: string;
  reviewedAt?: string;
  ipAddress?: string;
  createdAt: string;
}

@Injectable({ providedIn: 'root' })
export class OnboardingService {

  private readonly API = environment.apiUrl + '/onboarding';

  constructor(private http: HttpClient) {}

  // ─── Public ──────────────────────────────────────────────────────
  submitRequest(dto: RegistrationRequestDto): Observable<ApiResponse<RegistrationRequestResponse>> {
    return this.http.post<ApiResponse<RegistrationRequestResponse>>(`${this.API}/register`, dto);
  }

  activateAccount(dto: ActivateAccountRequest): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.API}/activate`, dto);
  }

  // ─── Admin ───────────────────────────────────────────────────────
  listRequests(page = 0, size = 20, status?: string): Observable<ApiResponse<PageResponse<RegistrationRequestResponse>>> {
    let url = `${this.API}/requests?page=${page}&size=${size}`;
    if (status) url += `&status=${status}`;
    return this.http.get<ApiResponse<PageResponse<RegistrationRequestResponse>>>(url);
  }

  createAccount(dto: CreateAccountFromRequestDto): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.API}/requests/create-account`, dto);
  }

  rejectRequest(id: number, reason: string): Observable<ApiResponse<void>> {
    return this.http.patch<ApiResponse<void>>(
      `${this.API}/requests/${id}/reject?reason=${encodeURIComponent(reason)}`, {}
    );
  }
}
