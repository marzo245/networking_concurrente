package com.networking.server;

import com.networking.chat.ChatMessage;
import com.networking.chat.ChatRoom;
import com.networking.chat.ChatUser;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Servidor WebSocket para manejar conexiones de chat en tiempo real
 */
public class WebSocketServer {

  private static final Logger logger = LoggerFactory.getLogger(
    WebSocketServer.class
  );

  private final int port;
  private final ThreadPoolManager threadPool;
  private final ChatRoom chatRoom;
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicLong connectionCounter = new AtomicLong(0);
  private final ConcurrentHashMap<String, WebSocketConnection> connections = new ConcurrentHashMap<>();
  private ServerSocket serverSocket;

  // WebSocket Magic String para handshake
  private static final String WEBSOCKET_MAGIC_STRING =
    "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
  private static final Pattern WEBSOCKET_KEY_PATTERN = Pattern.compile(
    "Sec-WebSocket-Key: (.+)"
  );

  public WebSocketServer(int port) {
    this.port = port;
    this.threadPool = new ThreadPoolManager();
    this.chatRoom = new ChatRoom();
  }

  /**
   * Inicia el servidor WebSocket
   */
  public void start() {
    try {
      serverSocket = new ServerSocket(port);
      running.set(true);

      logger.info("Servidor WebSocket iniciado en puerto {}", port);

      while (running.get()) {
        try {
          Socket clientSocket = serverSocket.accept();
          clientSocket.setSoTimeout(30000); // 30 segundos timeout

          // Procesar la conexión WebSocket en el pool de hilos
          threadPool.execute(() -> handleWebSocketConnection(clientSocket));
        } catch (SocketTimeoutException e) {
          // Timeout normal, continuar
        } catch (IOException e) {
          if (running.get()) {
            logger.error("Error aceptando conexión WebSocket", e);
          }
        }
      }
    } catch (IOException e) {
      logger.error("Error iniciando servidor WebSocket", e);
    } finally {
      cleanup();
    }
  }

  /**
   * Detiene el servidor WebSocket
   */
  public void stop() {
    logger.info("Deteniendo servidor WebSocket...");
    running.set(false);

    // Cerrar todas las conexiones activas
    connections.values().forEach(WebSocketConnection::close);
    connections.clear();

    try {
      if (serverSocket != null && !serverSocket.isClosed()) {
        serverSocket.close();
      }
    } catch (IOException e) {
      logger.error("Error cerrando server socket WebSocket", e);
    }

    cleanup();
  }

  /**
   * Maneja una nueva conexión WebSocket
   */
  private void handleWebSocketConnection(Socket clientSocket) {
    long connectionId = connectionCounter.incrementAndGet();
    String clientInfo = clientSocket.getRemoteSocketAddress().toString();

    logger.debug(
      "Nueva conexión WebSocket #{} desde {}",
      connectionId,
      clientInfo
    );

    try (
      BufferedReader in = new BufferedReader(
        new InputStreamReader(clientSocket.getInputStream())
      );
      OutputStream out = clientSocket.getOutputStream()
    ) {
      // Realizar handshake WebSocket
      if (performWebSocketHandshake(in, out)) {
        // Crear conexión WebSocket
        WebSocketConnection wsConnection = new WebSocketConnection(
          connectionId,
          clientSocket,
          chatRoom
        );

        // Manejar la conexión
        wsConnection.handleConnection();
      } else {
        logger.warn(
          "Handshake WebSocket falló para conexión #{}",
          connectionId
        );
      }
    } catch (Exception e) {
      logger.error("Error manejando conexión WebSocket #{}", connectionId, e);
    } finally {
      try {
        if (!clientSocket.isClosed()) {
          clientSocket.close();
        }
      } catch (IOException e) {
        logger.debug("Error cerrando socket WebSocket", e);
      }
    }
  }

  /**
   * Realiza el handshake WebSocket según RFC 6455
   */
  private boolean performWebSocketHandshake(
    BufferedReader in,
    OutputStream out
  ) throws IOException {
    String line;
    String webSocketKey = null;
    boolean isWebSocketRequest = false;

    // Leer headers de la request
    while ((line = in.readLine()) != null && !line.isEmpty()) {
      if (line.contains("Upgrade: websocket")) {
        isWebSocketRequest = true;
      }

      Matcher matcher = WEBSOCKET_KEY_PATTERN.matcher(line);
      if (matcher.find()) {
        webSocketKey = matcher.group(1).trim();
      }
    }

    if (!isWebSocketRequest || webSocketKey == null) {
      return false;
    }

    // Generar Sec-WebSocket-Accept
    String webSocketAccept = generateWebSocketAccept(webSocketKey);

    // Enviar response de handshake
    PrintWriter writer = new PrintWriter(out, true);
    writer.println("HTTP/1.1 101 Switching Protocols");
    writer.println("Upgrade: websocket");
    writer.println("Connection: Upgrade");
    writer.println("Sec-WebSocket-Accept: " + webSocketAccept);
    writer.println();
    writer.flush();

    logger.debug("Handshake WebSocket completado exitosamente");
    return true;
  }

