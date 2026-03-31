import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { Observable, tap, catchError, throwError } from 'rxjs';
import { environment } from '../../../environments/environment';
import {
  AuthResponse, LoginRequest, RegisterRequest,
  TotpVerifyRequest, User, ApiResponse
} from '../models/models';

@Injectable({ providedIn: 'root' })
export class AuthService {

  private readonly API = environment.apiUrl + '/auth';
  private readonly ACCESS_KEY  = 'ab_access_token';
  private readonly REFRESH_KEY = 'ab_refresh_token';
  private readonly USER_KEY    = 'ab_user';

  // ─── Reactive state (Angular Signals) ────────────────────────────
  private _currentUser = signal<User | null>(this.loadUser());
  private _isLoading   = signal(false);

  readonly currentUser  = this._currentUser.asReadonly();
  readonly isLoading    = this._isLoading.asReadonly();
  readonly isLoggedIn   = computed(() => !!this._currentUser());
  readonly isAdmin      = computed(() =>
    this._currentUser()?.roles?.some(r =>
      r === 'ROLE_ADMIN') ?? false
  );

  constructor(private http: HttpClient, private router: Router) {}

  // ─── Register ─────────────────────────────────────────────────────
  register(payload: RegisterRequest): Observable<ApiResponse<User>> {
    return this.http.post<ApiResponse<User>>(`${this.API}/register`, payload);
  }

  // ─── Login ────────────────────────────────────────────────────────
  login(payload: LoginRequest): Observable<ApiResponse<AuthResponse>> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.API}/login`, payload).pipe(
      tap(res => {
        const auth = res.data;
        if (auth.totpRequired) return; // caller handles 2FA step
        this.storeTokens(auth);
      })
    );
  }

  // ─── Verify TOTP ──────────────────────────────────────────────────
  verifyTotp(payload: TotpVerifyRequest): Observable<ApiResponse<AuthResponse>> {
    return this.http.post<ApiResponse<AuthResponse>>(`${this.API}/totp/verify`, payload).pipe(
      tap(res => this.storeTokens(res.data))
    );
  }

  // ─── Refresh token ────────────────────────────────────────────────
  refreshToken(): Observable<ApiResponse<AuthResponse>> {
    const refreshToken = this.getRefreshToken();
    if (!refreshToken) return throwError(() => new Error('No refresh token'));

    return this.http.post<ApiResponse<AuthResponse>>(
      `${this.API}/refresh`, { refreshToken }
    ).pipe(
      tap(res => this.storeTokens(res.data)),
      catchError(err => {
        this.logout();
        return throwError(() => err);
      })
    );
  }

  // ─── Logout ───────────────────────────────────────────────────────
  logout(): void {
    const refreshToken = this.getRefreshToken();
    if (refreshToken) {
      this.http.post(`${this.API}/logout`, { refreshToken }).subscribe({ error: () => {} });
    }
    this.clearStorage();
    this._currentUser.set(null);
    this.router.navigate(['/auth/login']);
  }

  // ─── Setup TOTP ───────────────────────────────────────────────────
  setupTotp(): Observable<ApiResponse<any>> {
    return this.http.post<ApiResponse<any>>(`${this.API}/totp/setup`, {});
  }

  enableTotp(totpCode: string): Observable<ApiResponse<string[]>> {
    return this.http.post<ApiResponse<string[]>>(`${this.API}/totp/enable?totpCode=${totpCode}`, {});
  }

  disableTotp(totpCode: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.API}/totp/disable?totpCode=${totpCode}`, {});
  }

  changePassword(currentPassword: string, newPassword: string, totpCode: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.API}/password/change`,
      { currentPassword, newPassword, totpCode });
  }

  revokeAllSessions(): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.API}/sessions/revoke-all`, {});
  }

  // ─── Password ─────────────────────────────────────────────────────
  forgotPassword(email: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.API}/password/forgot`, { email });
  }

  resetPassword(token: string, newPassword: string): Observable<ApiResponse<void>> {
    return this.http.post<ApiResponse<void>>(`${this.API}/password/reset`, { token, newPassword });
  }

  // ─── Me ───────────────────────────────────────────────────────────
  getCurrentUser(): Observable<ApiResponse<User>> {
    return this.http.get<ApiResponse<User>>(`${this.API}/me`).pipe(
      tap(res => {
        this._currentUser.set(res.data);
        sessionStorage.setItem(this.USER_KEY, JSON.stringify(res.data));
      })
    );
  }

  // ─── Token helpers ────────────────────────────────────────────────
  getAccessToken(): string | null {
    return localStorage.getItem(this.ACCESS_KEY);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_KEY);
  }

  private storeTokens(auth: AuthResponse): void {
    if (auth.accessToken)  localStorage.setItem(this.ACCESS_KEY, auth.accessToken);
    if (auth.refreshToken) localStorage.setItem(this.REFRESH_KEY, auth.refreshToken);
    if (auth.user) {
      this._currentUser.set(auth.user);
      sessionStorage.setItem(this.USER_KEY, JSON.stringify(auth.user));
    }
  }

  private clearStorage(): void {
    localStorage.removeItem(this.ACCESS_KEY);
    localStorage.removeItem(this.REFRESH_KEY);
    sessionStorage.removeItem(this.USER_KEY);
  }

  private loadUser(): User | null {
    try {
      const raw = sessionStorage.getItem(this.USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch { return null; }
  }
}
