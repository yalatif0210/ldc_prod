import { Component, OnInit, inject } from '@angular/core';
import { CommonModule, KeyValuePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatDividerModule } from '@angular/material/divider';
import { AppStats, FilteredStats, SuperAdminService } from '@shared/services/super-admin.service';
import { forkJoin } from 'rxjs';

@Component({
  selector: 'app-super-admin-dashboard',
  standalone: true,
  templateUrl: './super-admin-dashboard.html',
  imports: [
    CommonModule,
    KeyValuePipe,
    FormsModule,
    MatCardModule,
    MatIconModule,
    MatProgressSpinnerModule,
    MatFormFieldModule,
    MatSelectModule,
    MatButtonModule,
    MatDividerModule,
  ],
})
export class SuperAdminDashboard implements OnInit {
  private readonly superAdminService = inject(SuperAdminService);

  stats: AppStats | null = null;
  loading = true;
  error = false;

  // Reference data for filters
  periods: any[] = [];
  structures: any[] = [];
  equipments: any[] = [];

  // Selected filter values (null = "Tous/Toutes")
  selectedPeriodId: number | null = null;
  selectedStructureId: number | null = null;
  selectedEquipmentId: number | null = null;

  // Filtered stats result
  filteredStats: FilteredStats | null = null;
  filteredLoading = false;
  filteredError = false;

  statCards: Array<{ key: keyof AppStats; label: string; icon: string; color: string }> = [
    { key: 'totalUsers',      label: 'Utilisateurs total',   icon: 'group',                color: '#1976d2' },
    { key: 'activeUsers',     label: 'Comptes actifs',        icon: 'check_circle',         color: '#388e3c' },
    { key: 'totalReports',    label: 'Rapports total',        icon: 'description',          color: '#f57c00' },
    { key: 'totalPeriods',    label: 'Périodes',              icon: 'date_range',           color: '#7b1fa2' },
    { key: 'totalStructures', label: 'Structures',            icon: 'business',             color: '#0097a7' },
    { key: 'totalRoles',      label: 'Rôles',                 icon: 'admin_panel_settings', color: '#c62828' },
  ];

  ngOnInit(): void {
    forkJoin({
      stats: this.superAdminService.getStats(),
      periods: this.superAdminService.getPeriods(),
      structures: this.superAdminService.getStructures(),
      equipments: this.superAdminService.getEquipments(),
    }).subscribe({
      next: ({ stats, periods, structures, equipments }) => {
        this.stats = stats;
        this.periods = periods;
        this.structures = structures;
        this.equipments = equipments;
        this.loading = false;
        // Load initial filtered stats with no filters applied
        this.applyFilters();
      },
      error: () => {
        this.error = true;
        this.loading = false;
      },
    });
  }

  applyFilters(): void {
    this.filteredLoading = true;
    this.filteredError = false;
    this.filteredStats = null;

    this.superAdminService.getFilteredStats({
      periodId: this.selectedPeriodId,
      structureId: this.selectedStructureId,
      equipmentId: this.selectedEquipmentId,
    }).subscribe({
      next: data => {
        this.filteredStats = data;
        this.filteredLoading = false;
      },
      error: () => {
        this.filteredError = true;
        this.filteredLoading = false;
      },
    });
  }

  resetFilters(): void {
    this.selectedPeriodId = null;
    this.selectedStructureId = null;
    this.selectedEquipmentId = null;
    this.applyFilters();
  }
}