  /**
   * Genera el valor Sec-WebSocket-Accept para el handshake
   */
  private String generateWebSocketAccept(String webSocketKey) {
    try {
      String concatenated = webSocketKey + WEBSOCKET_MAGIC_STRING;
      MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
      byte[] hash = sha1.digest(concatenated.getBytes());
      return Base64.getEncoder().encodeToString(hash);
    } catch (Exception e) {
      throw new RuntimeException("Error generando WebSocket Accept", e);
    }
  }

  /**
   * Limpia recursos
   */
  private void cleanup() {
    threadPool.shutdown();
    chatRoom.cleanup();
  }

  /**
   * Clase para manejar una conexión WebSocket individual
   */
  public class WebSocketConnection {

    private final long connectionId;
    private final Socket socket;
    private final ChatRoom chatRoom;
    private final InputStream inputStream;
    private final OutputStream outputStream;
    private volatile ChatUser chatUser;
    private volatile boolean connected = true;

    public WebSocketConnection(
      long connectionId,
      Socket socket,
      ChatRoom chatRoom
    ) throws IOException {
      this.connectionId = connectionId;
      this.socket = socket;
      this.chatRoom = chatRoom;
      this.inputStream = socket.getInputStream();
      this.outputStream = socket.getOutputStream();
    }

    /**
     * Maneja la conexión WebSocket
     */
    public void handleConnection() {
      try {
        while (connected && !socket.isClosed()) {
          WebSocketFrame frame = readFrame();
          if (frame != null) {
            processFrame(frame);
          }
        }
      } catch (IOException e) {
        logger.debug(
          "Conexión WebSocket #{} cerrada: {}",
          connectionId,
          e.getMessage()
        );
      } finally {
        disconnect();
      }
    }

    /**
     * Lee un frame WebSocket
     */
    private WebSocketFrame readFrame() throws IOException {
      // Leer primer byte (FIN + Opcode)
      int firstByte = inputStream.read();
      if (firstByte == -1) {
        return null; // EOF
      }

      boolean fin = (firstByte & 0x80) != 0;
      int opcode = firstByte & 0x0F;

      // Leer segundo byte (MASK + Payload length)
      int secondByte = inputStream.read();
      if (secondByte == -1) {
        return null;
      }

      boolean masked = (secondByte & 0x80) != 0;
      long payloadLength = secondByte & 0x7F;

      // Leer payload length extendido si es necesario
      if (payloadLength == 126) {
        payloadLength = (inputStream.read() << 8) | inputStream.read();
      } else if (payloadLength == 127) {
        // Para simplicidad, no manejamos payloads muy grandes
        throw new IOException("Payload demasiado grande");
      }

      // Leer mask key si está presente
      byte[] maskKey = null;
      if (masked) {
        maskKey = new byte[4];
        inputStream.read(maskKey);
      }

      // Leer payload
      byte[] payload = new byte[(int) payloadLength];
      inputStream.read(payload);

      // Unmask payload si es necesario
      if (masked && maskKey != null) {
        for (int i = 0; i < payload.length; i++) {
          payload[i] ^= maskKey[i % 4];
        }
      }

      return new WebSocketFrame(fin, opcode, payload);
    }

    /**
     * Procesa un frame WebSocket recibido
     */
    private void processFrame(WebSocketFrame frame) throws IOException {
      switch (frame.opcode) {
        case 0x1: // Text frame
          String message = new String(frame.payload);
          handleTextMessage(message);
          break;
        case 0x8: // Close frame
          connected = false;
          break;
        case 0x9: // Ping frame
          sendPong(frame.payload);
          break;
        case 0xA: // Pong frame
          // Ignorar por ahora
          break;
        default:
          logger.warn("Opcode WebSocket no soportado: {}", frame.opcode);
      }
    }

    /**
     * Maneja un mensaje de texto recibido
     */
    private void handleTextMessage(String message) {
      logger.debug(
        "Mensaje recibido en conexión #{}: {}",
        connectionId,
        message
      );

      try {
        // Parsear mensaje JSON simple
        if (message.startsWith("{") && message.endsWith("}")) {
          if (message.contains("\"type\":\"join\"")) {
            handleJoinMessage(message);
          } else if (message.contains("\"type\":\"message\"")) {
            handleChatMessage(message);
          }
        }
      } catch (Exception e) {
        logger.error("Error procesando mensaje de chat", e);
      }
    }

