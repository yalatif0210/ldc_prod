import { AfterViewInit, Component, NgZone, OnDestroy, OnInit, inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatOptionModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatDialog, MatDialogModule } from '@angular/material/dialog';
import ApexCharts from 'apexcharts';
import { Subject, forkJoin } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { AuthService } from '@core/authentication/auth.service';
import { UserRole } from '@core/bootstrap';
import { ReportHistoryService } from '@shared/services/report-history.service';
import {
  DashboardService,
  DashboardKpi,
  DashboardSite,
  StockAlert,
  Completeness,
  PeriodInfo,
  CompletenessDetail,
} from './dashboard.service';
import { MatExpansionModule } from '@angular/material/expansion';
import { CompletenessDetailDialog } from './completeness-detail-dialog';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  templateUrl: './dashboard.html',
  styleUrls: ['./dashboard.scss'],
  imports: [
    CommonModule,
    FormsModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatSelectModule,
    MatFormFieldModule,
    MatOptionModule,
    MatProgressSpinnerModule,
    MatExpansionModule,
    MatDialogModule,
  ],
})
export class Dashboard implements OnInit, AfterViewInit, OnDestroy {
  private readonly ngZone = inject(NgZone);
  private readonly authService = inject(AuthService);
  private readonly historyService = inject(ReportHistoryService);
  private readonly dashService = inject(DashboardService);
  private readonly dialog = inject(MatDialog);

  // ── Rôle ──────────────────────────────────────────────────────────────────
  isAdmin = false;
  isSupervisor = false;

  // ── Compte ────────────────────────────────────────────────────────────────
  account: any;
  selectedEquipmentId: number | null = null;

  // ── Données brutes (chargées une fois par équipement) ─────────────────────
  allReports: any[] = [];
  allPeriods: PeriodInfo[] = [];
  allSites: DashboardSite[] = [];

  // ── Filtres toolbar ───────────────────────────────────────────────────────
  selectedPeriod = '';
  selectedRegion = 'all';
  selectedSiteId = 'all';

  // ── Rapports filtrés ──────────────────────────────────────────────────────
  filteredReports: any[] = [];

  // ── Sélecteurs in-card ────────────────────────────────────────────────────
  activityPeriod = '';
  trendStart = '';
  trendEnd = '';
  tatStart = '';
  tatEnd = '';
  retestStart = '';
  retestEnd = '';
  rejectStart = '';
  rejectEnd = '';

  // ── Résultats ─────────────────────────────────────────────────────────────
  kpis: DashboardKpi | null = null;
  stockAlerts: StockAlert[] = [];
  cmmConfigs: any[] = [];
  completeness: Completeness[] | null = null;
  completenessDetails: CompletenessDetail[] | null = null;
  hasBreakdownData = false;
  loading = false;

  // ── Lifecycle ─────────────────────────────────────────────────────────────
  private readonly destroy$ = new Subject<void>();
  private renderTimeout: ReturnType<typeof setTimeout> | null = null;

  // ── Charts ────────────────────────────────────────────────────────────────
  private charts: Record<string, ApexCharts | undefined> = {};

  // ── Listes dérivées ───────────────────────────────────────────────────────
  get equipmentList() { return this.historyService.getEquipmentList(this.account); }

  get regionList(): string[] {
    return ['all', ...new Set(this.allSites.map(s => s.regionName).sort((a, b) => a.localeCompare(b))).values()];
  }

  get siteListForRegion(): DashboardSite[] {
    if (this.selectedRegion === 'all') return this.allSites;
    return this.allSites.filter(s => s.regionName === this.selectedRegion);
  }

  get criticalAlertCount(): number {
    return this.stockAlerts.filter(a => a.level === 'critical').length;
  }

