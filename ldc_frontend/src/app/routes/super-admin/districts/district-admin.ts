import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ToastrService } from 'ngx-toastr';
import { SuperAdminService } from '@shared/services/super-admin.service';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-district-admin',
  standalone: true,
  templateUrl: './district-admin.html',
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatSelectModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
  ],
})
export class DistrictAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast   = inject(ToastrService);
  private destroy$         = new Subject<void>();

  districts: any[]           = [];
  paginatedDistricts: any[]  = [];
  regions: any[]              = [];
  pageSize                    = 10;
  pageIndex                   = 0;
  pageSizeOptions              = [5, 10, 25, 50];
  loading                      = true;
  showAddForm                  = false;
  editingId: number | null     = null;
  editingName                  = '';
  editingRegionId: number | null = null;

  addForm = new FormGroup({
    name: new FormControl('', [Validators.required]),
    regionId: new FormControl<number | null>(null, [Validators.required]),
  });

  ngOnInit(): void {
    this.loadRegions();
    this.loadDistricts();
  }

  loadRegions(): void {
    this.service.getRegions().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => (this.regions = data),
      error: () => this.toast.error('Erreur lors du chargement des régions'),
    });
  }

  loadDistricts(): void {
    this.loading = true;
    this.service.getDistricts().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        this.districts = data;
        this.loading = false;
        this.paginate();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des districts');
        this.loading = false;
      },
    });
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedDistricts = this.districts.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize  = event.pageSize;
    this.paginate();
  }

  startEdit(district: any): void {
    this.editingId       = district.id;
    this.editingName     = district.name;
    this.editingRegionId = district.region?.id ?? null;
  }

  cancelEdit(): void {
    this.editingId       = null;
    this.editingName     = '';
    this.editingRegionId = null;
  }

  saveEdit(district: any): void {
    if (this.editingRegionId == null) return;
    this.service.updateDistrict(district.id, {
      name: this.editingName,
      regionId: this.editingRegionId,
    }).subscribe({
      next: () => {
        this.toast.success('District mis à jour');
        this.cancelEdit();
        this.loadDistricts();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la mise à jour'),
    });
  }

  deleteDistrict(district: any): void {
    if (!confirm(`Supprimer le district "${district.name}" ?`)) return;
    this.service.deleteDistrict(district.id).subscribe({
      next: () => {
        this.toast.success('District supprimé');
        this.loadDistricts();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la suppression'),
    });
  }

  submitAdd(): void {
    if (this.addForm.invalid) return;
    const val = this.addForm.value;
    this.service.createDistrict({ name: val.name, regionId: val.regionId }).subscribe({
      next: () => {
        this.toast.success('District créé');
        this.addForm.reset();
        this.showAddForm = false;
        this.loadDistricts();
      },
      error: (err) => this.toast.error(err?.error?.description || 'Erreur lors de la création'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
