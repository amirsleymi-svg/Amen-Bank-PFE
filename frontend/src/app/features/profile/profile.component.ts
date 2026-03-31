import {
  Component, OnInit, ViewChildren, QueryList, ElementRef
} from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { User } from '../../core/models/models';

@Component({
  selector: 'app-profile',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, DatePipe],
  templateUrl: './profile.component.html',
  styleUrls: ['./profile.component.scss']
})
export class ProfileComponent implements OnInit {

  @ViewChildren('verifyDigit') verifyInputs!: QueryList<ElementRef<HTMLInputElement>>;
  @ViewChildren('pwDigit')     pwInputs!:     QueryList<ElementRef<HTMLInputElement>>;

  user: User | null = null;
  activeSection: 'password' | '2fa' | 'sessions' = '2fa';
  globalSuccess = '';

  // ── 2FA setup ──────────────────────────────────────────────────
  totpStep      = 0;   // 0=intro 1=qr 2=verify 3=backup codes
  setupMode     = false;
  setupLoading  = false;
  disabling2fa  = false;
  totpSetupData: any = null;
  verifyDigits  = ['','','','','',''];
  totpError     = '';
  shaking       = false;
  totpEnableLoading = false;
  backupCodes: string[] = [];
  copied        = false;

  // ── Password change ─────────────────────────────────────────────
  pwForm!:       FormGroup;
  pwLoading      = false;
  pwError        = '';
  pwSuccess      = '';
  showCurrent    = false;
  showNew        = false;
  pwTotpDigits   = ['','','','','',''];

