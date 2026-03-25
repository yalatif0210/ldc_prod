import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormGroup, FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatOptionModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ToastrService } from 'ngx-toastr';
import { SuperAdminService } from '@shared/services/super-admin.service';
import { forkJoin, Subject, takeUntil } from 'rxjs';
import { UserManagementService } from '@shared/services/user-management.service';
import { FormBaseComponent } from '@shared/components/base-component/form-base';
import { RegionInterface, StructureInterface } from '@shared/models/model.interface';
import { ReportHistoryService } from '@shared/services/report-history.service';

@Component({
  selector: 'app-report-admin',
  standalone: true,
  templateUrl: './report-admin.html',
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatOptionModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatPaginatorModule,
  ],
})
export class ReportAdmin extends FormBaseComponent implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  protected readonly users_service = inject(UserManagementService);
  protected readonly reportService = inject(ReportHistoryService);
  //private readonly toast = inject(ToastrService);
  //private destroy$ = new Subject<void>();

  pageSize = 10;
  pageIndex = 0;
  pageSizeOptions = [5, 10, 25, 50];
  paginatedReports: any[] = [];

  allReports: any[] = [];
  filteredReports: any[] = [];
  periods: any[] = [];
  structures: any[] = [];
  filteredPlatforms: any[] = [];
  regions: RegionInterface[] = [];
  loading = true;
  filterStatus: any = '';
  filterPeriodId: any = '';
  filterStructureId: any = '';
  editingId: number | null = null;
  editingData: any = {};

  form: FormGroup | undefined;

  statusOptions = [
    { id: 1, label: 'Draft',     background: '#f5f5f5', color: '#616161' },
    { id: 2, label: 'Suggested', background: '#e3f2fd', color: '#1565c0' },
    { id: 3, label: 'Submitted', background: '#fff3e0', color: '#e65100' },
    { id: 4, label: 'Approved',  background: '#e8f5e9', color: '#2e7d32' },
  ];

  constructor() {
    super();
    this.form = this.buildFormFromArray([
      { key: 'region', defaultValue: '', validators: [] },
    ]);
  }

  ngOnInit(): void {
    this.form!.get('region')?.valueChanges.subscribe(selectedRegion => {
      this.onRegionChange(selectedRegion);
    });
    this.loadAll();
  }

  loadAll(): void {
    this.loading = true;
    forkJoin({
      reports: this.reportService.getAllReports(),
      periods: this.service.getPeriods(),
      structures: this.users_service.getStructure(),
      regions: this.users_service.getRegion(),
    }).pipe(takeUntil(this.destroy$)).subscribe({
      next: (
        { reports,
          periods,
          regions,
          structures
        }: { reports: any; periods: any[]; regions: any, structures: any }) => {

        this.allReports = reports.data.reports as any[];
        this.filteredReports = reports;
        this.periods = periods;
        this.regions = regions.data.regions;
        this.structures = structures.data.structures as StructureInterface[];
        this.loading = false;
        this.applyFilter();
      },
      error: (e) => {
        this.toast.error('Erreur lors du chargement des données');
        this.loading = false;
      },
    });
  }

  loadReports(): void {
    this.loadAll();
  }

  applyFilter(): void {
    let result = [...this.allReports].sort((a, b) => a.id - b.id);

    if (this.filterStatus) {
      result = result.filter(r => String(r.status?.id ?? r.statusId) === String(this.filterStatus));
    }
    if (this.filterStructureId) {
      result = result.filter(r =>
        r.account?.structures?.some((s: any) => String(s.id) === String(this.filterStructureId))
      );
    }
    if (this.filterPeriodId) {
      result = result.filter(
        r => String(r.period?.id ?? r.periodId) === String(this.filterPeriodId));
    }

    this.filteredReports = result;
    this.pageIndex = 0;
    this.paginate();
  }

  onRegionChange(region: any) {
    this.filteredPlatforms = this.structures.filter(element =>
      [region].includes(element!.district!.region!.id) && element.active
    );
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedReports = this.filteredReports.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.paginate();
  }

  startEdit(report: any): void {
    this.editingId = report.id;
    this.editingData = {
      ...report,
      statusId: report.status?.id ?? report.statusId,
      accountId: report.account?.id ?? report.accountId,
    };
  }

  cancelEdit(): void {
    this.editingId = null;
    this.editingData = {};
  }

  saveStatus(report: any): void {
    this.service.changeReportStatus(report.id, this.editingData.statusId).subscribe({
      next: () => {
        this.toast.success('Statut mis à jour');
        this.cancelEdit();
        this.loadReports();
      },
      error: () => this.toast.error('Erreur lors de la mise à jour du statut'),
    });
  }

  reassign(report: any): void {
    const input = prompt('Entrez le nouvel ID de compte :');
    if (!input || isNaN(Number(input))) return;
    this.service.reassignReport(report.id, Number(input)).subscribe({
      next: () => {
        this.toast.success('Rapport réassigné');
        this.loadReports();
      },
      error: () => this.toast.error('Erreur lors de la réassignation'),
    });
  }

  deleteReport(report: any): void {
    if (!confirm(`Supprimer le rapport #${report.id} ?`)) return;
    this.service.deleteReport(report.id).subscribe({
      next: () => {
        this.toast.success('Rapport supprimé');
        this.loadReports();
      },
      error: () => this.toast.error('Erreur lors de la suppression'),
    });
  }

  getStatusLabel(statusId: number): string {
    return this.statusOptions.find(s => s.id === statusId)?.label ?? String(statusId);
  }

  getStatusStyle(statusId: any): { background: string; color: string } {
    const s = this.statusOptions.find(s => s.id === Number(statusId));
    return s ? { background: s.background, color: s.color } : { background: '#f5f5f5', color: '#616161' };
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
