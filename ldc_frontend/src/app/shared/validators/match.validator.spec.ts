import { FormBuilder, Validators } from '@angular/forms';
import { matchValidator } from './match.validator';

describe('matchValidator', () => {
  const fb = new FormBuilder();

  function buildForm() {
    return fb.group(
      {
        password: ['', [Validators.required]],
        confirmPassword: ['', [Validators.required]],
      },
      { validators: [matchValidator('password', 'confirmPassword')] }
    );
  }

  it('is valid when both fields match', () => {
    const form = buildForm();
    form.setValue({ password: 'Secret1!', confirmPassword: 'Secret1!' });
    expect(form.get('confirmPassword')?.hasError('mismatch')).toBeFalse();
  });

  it('flags "mismatch" on the target field when values differ', () => {
    const form = buildForm();
    form.setValue({ password: 'Secret1!', confirmPassword: 'Different1!' });
    expect(form.get('confirmPassword')?.hasError('mismatch')).toBeTrue();
  });

  it('clears a previous mismatch once the values are edited to match again', () => {
    const form = buildForm();
    form.setValue({ password: 'Secret1!', confirmPassword: 'Different1!' });
    expect(form.get('confirmPassword')?.hasError('mismatch')).toBeTrue();

    form.setValue({ password: 'Secret1!', confirmPassword: 'Secret1!' });
    expect(form.get('confirmPassword')?.hasError('mismatch')).toBeFalse();
  });
});
