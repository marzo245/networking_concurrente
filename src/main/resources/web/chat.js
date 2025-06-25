/**
 * Cliente JavaScript para el chat concurrente
 * Maneja la comunicación WebSocket y la interfaz de usuario
 */

class ConcurrentChat {
    constructor() {
        this.websocket = null;
        this.username = null;
        this.connected = false;
        this.reconnectAttempts = 0;
        this.maxReconnectAttempts = 5;
        this.startTime = Date.now();
        
        this.init();
    }
    
    init() {
        this.bindEvents();
        this.loadServerStats();
        this.startStatsUpdater();
        this.startUptimeUpdater();
    }
    
    bindEvents() {
        // Join form
        const joinButton = document.getElementById('joinButton');
        const usernameInput = document.getElementById('usernameInput');
        
        joinButton.addEventListener('click', () => this.joinChat());
        usernameInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.joinChat();
        });
        
        usernameInput.addEventListener('input', () => {
            const username = usernameInput.value.trim();
            joinButton.disabled = username.length < 2;
        });
        
        // Message form
        const sendButton = document.getElementById('sendButton');
        const messageInput = document.getElementById('messageInput');
        
        sendButton.addEventListener('click', () => this.sendMessage());
        messageInput.addEventListener('keypress', (e) => {
            if (e.key === 'Enter') this.sendMessage();
        });
        
        messageInput.addEventListener('input', () => this.updateCharacterCount());
        
        // Page visibility API para reconectar cuando la página vuelve a estar visible
        document.addEventListener('visibilitychange', () => {
            if (!document.hidden && !this.connected && this.username) {
                setTimeout(() => this.connectWebSocket(), 1000);
            }
        });
        
        // Prevenir cierre accidental de la página cuando se está en el chat
        window.addEventListener('beforeunload', (e) => {
            if (this.connected) {
                e.preventDefault();
                e.returnValue = '¿Estás seguro de que quieres salir del chat?';
                return e.returnValue;
            }
        });
    }
    
    async joinChat() {
        const usernameInput = document.getElementById('usernameInput');
        const username = usernameInput.value.trim();
        
        if (username.length < 2) {
            this.showError('El nombre de usuario debe tener al menos 2 caracteres');
            return;
        }
        
        if (username.length > 20) {
            this.showError('El nombre de usuario no puede tener más de 20 caracteres');
            return;
        }
        
        // Validar caracteres especiales
        if (!/^[a-zA-Z0-9_\-\s]+$/.test(username)) {
            this.showError('El nombre de usuario solo puede contener letras, números, guiones y espacios');
            return;
        }
        
        this.username = username;
        
        try {
            // Crear sesión HTTP
            await this.createSession();
            
            // Conectar WebSocket
            this.connectWebSocket();
            
        } catch (error) {
            console.error('Error joining chat:', error);
            this.showError('Error conectando al servidor. Inténtalo de nuevo.');
        }
    }
    
    async createSession() {
        try {
            const response = await fetch('/api/session', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            if (!response.ok) {
                throw new Error('Failed to create session');
            }
            
            const data = await response.json();
            console.log('Session created:', data.sessionId);
            
        } catch (error) {
            console.error('Error creating session:', error);
            // Continue anyway, session is optional for chat functionality
        }
    }
    
    connectWebSocket() {
        if (this.websocket) {
            this.websocket.close();
        }
        
        const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
        const wsUrl = `${protocol}//${window.location.hostname}:8081`;
        
        console.log('Connecting to WebSocket:', wsUrl);
        
        try {
            this.websocket = new WebSocket(wsUrl);
            
            this.websocket.onopen = () => {
                console.log('WebSocket connected');
                this.connected = true;
                this.reconnectAttempts = 0;
                this.updateConnectionStatus(true);
                this.joinChatRoom();
                this.showChatScreen();
            };
            
            this.websocket.onmessage = (event) => {
                this.handleMessage(event.data);
            };
            
            this.websocket.onclose = (event) => {
                console.log('WebSocket closed:', event.code, event.reason);
                this.connected = false;
                this.updateConnectionStatus(false);
                this.handleDisconnection();
            };
            
            this.websocket.onerror = (error) => {
                console.error('WebSocket error:', error);
                this.showError('Error de conexión WebSocket');
            };
            
        } catch (error) {
            console.error('Error creating WebSocket:', error);
            this.showError('No se pudo conectar al servidor de chat');
        }
    }
    
    joinChatRoom() {
        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            const joinMessage = {
                type: 'join',
                username: this.username
            };
            this.websocket.send(JSON.stringify(joinMessage));
        }
    }
    
    sendMessage() {
        const messageInput = document.getElementById('messageInput');
        const content = messageInput.value.trim();
        
        if (!content) return;
        
        if (content.length > 500) {
            this.showError('El mensaje no puede tener más de 500 caracteres');
            return;
        }
        
        if (this.websocket && this.websocket.readyState === WebSocket.OPEN) {
            const message = {
                type: 'message',
                content: content
            };
            
            this.websocket.send(JSON.stringify(message));
            messageInput.value = '';
            this.updateCharacterCount();
            
            // Focus back to input
            messageInput.focus();
        } else {
            this.showError('No hay conexión con el servidor');
        }
    }
    
    handleMessage(data) {
        try {
            const message = JSON.parse(data);
            
            switch (message.type) {
                case 'message':
                    this.displayMessage(message);
                    break;
                case 'notification':
                    this.displayNotification(message.message);
                    break;
                default:
                    console.log('Unknown message type:', message);
            }
        } catch (error) {
            console.error('Error parsing message:', error);
        }
    }
    
    displayMessage(message) {
        const messagesContainer = document.getElementById('messagesContainer');
        const messageDiv = document.createElement('div');
        
        const isOwnMessage = message.username === this.username;
        messageDiv.className = `message ${isOwnMessage ? 'own' : 'other'}`;
        
        const timestamp = new Date(message.timestamp || Date.now());
        const timeString = timestamp.toLocaleTimeString('es-ES', { 
            hour: '2-digit', 
            minute: '2-digit' 
        });
        
        messageDiv.innerHTML = `
            <div class="message-header">
                <span class="message-username">${this.escapeHtml(message.username)}</span>
                <span class="message-time">${timeString}</span>
            </div>
            <div class="message-content">${this.escapeHtml(message.content)}</div>
        `;
        
        messagesContainer.appendChild(messageDiv);
        this.scrollToBottom();
        
        // Remove welcome message if it exists
        const welcomeMessage = messagesContainer.querySelector('.welcome-message');
        if (welcomeMessage) {
            welcomeMessage.remove();
        }
    }
    
    displayNotification(text) {
        const messagesContainer = document.getElementById('messagesContainer');
        const notificationDiv = document.createElement('div');
        
        notificationDiv.className = 'message notification';
        notificationDiv.innerHTML = `
            <div class="message-content">${this.escapeHtml(text)}</div>
        `;
        
        messagesContainer.appendChild(notificationDiv);
        this.scrollToBottom();
    }
    
    handleDisconnection() {
        if (this.username && this.reconnectAttempts < this.maxReconnectAttempts) {
            const delay = Math.min(1000 * Math.pow(2, this.reconnectAttempts), 10000);
            this.reconnectAttempts++;
            
            console.log(`Attempting to reconnect in ${delay}ms (attempt ${this.reconnectAttempts})`);
            
            setTimeout(() => {
                if (!this.connected) {
                    this.connectWebSocket();
                }
            }, delay);
        } else if (this.reconnectAttempts >= this.maxReconnectAttempts) {
            this.showError('No se pudo reconectar al servidor. Recarga la página para intentar de nuevo.');
        }
    }
    
    showChatScreen() {
        document.getElementById('welcomeScreen').classList.add('hidden');
        document.getElementById('chatScreen').classList.remove('hidden');
        document.getElementById('chatScreen').classList.add('fade-in');
        
        // Focus on message input
        setTimeout(() => {
            document.getElementById('messageInput').focus();
        }, 100);
    }
    
    updateConnectionStatus(connected) {
        const statusIndicator = document.getElementById('connectionStatus');
        if (connected) {
            statusIndicator.textContent = '🟢 Conectado';
            statusIndicator.classList.add('connected');
        } else {
            statusIndicator.textContent = '🔴 Desconectado';
            statusIndicator.classList.remove('connected');
        }
    }
    
    updateCharacterCount() {
        const messageInput = document.getElementById('messageInput');
        const characterCount = document.getElementById('characterCount');
        const currentLength = messageInput.value.length;
        
        characterCount.textContent = `${currentLength}/500`;
        
        // Update styling based on character count
        characterCount.classList.remove('warning', 'danger');
        if (currentLength > 400) {
            characterCount.classList.add('warning');
        }
        if (currentLength > 450) {
            characterCount.classList.add('danger');
        }
        
        // Disable send button if too long
        const sendButton = document.getElementById('sendButton');
        sendButton.disabled = currentLength > 500 || currentLength === 0;
    }
    
    scrollToBottom() {
        const messagesContainer = document.getElementById('messagesContainer');
        messagesContainer.scrollTop = messagesContainer.scrollHeight;
    }
    
    showError(message) {
        // Simple error display - could be enhanced with a proper toast/modal system
        alert(message);
    }
    
    escapeHtml(text) {
        const div = document.createElement('div');
        div.textContent = text;
        return div.innerHTML;
    }
    
    async loadServerStats() {
        try {
            const response = await fetch('/api/stats');
            if (response.ok) {
                const stats = await response.json();
                this.updateServerStats(stats);
            }
        } catch (error) {
            console.error('Error loading server stats:', error);
        }
    }
    
    updateServerStats(stats) {
        document.getElementById('activeThreads').textContent = stats.activeThreads || '-';
        document.getElementById('poolSize').textContent = stats.poolSize || '-';
        document.getElementById('queueSize').textContent = stats.queueSize || '-';
        document.getElementById('totalRequests').textContent = stats.totalRequests || '-';
    }
    
    startStatsUpdater() {
        // Update server stats every 10 seconds
        setInterval(() => {
            if (!document.hidden) {
                this.loadServerStats();
            }
        }, 10000);
    }
    
    startUptimeUpdater() {
        setInterval(() => {
            const uptime = Date.now() - this.startTime;
            const uptimeString = this.formatUptime(uptime);
            document.getElementById('uptimeInfo').textContent = `Tiempo activo: ${uptimeString}`;
        }, 1000);
    }
    
    formatUptime(milliseconds) {
        const seconds = Math.floor(milliseconds / 1000);
        const minutes = Math.floor(seconds / 60);
        const hours = Math.floor(minutes / 60);
        
        if (hours > 0) {
            return `${hours}h ${minutes % 60}m ${seconds % 60}s`;
        } else if (minutes > 0) {
            return `${minutes}m ${seconds % 60}s`;
        } else {
            return `${seconds}s`;
        }
    }
}

// Initialize the chat application when the page loads
document.addEventListener('DOMContentLoaded', () => {
    window.chat = new ConcurrentChat();
});

// Expose some methods for debugging
window.chatDebug = {
    getWebSocket: () => window.chat?.websocket,
    getConnectionState: () => window.chat?.connected,
    reconnect: () => window.chat?.connectWebSocket(),
    sendTestMessage: (message) => {
        if (window.chat?.websocket?.readyState === WebSocket.OPEN) {
            window.chat.websocket.send(JSON.stringify({
                type: 'message',
                content: message || 'Test message from console'
            }));
        }
    }
};
