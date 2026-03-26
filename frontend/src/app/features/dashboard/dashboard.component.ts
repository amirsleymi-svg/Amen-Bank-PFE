import { Component, OnInit, OnDestroy, AfterViewInit } from '@angular/core';
import { CommonModule, DatePipe, DecimalPipe } from '@angular/common';
import { RouterModule, Router } from '@angular/router';
import { AuthService } from '../../core/services/auth.service';
import { AccountService } from '../../core/services/account.service';
import { Account, Transaction, User } from '../../core/models/models';
import { Subject, takeUntil } from 'rxjs';
import { Chart, registerables } from 'chart.js';

Chart.register(...registerables);

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, DatePipe, DecimalPipe],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit, OnDestroy, AfterViewInit {

  user: User | null = null;
  accounts: Account[] = [];
  selectedAccount: Account | null = null;
  recentTransactions: Transaction[] = [];

  isLoading    = true;
  isLoadingTx  = true;
  chatOpen     = false;
  sidebarCollapsed = false;
  unreadCount  = 3;

  today = new Date();

  periods = [
    { label: '7J',  value: '7d' },
    { label: '1M',  value: '1m' },
    { label: '3M',  value: '3m' },
    { label: '6M',  value: '6m' },
  ];
  activePeriod = '1m';

  private chart: Chart | null = null;
  private destroy$ = new Subject<void>();

  constructor(
    private authService: AuthService,
    private accountService: AccountService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.user = this.authService.currentUser();
    this.loadAccounts();
  }

  ngAfterViewInit(): void {
    setTimeout(() => this.initChart(), 300);
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    this.chart?.destroy();
  }

  // ─── Load data ────────────────────────────────────────────────────
  loadAccounts(): void {
    this.isLoading = true;
    this.accountService.getAccounts()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.accounts   = res.data;
          this.isLoading  = false;
          if (this.accounts.length > 0) {
            this.selectedAccount = this.accounts[0];
            this.loadTransactions(this.accounts[0].id);
          } else {
            this.isLoadingTx = false;
          }
        },
        error: () => { this.isLoading = false; this.isLoadingTx = false; }
      });
  }

  loadTransactions(accountId: number): void {
    this.isLoadingTx = true;
    this.accountService.getTransactions(accountId, 0, 8)
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: res => {
          this.recentTransactions = res.data.content;
          this.isLoadingTx = false;
        },
        error: () => { this.isLoadingTx = false; }
      });
  }

  selectAccount(account: Account): void {
    this.selectedAccount = account;
    this.loadTransactions(account.id);
  }

  // ─── Chart ────────────────────────────────────────────────────────
  initChart(): void {
    const canvas = document.getElementById('activityChart') as HTMLCanvasElement;
    if (!canvas) return;

    this.chart?.destroy();

    const labels = ['Jan', 'Fév', 'Mar', 'Avr', 'Mai', 'Jun'];
    const debits  = [1200, 980, 1450, 870, 1100, 1350];
    const credits = [3200, 3200, 3200, 3200, 3200, 3200];

    this.chart = new Chart(canvas, {
      type: 'bar',
      data: {
        labels,
        datasets: [
          {
            label: 'Dépenses',
            data: debits,
            backgroundColor: 'rgba(10, 63, 107, 0.15)',
            borderColor: 'rgba(10, 63, 107, 0.8)',
            borderWidth: 2,
            borderRadius: 6,
            borderSkipped: false,
          },
          {
            label: 'Revenus',
            data: credits,
            type: 'line' as any,
            borderColor: '#C8A84B',
            backgroundColor: 'rgba(200, 168, 75, 0.08)',
            borderWidth: 2.5,
            pointBackgroundColor: '#C8A84B',
            pointRadius: 4,
            tension: 0.4,
            fill: true,
          }
        ]
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: {
            position: 'top',
            labels: {
              boxWidth: 12,
              padding: 16,
              font: { size: 12, family: 'Inter, sans-serif' }
            }
          },
          tooltip: {
            backgroundColor: '#1A2540',
            padding: 12,
            cornerRadius: 8,
            callbacks: {
              label: ctx => ` ${ctx.dataset.label}: ${ctx.parsed.y.toLocaleString('fr-TN')} TND`
            }
          }
        },
        scales: {
          x: {
            grid: { display: false },
            border: { display: false },
            ticks: { font: { size: 12 }, color: '#6B7A99' }
          },
          y: {
            grid: { color: 'rgba(0,0,0,0.05)', lineWidth: 1 },
            border: { display: false, dash: [4, 4] },
            ticks: {
              font: { size: 11 }, color: '#6B7A99',
              callback: v => `${(+v).toLocaleString('fr')} TND`
            }
          }
        }
      }
    });
  }

  // ─── Helpers ──────────────────────────────────────────────────────
  get totalBalance(): number {
    return this.accounts.reduce((sum, a) => sum + a.availableBalance, 0);
  }

  formatIban(iban: string): string {
    return iban.replace(/(.{4})/g, '$1 ').trim();
  }

  accountTypeLabel(type: string): string {
    const labels: Record<string, string> = {
      CHECKING: 'Courant', SAVINGS: 'Épargne',
      CREDIT: 'Crédit', INVESTMENT: 'Investissement'
    };
    return labels[type] ?? type;
  }

  logout(): void {
    this.authService.logout();
  }
}
