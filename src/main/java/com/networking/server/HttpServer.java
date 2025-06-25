package com.networking.server;

import com.networking.session.SessionManager;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servidor HTTP concurrente que utiliza pools de hilos para manejar múltiples conexiones
 */
public class HttpServer {

  private static final Logger logger = LoggerFactory.getLogger(
    HttpServer.class
  );

  private final int port;
  private final ThreadPoolManager threadPool;
  private final SessionManager sessionManager;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicLong requestCounter = new AtomicLong(0);
  private ServerSocket serverSocket;

  // Configuración
  private static final String WEB_ROOT = "src/main/resources/web";
  private static final String DEFAULT_FILE = "index.html";
  private static final int SOCKET_TIMEOUT = 30000; // 30 segundos

  // MIME types
  private static final Map<String, String> MIME_TYPES = Map.of(
    "html",
    "text/html",
    "css",
    "text/css",
    "js",
    "application/javascript",
    "json",
    "application/json",
    "png",
    "image/png",
    "jpg",
    "image/jpeg",
    "gif",
    "image/gif",
    "ico",
    "image/x-icon"
  );

  public HttpServer(int port) {
    this.port = port;
    this.threadPool = new ThreadPoolManager();
    this.sessionManager = new SessionManager();
  }

  /**
   * Inicia el servidor HTTP
   */
  public void start() {
    try {
      serverSocket = new ServerSocket(port);
      running.set(true);

      logger.info("Servidor HTTP iniciado en puerto {}", port);

      while (running.get()) {
        try {
          Socket clientSocket = serverSocket.accept();
          clientSocket.setSoTimeout(SOCKET_TIMEOUT);

          // Procesar la conexión en el pool de hilos
          threadPool.execute(() -> handleClient(clientSocket));
        } catch (SocketTimeoutException e) {
          // Timeout normal, continuar
        } catch (IOException e) {
          if (running.get()) {
            logger.error("Error aceptando conexión", e);
          }
        }
      }
    } catch (IOException e) {
      logger.error("Error iniciando servidor HTTP", e);
    } finally {
      cleanup();
    }
  }

  /**
   * Detiene el servidor
   */
  public void stop() {
    logger.info("Deteniendo servidor HTTP...");
    running.set(false);

    try {
      if (serverSocket != null && !serverSocket.isClosed()) {
        serverSocket.close();
      }
    } catch (IOException e) {
      logger.error("Error cerrando server socket", e);
    }

    cleanup();
  }

  /**
   * Maneja una conexión de cliente individual
   */
  private void handleClient(Socket clientSocket) {
    long requestId = requestCounter.incrementAndGet();
    String clientInfo = clientSocket.getRemoteSocketAddress().toString();

    logger.debug("Procesando request #{} de {}", requestId, clientInfo);

    try (
      BufferedReader in = new BufferedReader(
        new InputStreamReader(clientSocket.getInputStream())
      );
      OutputStream out = clientSocket.getOutputStream()
    ) {
      // Parsear request HTTP
      HttpRequest request = parseHttpRequest(in);

      if (request != null) {
        // Procesar request y generar response
        HttpResponse response = processRequest(request);

        // Enviar response
        sendResponse(out, response);

        logger.debug(
          "Request #{} completado: {} {}",
          requestId,
          request.method,
          request.path
        );
      }
    } catch (Exception e) {
      logger.error(
        "Error procesando request #{} de {}",
        requestId,
        clientInfo,
        e
      );
      try {
        sendErrorResponse(
          clientSocket.getOutputStream(),
          500,
          "Internal Server Error"
        );
      } catch (IOException ex) {
        logger.error("Error enviando error response", ex);
      }
    } finally {
      try {
        clientSocket.close();
      } catch (IOException e) {
        logger.debug("Error cerrando socket cliente", e);
      }
    }
  }

  /**
   * Parsea un request HTTP
   */
  private HttpRequest parseHttpRequest(BufferedReader in) throws IOException {
    String requestLine = in.readLine();
    if (requestLine == null) {
      return null;
    }

    String[] parts = requestLine.split(" ");
    if (parts.length != 3) {
      throw new IOException("Request line inválida: " + requestLine);
    }

    String method = parts[0];
    String path = parts[1];
    String version = parts[2];

    // Parsear headers
    Map<String, String> headers = new HashMap<>();
    String line;
    while ((line = in.readLine()) != null && !line.isEmpty()) {
      int colonIndex = line.indexOf(':');
      if (colonIndex > 0) {
        String name = line.substring(0, colonIndex).trim().toLowerCase();
        String value = line.substring(colonIndex + 1).trim();
        headers.put(name, value);
      }
    }

    // Leer body si existe
    String body = null;
    String contentLength = headers.get("content-length");
    if (contentLength != null) {
      try {
        int length = Integer.parseInt(contentLength);
        char[] bodyChars = new char[length];
        in.read(bodyChars);
        body = new String(bodyChars);
      } catch (NumberFormatException e) {
        logger.warn("Content-Length inválido: {}", contentLength);
      }
    }

    return new HttpRequest(method, path, version, headers, body);
  }

  /**
   * Procesa un request HTTP y genera la response
   */
  private HttpResponse processRequest(HttpRequest request) {
    try {
      // Manejar diferentes rutas
      switch (request.path) {
        case "/":
        case "/index.html":
          return serveStaticFile("index.html");
        case "/chat.html":
          return serveStaticFile("chat.html");
        case "/api/session":
          return handleSessionApi(request);
        case "/api/stats":
          return handleStatsApi();
        default:
          if (request.path.startsWith("/")) {
            return serveStaticFile(request.path.substring(1));
          }
          return new HttpResponse(
            404,
            "Not Found",
            "text/plain",
            "404 - Página no encontrada"
          );
      }
    } catch (Exception e) {
      logger.error("Error procesando request: {}", request.path, e);
      return new HttpResponse(
        500,
        "Internal Server Error",
        "text/plain",
        "500 - Error interno del servidor"
      );
    }
  }

