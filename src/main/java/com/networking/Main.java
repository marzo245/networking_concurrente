package com.networking;

import com.networking.server.HttpServer;
import com.networking.server.WebSocketServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Clase principal que inicia el servidor web concurrente y el servidor WebSocket para chat
 */
public class Main {

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  private static final int HTTP_PORT = 8080;
  private static final int WEBSOCKET_PORT = 8081;

  public static void main(String[] args) {
    try {
      logger.info("Iniciando Servidor Web Concurrente...");

      // Iniciar servidor HTTP
      HttpServer httpServer = new HttpServer(HTTP_PORT);
      Thread httpThread = new Thread(httpServer::start);
      httpThread.setName("HTTP-Server");
      httpThread.start();

      // Iniciar servidor WebSocket para chat
      WebSocketServer webSocketServer = new WebSocketServer(WEBSOCKET_PORT);
      Thread wsThread = new Thread(webSocketServer::start);
      wsThread.setName("WebSocket-Server");
      wsThread.start();

      logger.info("Servidores iniciados:");
      logger.info("- Servidor HTTP: http://localhost:{}", HTTP_PORT);
      logger.info("- Servidor WebSocket: ws://localhost:{}", WEBSOCKET_PORT);
      logger.info("Presiona Ctrl+C para detener los servidores");

      // Hook para shutdown graceful
      Runtime
        .getRuntime()
        .addShutdownHook(
          new Thread(() -> {
            logger.info("Deteniendo servidores...");
            httpServer.stop();
            webSocketServer.stop();
            logger.info("Servidores detenidos exitosamente");
          })
        );

      // Mantener el hilo principal vivo
      httpThread.join();
      wsThread.join();
    } catch (Exception e) {
      logger.error("Error al iniciar los servidores", e);
      System.exit(1);
    }
  }
}
