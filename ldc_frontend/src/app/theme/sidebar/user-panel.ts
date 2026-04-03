import { Component, OnInit, OnDestroy, ViewEncapsulation, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink } from '@angular/router';
import { Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';
import { AuthService, User } from '@core/authentication';
import { TranslateModule } from '@ngx-translate/core';

@Component({
  selector: 'app-user-panel',
  template: `
    <div class="matero-user-panel" routerLink="/profile/overview">
      <img class="matero-user-panel-avatar" [src]="user.avatar" alt="avatar" width="64" />
      <div class="matero-user-panel-info">
        <h4>{{ user.name }}</h4>
        <h5>{{ user.email }}</h5>
      </div>
    </div>
  `,
  styleUrl: './user-panel.scss',
  encapsulation: ViewEncapsulation.None,
  imports: [RouterLink, MatButtonModule, MatIconModule, MatTooltipModule, TranslateModule],
})
export class UserPanel implements OnInit, OnDestroy {
  private readonly auth = inject(AuthService);
  private readonly destroy$ = new Subject<void>();

  user!: User;

  ngOnInit(): void {
    this.auth.user()
      .pipe(takeUntil(this.destroy$))
      .subscribe(user => (this.user = user));
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
