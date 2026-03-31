import { Component, OnInit } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule, ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { HttpClient, HttpParams } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { OnboardingRequestsComponent } from './components/onboarding-requests/onboarding-requests.component';

@Component({
  selector: 'app-admin',
  standalone: true,
  imports: [CommonModule, FormsModule, ReactiveFormsModule, RouterModule, DatePipe, DecimalPipe, OnboardingRequestsComponent],
  templateUrl: './admin.component.html',
  styleUrls: ['./admin.component.scss']
})
export class AdminComponent implements OnInit {

  activeView: 'dashboard' | 'kyc' | 'users' | 'credits' | 'audit' | 'onboarding' | 'roles' = 'dashboard';

  stats: any        = null;
  kycList: any[]    = [];
  userList: any[]   = [];
  creditList: any[] = [];
  auditList: any[]  = [];
  adminList: any[]  = [];
  roleList: any[]   = [];

  tableLoading = false;
  now = new Date();

  // Filters
  kycFilter        = 'PENDING';
  userSearch       = '';
  userStatusFilter = 'ACTIVE';
  creditFilter     = 'PENDING';
  auditSearch      = '';

  // Pagination
  userPage  = 0;  userTotalPages  = 1;
  auditPage = 0;  auditTotalPages = 1;
  adminPage = 0;  adminTotalPages = 1;

  // Reject modal
  rejectModalOpen = false;
  rejectTarget: { type: string; id: number } | null = null;
  rejectReason = '';

  // Create admin modal
  createAdminModalOpen = false;
  createAdminLoading   = false;
  createAdminError     = '';
  createAdminForm!: FormGroup;

  // Change role modal
  changeRoleModalOpen = false;
  changeRoleTarget: any = null;
  changeRoleValue = 'ADMIN';

  readonly ADMIN_ROLES = ['ADMIN'];

  private readonly API = environment.apiUrl + '/admin';

  constructor(private http: HttpClient, private fb: FormBuilder) {}

  ngOnInit(): void {
    this.loadStats();
    this.createAdminForm = this.fb.group({
      username:  ['', [Validators.required, Validators.minLength(3), Validators.maxLength(50)]],
      email:     ['', [Validators.required, Validators.email]],
      password:  ['', [Validators.required, Validators.minLength(8)]],
      firstName: ['', Validators.required],
      lastName:  ['', Validators.required],
      role:      ['ADMIN', Validators.required]
    });
  }

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

  // ── Gestion des rôles / admins ────────────────────────────────
  loadRoles(): void {
    this.activeView = 'roles';
    this.tableLoading = true;
    this.http.get<any>(`${this.API}/roles`).subscribe({
      next: r => { this.roleList = r.data; this.tableLoading = false; }
    });
    this.loadAdmins();
  }

  loadAdmins(): void {
    const params = new HttpParams().set('page', this.adminPage).set('size', 20);
    this.http.get<any>(`${this.API}/admins`, { params }).subscribe({
      next: r => {
        this.adminList       = r.data.content;
        this.adminTotalPages = r.data.totalPages;
      }
    });
  }

  changeAdminPage(p: number): void { this.adminPage = p; this.loadAdmins(); }

  // Create admin modal
  openCreateAdminModal(): void {
    this.createAdminForm.reset({ role: 'ADMIN' });
    this.createAdminError   = '';
    this.createAdminLoading = false;
    this.createAdminModalOpen = true;
  }

  closeCreateAdminModal(): void { this.createAdminModalOpen = false; }

  submitCreateAdmin(): void {
    if (this.createAdminForm.invalid) {
      this.createAdminForm.markAllAsTouched();
      return;
    }
    this.createAdminLoading = true;
    this.createAdminError   = '';
    this.http.post<any>(`${this.API}/admins`, this.createAdminForm.value).subscribe({
      next: () => {
        this.createAdminLoading   = false;
        this.createAdminModalOpen = false;
        this.loadAdmins();
      },
      error: err => {
        this.createAdminLoading = false;
        this.createAdminError   = err?.error?.message || 'Erreur lors de la création';
      }
    });
  }

  // Change role modal
  openChangeRoleModal(admin: any): void {
    this.changeRoleTarget   = admin;
    this.changeRoleValue    = admin.role;
    this.changeRoleModalOpen = true;
  }

  closeChangeRoleModal(): void { this.changeRoleModalOpen = false; this.changeRoleTarget = null; }

  confirmChangeRole(): void {
    if (!this.changeRoleTarget) return;
    this.http.patch<any>(`${this.API}/admins/${this.changeRoleTarget.id}/role`, null,
      { params: { role: this.changeRoleValue } }
    ).subscribe({ next: () => { this.closeChangeRoleModal(); this.loadAdmins(); } });
  }

  toggleAdmin(admin: any): void {
    const action = admin.active ? 'désactiver' : 'activer';
    if (!confirm(`Voulez-vous ${action} cet administrateur ?`)) return;
    this.http.patch<any>(`${this.API}/admins/${admin.id}/status`, null,
      { params: { active: String(!admin.active) } }
    ).subscribe({ next: () => this.loadAdmins() });
  }

  // ── Reject modal ──────────────────────────────────────────────
  openRejectModal(type: string, id: number): void {
    this.rejectTarget    = { type, id };
    this.rejectReason    = '';
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
  adminRoleLabel(r: string): string {
    return r === 'ADMIN' ? 'Administrateur' : r;
  }
  fieldError(field: string): boolean {
    const c = this.createAdminForm.get(field);
    return !!(c && c.invalid && c.touched);
  }
}
