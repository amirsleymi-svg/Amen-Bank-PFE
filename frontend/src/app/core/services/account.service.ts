import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  ApiResponse, Account, PageResponse, Transaction,
  TransferRequest, Transfer, CreditSimulationRequest,
  CreditSimulationResponse, CreditApplication
} from '../models/models';

@Injectable({ providedIn: 'root' })
export class AccountService {
  private readonly API = environment.apiUrl;

  constructor(private http: HttpClient) {}

  getAccounts(): Observable<ApiResponse<Account[]>> {
    return this.http.get<ApiResponse<Account[]>>(`${this.API}/accounts`);
  }

  getAccount(id: number): Observable<ApiResponse<Account>> {
    return this.http.get<ApiResponse<Account>>(`${this.API}/accounts/${id}`);
  }

  getTransactions(accountId: number, page = 0, size = 20, filters?: any): Observable<ApiResponse<PageResponse<Transaction>>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (filters?.from)   params = params.set('from', filters.from);
    if (filters?.to)     params = params.set('to', filters.to);
    if (filters?.type)   params = params.set('type', filters.type);
    if (filters?.status) params = params.set('status', filters.status);
    return this.http.get<ApiResponse<PageResponse<Transaction>>>(
      `${this.API}/accounts/${accountId}/transactions`, { params });
  }

  exportCsv(accountId: number, from?: string, to?: string): Observable<Blob> {
    let params = new HttpParams();
    if (from) params = params.set('from', from);
    if (to)   params = params.set('to', to);
    return this.http.get(`${this.API}/accounts/${accountId}/transactions/export`,
      { params, responseType: 'blob' });
  }
}

@Injectable({ providedIn: 'root' })
export class TransferService {
  private readonly API = environment.apiUrl;

  constructor(private http: HttpClient) {}

  initiateTransfer(payload: TransferRequest): Observable<ApiResponse<Transfer>> {
    return this.http.post<ApiResponse<Transfer>>(`${this.API}/transfers`, payload);
  }

  getTransfers(page = 0, size = 20): Observable<ApiResponse<PageResponse<Transfer>>> {
    return this.http.get<ApiResponse<PageResponse<Transfer>>>(
      `${this.API}/transfers`, { params: { page, size } });
  }

  cancelTransfer(id: number): Observable<ApiResponse<void>> {
    return this.http.delete<ApiResponse<void>>(`${this.API}/transfers/${id}`);
  }

  uploadBatch(file: File, fromAccountId: number, totpCode: string): Observable<ApiResponse<any>> {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('fromAccountId', fromAccountId.toString());
    fd.append('totpCode', totpCode);
    return this.http.post<ApiResponse<any>>(`${this.API}/transfers/batch`, fd);
  }

  downloadTemplate(): Observable<Blob> {
    return this.http.get(`${this.API}/transfers/batch/template`, { responseType: 'blob' });
  }
}

@Injectable({ providedIn: 'root' })
export class CreditService {
  private readonly API = environment.apiUrl;

  constructor(private http: HttpClient) {}

  simulate(payload: CreditSimulationRequest): Observable<ApiResponse<CreditSimulationResponse>> {
    return this.http.post<ApiResponse<CreditSimulationResponse>>(`${this.API}/credits/simulate`, payload);
  }

  apply(payload: any): Observable<ApiResponse<CreditApplication>> {
    return this.http.post<ApiResponse<CreditApplication>>(`${this.API}/credits/apply`, payload);
  }

  getApplications(page = 0, size = 10): Observable<ApiResponse<PageResponse<CreditApplication>>> {
    return this.http.get<ApiResponse<PageResponse<CreditApplication>>>(
      `${this.API}/credits`, { params: { page, size } });
  }
}
