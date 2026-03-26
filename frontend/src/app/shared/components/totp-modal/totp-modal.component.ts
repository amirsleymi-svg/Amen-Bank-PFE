import {
  Component, Input, Output, EventEmitter,
  OnChanges, SimpleChanges, OnDestroy,
  ViewChildren, QueryList, ElementRef, AfterViewInit
} from '@angular/core';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-totp-modal',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './totp-modal.component.html',
  styleUrls: ['./totp-modal.component.scss']
})
export class TotpModalComponent implements OnChanges, OnDestroy {

  @Input()  visible  = false;
  @Input()  loading  = false;
  @Input()  error    = '';
  @Input()  summary: Array<{ label: string; value: string; highlight?: boolean }> = [];

  @Output() confirmed = new EventEmitter<string>();
  @Output() cancelled = new EventEmitter<void>();

  @ViewChildren('digit') inputs!: QueryList<ElementRef<HTMLInputElement>>;

  digits:  string[] = ['', '', '', '', '', ''];
  shaking  = false;
  timerSecs = 30;
  timerOffset = 0;

  private timerInterval: any = null;

  get code(): string { return this.digits.join(''); }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['visible']?.currentValue === true) {
      this.reset();
      this.startTimer();
      // Focus first input after render
      setTimeout(() => this.focusInput(0), 80);
    }
    if (changes['visible']?.currentValue === false) {
      this.stopTimer();
    }
    // Shake on error
    if (changes['error']?.currentValue && changes['error'].currentValue !== changes['error'].previousValue) {
      this.triggerShake();
      this.clearDigits();
      setTimeout(() => this.focusInput(0), 80);
    }
  }

  ngOnDestroy(): void { this.stopTimer(); }

  // ── Digit input handlers ────────────────────────────────────────
  onInput(event: Event, idx: number): void {
    const el    = event.target as HTMLInputElement;
    const value = el.value.replace(/\D/g, '').slice(-1);
    el.value        = value;
    this.digits[idx] = value;

    if (value && idx < 5) {
      this.focusInput(idx + 1);
    }
    if (this.code.length === 6) {
      this.autoConfirm();
    }
  }

  onKeydown(event: KeyboardEvent, idx: number): void {
    if (event.key === 'Backspace') {
      if (this.digits[idx]) {
        this.digits[idx] = '';
        (event.target as HTMLInputElement).value = '';
      } else if (idx > 0) {
        this.digits[idx - 1] = '';
        this.focusInput(idx - 1);
      }
    }
    if (event.key === 'ArrowLeft'  && idx > 0) this.focusInput(idx - 1);
    if (event.key === 'ArrowRight' && idx < 5) this.focusInput(idx + 1);
  }

  onPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const digits = (event.clipboardData?.getData('text') ?? '')
      .replace(/\D/g, '').slice(0, 6).split('');

    digits.forEach((d, i) => {
      this.digits[i] = d;
      const el = this.inputs.get(i)?.nativeElement;
      if (el) el.value = d;
    });

    const last = Math.min(digits.length - 1, 5);
    this.focusInput(last);
    if (this.code.length === 6) this.autoConfirm();
  }

  onFocus(idx: number): void {
    // Select the digit on focus so re-typing replaces it
    const el = this.inputs.get(idx)?.nativeElement;
    if (el) el.select();
  }

  // ── Actions ─────────────────────────────────────────────────────
  confirm(): void {
    if (this.code.length === 6 && !this.loading) {
      this.confirmed.emit(this.code);
    }
  }

  cancel(): void {
    this.reset();
    this.cancelled.emit();
  }

  onBackdropClick(event: MouseEvent): void {
    if ((event.target as HTMLElement).classList.contains('modal-backdrop')) {
      this.cancel();
    }
  }

  // ── Timer ────────────────────────────────────────────────────────
  private startTimer(): void {
    this.stopTimer();
    // Sync with real TOTP 30-second window
    const now       = Date.now() / 1000;
    this.timerSecs  = 30 - Math.floor(now % 30);
    this.updateOffset();

    this.timerInterval = setInterval(() => {
      const n = Date.now() / 1000;
      this.timerSecs = 30 - Math.floor(n % 30);
      this.updateOffset();
    }, 1000);
  }

  private stopTimer(): void {
    if (this.timerInterval) { clearInterval(this.timerInterval); this.timerInterval = null; }
  }

  private updateOffset(): void {
    // stroke-dashoffset: 100 = full circle hidden, 0 = full circle shown
    this.timerOffset = 100 - (this.timerSecs / 30) * 100;
  }

  // ── Helpers ─────────────────────────────────────────────────────
  private autoConfirm(): void {
    // Small delay so user sees the last digit typed
    setTimeout(() => this.confirm(), 120);
  }

  private focusInput(idx: number): void {
    this.inputs?.get(idx)?.nativeElement.focus();
  }

  private clearDigits(): void {
    this.digits = ['', '', '', '', '', ''];
    this.inputs?.forEach(el => el.nativeElement.value = '');
  }

  private reset(): void {
    this.clearDigits();
    this.shaking = false;
    this.timerSecs = 30;
    this.timerOffset = 0;
  }

  private triggerShake(): void {
    this.shaking = true;
    setTimeout(() => this.shaking = false, 600);
  }
}