  /**
   * Sirve un archivo estático
   */
  private HttpResponse serveStaticFile(String filename) throws IOException {
    Path filePath = Paths.get(WEB_ROOT, filename);

    if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
      return new HttpResponse(
        404,
        "Not Found",
        "text/plain",
        "404 - Archivo no encontrado"
      );
    }

    byte[] content = Files.readAllBytes(filePath);
    String mimeType = getMimeType(filename);

    Map<String, String> headers = new HashMap<>();
    headers.put("Content-Length", String.valueOf(content.length));
    headers.put("Cache-Control", "public, max-age=3600");

    return new HttpResponse(200, "OK", mimeType, content, headers);
  }

  /**
   * Maneja el API de sesiones
   */
  private HttpResponse handleSessionApi(HttpRequest request) {
    if ("POST".equals(request.method)) {
      // Crear nueva sesión
      String sessionId = sessionManager.createSession();
      String response = "{\"sessionId\":\"" + sessionId + "\"}";

      Map<String, String> headers = new HashMap<>();
      headers.put(
        "Set-Cookie",
        "JSESSIONID=" + sessionId + "; HttpOnly; Path=/"
      );

      return new HttpResponse(200, "OK", "application/json", response, headers);
    } else {
      return new HttpResponse(
        405,
        "Method Not Allowed",
        "text/plain",
        "Método no permitido"
      );
    }
  }

  /**
   * Maneja el API de estadísticas
   */
  private HttpResponse handleStatsApi() {
    ThreadPoolManager.ThreadPoolStats stats = threadPool.getStats();
    String response = String.format(
      "{\"activeThreads\":%d,\"poolSize\":%d,\"queueSize\":%d," +
      "\"completedTasks\":%d,\"totalRequests\":%d}",
      stats.activeCount,
      stats.poolSize,
      stats.queueSize,
      stats.completedTaskCount,
      requestCounter.get()
    );

    return new HttpResponse(200, "OK", "application/json", response);
  }

  /**
   * Envía una response HTTP
   */
  private void sendResponse(OutputStream out, HttpResponse response)
    throws IOException {
    PrintWriter writer = new PrintWriter(out, true);

    // Status line
    writer.println(
      "HTTP/1.1 " + response.statusCode + " " + response.statusText
    );

    // Headers por defecto
    writer.println("Content-Type: " + response.contentType);
    writer.println("Server: ConcurrentWebServer/1.0");
    writer.println(
      "Date: " +
      new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        .format(new Date())
    );

    // Headers adicionales
    if (response.headers != null) {
      for (Map.Entry<String, String> header : response.headers.entrySet()) {
        writer.println(header.getKey() + ": " + header.getValue());
      }
    }

    if (response.content != null) {
      if (response.content instanceof String) {
        String content = (String) response.content;
        writer.println("Content-Length: " + content.getBytes().length);
        writer.println(); // Línea vacía
        writer.print(content);
      } else if (response.content instanceof byte[]) {
        byte[] content = (byte[]) response.content;
        writer.println("Content-Length: " + content.length);
        writer.println(); // Línea vacía
        writer.flush();
        out.write(content);
      }
    } else {
      writer.println("Content-Length: 0");
      writer.println(); // Línea vacía
    }

    writer.flush();
  }

  /**
   * Envía una response de error
   */
  private void sendErrorResponse(
    OutputStream out,
    int statusCode,
    String statusText
  ) throws IOException {
    HttpResponse errorResponse = new HttpResponse(
      statusCode,
      statusText,
      "text/plain",
      statusCode + " - " + statusText
    );
    sendResponse(out, errorResponse);
  }

  /**
   * Obtiene el tipo MIME basado en la extensión del archivo
   */
  private String getMimeType(String filename) {
    int dotIndex = filename.lastIndexOf('.');
    if (dotIndex > 0 && dotIndex < filename.length() - 1) {
      String extension = filename.substring(dotIndex + 1).toLowerCase();
      return MIME_TYPES.getOrDefault(extension, "application/octet-stream");
    }
    return "application/octet-stream";
  }

  /**
   * Limpia recursos
   */
  private void cleanup() {
    threadPool.shutdown();
    sessionManager.cleanup();
  }

  /**
   * Clase para representar un request HTTP
   */
  private static class HttpRequest {

    final String method;
    final String path;
    final String version;
    final Map<String, String> headers;
    final String body;

    HttpRequest(
      String method,
      String path,
      String version,
      Map<String, String> headers,
      String body
    ) {
      this.method = method;
      this.path = path;
      this.version = version;
      this.headers = headers;
      this.body = body;
    }
  }

  /**
   * Clase para representar una response HTTP
   */
  private static class HttpResponse {

    final int statusCode;
    final String statusText;
    final String contentType;
    final Object content;
    final Map<String, String> headers;

    HttpResponse(
      int statusCode,
      String statusText,
      String contentType,
      Object content
    ) {
      this(statusCode, statusText, contentType, content, null);
    }

    HttpResponse(
      int statusCode,
      String statusText,
      String contentType,
      Object content,
      Map<String, String> headers
    ) {
      this.statusCode = statusCode;
      this.statusText = statusText;
      this.contentType = contentType;
      this.content = content;
      this.headers = headers;
    }
  }
}
