package com.networking.chat;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gestor de sala de chat que maneja usuarios y mensajes
 */
public class ChatRoom {

  private static final Logger logger = LoggerFactory.getLogger(ChatRoom.class);

  private static final int MAX_MESSAGES = 1000;
  private static final int MAX_USERS = 100;
  private static final int CLEANUP_INTERVAL_MINUTES = 5;
  private static final int INACTIVE_USER_TIMEOUT_MINUTES = 30;

  private final String roomId;
  private final String roomName;
  private final Map<String, ChatUser> users;
  private final List<ChatMessage> messages;
  private final LocalDateTime createdAt;
  private final ScheduledExecutorService cleanupExecutor;

  public ChatRoom() {
    this("default", "Sala Principal");
  }

  public ChatRoom(String roomId, String roomName) {
    this.roomId = roomId;
    this.roomName = roomName;
    this.users = new ConcurrentHashMap<>();
    this.messages = new CopyOnWriteArrayList<>();
    this.createdAt = LocalDateTime.now();

    // Executor para tareas de limpieza
    this.cleanupExecutor =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ChatRoom-Cleanup");
        t.setDaemon(true);
        return t;
      });

    // Programar limpieza periódica
    cleanupExecutor.scheduleAtFixedRate(
      this::performCleanup,
      CLEANUP_INTERVAL_MINUTES,
      CLEANUP_INTERVAL_MINUTES,
      TimeUnit.MINUTES
    );

    logger.info("ChatRoom '{}' creada", roomName);
  }

  /**
   * Añade un usuario a la sala
   */
  public boolean addUser(ChatUser user) {
    if (user == null) {
      return false;
    }

    if (users.size() >= MAX_USERS) {
      logger.warn(
        "No se puede añadir usuario '{}': sala llena",
        user.getUsername()
      );
      return false;
    }

    // Verificar si el nombre de usuario ya existe
    boolean usernameExists = users
      .values()
      .stream()
      .anyMatch(u -> u.getUsername().equalsIgnoreCase(user.getUsername()));

    if (usernameExists) {
      logger.warn(
        "No se puede añadir usuario '{}': nombre ya existe",
        user.getUsername()
      );
      return false;
    }

    users.put(user.getId(), user);
    logger.info(
      "Usuario '{}' añadido a la sala '{}' (total: {})",
      user.getUsername(),
      roomName,
      users.size()
    );

    // Añadir mensaje de sistema
    addSystemMessage(user.getUsername() + " se unió al chat");

    return true;
  }

  /**
   * Remueve un usuario de la sala
   */
  public boolean removeUser(ChatUser user) {
    if (user == null) {
      return false;
    }

    ChatUser removed = users.remove(user.getId());
    if (removed != null) {
      removed.setOnline(false);
      logger.info(
        "Usuario '{}' removido de la sala '{}' (total: {})",
        user.getUsername(),
        roomName,
        users.size()
      );

      // Añadir mensaje de sistema
      addSystemMessage(user.getUsername() + " abandonó el chat");
      return true;
    }

    return false;
  }

  /**
   * Obtiene un usuario por ID
   */
  public ChatUser getUser(String userId) {
    return users.get(userId);
  }

  /**
   * Obtiene todos los usuarios activos
   */
  public List<ChatUser> getActiveUsers() {
    return users
      .values()
      .stream()
      .filter(ChatUser::isOnline)
      .sorted((u1, u2) -> u1.getUsername().compareToIgnoreCase(u2.getUsername())
      )
      .collect(Collectors.toList());
  }

  /**
   * Obtiene el número de usuarios activos
   */
  public int getActiveUserCount() {
    return (int) users.values().stream().filter(ChatUser::isOnline).count();
  }

  /**
   * Añade un mensaje a la sala
   */
  public void addMessage(ChatMessage message) {
    if (message == null) {
      return;
    }

    // Actualizar actividad del usuario
    if (message.getUser() != null) {
      message.getUser().updateActivity();
    }

    messages.add(message);

    // Mantener solo los últimos N mensajes
    if (messages.size() > MAX_MESSAGES) {
      messages.remove(0);
    }

    logger.debug(
      "Mensaje añadido en sala '{}': {} caracteres de {}",
      roomName,
      message.getContent().length(),
      message.getUser() != null ? message.getUser().getUsername() : "sistema"
    );
  }

  /**
   * Añade un mensaje de sistema
   */
  public void addSystemMessage(String content) {
    ChatMessage systemMessage = new ChatMessage(
      null,
      content,
      ChatMessage.MessageType.SYSTEM
    );
    addMessage(systemMessage);
  }

  /**
   * Obtiene los mensajes recientes
   */
  public List<ChatMessage> getRecentMessages(int limit) {
    if (limit <= 0) {
      return new ArrayList<>(messages);
    }

    int fromIndex = Math.max(0, messages.size() - limit);
    return new ArrayList<>(messages.subList(fromIndex, messages.size()));
  }

  /**
   * Obtiene todos los mensajes
   */
  public List<ChatMessage> getAllMessages() {
    return new ArrayList<>(messages);
  }

  /**
   * Busca mensajes que contengan un texto específico
   */
  public List<ChatMessage> searchMessages(String query) {
    if (query == null || query.trim().isEmpty()) {
      return new ArrayList<>();
    }

    String lowerQuery = query.toLowerCase();
    return messages
      .stream()
      .filter(msg -> msg.getContent().toLowerCase().contains(lowerQuery))
      .collect(Collectors.toList());
  }

  /**
   * Obtiene estadísticas de la sala
   */
  public RoomStats getStats() {
    int totalUsers = users.size();
    int activeUsers = getActiveUserCount();
    int totalMessages = messages.size();

    return new RoomStats(
      roomId,
      roomName,
      totalUsers,
      activeUsers,
      totalMessages,
      createdAt
    );
  }

  /**
   * Limpia usuarios inactivos y mensajes antiguos
   */
  private void performCleanup() {
    try {
      LocalDateTime cutoffTime = LocalDateTime
        .now()
        .minusMinutes(INACTIVE_USER_TIMEOUT_MINUTES);

      // Marcar usuarios inactivos como offline
      int inactiveCount = 0;
      for (ChatUser user : users.values()) {
        if (user.isOnline() && user.getLastActivity().isBefore(cutoffTime)) {
          user.setOnline(false);
          inactiveCount++;
          logger.debug(
            "Usuario '{}' marcado como inactivo",
            user.getUsername()
          );
        }
      }

      if (inactiveCount > 0) {
        logger.info(
          "Limpieza completada en sala '{}': {} usuarios marcados como inactivos",
          roomName,
          inactiveCount
        );
      }
    } catch (Exception e) {
      logger.error("Error durante limpieza de sala '{}'", roomName, e);
    }
  }

  /**
   * Cierra la sala y libera recursos
   */
  public void cleanup() {
    logger.info("Cerrando sala '{}'...", roomName);

    cleanupExecutor.shutdown();
    try {
      if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      cleanupExecutor.shutdownNow();
    }

    users.clear();
    messages.clear();

    logger.info("Sala '{}' cerrada", roomName);
  }

  // Getters
  public String getRoomId() {
    return roomId;
  }

  public String getRoomName() {
    return roomName;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  /**
   * Clase para estadísticas de la sala
   */
  public static class RoomStats {

    public final String roomId;
    public final String roomName;
    public final int totalUsers;
    public final int activeUsers;
    public final int totalMessages;
    public final LocalDateTime createdAt;

    public RoomStats(
      String roomId,
      String roomName,
      int totalUsers,
      int activeUsers,
      int totalMessages,
      LocalDateTime createdAt
    ) {
      this.roomId = roomId;
      this.roomName = roomName;
      this.totalUsers = totalUsers;
      this.activeUsers = activeUsers;
      this.totalMessages = totalMessages;
      this.createdAt = createdAt;
    }

    @Override
    public String toString() {
      return String.format(
        "RoomStats{roomId='%s', roomName='%s', " +
        "totalUsers=%d, activeUsers=%d, totalMessages=%d, createdAt=%s}",
        roomId,
        roomName,
        totalUsers,
        activeUsers,
        totalMessages,
        createdAt
      );
    }
  }
}
