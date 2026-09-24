import { Routes } from '@angular/router';
import { superAdminGuard } from '@core/authentication/role-guard';

export const routes: Routes = [
  {
    path: '',
    loadComponent: () => import('./dashboard/super-admin-dashboard').then(m => m.SuperAdminDashboard),
    canActivate: [superAdminGuard],
  },
  {
    path: 'periods',
    loadComponent: () => import('./periods/period-admin').then(m => m.PeriodAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'reports',
    loadComponent: () => import('./reports/report-admin').then(m => m.ReportAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'users',
    loadComponent: () => import('./users/user-admin').then(m => m.UserAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'roles',
    loadComponent: () => import('./roles/role-admin').then(m => m.RoleAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'structures',
    loadComponent: () => import('./structures/structure-admin').then(m => m.StructureAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'regions',
    loadComponent: () => import('./regions/region-admin').then(m => m.RegionAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'districts',
    loadComponent: () => import('./districts/district-admin').then(m => m.DistrictAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'synthesis',
    loadComponent: () => import('./synthesis/synthesis-admin').then(m => m.SynthesisAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'intrants',
    loadComponent: () => import('./intrants/intrant-admin').then(m => m.IntrantAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'transactions',
    loadComponent: () => import('./transactions/transaction-admin').then(m => m.TransactionAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'notifications',
    loadComponent: () =>
      import('./notifications/notification-admin').then(m => m.NotificationAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'information-units',
    loadComponent: () =>
      import('./information-units/information-unit-admin').then(m => m.InformationUnitAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'information-sub-units',
    loadComponent: () =>
      import('./information-sub-units/information-sub-unit-admin').then(m => m.InformationSubUnitAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'information-sub-sub-units',
    loadComponent: () =>
      import('./information-sub-sub-units/information-sub-sub-unit-admin').then(
        m => m.InformationSubSubUnitAdmin,
      ),
    canActivate: [superAdminGuard],
  },
  {
    path: 'equipments',
    loadComponent: () => import('./equipments/equipment-admin').then(m => m.EquipmentAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'sanguine-products',
    loadComponent: () => import('./sanguine-products/sanguine-product-admin').then(m => m.SanguineProductAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'statuses',
    loadComponent: () => import('./statuses/status-admin').then(m => m.StatusAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'months',
    loadComponent: () => import('./months/month-admin').then(m => m.MonthAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'adjustment-types',
    loadComponent: () => import('./adjustment-types/adjustment-type-admin').then(m => m.AdjustmentTypeAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'intrant-types',
    loadComponent: () => import('./intrant-types/intrant-type-admin').then(m => m.IntrantTypeAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'synthesis-types',
    loadComponent: () => import('./synthesis-types/synthesis-type-admin').then(m => m.SynthesisTypeAdmin),
    canActivate: [superAdminGuard],
  },
  {
    path: 'system',
    loadComponent: () => import('./system/system-logs').then(m => m.SystemLogs),
    canActivate: [superAdminGuard],
  },
];
