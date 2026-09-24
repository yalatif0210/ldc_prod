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
    path: 'system',
    loadComponent: () => import('./system/system-logs').then(m => m.SystemLogs),
    canActivate: [superAdminGuard],
  },
];