  // ── Init ──────────────────────────────────────────────────────────────────
  ngOnInit(): void {
    this.authService.userRole()
      .pipe(takeUntil(this.destroy$))
      .subscribe(role => {
        this.isAdmin = [UserRole.SUPER_ADMIN, UserRole.ADMIN].includes(role as UserRole);
        this.isSupervisor = role === UserRole.SUPERVISOR;
      });

    forkJoin([this.historyService.getEquipments()])
      .pipe(takeUntil(this.destroy$))
      .subscribe(([accountRes]: [any]) => {
        if (accountRes?.data) {
          this.account = accountRes.data.account;
          const eqList = this.equipmentList;
          if (eqList.length) {
            this.selectedEquipmentId = eqList[0].id;
            this.loadDashboardData();
          }
        }
      });
  }

  ngAfterViewInit(): void { }

  // ── Handlers toolbar ──────────────────────────────────────────────────────
  onEquipmentChange(): void { this.loadDashboardData(); }

  onPeriodChange(): void { this.applyFiltersAndRender(); }

  onRegionChange(): void {
    if (this.selectedSiteId !== 'all') {
      const inRegion = this.siteListForRegion.some(s => s.id === this.selectedSiteId);
      if (!inRegion) this.selectedSiteId = 'all';
    }
    this.applyFiltersAndRender();
  }

  onSiteChange(): void { this.applyFiltersAndRender(); }

  // ── Handlers sélecteurs in-card ───────────────────────────────────────────
  onActivityPeriodChange(): void {
    this.renderChart('chartActivity',
      this.dashService.buildActivityChart(this.filteredReports, this.activityPeriod));
  }

  onTrendRangeChange(): void {
    this.renderChart('chartTrend',
      this.dashService.buildTrendChart(
        this.filteredReports, this.allPeriods, this.trendStart, this.trendEnd));
  }

  onTatRangeChange(): void {
    this.renderChart('chartTat',
      this.dashService.buildTatChart(
        this.filteredReports, this.allPeriods, this.tatStart, this.tatEnd));
  }

  onRetestRangeChange(): void {
    this.renderChart('chartRetest',
      this.dashService.buildRetestChart(
        this.filteredReports, this.allPeriods, this.retestStart, this.retestEnd));
  }

  onRejectRangeChange(): void {
    this.renderChart('chartReject',
      this.dashService.buildRejectChart(
        this.filteredReports, this.allPeriods, this.rejectStart, this.rejectEnd));
  }

  // ── Chargement HTTP ───────────────────────────────────────────────────────
  loadDashboardData(): void {
    if (!this.selectedEquipmentId || !this.account) return;

    this.loading = true;
    this.kpis = null;
    this.destroyAllCharts();

    const structureIds = this.historyService.getAdminSuperivisedStructuresIds(
      this.account.structures ?? []
    );

    forkJoin([
      this.dashService.loadReports(structureIds, Number(this.selectedEquipmentId)),
      this.dashService.loadCmmConfigs(structureIds, Number(this.selectedEquipmentId)),
    ]).pipe(takeUntil(this.destroy$))
      .subscribe({
        next: ([reports, cmmConfigs]: [any[], any[]]) => {
          this.allReports = reports;
          this.cmmConfigs = cmmConfigs;
          this.allPeriods = this.dashService.extractPeriods(reports);
          this.allSites = this.dashService.extractSitesFromAccount(
            this.account, Number(this.selectedEquipmentId));

          const last = this.allPeriods[this.allPeriods.length - 1];
          const prev = this.allPeriods[this.allPeriods.length - 2] ?? last;

          this.selectedPeriod = last?.name ?? '';
          this.activityPeriod = last?.name ?? '';
          this.trendStart = prev?.name ?? '';
          this.trendEnd = last?.name ?? '';
          this.tatStart = prev?.name ?? '';
          this.tatEnd = last?.name ?? '';
          this.retestStart = prev?.name ?? '';
          this.retestEnd = last?.name ?? '';
          this.rejectStart = prev?.name ?? '';
          this.rejectEnd = last?.name ?? '';

          this.selectedRegion = 'all';
          this.selectedSiteId = 'all';

          this.loading = false;
          this.applyFiltersAndRender();
        },
        error: () => { this.loading = false; },
      });
  }

