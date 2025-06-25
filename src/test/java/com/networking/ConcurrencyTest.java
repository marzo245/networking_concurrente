package com.networking;

import java.io.*;
import java.net.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pruebas de concurrencia para el servidor web
 * Valida el comportamiento bajo múltiples conexiones simultáneas
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ConcurrencyTest {

  private static final Logger logger = LoggerFactory.getLogger(
    ConcurrencyTest.class
  );

  private static final String SERVER_HOST = "localhost";
  private static final int HTTP_PORT = 8080;
  private static final int WEBSOCKET_PORT = 8081;
  private static final int TEST_TIMEOUT_SECONDS = 30;

  private static Thread serverThread;
  private static boolean serverStarted = false;

  @BeforeAll
  static void startServer() throws InterruptedException {
    logger.info("Iniciando servidor para pruebas de concurrencia...");

    serverThread =
      new Thread(() -> {
        try {
          Main.main(new String[] {});
        } catch (Exception e) {
          logger.error("Error iniciando servidor para pruebas", e);
        }
      });

    serverThread.setDaemon(true);
    serverThread.start();

    // Esperar a que el servidor esté listo
    int attempts = 0;
    while (attempts < 30 && !serverStarted) {
      try {
        Socket testSocket = new Socket();
        testSocket.connect(new InetSocketAddress(SERVER_HOST, HTTP_PORT), 1000);
        testSocket.close();
        serverStarted = true;
        logger.info("Servidor iniciado exitosamente");
      } catch (IOException e) {
        attempts++;
        Thread.sleep(1000);
      }
    }

    if (!serverStarted) {
      throw new RuntimeException(
        "No se pudo iniciar el servidor para las pruebas"
      );
    }
  }

  @Test
  @Order(1)
  @DisplayName("Prueba de conexiones HTTP concurrentes")
  void testConcurrentHttpConnections() throws InterruptedException {
    logger.info("Iniciando prueba de conexiones HTTP concurrentes...");

    final int NUM_THREADS = 20;
    final int REQUESTS_PER_THREAD = 10;
    final ExecutorService executor = Executors.newFixedThreadPool(NUM_THREADS);
    final CountDownLatch latch = new CountDownLatch(NUM_THREADS);
    final AtomicInteger successfulRequests = new AtomicInteger(0);
    final AtomicInteger failedRequests = new AtomicInteger(0);
    final AtomicLong totalResponseTime = new AtomicLong(0);

    for (int i = 0; i < NUM_THREADS; i++) {
      final int threadId = i;
      executor.submit(() -> {
        try {
          for (int j = 0; j < REQUESTS_PER_THREAD; j++) {
            long startTime = System.currentTimeMillis();

            if (makeHttpRequest("/")) {
              successfulRequests.incrementAndGet();
              long responseTime = System.currentTimeMillis() - startTime;
              totalResponseTime.addAndGet(responseTime);
            } else {
              failedRequests.incrementAndGet();
            }

            // Pequeña pausa entre requests
            Thread.sleep(10);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          latch.countDown();
        }
      });
    }

    boolean completed = latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    executor.shutdown();

    Assertions.assertTrue(
      completed,
      "Las pruebas concurrentes no se completaron en el tiempo esperado"
    );

    int totalRequests = NUM_THREADS * REQUESTS_PER_THREAD;
    double successRate = (double) successfulRequests.get() /
    totalRequests *
    100;
    double avgResponseTime = (double) totalResponseTime.get() /
    successfulRequests.get();

    logger.info("Resultados de pruebas HTTP concurrentes:");
    logger.info("- Total requests: {}", totalRequests);
    logger.info("- Successful: {}", successfulRequests.get());
    logger.info("- Failed: {}", failedRequests.get());
    logger.info("- Success rate: {:.2f}%", successRate);
    logger.info("- Average response time: {:.2f}ms", avgResponseTime);

    Assertions.assertTrue(
      successRate >= 95.0,
      "Success rate too low: " + successRate + "%"
    );
    Assertions.assertTrue(
      avgResponseTime < 1000,
      "Average response time too high: " + avgResponseTime + "ms"
    );
  }

  @Test
  @Order(2)
  @DisplayName("Prueba de múltiples conexiones WebSocket")
  void testConcurrentWebSocketConnections() throws InterruptedException {
    logger.info("Iniciando prueba de conexiones WebSocket concurrentes...");

    final int NUM_CONNECTIONS = 15;
    final int MESSAGES_PER_CONNECTION = 5;
    final ExecutorService executor = Executors.newFixedThreadPool(
      NUM_CONNECTIONS
    );
    final CountDownLatch latch = new CountDownLatch(NUM_CONNECTIONS);
    final AtomicInteger successfulConnections = new AtomicInteger(0);
    final AtomicInteger messagesSent = new AtomicInteger(0);
    final AtomicInteger messagesReceived = new AtomicInteger(0);

    for (int i = 0; i < NUM_CONNECTIONS; i++) {
      final int connectionId = i;
      executor.submit(() -> {
        try {
          WebSocketTestClient client = new WebSocketTestClient(
            "User" + connectionId,
            MESSAGES_PER_CONNECTION
          );

          if (client.connect()) {
            successfulConnections.incrementAndGet();

            int sent = client.sendMessages();
            messagesSent.addAndGet(sent);

            Thread.sleep(2000); // Esperar respuestas

            int received = client.getReceivedMessageCount();
            messagesReceived.addAndGet(received);

            client.disconnect();
          }
        } catch (Exception e) {
          logger.error("Error en conexión WebSocket {}", connectionId, e);
        } finally {
          latch.countDown();
        }
      });
    }

    boolean completed = latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    executor.shutdown();

    Assertions.assertTrue(
      completed,
      "Las pruebas WebSocket no se completaron en el tiempo esperado"
    );

    double connectionSuccessRate = (double) successfulConnections.get() /
    NUM_CONNECTIONS *
    100;

    logger.info("Resultados de pruebas WebSocket concurrentes:");
    logger.info("- Total connections attempted: {}", NUM_CONNECTIONS);
    logger.info("- Successful connections: {}", successfulConnections.get());
    logger.info("- Connection success rate: {:.2f}%", connectionSuccessRate);
    logger.info("- Messages sent: {}", messagesSent.get());
    logger.info("- Messages received: {}", messagesReceived.get());

    Assertions.assertTrue(
      connectionSuccessRate >= 80.0,
      "WebSocket connection success rate too low: " +
      connectionSuccessRate +
      "%"
    );
    Assertions.assertTrue(
      successfulConnections.get() >= NUM_CONNECTIONS * 0.8,
      "Too few successful WebSocket connections"
    );
  }

  @Test
  @Order(3)
  @DisplayName("Prueba de carga sostenida")
  void testSustainedLoad() throws InterruptedException {
    logger.info("Iniciando prueba de carga sostenida...");

    final int DURATION_SECONDS = 15;
    final int CONCURRENT_CLIENTS = 10;
    final ExecutorService executor = Executors.newFixedThreadPool(
      CONCURRENT_CLIENTS
    );
    final AtomicBoolean running = new AtomicBoolean(true);
    final AtomicInteger totalRequests = new AtomicInteger(0);
    final AtomicInteger successfulRequests = new AtomicInteger(0);
    final AtomicLong totalResponseTime = new AtomicLong(0);

    // Iniciar clientes que hacen requests continuamente
    for (int i = 0; i < CONCURRENT_CLIENTS; i++) {
      final int clientId = i;
      executor.submit(() -> {
        while (running.get() && !Thread.currentThread().isInterrupted()) {
          try {
            long startTime = System.currentTimeMillis();
            totalRequests.incrementAndGet();

            if (makeHttpRequest("/api/stats")) {
              successfulRequests.incrementAndGet();
              long responseTime = System.currentTimeMillis() - startTime;
              totalResponseTime.addAndGet(responseTime);
            }

            Thread.sleep(100); // 10 requests/segundo por cliente
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      });
    }

    // Ejecutar durante el tiempo especificado
    Thread.sleep(DURATION_SECONDS * 1000);
    running.set(false);

    executor.shutdown();
    boolean terminated = executor.awaitTermination(5, TimeUnit.SECONDS);

    Assertions.assertTrue(
      terminated,
      "No se pudieron terminar todos los clientes de carga"
    );

    double successRate = (double) successfulRequests.get() /
    totalRequests.get() *
    100;
    double avgResponseTime = totalResponseTime.get() > 0
      ? (double) totalResponseTime.get() / successfulRequests.get()
      : 0;
    double requestsPerSecond = (double) totalRequests.get() / DURATION_SECONDS;

    logger.info("Resultados de prueba de carga sostenida:");
    logger.info("- Duration: {}s", DURATION_SECONDS);
    logger.info("- Concurrent clients: {}", CONCURRENT_CLIENTS);
    logger.info("- Total requests: {}", totalRequests.get());
    logger.info("- Successful requests: {}", successfulRequests.get());
    logger.info("- Success rate: {:.2f}%", successRate);
    logger.info("- Average response time: {:.2f}ms", avgResponseTime);
    logger.info("- Requests per second: {:.2f}", requestsPerSecond);

    Assertions.assertTrue(
      successRate >= 95.0,
      "Success rate too low under sustained load: " + successRate + "%"
    );
    Assertions.assertTrue(
      avgResponseTime < 500,
      "Average response time too high under load: " + avgResponseTime + "ms"
    );
    Assertions.assertTrue(
      requestsPerSecond >= CONCURRENT_CLIENTS * 8,
      "Request throughput too low: " + requestsPerSecond + " req/s"
    );
  }

  @Test
  @Order(4)
  @DisplayName("Prueba de resistencia a conexiones rápidas")
  void testRapidConnections() throws InterruptedException {
    logger.info("Iniciando prueba de conexiones rápidas...");

    final int NUM_RAPID_CONNECTIONS = 50;
    final ExecutorService executor = Executors.newFixedThreadPool(20);
    final CountDownLatch latch = new CountDownLatch(NUM_RAPID_CONNECTIONS);
    final AtomicInteger successfulConnections = new AtomicInteger(0);
    final AtomicInteger connectionErrors = new AtomicInteger(0);

    long startTime = System.currentTimeMillis();

    for (int i = 0; i < NUM_RAPID_CONNECTIONS; i++) {
      executor.submit(() -> {
        try {
          Socket socket = new Socket();
          socket.connect(new InetSocketAddress(SERVER_HOST, HTTP_PORT), 5000);

          PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
          BufferedReader in = new BufferedReader(
            new InputStreamReader(socket.getInputStream())
          );

          out.println("GET / HTTP/1.1");
          out.println("Host: " + SERVER_HOST);
          out.println("Connection: close");
          out.println();

          String response = in.readLine();
          if (response != null && response.contains("200")) {
            successfulConnections.incrementAndGet();
          } else {
            connectionErrors.incrementAndGet();
          }

          socket.close();
        } catch (IOException e) {
          connectionErrors.incrementAndGet();
        } finally {
          latch.countDown();
        }
      });
    }

    boolean completed = latch.await(TEST_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    executor.shutdown();

    long totalTime = System.currentTimeMillis() - startTime;
    double connectionsPerSecond = (double) NUM_RAPID_CONNECTIONS /
    (totalTime / 1000.0);
    double successRate = (double) successfulConnections.get() /
    NUM_RAPID_CONNECTIONS *
    100;

    logger.info("Resultados de prueba de conexiones rápidas:");
    logger.info("- Total connections: {}", NUM_RAPID_CONNECTIONS);
    logger.info("- Successful: {}", successfulConnections.get());
    logger.info("- Errors: {}", connectionErrors.get());
    logger.info("- Success rate: {:.2f}%", successRate);
    logger.info("- Total time: {}ms", totalTime);
    logger.info("- Connections per second: {:.2f}", connectionsPerSecond);

    Assertions.assertTrue(
      completed,
      "Las conexiones rápidas no se completaron en el tiempo esperado"
    );
    Assertions.assertTrue(
      successRate >= 90.0,
      "Success rate too low for rapid connections: " + successRate + "%"
    );
    Assertions.assertTrue(
      connectionsPerSecond >= 10,
      "Connection rate too low: " + connectionsPerSecond + " conn/s"
    );
  }

  /**
   * Realiza una request HTTP simple
   */
  private boolean makeHttpRequest(String path) {
    try (Socket socket = new Socket(SERVER_HOST, HTTP_PORT)) {
      socket.setSoTimeout(5000);

      PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
      BufferedReader in = new BufferedReader(
        new InputStreamReader(socket.getInputStream())
      );

      out.println("GET " + path + " HTTP/1.1");
      out.println("Host: " + SERVER_HOST);
      out.println("Connection: close");
      out.println();

      String response = in.readLine();
      return response != null && response.contains("200");
    } catch (IOException e) {
      return false;
    }
  }

  /**
   * Cliente WebSocket simple para pruebas
   */
  private static class WebSocketTestClient {

    private final String username;
    private final int messagesToSend;
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;
    private final AtomicInteger receivedMessages = new AtomicInteger(0);
    private volatile boolean connected = false;

    public WebSocketTestClient(String username, int messagesToSend) {
      this.username = username;
      this.messagesToSend = messagesToSend;
    }

    public boolean connect() {
      try {
        socket = new Socket(SERVER_HOST, WEBSOCKET_PORT);
        socket.setSoTimeout(10000);

        out = new PrintWriter(socket.getOutputStream(), true);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));

        // Realizar handshake WebSocket simplificado
        out.println("GET / HTTP/1.1");
        out.println("Host: " + SERVER_HOST);
        out.println("Upgrade: websocket");
        out.println("Connection: Upgrade");
        out.println("Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==");
        out.println("Sec-WebSocket-Version: 13");
        out.println();

        // Leer respuesta de handshake
        String line;
        boolean handshakeSuccess = false;
        while ((line = in.readLine()) != null) {
          if (line.contains("101 Switching Protocols")) {
            handshakeSuccess = true;
          }
          if (line.isEmpty()) {
            break;
          }
        }

        if (handshakeSuccess) {
          connected = true;

          // Enviar mensaje de join
          sendWebSocketMessage(
            "{\"type\":\"join\",\"username\":\"" + username + "\"}"
          );

          // Iniciar listener en hilo separado
          new Thread(this::messageListener).start();

          return true;
        }
      } catch (IOException e) {
        logger.debug("Error conectando WebSocket client: {}", e.getMessage());
      }

      return false;
    }

    public int sendMessages() {
      int sent = 0;
      for (int i = 0; i < messagesToSend && connected; i++) {
        try {
          String message = "Test message " + i + " from " + username;
          sendWebSocketMessage(
            "{\"type\":\"message\",\"content\":\"" + message + "\"}"
          );
          sent++;
          Thread.sleep(200); // Pausa entre mensajes
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          break;
        }
      }
      return sent;
    }

    private void sendWebSocketMessage(String message) {
      if (connected && out != null) {
        // Simplificado: enviar como texto plano (no es WebSocket real)
        // En una implementación completa, se enviarían frames WebSocket
        try {
          byte[] payload = message.getBytes();
          socket.getOutputStream().write(0x81); // Text frame
          socket.getOutputStream().write(payload.length);
          socket.getOutputStream().write(payload);
          socket.getOutputStream().flush();
        } catch (IOException e) {
          logger.debug("Error enviando mensaje WebSocket: {}", e.getMessage());
          connected = false;
        }
      }
    }

    private void messageListener() {
      try {
        byte[] buffer = new byte[1024];
        while (connected && socket != null && !socket.isClosed()) {
          int bytesRead = socket.getInputStream().read(buffer);
          if (bytesRead > 0) {
            receivedMessages.incrementAndGet();
          }
        }
      } catch (IOException e) {
        logger.debug("WebSocket listener terminated: {}", e.getMessage());
      }
    }

    public int getReceivedMessageCount() {
      return receivedMessages.get();
    }

    public void disconnect() {
      connected = false;
      try {
        if (socket != null && !socket.isClosed()) {
          socket.close();
        }
      } catch (IOException e) {
        logger.debug("Error cerrando WebSocket client: {}", e.getMessage());
      }
    }
  }
}
