import { Component, OnInit, OnDestroy, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { ToastrService } from 'ngx-toastr';
import { SuperAdminService } from '@shared/services/super-admin.service';
import { Subject, takeUntil } from 'rxjs';

@Component({
  selector: 'app-structure-admin',
  standalone: true,
  templateUrl: './structure-admin.html',
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatPaginatorModule,
  ],
})
export class StructureAdmin implements OnInit, OnDestroy {
  private readonly service = inject(SuperAdminService);
  private readonly toast = inject(ToastrService);
  private destroy$ = new Subject<void>();

  structures: any[]          = [];
  filteredStructures: any[]  = [];
  paginatedStructures: any[] = [];
  pageSize                   = 10;
  pageIndex                  = 0;
  pageSizeOptions            = [5, 10, 25, 50];
  loading                    = true;

  filterRegion   = '';
  filterDistrict = '';
  filterName     = '';
  filterActive   = '';

  regions: any[]          = [];
  allDistricts: any[]     = [];
  filteredDistricts: any[] = [];

  ngOnInit(): void {
    this.loadStructures();
  }

  loadStructures(): void {
    this.loading = true;
    this.service.getStructures().pipe(takeUntil(this.destroy$)).subscribe({
      next: data => {
        console.log('sr - structure-admin.ts:45', data);
        this.structures = data.sort((a, b) => a.id - b.id);
        this.regions = this.unique(data.map((s: any) => s.district?.region).filter(Boolean), 'id');
        this.allDistricts = this.unique(data.map((s: any) => s.district).filter(Boolean), 'id');
        this.filteredDistricts = [...this.allDistricts];
        this.loading = false;
        this.applyFilter();
      },
      error: () => {
        this.toast.error('Erreur lors du chargement des structures');
        this.loading = false;
      },
    });
  }

  unique(arr: any[], key: string): any[] {
    return arr.filter((item, i, self) => self.findIndex(x => x[key] === item[key]) === i);
  }

  resetFilters(): void {
    this.filterRegion   = '';
    this.filterDistrict = '';
    this.filterName     = '';
    this.filterActive   = '';
    this.filteredDistricts = [...this.allDistricts];
    this.applyFilter();
  }

  onRegionChange(): void {
    this.filterDistrict = '';
    this.filteredDistricts = this.filterRegion
      ? this.allDistricts.filter(d => String(d.region?.id) === String(this.filterRegion))
      : [...this.allDistricts];
    this.applyFilter();
  }

  applyFilter(): void {
    let result = [...this.structures];
    if (this.filterRegion) {
      result = result.filter(s => String(s.district?.region?.id) === String(this.filterRegion));
    }
    if (this.filterDistrict) {
      result = result.filter(s => String(s.district?.id) === String(this.filterDistrict));
    }
    if (this.filterName.trim()) {
      const term = this.filterName.toLowerCase().trim();
      result = result.filter(s => s.name?.toLowerCase().includes(term));
    }
    if (this.filterActive !== '') {
      const activeVal = this.filterActive === 'true';
      result = result.filter(s => s.active === activeVal);
    }
    this.filteredStructures = result;
    this.pageIndex = 0;
    this.paginate();
  }

  paginate(): void {
    const start = this.pageIndex * this.pageSize;
    this.paginatedStructures = this.filteredStructures.slice(start, start + this.pageSize);
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.paginate();
  }

  private readonly equipmentColors = [
    { background: '#e3f2fd', color: '#1565c0' },
    { background: '#e8f5e9', color: '#2e7d32' },
    { background: '#fff3e0', color: '#e65100' },
    { background: '#f3e5f5', color: '#6a1b9a' },
    { background: '#fce4ec', color: '#880e4f' },
    { background: '#e0f7fa', color: '#006064' },
    { background: '#f1f8e9', color: '#33691e' },
  ];

  getEquipmentStyle(index: number): { background: string; color: string } {
    return this.equipmentColors[index % this.equipmentColors.length];
  }

  removeEquipment(structure: any, equipment: any): void {
    if (!confirm(`Retirer "${equipment.name}" de la structure "${structure.name}" ?`)) return;
    this.service.removeEquipmentFromStructure(structure.id, equipment.id).subscribe({
      next: () => {
        this.toast.success(`Équipement retiré`);
        this.loadStructures();
      },
      error: () => this.toast.error('Erreur lors de la suppression de l\'équipement'),
    });
  }

  toggle(structure: any): void {
    const action = structure.isActive ? 'désactiver' : 'activer';
    if (!confirm(`Voulez-vous ${action} la structure "${structure.name}" ?`)) return;
    this.service.toggleStructure(structure.id).subscribe({
      next: () => {
        this.toast.success(`Structure ${structure.isActive ? 'désactivée' : 'activée'}`);
        this.loadStructures();
      },
      error: () => this.toast.error('Erreur lors du changement de statut'),
    });
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
