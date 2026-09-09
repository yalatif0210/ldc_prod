import { AbstractControl, ValidatorFn } from '@angular/forms';

/**
 * Valide que deux champs d'un même FormGroup ont la même valeur
 * (ex. mot de passe / confirmation), en posant l'erreur "mismatch"
 * sur le champ cible.
 */
export function matchValidator(source: string, target: string): ValidatorFn {
  return (control: AbstractControl) => {
    const sourceControl = control.get(source)!;
    const targetControl = control.get(target)!;
    if (targetControl.errors && !targetControl.errors.mismatch) {
      return null;
    }
    if (sourceControl.value !== targetControl.value) {
      targetControl.setErrors({ mismatch: true });
      return { mismatch: true };
    } else {
      targetControl.setErrors(null);
      return null;
    }
  };
}
