import { Component, inject } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ToastrService } from 'ngx-toastr';
import { AuthService } from '@core/authentication';
import { matchValidator } from '@shared/validators/match.validator';

// Même politique que côté serveur (SignupRequest.password) : 8+ caractères,
// au moins une majuscule, un chiffre et un caractère spécial.
const PASSWORD_PATTERN = /^(?=.*[A-Z])(?=.*[0-9])(?=.*[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]).+$/;

@Component({
  selector: 'app-profile-settings',
  templateUrl: './settings.html',
  styleUrl: './settings.scss',
  imports: [
    FormsModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
  ],
})
export class ProfileSettings {
  private readonly fb = inject(FormBuilder);
  private readonly authService = inject(AuthService);
  private readonly toast = inject(ToastrService);

  isSubmitting = false;

  reactiveForm = this.fb.nonNullable.group(
    {
      currentPassword: ['', [Validators.required]],
      newPassword: [
        '',
        [
          Validators.required,
          Validators.minLength(8),
          Validators.maxLength(64),
          Validators.pattern(PASSWORD_PATTERN),
        ],
      ],
      confirmPassword: ['', [Validators.required]],
    },
    {
      validators: [matchValidator('newPassword', 'confirmPassword')],
    }
  );

  submit(): void {
    if (this.reactiveForm.invalid || this.isSubmitting) {
      return;
    }
    this.isSubmitting = true;
    const { currentPassword, newPassword } = this.reactiveForm.getRawValue();
    this.authService.changePassword(currentPassword, newPassword).subscribe({
      next: () => {
        this.toast.success('Mot de passe modifié avec succès.');
        this.reactiveForm.reset();
        this.isSubmitting = false;
      },
      error: () => {
        // L'erreur (mot de passe actuel incorrect, politique de complexité...) est
        // déjà affichée par errorInterceptor.
        this.isSubmitting = false;
      },
    });
  }
}
