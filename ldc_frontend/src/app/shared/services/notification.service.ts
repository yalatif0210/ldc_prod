import { Injectable } from '@angular/core';
import { Client, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private stompClient!: Client;
  private subscriptions: StompSubscription[] = [];

  connect(
    strucuteId: string,
    isUserAdminOrSupervisor: boolean,
    callBack: (notif: any, id: any, isUserAdminOrSupervisor: boolean) => void
  ) {
    this.stompClient = new Client({
      //webSocketFactory: () => new SockJS('http://localhost:8081/ws'),
      webSocketFactory: () => new SockJS('/ws'),
      reconnectDelay: 5000,
    });

    this.stompClient.onConnect = () => {
      // 🟢 Notifications privées
      const url = isUserAdminOrSupervisor ? '/topic/admin' : `/topic/notifications/${strucuteId}`;
      this.subscriptions.push(
        this.stompClient.subscribe(url, (message: any) => {
          const notif = JSON.parse(message.body);
          //this.showBrowserNotification('📢 LDC Notification', notif.message);
          callBack(notif, strucuteId, isUserAdminOrSupervisor);
        })
      );

      // 🔵 Notifications globales
      this.subscriptions.push(
        this.stompClient.subscribe(`/topic/broadcast`, (message: any) => {
          const notif = JSON.parse(message.body);
          //this.showBrowserNotification('📢 LDC Notification', notif.message);
          callBack(notif, strucuteId, isUserAdminOrSupervisor);
        })
      );
    };
    // 🟢 Très important → **Activer la connexion ici**
    this.stompClient.activate();
  }

  requestBrowserPermission() {
    if (!('Notification' in window)) {
      console.warn('Notifications non supportées. - notification.service.ts:47');
      return;
    }

    Notification.requestPermission().then(permission => {
    });
  }

  private showBrowserNotification(title: string, body: string) {
    if (Notification.permission === 'granted') {
      //this.playSound();
      new Notification(title, { body });
    }
  }

  private playSound() {
    const audio = new Audio('audio/ping.mp3');
    audio.play();
  }

  disconnect() {
    this.subscriptions.forEach(s => s.unsubscribe());
    this.subscriptions = [];
    if (this.stompClient) {
      this.stompClient.deactivate();
    }
  }
}
