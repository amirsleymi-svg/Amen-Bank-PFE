import { Component, OnInit, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonModule, DecimalPipe, DatePipe } from '@angular/common';
import { ReactiveFormsModule, FormBuilder, FormGroup, Validators } from '@angular/forms';
import { RouterModule } from '@angular/router';
import { CreditService } from '../../core/services/account.service';
import { CreditApplication, CreditSimulationResponse } from '../../core/models/models';
import { debounceTime, distinctUntilChanged, Subject, takeUntil } from 'rxjs';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

@Component({
  selector: 'app-credits',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule, DecimalPipe, DatePipe],
  templateUrl: './credits.component.html',
  styleUrls: ['./credits.component.scss']
})
export class CreditsComponent implements OnInit, AfterViewInit, OnDestroy {

  activeTab: 'simulate' | 'apply' | 'history' = 'simulate';

  simForm!:   FormGroup;
  applyForm!: FormGroup;

  simulation:      CreditSimulationResponse | null = null;
  applySimResult:  CreditSimulationResponse | null = null;
  applications:    CreditApplication[] = [];

  simLoading   = false;
  applyLoading = false;
  appsLoading  = false;
  applySuccess = false;
  applyError   = '';
  showFullTable = false;
  appliedCredit: CreditApplication | null = null;

  private amortChart: Chart | null = null;
  private destroy$ = new Subject<void>();

  creditTypes = [
    { value: 'PERSONAL',   label: 'Personnel',   rate: '8.95%', icon: '👤' },
    { value: 'MORTGAGE',   label: 'Immobilier',  rate: '7.25%', icon: '🏠' },
    { value: 'AUTO',       label: 'Automobile',  rate: '8.50%', icon: '🚗' },
    { value: 'BUSINESS',   label: 'Entreprise',  rate: '9.50%', icon: '🏢' },
    { value: 'STUDENT',    label: 'Étudiant',    rate: '6.20%', icon: '🎓' },
  ];

  get displayedAmortRows() {
    if (!this.simulation) return [];
    return this.showFullTable
      ? this.simulation.amortizationTable
      : this.simulation.amortizationTable.slice(0, 12);
  }

  constructor(private fb: FormBuilder, private creditService: CreditService) {}

