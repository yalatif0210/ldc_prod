import { Component, inject } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule, MatDialogRef, MAT_DIALOG_DATA } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-reset-password-dialog',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatDialogModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
  ],
  template: `
    <h2 mat-dialog-title>Réinitialiser le mot de passe</h2>
    <mat-dialog-content>
      <p style="margin:0 0 12px; color:#555;">
        Compte : <strong>{{ data.account?.user?.name ?? data.account?.name }}</strong>
      </p>
      <mat-form-field appearance="outline" style="width:100%;">
        <mat-label>Nouveau mot de passe</mat-label>
        <input matInput [type]="hide ? 'password' : 'text'" [formControl]="passwordCtrl" />
        <button mat-icon-button matSuffix (click)="hide = !hide" type="button">
          <mat-icon>{{ hide ? 'visibility_off' : 'visibility' }}</mat-icon>
        </button>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button (click)="dialogRef.close()">Annuler</button>
      <button mat-raised-button color="primary" (click)="confirm()" [disabled]="passwordCtrl.invalid">
        Confirmer
      </button>
    </mat-dialog-actions>
  `,
})
export class ResetPasswordDialog {
  data       = inject(MAT_DIALOG_DATA);
  dialogRef  = inject(MatDialogRef<ResetPasswordDialog>);
  hide       = true;
  passwordCtrl = new FormControl('', [Validators.required, Validators.minLength(6)]);

  confirm(): void {
    if (this.passwordCtrl.invalid) return;
    this.dialogRef.close(this.passwordCtrl.value);
  }
}
