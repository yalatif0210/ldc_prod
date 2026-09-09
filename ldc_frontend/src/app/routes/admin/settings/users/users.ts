import { KeyValue, KeyValuePipe } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { DateAdapter, MatOptionModule } from '@angular/material/core';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatStepperModule } from '@angular/material/stepper';
import { AuthService } from '@core';
import { MtxGridModule } from '@ng-matero/extensions/grid';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

import {  FormBaseComponent, PageHeader } from '@shared';
import {
  RoleInterface,
  RegionInterface,
  StructureInterface,
  AccountInterface,
} from '@shared/models/model.interface';
import { UserManagementService } from '@shared/services/user-management.service';
import { forkJoin, Subscription } from 'rxjs';
import { AppTable } from '@shared/components/table/app-table';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { isAllSelected, toggleAllSelection } from '@shared/utils/multi-select.util';

@Component({
  selector: 'app-users',
  standalone: true,
  templateUrl: './users.html',
  styleUrl: './users.scss',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDatepickerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatOptionModule,
    MatSelectModule,
    TranslateModule,
    MatStepperModule,
    MtxGridModule,
    AppTable,
    MatCheckboxModule
],
})
export class Users extends FormBaseComponent implements OnInit, OnDestroy {
  private readonly dateAdapter = inject(DateAdapter);
  private readonly translate = inject(TranslateService);
  protected readonly service = inject(UserManagementService);
  private readonly authService = inject(AuthService);
  roles: RoleInterface[] = [];
  regions: RegionInterface[] = [];
  plateformes: StructureInterface[] = [];
  filteredPlatforms: StructureInterface[] = [];
  reactiveStep1: FormGroup | undefined;
  reactiveStep2: FormGroup | undefined;

  isLinear = false;
  isSubmitting = false;
  isSuperAdminRole = false;
  showSelectAllShortcut = false;

  constructor() {
    super();
    this.reactiveStep1 = this.buildFormFromArray([
      { key: 'role', defaultValue: '', validators: [Validators.required] },
      { key: 'region', defaultValue: '', validators: [Validators.required] },
      { key: 'platform', defaultValue: '', validators: [Validators.required] },
    ]);

    this.reactiveStep2 = this.buildFormFromArray([
      { key: 'name', defaultValue: '', validators: [Validators.required] },
      { key: 'phone', defaultValue: '', validators: [Validators.required] },
    ]);
  }

  private translateSubscription = Subscription.EMPTY;

  ngOnInit(): void {
    this.service.clearList();
    this.reactiveStep1!.get('region')?.valueChanges.subscribe(selectedRegion => {
      this.onRegionChange(selectedRegion);
    });
    this.reactiveStep1!.get('role')?.valueChanges.subscribe(selectedRole => {
      this.onRoleChange(selectedRole);
    });
    this.translateSubscription = this.translate.onLangChange.subscribe((res: { lang: any }) => {
      this.dateAdapter.setLocale(res.lang);
    });
    forkJoin([
      this.service.getUserRolesFromRemote(),
      this.service.getRegion(),
      this.service.getStructure(),
      this.service.getAccount(),
    ]).subscribe(([roles, regions, plateformes, accounts]) => {
      this.roles = roles.data.roles.filter((el: any) => el.id != 1) as RoleInterface[];
      this.regions = regions.data.regions as RegionInterface[];
      this.plateformes = plateformes.data.structures as StructureInterface[];
      this.service.setList(
        this.service.accountsToList(accounts.data.accounts as AccountInterface[])
      );
      //this.filteredPlateformes = plateformes.data.structures as Structure[]
    });
  }

  submitForm(form: FormGroup): void {
    if (this.isSubmitting) return;
    this.isSubmitting = true;
    const password = this.authService.buildPassword();
    this.authService
      .register(this.authService.getNewUser(this.reactiveStep1?.value, form.value, password))
      .subscribe({
        next: (value: any) => {
          this._dialog_props = {
            title: 'IDENTIFIANTS :',
            content: `Username : ${value.username}     ||     Mot de passe : ${password}`,
          };
          this.openDialog();
          this.service.onCreate();
        },
        complete: () => { this.isSubmitting = false; },
        error: () => { this.isSubmitting = false; },
      });
  }

  ngOnDestroy() {
    this.translateSubscription.unsubscribe();
  }

  onRegionChange(region: any[]) {
    this.filteredPlatforms = this.plateformes.filter(element =>
      region.includes(element!.district!.region!.id) && element.active
    );

    // Purge les plateformes sélectionnées qui ne sont plus proposées après le
    // changement de région, pour éviter d'envoyer des ids obsolètes (et une
    // case "Tout sélectionner" faussement cochée).
    const validIds = this.filteredPlatforms.map(p => p.id);
    const platformControl = this.reactiveStep1!.get('platform')!;
    const current: any[] = platformControl.value || [];
    const pruned = current.filter((id: any) => validIds.includes(id));
    if (pruned.length !== current.length) {
      platformControl.setValue(pruned);
    }
  }

  onRoleChange(role: any): void {
    const roleId = Number(role);
    this.isSuperAdminRole = this.authService.isSuperAdminUser(roleId);
    this.showSelectAllShortcut =
      this.authService.isUserAdminOrSupervisor(roleId) && !this.isSuperAdminRole;

    const regionControl = this.reactiveStep1!.get('region')!;
    const platformControl = this.reactiveStep1!.get('platform')!;
    if (this.isSuperAdminRole) {
      regionControl.clearValidators();
      platformControl.clearValidators();
      regionControl.setValue([]);
      platformControl.setValue([]);
    } else {
      regionControl.setValidators([Validators.required]);
      platformControl.setValidators([Validators.required]);
    }
    regionControl.updateValueAndValidity();
    platformControl.updateValueAndValidity();
  }

  isAllRegionsSelected(): boolean {
    return isAllSelected(this.reactiveStep1!.get('region')!.value, this.regions.map(r => r.id!));
  }

  toggleAllRegions(): void {
    this.reactiveStep1!.get('region')!.setValue(
      toggleAllSelection(this.reactiveStep1!.get('region')!.value, this.regions.map(r => r.id!))
    );
  }

  isAllPlatformsSelected(): boolean {
    return isAllSelected(
      this.reactiveStep1!.get('platform')!.value,
      this.filteredPlatforms.map(p => p.id!)
    );
  }

  toggleAllPlatforms(): void {
    this.reactiveStep1!.get('platform')!.setValue(
      toggleAllSelection(
        this.reactiveStep1!.get('platform')!.value,
        this.filteredPlatforms.map(p => p.id!)
      )
    );
  }
}
