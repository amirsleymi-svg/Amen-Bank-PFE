import { Component, OnInit } from '@angular/core';
import { CommonModule, DecimalPipe, DatePipe } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators, FormsModule } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { AccountService, TransferService } from '../../core/services/account.service';
import { Account, Transfer } from '../../core/models/models';
import { TotpModalComponent } from '../../shared/components/totp-modal/totp-modal.component';

@Component({
  selector: 'app-transfers',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, FormsModule,
    RouterModule, DecimalPipe, DatePipe,
    TotpModalComponent
  ],
  templateUrl: './transfers.component.html',
  styleUrls: ['./transfers.component.scss']
})
export class TransfersComponent implements OnInit {

  activeTab: 'simple' | 'batch' | 'history' = 'simple';

  // ── Form state ─────────────────────────────────────────────────
  transferForm!: FormGroup;
  accounts: Account[]  = [];
  isLoading            = false;
  formError            = '';
  transferSuccess      = false;
  lastTransfer: Transfer | null = null;
  dailyUsed            = 0;

  // ── TOTP modal state ───────────────────────────────────────────
  totpModalOpen = false;
  totpLoading   = false;
  totpError     = '';
  totpSummary:  Array<{ label: string; value: string; highlight?: boolean }> = [];

  // ── Batch state ────────────────────────────────────────────────
  batchFile: File | null = null;
  batchResult: any       = null;
  batchLoading           = false;
  isDragging             = false;
  batchAccountId: number | null = null;

  // ── History state ──────────────────────────────────────────────
  transfers:      Transfer[] = [];
  historyFilter   = '';
  currentPage     = 0;
  totalPages      = 1;

  tomorrow = (() => {
    const d = new Date();
    d.setDate(d.getDate() + 1);
    return d.toISOString().split('T')[0];
  })();

  constructor(
    private fb: FormBuilder,
    private accountService: AccountService,
    private transferService: TransferService
  ) {}

  ngOnInit(): void {
    this.buildForm();
    this.loadAccounts();
  }

  // ── Build form ─────────────────────────────────────────────────
  buildForm(): void {
    this.transferForm = this.fb.group({
      fromAccountId: ['', Validators.required],
      toName:        ['', [Validators.required, Validators.maxLength(150)]],
      toIban:        ['', [Validators.required,
                           Validators.pattern(/^[A-Z]{2}[0-9]{2}[A-Z0-9]{4,30}$/)]],
      amount:        [null, [Validators.required, Validators.min(0.001)]],
      label:         [''],
      scheduledDate: ['']
    });
  }

  get tf() { return this.transferForm.controls; }

  get filteredTransfers(): Transfer[] {
    return this.historyFilter
      ? this.transfers.filter(t => t.status === this.historyFilter)
      : this.transfers;
  }

  // ── Load accounts ──────────────────────────────────────────────
  loadAccounts(): void {
    this.accountService.getAccounts().subscribe({
      next: res => {
        this.accounts = res.data.filter(a => a.status === 'ACTIVE');
        if (this.accounts.length) {
          this.tf['fromAccountId'].setValue(this.accounts[0].id);
          this.batchAccountId = this.accounts[0].id;
        }
      }
    });
  }

  // ─────────────────────────────────────────────────────────────
  // STEP 1 — User clicks "Procéder au virement"
  //          → validate form, build summary, open TOTP modal
  // ─────────────────────────────────────────────────────────────
  openTotpModal(): void {
    if (this.transferForm.invalid) {
      this.transferForm.markAllAsTouched();
      return;
    }

    const v = this.transferForm.value;
    const selectedAccount = this.accounts.find(a => a.id == v.fromAccountId);

    // Build the operation summary shown inside the modal
    this.totpSummary = [
      { label: 'De',       value: selectedAccount?.accountNumber ?? '—' },
      { label: 'Vers',     value: v.toName },
      { label: 'IBAN',     value: this.formatIban(v.toIban.replace(/\s/g, '')) },
      { label: 'Montant',  value: `${(+v.amount).toFixed(3)} TND`, highlight: true },
      ...(v.label ? [{ label: 'Libellé', value: v.label }] : []),
      ...(v.scheduledDate
          ? [{ label: 'Date', value: new Date(v.scheduledDate).toLocaleDateString('fr-FR') }]
          : [{ label: 'Exécution', value: 'Immédiate' }])
    ];

    this.totpError    = '';
    this.totpModalOpen = true;
  }

  // ─────────────────────────────────────────────────────────────
  // STEP 2 — User enters TOTP code and confirms
  //          → call API with TOTP code
  // ─────────────────────────────────────────────────────────────
  onTotpConfirmed(totpCode: string): void {
    this.totpLoading = true;
    this.totpError   = '';

    const v = this.transferForm.value;

    this.transferService.initiateTransfer({
      fromAccountId: +v.fromAccountId,
      toIban:        v.toIban.replace(/\s/g, '').toUpperCase(),
      toName:        v.toName,
      amount:        +v.amount,
      label:         v.label || undefined,
      scheduledDate: v.scheduledDate || undefined,
      totpCode
    }).subscribe({
      next: res => {
        this.totpLoading   = false;
        this.totpModalOpen = false;
        this.transferSuccess = true;
        this.lastTransfer    = res.data;
        this.loadAccounts(); // refresh balances
      },
      error: err => {
        this.totpLoading = false;
        const msg = err.error?.message ?? '';

        // TOTP-specific errors → keep modal open, show error inside it
        if (err.error?.error === 'INVALID_TOTP' || msg.toLowerCase().includes('totp') || msg.toLowerCase().includes('code')) {
          this.totpError = 'Code incorrect ou expiré. Réessayez.';
        } else {
          // Non-TOTP error (insufficient funds, frozen…) → close modal, show on form
          this.totpModalOpen = false;
          this.formError = msg || 'Erreur lors du virement. Veuillez réessayer.';
        }
      }
    });
  }

