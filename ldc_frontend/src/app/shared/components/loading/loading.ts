import { Component, Input, ViewEncapsulation } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

@Component({
  selector: 'loading-component',
  templateUrl: './loading.html',
  standalone: true,
  encapsulation: ViewEncapsulation.None,
  imports: [MatButtonModule, MatCardModule, MatProgressSpinnerModule],
})
export class LoadingComponent {
  @Input() size= 36;
  @Input() title = 'Chargement des données…';
}