    /**
     * Maneja un mensaje de unirse al chat
     */
    private void handleJoinMessage(String message) {
      // Parseo simple del JSON
      String username = extractJsonValue(message, "username");
      if (username != null && !username.trim().isEmpty()) {
        chatUser = new ChatUser(String.valueOf(connectionId), username.trim());
        chatRoom.addUser(chatUser);
        connections.put(String.valueOf(connectionId), this);

        // Notificar a todos que el usuario se unió
        String joinNotification = String.format(
          "{\"type\":\"notification\",\"message\":\"%s se unió al chat\"}",
          username
        );
        broadcastMessage(joinNotification);

        logger.info(
          "Usuario {} se unió al chat (conexión #{})",
          username,
          connectionId
        );
      }
    }

    /**
     * Maneja un mensaje de chat
     */
    private void handleChatMessage(String message) {
      if (chatUser != null) {
        String content = extractJsonValue(message, "content");
        if (content != null && !content.trim().isEmpty()) {
          ChatMessage chatMessage = new ChatMessage(chatUser, content.trim());
          chatRoom.addMessage(chatMessage);

          // Broadcast del mensaje a todos los usuarios conectados
          String messageJson = String.format(
            "{\"type\":\"message\",\"username\":\"%s\",\"content\":\"%s\",\"timestamp\":\"%s\"}",
            chatMessage.getUser().getUsername(),
            escapeJson(chatMessage.getContent()),
            chatMessage.getTimestamp().toString()
          );

          broadcastMessage(messageJson);
        }
      }
    }

    /**
     * Envía un mensaje a esta conexión WebSocket
     */
    public void sendMessage(String message) {
      try {
        sendTextFrame(message);
      } catch (IOException e) {
        logger.error("Error enviando mensaje a conexión #{}", connectionId, e);
        disconnect();
      }
    }

    /**
     * Envía un frame de texto
     */
    private void sendTextFrame(String text) throws IOException {
      byte[] payload = text.getBytes();

      // Construir frame header
      ByteBuffer header;
      if (payload.length < 126) {
        header = ByteBuffer.allocate(2);
        header.put((byte) 0x81); // FIN=1, Opcode=1 (text)
        header.put((byte) payload.length);
      } else if (payload.length < 65536) {
        header = ByteBuffer.allocate(4);
        header.put((byte) 0x81);
        header.put((byte) 126);
        header.putShort((short) payload.length);
      } else {
        throw new IOException("Payload demasiado grande");
      }

      // Enviar frame
      synchronized (outputStream) {
        outputStream.write(header.array());
        outputStream.write(payload);
        outputStream.flush();
      }
    }

    /**
     * Envía un frame Pong en respuesta a un Ping
     */
    private void sendPong(byte[] payload) throws IOException {
      ByteBuffer frame = ByteBuffer.allocate(2 + payload.length);
      frame.put((byte) 0x8A); // FIN=1, Opcode=A (pong)
      frame.put((byte) payload.length);
      frame.put(payload);

      synchronized (outputStream) {
        outputStream.write(frame.array());
        outputStream.flush();
      }
    }

    /**
     * Desconecta al usuario
     */
    private void disconnect() {
      if (chatUser != null) {
        chatRoom.removeUser(chatUser);
        connections.remove(String.valueOf(connectionId));

        // Notificar que el usuario se desconectó
        String leaveNotification = String.format(
          "{\"type\":\"notification\",\"message\":\"%s abandonó el chat\"}",
          chatUser.getUsername()
        );
        broadcastMessage(leaveNotification);

        logger.info(
          "Usuario {} se desconectó del chat (conexión #{})",
          chatUser.getUsername(),
          connectionId
        );
      }

      connected = false;
    }

    /**
     * Cierra la conexión
     */
    public void close() {
      try {
        if (!socket.isClosed()) {
          socket.close();
        }
      } catch (IOException e) {
        logger.debug("Error cerrando socket WebSocket", e);
      }
    }

    /**
     * Extrae un valor de un JSON simple
     */
    private String extractJsonValue(String json, String key) {
      String pattern = "\"" + key + "\"\\s*:\\s*\"([^\"]+)\"";
      java.util.regex.Pattern p = java.util.regex.Pattern.compile(pattern);
      java.util.regex.Matcher m = p.matcher(json);
      return m.find() ? m.group(1) : null;
    }

    /**
     * Escapa caracteres especiales para JSON
     */
    private String escapeJson(String str) {
      return str
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
    }

    /**
     * Envía un mensaje a todas las conexiones activas
     */
    private void broadcastMessage(String message) {
      connections.values().forEach(conn -> conn.sendMessage(message));
    }
  }

  /**
   * Clase para representar un frame WebSocket
   */
  private static class WebSocketFrame {

    final boolean fin;
    final int opcode;
    final byte[] payload;

    WebSocketFrame(boolean fin, int opcode, byte[] payload) {
      this.fin = fin;
      this.opcode = opcode;
      this.payload = payload;
    }
  }
}