  // User clicks "Annuler" inside the modal
  onTotpCancelled(): void {
    this.totpModalOpen = false;
    this.totpError     = '';
    this.totpLoading   = false;
  }

  // ── Reset after success ────────────────────────────────────────
  resetTransfer(): void {
    this.transferSuccess = false;
    this.formError       = '';
    this.lastTransfer    = null;
    this.transferForm.reset();
    if (this.accounts.length) {
      this.tf['fromAccountId'].setValue(this.accounts[0].id);
    }
  }

  // ── Tab navigation ─────────────────────────────────────────────
  setTab(tab: 'simple' | 'batch' | 'history'): void {
    this.activeTab = tab;
    if (tab === 'history') this.loadHistory();
  }

  // ── IBAN formatter ─────────────────────────────────────────────
  formatIbanInput(event: Event): void {
    const el  = event.target as HTMLInputElement;
    const raw = el.value.replace(/\s/g, '').toUpperCase();
    el.value  = raw.replace(/(.{4})/g, '$1 ').trim();
    this.tf['toIban'].setValue(raw, { emitEvent: false });
  }

  formatIban(iban: string): string {
    return iban.replace(/(.{4})/g, '$1 ').trim();
  }

  // ── Batch CSV ──────────────────────────────────────────────────
  onDrop(e: DragEvent): void {
    e.preventDefault();
    this.isDragging = false;
    const f = e.dataTransfer?.files[0];
    if (f?.name.endsWith('.csv')) { this.batchFile = f; this.batchResult = null; }
  }

  onFileSelect(e: Event): void {
    const f = (e.target as HTMLInputElement).files?.[0];
    if (f) { this.batchFile = f; this.batchResult = null; }
  }

  removeBatch(e: Event): void {
    e.stopPropagation();
    this.batchFile = null;
    this.batchResult = null;
  }

  parseBatch(): void {
    if (!this.batchFile) return;
    this.batchLoading = true;
    const reader = new FileReader();
    reader.onload = ev => {
      const csv     = ev.target?.result as string;
      const lines   = csv.split('\n').filter(l => l.trim());
      const headers = lines[0].split(',').map(h => h.trim().toLowerCase());
      const rows: any[] = [];
      let totalAmount = 0, errors = 0, valid = 0;

      for (let i = 1; i < lines.length; i++) {
        const vals = lines[i].split(',');
        const row: any = { row: i + 1 };
        headers.forEach((h, idx) => row[h] = vals[idx]?.trim() ?? '');

        if (!row.iban || !row.name || !row.amount) {
          row.status = 'ERROR'; row.error = 'Champs manquants'; errors++;
        } else if (isNaN(+row.amount) || +row.amount <= 0) {
          row.status = 'ERROR'; row.error = 'Montant invalide'; errors++;
        } else {
          row.status = 'VALID'; totalAmount += +row.amount; valid++;
        }
        rows.push(row);
      }

      this.batchResult  = { totalRows: rows.length, validRows: valid, errorRows: errors, totalAmount, rows };
      this.batchLoading = false;
    };
    reader.readAsText(this.batchFile);
  }

  confirmBatch(): void {
    if (!this.batchFile || !this.batchAccountId) return;
    this.batchLoading = true;
    this.transferService.uploadBatch(this.batchFile, this.batchAccountId, '').subscribe({
      next: () => {
        this.batchLoading = false;
        this.batchFile    = null;
        this.batchResult  = null;
        this.setTab('history');
      },
      error: err => {
        this.batchLoading = false;
        this.formError    = err.error?.message ?? 'Erreur lot.';
      }
    });
  }

  downloadTemplate(): void {
    this.transferService.downloadTemplate().subscribe(blob => {
      const url = URL.createObjectURL(blob);
      const a   = Object.assign(document.createElement('a'), { href: url, download: 'transfer-template.csv' });
      a.click();
      URL.revokeObjectURL(url);
    });
  }

  // ── History ────────────────────────────────────────────────────
  loadHistory(): void {
    this.transferService.getTransfers(this.currentPage, 20).subscribe({
      next: res => { this.transfers = res.data.content; this.totalPages = res.data.totalPages; }
    });
  }

  changePage(page: number): void { this.currentPage = page; this.loadHistory(); }

  cancelTransfer(id: number): void {
    if (!confirm('Annuler ce virement ?')) return;
    this.transferService.cancelTransfer(id).subscribe({ next: () => this.loadHistory() });
  }

  // ── Helpers ────────────────────────────────────────────────────
  accountTypeLabel(type: string): string {
    return ({ CHECKING: 'Courant', SAVINGS: 'Épargne', CREDIT: 'Crédit', INVESTMENT: 'Invest.' } as any)[type] ?? type;
  }

  statusLabel(status: string): string {
    return ({ COMPLETED: 'Effectué', PENDING: 'En attente', FAILED: 'Échoué',
              CANCELLED: 'Annulé', PROCESSING: 'En cours' } as any)[status] ?? status;
  }
}
