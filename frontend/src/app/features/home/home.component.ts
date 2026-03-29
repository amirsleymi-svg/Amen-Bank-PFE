import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterModule } from '@angular/router';

@Component({
  selector: 'app-home',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './home.component.html',
  styleUrls: ['./home.component.scss']
})
export class HomeComponent {
  
  features = [
    {
      title: 'Sécurité Maximale',
      desc: 'Authentification forte et cryptage de bout en bout pour protéger vos actifs.',
      icon: '🛡️'
    },
    {
      title: 'Virements Instantanés',
      desc: 'Envoyez de l\'argent en quelques secondes à vos bénéficiaires.',
      icon: '⚡'
    },
    {
      title: 'Crédits sur mesure',
      desc: 'Des solutions de financement adaptées à vos projets personnels et professionnels.',
      icon: '📈'
    },
    {
      title: 'Support 24/7',
      desc: 'Notre assistant intelligent et nos experts sont là pour vous aider à tout moment.',
      icon: '💬'
    }
  ];

  stats = [
    { val: '1M+', label: 'Clients satisfaits' },
    { val: '450+', label: 'Agences en Tunisie' },
    { val: '24/7', label: 'Disponibilité' },
    { val: '0 dt', label: 'Frais de tenue (Silver)' }
  ];
}
