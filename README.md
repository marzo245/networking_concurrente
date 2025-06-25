# Servidor Web Concurrente con Chat

## Descripción
Un servidor web concurrente implementado en Java que utiliza pools de hilos para manejar múltiples conexiones simultáneas. Incluye un sistema de chat en tiempo real usando WebSockets.

## Características
- ✅ Servidor web concurrente con thread pools
- ✅ Chat en tiempo real con WebSockets
- ✅ Interfaz web moderna y responsiva
- ✅ Manejo de cookies y sesiones
- ✅ Soporte para múltiples usuarios
- ✅ Pruebas concurrentes automatizadas
- ✅ Logging detallado

## Arquitectura
- `HttpServer`: Servidor HTTP principal con pool de hilos
- `WebSocketServer`: Servidor WebSocket para chat en tiempo real
- `ChatRoom`: Gestión de salas de chat y usuarios
- `SessionManager`: Manejo de sesiones y cookies
- `ThreadPoolManager`: Gestión optimizada de pools de hilos

## Tecnologías
- Java 11+
- WebSockets nativos de Java
- HTML5/CSS3/JavaScript
- JUnit 5 para pruebas
- Maven para gestión de dependencias

## Estructura del Proyecto
```
src/
├── main/
│   ├── java/
│   │   └── com/
│   │       └── networking/
│   │           ├── server/
│   │           │   ├── HttpServer.java
│   │           │   ├── WebSocketServer.java
│   │           │   └── ThreadPoolManager.java
│   │           ├── chat/
│   │           │   ├── ChatRoom.java
│   │           │   ├── ChatUser.java
│   │           │   └── ChatMessage.java
│   │           ├── session/
│   │           │   ├── SessionManager.java
│   │           │   └── CookieHandler.java
│   │           └── Main.java
│   └── resources/
│       └── web/
│           ├── index.html
│           ├── chat.html
│           ├── style.css
│           └── chat.js
└── test/
    └── java/
        └── com/
            └── networking/
                ├── ConcurrencyTest.java
                ├── LoadTest.java
                └── ChatTest.java
```

## Instrucciones de Uso

### Compilar y Ejecutar
```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.networking.Main"
```

### Acceder al Chat
1. Abrir http://localhost:8080 en el navegador
2. Ingresar nombre de usuario
3. Comenzar a chatear en tiempo real

### Ejecutar Pruebas
```bash
mvn test
```

### Pruebas de Carga
```bash
mvn test -Dtest=LoadTest
```

## Funcionalidades del Chat
- Mensajes en tiempo real
- Múltiples usuarios simultáneos
- Notificaciones de conexión/desconexión
- Historial de mensajes
- Interfaz responsiva

## Configuración
- Puerto HTTP: 8080
- Puerto WebSocket: 8081
- Máximo hilos: 50
- Timeout conexión: 30 segundos

## Autores
- Diego Chicuazuque
- Estudiante íngenieria de sistemas

## Licencia
MIT License
