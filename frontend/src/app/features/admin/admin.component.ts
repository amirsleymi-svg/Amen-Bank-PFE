import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, DatePipe, DecimalPipe],
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.scss']
})
export class AdminComponent implements OnInit {

  activeView: 'dashboard' | 'kyc' | 'users' | 'credits' | 'audit' = 'dashboard';

  stats: any       = null;
  kycList: any[]   = [];
  userList: any[]  = [];
  creditList: any[]= [];
  auditList: any[] = [];

  tableLoading = false;
  now = new Date();

  // Filters
  kycFilter        = 'PENDING';
  userSearch       = '';
  userStatusFilter = 'ACTIVE';
  creditFilter     = 'PENDING';
  auditSearch      = '';

  // Pagination
  userPage = 0;   userTotalPages  = 1;
  auditPage = 0;  auditTotalPages = 1;

  // Reject modal
  rejectModalOpen = false;
  rejectTarget: { type: string; id: number } | null = null;
  rejectReason = '';

  private readonly API = environment.apiUrl + '/admin';

  constructor(private http: HttpClient) {}

  ngOnInit(): void { this.loadStats(); }

  // ── Stats ─────────────────────────────────────────────────────
  loadStats(): void {
    this.http.get<any>(`${this.API}/dashboard`).subscribe({
      next: r => { this.stats = r.data; this.activeView = 'dashboard'; }
    });
  }

  // ── KYC ──────────────────────────────────────────────────────
  loadKyc(): void {
    this.activeView = 'kyc';
    this.tableLoading = true;
    this.http.get<any>(`${this.API}/kyc`, { params: { status: this.kycFilter, page: 0, size: 50 } })
      .subscribe({ next: r => { this.kycList = r.data.content; this.tableLoading = false; } });
  }

  approveKyc(id: number): void {
    this.http.patch<any>(`${this.API}/kyc/${id}/approve`, {}).subscribe({ next: () => this.loadKyc() });
  }

  // ── Users ─────────────────────────────────────────────────────
  loadUsers(): void {
    this.activeView = 'users';
    this.tableLoading = true;
    let params = new HttpParams()
      .set('page', this.userPage).set('size', 20)
      .set('status', this.userStatusFilter);
    if (this.userSearch) params = params.set('search', this.userSearch);

    this.http.get<any>(`${this.API}/users`, { params }).subscribe({
      next: r => {
        this.userList       = r.data.content;
        this.userTotalPages = r.data.totalPages;
        this.tableLoading   = false;
      }
    });
  }

  changeUserPage(p: number): void { this.userPage = p; this.loadUsers(); }

  suspendUser(id: number): void {
    if (!confirm('Suspendre cet utilisateur ?')) return;
    this.http.patch<any>(`${this.API}/users/${id}/status`, null, { params: { status: 'SUSPENDED' } })
      .subscribe({ next: () => this.loadUsers() });
  }

  activateUser(id: number): void {
    this.http.patch<any>(`${this.API}/users/${id}/status`, null, { params: { status: 'ACTIVE' } })
      .subscribe({ next: () => this.loadUsers() });
  }

  viewUser(u: any): void {
    alert(`Utilisateur : ${u.firstName} ${u.lastName}\nEmail : ${u.email}\nStatut : ${u.status}`);
  }

  // ── Credits ───────────────────────────────────────────────────
  loadCredits(): void {
    this.activeView = 'credits';
    this.tableLoading = true;
    const params: any = { page: 0, size: 50 };
    if (this.creditFilter) params.status = this.creditFilter;
    this.http.get<any>(`${this.API}/credits`, { params }).subscribe({
      next: r => { this.creditList = r.data.content; this.tableLoading = false; }
    });
  }

  approveCredit(id: number): void {
    this.http.patch<any>(`${this.API}/credits/${id}/status`, null, { params: { status: 'APPROVED' } })
      .subscribe({ next: () => this.loadCredits() });
  }

  // ── Audit logs ────────────────────────────────────────────────
  loadAuditLogs(): void {
    this.activeView = 'audit';
    this.tableLoading = true;
    let params = new HttpParams().set('page', this.auditPage).set('size', 50);
    if (this.auditSearch) params = params.set('action', this.auditSearch);
    this.http.get<any>(`${this.API}/audit-logs`, { params }).subscribe({
      next: r => {
        this.auditList       = r.data.content;
        this.auditTotalPages = r.data.totalPages;
        this.tableLoading    = false;
      }
    });
  }

  changeAuditPage(p: number): void { this.auditPage = p; this.loadAuditLogs(); }

  // ── Reject modal ──────────────────────────────────────────────
  openRejectModal(type: string, id: number): void {
    this.rejectTarget   = { type, id };
    this.rejectReason   = '';
    this.rejectModalOpen = true;
  }

  closeRejectModal(): void { this.rejectModalOpen = false; this.rejectTarget = null; }

  confirmReject(): void {
    if (!this.rejectTarget) return;
    const { type, id } = this.rejectTarget;

    if (type === 'kyc') {
      this.http.patch<any>(`${this.API}/kyc/${id}/reject`, null, {
        params: { reason: this.rejectReason || 'Documents insuffisants' }
      }).subscribe({ next: () => { this.closeRejectModal(); this.loadKyc(); } });
    } else {
      this.http.patch<any>(`${this.API}/credits/${id}/status`, null, {
        params: { status: 'REJECTED', reason: this.rejectReason || 'Demande refusée' }
      }).subscribe({ next: () => { this.closeRejectModal(); this.loadCredits(); } });
    }
  }

  // ── Labels ────────────────────────────────────────────────────
  statusLabel(s: string): string {
    return ({ PENDING:'En attente', REVIEWING:'En examen', APPROVED:'Approuvé', REJECTED:'Rejeté' } as any)[s] ?? s;
  }
  userStatusLabel(s: string): string {
    return ({ ACTIVE:'Actif', PENDING:'En attente', SUSPENDED:'Suspendu', DELETED:'Supprimé' } as any)[s] ?? s;
  }
  creditStatusLabel(s: string): string {
    return ({ PENDING:'En attente', REVIEWING:'En examen', APPROVED:'Approuvé', REJECTED:'Rejeté', DISBURSED:'Décaissé' } as any)[s] ?? s;
  }
  creditTypeLabel(t: string): string {
    return ({ PERSONAL:'Personnel', MORTGAGE:'Immobilier', AUTO:'Auto', BUSINESS:'Entreprise', STUDENT:'Étudiant' } as any)[t] ?? t;
  }
}