  constructor(
    private fb: FormBuilder,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.user = this.authService.currentUser();
    this.authService.getCurrentUser().subscribe({
      next: r => this.user = r.data
    });

    this.pwForm = this.fb.group({
      currentPassword: ['', Validators.required],
      newPassword: ['', [Validators.required, Validators.minLength(8),
        Validators.pattern(/^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[@$!%*?&]).{8,}$/)]]
    });
  }

  // ─── 2FA Setup ──────────────────────────────────────────────────
  startSetup(): void {
    this.setupLoading = true;
    this.authService.setupTotp().subscribe({
      next: r => {
        this.setupLoading  = false;
        this.totpSetupData = r.data;
        this.totpStep      = 1;
      },
      error: () => { this.setupLoading = false; }
    });
  }

  confirmEnable(): void {
    if (this.verifyCode.length < 6) return;
    this.totpEnableLoading = true;
    this.totpError = '';

    this.authService.enableTotp(this.verifyCode).subscribe({
      next: res => {
        this.totpEnableLoading = false;
        this.backupCodes = res.data ?? []; // real server-generated codes
        this.totpStep = 3;
      },
      error: err => {
        this.totpEnableLoading = false;
        this.totpError = err.error?.message ?? 'Code incorrect. Réessayez.';
        this.triggerShake();
        this.clearVerifyDigits();
      }
    });
  }

  disable2FA(): void {
    const code = prompt('Entrez votre code TOTP pour désactiver la 2FA :');
    if (!code) return;
    this.authService.disableTotp(code).subscribe({
      next: () => {
        this.disabling2fa  = false;
        this.globalSuccess = '2FA désactivée.';
        this.reloadUser();
      },
      error: err => {
        this.globalSuccess = '';
        alert(err.error?.message ?? 'Échec de la désactivation.');
      }
    });
  }

  reloadUser(): void {
    this.authService.getCurrentUser().subscribe({ next: r => this.user = r.data });
  }

  // ─── Password change ────────────────────────────────────────────
  changePassword(): void {
    if (this.pwForm.invalid) return;
    const needsTotp = this.user?.totpEnabled;
    if (needsTotp && this.pwTotpCode.length < 6) return;
    this.pwLoading = true;
    this.pwError   = '';
    this.pwSuccess = '';

    this.authService.changePassword(
      this.pwForm.value.currentPassword,
      this.pwForm.value.newPassword,
      this.pwTotpCode
    ).subscribe({
      next: () => {
        this.pwLoading  = false;
        this.pwSuccess  = 'Mot de passe modifié avec succès.';
        this.pwForm.reset();
        this.clearPwTotpDigits();
      },
      error: err => {
        this.pwLoading = false;
        this.pwError   = err.error?.message ?? 'Erreur lors de la modification.';
      }
    });
  }

  revokeAllSessions(): void {
    if (!confirm('Déconnecter tous les autres appareils ?')) return;
    this.authService.revokeAllSessions().subscribe({
      next: () => { this.globalSuccess = 'Toutes les sessions ont été révoquées.'; }
    });
  }

  // ─── Digit inputs: verify ────────────────────────────────────────
  get verifyCode(): string { return this.verifyDigits.join(''); }

  onVerifyInput(e: Event, i: number): void {
    const el = e.target as HTMLInputElement;
    el.value = el.value.replace(/\D/,'').slice(-1);
    this.verifyDigits[i] = el.value;
    if (el.value && i < 5) this.verifyInputs.get(i+1)?.nativeElement.focus();
  }

  onVerifyKeydown(e: KeyboardEvent, i: number): void {
    if (e.key==='Backspace') {
      const el = e.target as HTMLInputElement;
      if (!el.value && i>0) {
        this.verifyDigits[i-1]='';
        this.verifyInputs.get(i-1)?.nativeElement.focus();
      } else { this.verifyDigits[i]=''; }
    }
  }

  onVerifyPaste(e: ClipboardEvent): void {
    e.preventDefault();
    const digits = (e.clipboardData?.getData('text')??'').replace(/\D/g,'').slice(0,6).split('');
    digits.forEach((d,i)=>{ this.verifyDigits[i]=d; const el=this.verifyInputs.get(i)?.nativeElement; if(el) el.value=d; });
  }

  private clearVerifyDigits(): void {
    this.verifyDigits=['','','','','',''];
    this.verifyInputs?.forEach(el=>el.nativeElement.value='');
  }

  // ─── Digit inputs: password TOTP ────────────────────────────────
  get pwTotpCode(): string { return this.pwTotpDigits.join(''); }

  onPwTotpInput(e: Event, i: number): void {
    const el = e.target as HTMLInputElement;
    el.value = el.value.replace(/\D/,'').slice(-1);
    this.pwTotpDigits[i]=el.value;
    if(el.value && i<5) this.pwInputs.get(i+1)?.nativeElement.focus();
  }

  onPwTotpKeydown(e: KeyboardEvent, i: number): void {
    if(e.key==='Backspace'){
      const el=e.target as HTMLInputElement;
      if(!el.value && i>0){ this.pwTotpDigits[i-1]=''; this.pwInputs.get(i-1)?.nativeElement.focus(); }
      else { this.pwTotpDigits[i]=''; }
    }
  }

  onPwTotpPaste(e: ClipboardEvent): void {
    e.preventDefault();
    const d=(e.clipboardData?.getData('text')??'').replace(/\D/g,'').slice(0,6).split('');
    d.forEach((v,i)=>{ this.pwTotpDigits[i]=v; const el=this.pwInputs.get(i)?.nativeElement; if(el) el.value=v; });
  }

  private clearPwTotpDigits(): void {
    this.pwTotpDigits=['','','','','',''];
    this.pwInputs?.forEach(el=>el.nativeElement.value='');
  }

  // ─── Helpers ─────────────────────────────────────────────────────
  get pwStrength(): number {
    const pw = this.pwForm.value.newPassword ?? '';
    let s=0;
    if(pw.length>=8) s++;
    if(/[A-Z]/.test(pw)) s++;
    if(/\d/.test(pw)) s++;
    if(/[@$!%*?&]/.test(pw)) s++;
    return Math.max(1,s);
  }

  formatSecret(s: string): string {
    return s?.match(/.{1,4}/g)?.join(' ')??s;
  }

  copySecret(): void {
    navigator.clipboard.writeText(this.totpSetupData?.secret??'');
    this.copied=true; setTimeout(()=>this.copied=false, 2000);
  }

  downloadBackupCodes(): void {
    const txt = 'Amen Bank — Codes de secours\n\n'+this.backupCodes.join('\n');
    const a=document.createElement('a');
    a.href='data:text/plain;charset=utf-8,'+encodeURIComponent(txt);
    a.download='amenbank-backup-codes.txt'; a.click();
  }

  private triggerShake(): void {
    this.shaking=true; setTimeout(()=>this.shaking=false,600);
  }

  roleLabel(role: string): string {
    return ({ ROLE_USER:'Utilisateur', ROLE_ADMIN:'Administrateur' } as any)[role] ?? role;
  }
}