  ngOnInit(): void {
    this.simForm = this.fb.group({
      amount:         [50000, [Validators.required, Validators.min(1000), Validators.max(500000)]],
      durationMonths: [60,    [Validators.required, Validators.min(6),    Validators.max(300)]],
      creditType:     ['PERSONAL', Validators.required]
    });

    this.applyForm = this.fb.group({
      creditType:     ['PERSONAL', Validators.required],
      amount:         [null, [Validators.required, Validators.min(1000)]],
      durationMonths: [null, [Validators.required, Validators.min(6), Validators.max(300)]],
      purpose:        ['']
    });

    // Auto-simulate on apply form changes
    this.applyForm.valueChanges.pipe(
      debounceTime(500), distinctUntilChanged(), takeUntil(this.destroy$)
    ).subscribe(() => {
      if (this.applyForm.valid) this.computeApplySimulation();
    });

    this.onSimulate();
    this.loadApplications();
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.renderAmortChart(), 500);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.amortChart?.destroy();
  }

  // ─── Simulation ───────────────────────────────────────────────────
  onSimulate(): void {
    if (this.simForm.invalid) return;
    this.simLoading = true;

    this.creditService.simulate(this.simForm.value).subscribe({
      next: res => {
        this.simulation = res.data;
        this.simLoading = false;
        setTimeout(() => this.renderAmortChart(), 100);
      },
      error: () => { this.simLoading = false; }
    });
  }

  computeApplySimulation(): void {
    this.creditService.simulate({
      amount:         this.applyForm.value.amount,
      durationMonths: this.applyForm.value.durationMonths,
      creditType:     this.applyForm.value.creditType
    }).subscribe({ next: res => this.applySimResult = res.data });
  }

  applySimulation2(): void {
    if (this.simulation) {
      this.applyForm.patchValue({
        creditType:     this.simForm.value.creditType,
        amount:         this.simForm.value.amount,
        durationMonths: this.simForm.value.durationMonths
      });
      this.applySimResult = this.simulation;
      this.activeTab = 'apply';
    }
  }

  // ─── Apply ────────────────────────────────────────────────────────
  onApply(): void {
    if (this.applyForm.invalid) { this.applyForm.markAllAsTouched(); return; }
    this.applyLoading = true;
    this.applyError   = '';

    this.creditService.apply(this.applyForm.value).subscribe({
      next: res => {
        this.applyLoading  = false;
        this.applySuccess  = true;
        this.appliedCredit = res.data;
        this.loadApplications();
      },
      error: err => {
        this.applyLoading = false;
        this.applyError   = err.error?.message ?? 'Erreur lors de la soumission.';
      }
    });
  }

  // ─── Applications list ────────────────────────────────────────────
  loadApplications(): void {
    this.appsLoading = true;
    this.creditService.getApplications().subscribe({
      next: res => { this.applications = res.data.content; this.appsLoading = false; },
      error: () => { this.appsLoading = false; }
    });
  }

  // ─── Chart ────────────────────────────────────────────────────────
  renderAmortChart(): void {
    const canvas = document.getElementById('amortChart') as HTMLCanvasElement;
    if (!canvas || !this.simulation) return;

    this.amortChart?.destroy();

    const table = this.simulation.amortizationTable;
    const step  = Math.max(1, Math.floor(table.length / 24));
    const labels   = table.filter((_, i) => i % step === 0).map(r => `M${r.month}`);
    const balances = table.filter((_, i) => i % step === 0).map(r => r.remainingBalance);
    const interests = table.filter((_, i) => i % step === 0).map(r => r.interest);

    this.amortChart = new Chart(canvas, {
      type: 'line',
      data: {
        labels,
        datasets: [
          {
            label: 'Capital restant',
            data: balances,
            borderColor: '#0A3F6B',
            backgroundColor: 'rgba(10, 63, 107, 0.08)',
            fill: true,
            tension: 0.4,
            borderWidth: 2,
            pointRadius: 0,
          },
          {
            label: 'Intérêts',
            data: interests,
            borderColor: '#C8A84B',
            backgroundColor: 'rgba(200, 168, 75, 0.08)',
            fill: true,
            tension: 0.4,
            borderWidth: 2,
            pointRadius: 0,
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { position: 'top', labels: { boxWidth: 12, padding: 16, font: { size: 11 } } },
          tooltip: { backgroundColor: '#1A2540', padding: 10, cornerRadius: 8 }
        },
        scales: {
          x: { grid: { display: false }, border: { display: false },
               ticks: { font: { size: 10 }, color: '#6B7A99', maxTicksLimit: 8 } },
          y: { grid: { color: 'rgba(0,0,0,0.05)' }, border: { display: false },
               ticks: { font: { size: 10 }, color: '#6B7A99',
                        callback: v => `${(+v).toLocaleString()} TND` } }
        }
      }
    });
  }

  // ─── Export ───────────────────────────────────────────────────────
  exportAmortCsv(): void {
    if (!this.simulation) return;
    let csv = 'Mois,Mensualité,Capital,Intérêts,Solde restant\n';
    for (const row of this.simulation.amortizationTable) {
      csv += `${row.month},${row.payment.toFixed(3)},${row.principal.toFixed(3)},${row.interest.toFixed(3)},${row.remainingBalance.toFixed(3)}\n`;
    }
    const blob = new Blob([csv], { type: 'text/csv' });
    const url  = URL.createObjectURL(blob);
    const a    = document.createElement('a');
    a.href = url; a.download = 'amortissement.csv'; a.click();
    URL.revokeObjectURL(url);
  }

  exportAmortPdf(): void {
    // PDF export via print stylesheet in production
    window.print();
  }

  applySimulation(): void { this.applySimulation2(); }

  // ─── Helpers ──────────────────────────────────────────────────────
  creditTypeLabel(type: string): string {
    return this.creditTypes.find(c => c.value === type)?.label ?? type;
  }

  statusLabel(status: string): string {
    const map: Record<string, string> = {
      PENDING: 'En attente', REVIEWING: 'En examen',
      APPROVED: 'Approuvé', REJECTED: 'Refusé',
      DISBURSED: 'Décaissé', CLOSED: 'Clôturé'
    };
    return map[status] ?? status;
  }
}