  // ── Filtrage + rendu ──────────────────────────────────────────────────────
  private applyFiltersAndRender(): void {
    this.filteredReports = this.dashService.filterReports(
      this.allReports, this.selectedRegion, this.selectedSiteId
    );
    const kpiReports = this.filteredReports.filter(
      r => r.period?.periodName === this.selectedPeriod
    );

    this.kpis = this.dashService.computeKpis(kpiReports);
    this.stockAlerts = this.dashService.computeStockAlerts(
      this.filteredReports, this.cmmConfigs, this.selectedPeriod
    );
    this.completeness = this.dashService.computeCompleteness(
      this.filteredReports,
      this.dashService.completenessSites(
        this.account?.structures,
        this.selectedRegion,
        this.selectedSiteId,
        Number(this.selectedEquipmentId)
      ).length ?? 0,
      this.selectedPeriod,
    );
    this.completenessDetails = this.dashService.completenessDetails(
      this.filteredReports,
      this.selectedPeriod,
      this.siteListForRegion
    );


    if (this.renderTimeout) clearTimeout(this.renderTimeout);
    this.ngZone.runOutsideAngular(() => {
      this.renderTimeout = setTimeout(() => this.renderAllCharts(), 0);
    });
  }

  private renderAllCharts(): void {
    const r = this.filteredReports;
    const ap = this.allPeriods;

    this.renderChart('chartActivity', this.dashService.buildActivityChart(r, this.activityPeriod));
    this.renderChart('chartTrend', this.dashService.buildTrendChart(r, ap, this.trendStart, this.trendEnd));
    this.renderChart('chartTat', this.dashService.buildTatChart(r, ap, this.tatStart, this.tatEnd));
    this.renderChart('chartRetest', this.dashService.buildRetestChart(r, ap, this.retestStart, this.retestEnd));
    this.renderChart('chartReject', this.dashService.buildRejectChart(r, ap, this.rejectStart, this.rejectEnd));

    const brkOpts = this.dashService.buildBreakdownChart(r);
    this.hasBreakdownData = brkOpts !== null;
    if (brkOpts) this.renderChart('chartBreakdown', brkOpts);

    if (!this.isAdmin && this.kpis) {
      this.renderChart('chartRealization', this.dashService.buildRealizationChart(this.kpis.realizationRate));
    }
  }

  private renderChart(id: string, opts: any): void {
    const el = document.querySelector(`#${id}`);
    if (!el) return;
    this.charts[id]?.destroy();
    const chart = new ApexCharts(el, opts);
    chart.render();
    this.charts[id] = chart;
  }

  private destroyAllCharts(): void {
    Object.values(this.charts).forEach(c => c?.destroy());
    this.charts = {};
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
    if (this.renderTimeout) clearTimeout(this.renderTimeout);
    this.destroyAllCharts();
  }

  // ── Helpers template ──────────────────────────────────────────────────────
  alertIcon(level: string): string {
    return level === 'critical' ? 'dangerous' : 'warning';
  }

  alertLabel(level: string): string {
    return level === 'critical' ? 'CRITIQUE' : 'SURVEILLER';
  }

  tatColor(tat: number): string {
    return tat === 0 ? '#888' : tat <= 10 ? '#28a745' : tat <= 15 ? '#f48c06' : '#e85d04';
  }

  rateColor(rate: number): string {
    return rate >= 90 ? '#28a745' : rate >= 70 ? '#f48c06' : '#e85d04';
  }

  completenessColor(rate: number): string {
    return rate >= 80 ? '#28a745' : rate >= 50 ? '#f48c06' : '#e85d04';
  }

  openCompletenessDialog(): void {
    if (!this.completenessDetails?.length) return;
    const eq = this.equipmentList.find(e => e.id === this.selectedEquipmentId);
    this.dialog.open(CompletenessDetailDialog, {
      width: '820px',
      maxWidth: '95vw',
      maxHeight: '80vh',
      data: {
        details: this.completenessDetails,
        period: this.selectedPeriod,
        equipment: eq?.name ?? '',
      },
    });
  }
}
