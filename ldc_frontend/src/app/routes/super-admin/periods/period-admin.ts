import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ToastrService } from 'ngx-toastr';
import { SuperAdminService } from '@shared/services/super-admin.service';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-period-admin',
  standalone: true,
  templateUrl: './period-admin.html',
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
  ],
})
export class PeriodAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  periods: any[]          = [];
  paginatedPeriods: any[] = [];
  pageSize                = 10;
  pageIndex               = 0;
  pageSizeOptions         = [5, 10, 25, 50];
  loading                 = true;
  showAddForm             = false;
  editingId: number | null = null;
  editingData: any = {};

  addForm = new FormGroup({
    periodName: new FormControl('', [Validators.required]),
    startDate:  new FormControl('', [Validators.required]),
    endDate:    new FormControl('', [Validators.required]),
  });

  ngOnInit(): void {
    this.loadPeriods();
  }

  loadPeriods(): void {
    this.loading = true;
    this.service.getPeriods().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.periods = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des périodes');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedPeriods = this.periods.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(period: any): void {
    this.editingId   = period.id;
    this.editingData = { ...period };
  }

  cancelEdit(): void {
    this.editingId   = null;
    this.editingData = {};
  }

  saveEdit(period: any): void {
    const payload = {
      periodName: this.editingData.periodName,
      startDate:  this.editingData.startDate,
      endDate:    this.editingData.endDate,
    };
    this.service.updatePeriod(period.id, payload).subscribe({
      next: () => {
        this.toast.success('Période mise à jour');
        this.cancelEdit();
        this.loadPeriods();
      },
      error: () => this.toast.error('Erreur lors de la mise à jour'),
    });
  }

  deletePeriod(period: any): void {
    if (!confirm(`Supprimer la période "${period.periodName}" ?`)) return;
    this.service.deletePeriod(period.id).subscribe({
      next: () => {
        this.toast.success('Période supprimée');
        this.loadPeriods();
      },
      error: () => this.toast.error('Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    const val = this.addForm.value;
    this.service.createPeriod({
      periodName: val.periodName,
      startDate:  val.startDate,
      endDate:    val.endDate,
    }).subscribe({
      next: () => {
        this.toast.success('Période créée');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadPeriods();
      },
      error: () => this.toast.error('Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
