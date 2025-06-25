package com.networking.session;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gestor de sesiones para el servidor web
 * Maneja la creación, validación y limpieza de sesiones de usuario
 */
public class SessionManager {

  private static final Logger logger = LoggerFactory.getLogger(
    SessionManager.class
  );

  private static final int SESSION_TIMEOUT_MINUTES = 30;
  private static final String SESSION_ID_CHARS =
    "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
  private static final int SESSION_ID_LENGTH = 32;

  private final Map<String, SessionData> sessions;
  private final SecureRandom random;
  private final ScheduledExecutorService cleanupExecutor;

  public SessionManager() {
    this.sessions = new ConcurrentHashMap<>();
    this.random = new SecureRandom();
    this.cleanupExecutor =
      Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SessionCleanup");
        t.setDaemon(true);
        return t;
      });

    // Programar limpieza de sesiones expiradas cada 5 minutos
    cleanupExecutor.scheduleAtFixedRate(
      this::cleanupExpiredSessions,
      5,
      5,
      TimeUnit.MINUTES
    );

    logger.info(
      "SessionManager inicializado con timeout de {} minutos",
      SESSION_TIMEOUT_MINUTES
    );
  }

  /**
   * Crea una nueva sesión
   * @return ID de la sesión creada
   */
  public String createSession() {
    String sessionId = generateSessionId();
    SessionData sessionData = new SessionData(sessionId, LocalDateTime.now());

    sessions.put(sessionId, sessionData);
    logger.debug("Nueva sesión creada: {}", sessionId);

    return sessionId;
  }

  /**
   * Crea una sesión para un usuario específico
   * @param username nombre del usuario
   * @return ID de la sesión creada
   */
  public String createSession(String username) {
    String sessionId = generateSessionId();
    SessionData sessionData = new SessionData(sessionId, LocalDateTime.now());
    sessionData.setUsername(username);

    sessions.put(sessionId, sessionData);
    logger.debug(
      "Nueva sesión creada para usuario {}: {}",
      username,
      sessionId
    );

    return sessionId;
  }

  /**
   * Valida si una sesión existe y no ha expirado
   * @param sessionId ID de la sesión
   * @return true si la sesión es válida
   */
  public boolean isValidSession(String sessionId) {
    if (sessionId == null || sessionId.trim().isEmpty()) {
      return false;
    }

    SessionData session = sessions.get(sessionId);
    if (session == null) {
      return false;
    }

    // Verificar si la sesión ha expirado
    if (session.isExpired()) {
      sessions.remove(sessionId);
      logger.debug("Sesión expirada removida: {}", sessionId);
      return false;
    }

    // Actualizar tiempo de último acceso
    session.updateLastAccess();
    return true;
  }

  /**
   * Obtiene los datos de una sesión
   * @param sessionId ID de la sesión
   * @return datos de la sesión o null si no existe
   */
  public SessionData getSessionData(String sessionId) {
    if (!isValidSession(sessionId)) {
      return null;
    }
    return sessions.get(sessionId);
  }

  /**
   * Invalida una sesión
   * @param sessionId ID de la sesión a invalidar
   */
  public void invalidateSession(String sessionId) {
    SessionData removed = sessions.remove(sessionId);
    if (removed != null) {
      logger.debug("Sesión invalidada: {}", sessionId);
    }
  }

  /**
   * Obtiene el número de sesiones activas
   * @return número de sesiones activas
   */
  public int getActiveSessionCount() {
    return sessions.size();
  }

  /**
   * Limpia sesiones expiradas
   */
  public void cleanupExpiredSessions() {
    int initialCount = sessions.size();

    sessions
      .entrySet()
      .removeIf(entry -> {
        if (entry.getValue().isExpired()) {
          logger.debug("Removiendo sesión expirada: {}", entry.getKey());
          return true;
        }
        return false;
      });

    int removedCount = initialCount - sessions.size();
    if (removedCount > 0) {
      logger.info(
        "Limpieza de sesiones completada: {} sesiones removidas, {} activas",
        removedCount,
        sessions.size()
      );
    }
  }

  /**
   * Cierra el gestor de sesiones
   */
  public void cleanup() {
    logger.info("Cerrando SessionManager...");
    cleanupExecutor.shutdown();

    try {
      if (!cleanupExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        cleanupExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      cleanupExecutor.shutdownNow();
    }

    sessions.clear();
    logger.info("SessionManager cerrado");
  }

  /**
   * Genera un ID de sesión aleatorio y seguro
   */
  private String generateSessionId() {
    StringBuilder sb = new StringBuilder(SESSION_ID_LENGTH);
    for (int i = 0; i < SESSION_ID_LENGTH; i++) {
      sb.append(
        SESSION_ID_CHARS.charAt(random.nextInt(SESSION_ID_CHARS.length()))
      );
    }
    return sb.toString();
  }

  /**
   * Clase para almacenar datos de sesión
   */
  public static class SessionData {

    private final String sessionId;
    private final LocalDateTime createdAt;
    private volatile LocalDateTime lastAccessedAt;
    private volatile String username;
    private volatile String userAgent;
    private volatile String ipAddress;
    private final Map<String, Object> attributes;

    public SessionData(String sessionId, LocalDateTime createdAt) {
      this.sessionId = sessionId;
      this.createdAt = createdAt;
      this.lastAccessedAt = createdAt;
      this.attributes = new ConcurrentHashMap<>();
    }

    public String getSessionId() {
      return sessionId;
    }

    public LocalDateTime getCreatedAt() {
      return createdAt;
    }

    public LocalDateTime getLastAccessedAt() {
      return lastAccessedAt;
    }

    public void updateLastAccess() {
      this.lastAccessedAt = LocalDateTime.now();
    }

    public String getUsername() {
      return username;
    }

    public void setUsername(String username) {
      this.username = username;
    }

    public String getUserAgent() {
      return userAgent;
    }

    public void setUserAgent(String userAgent) {
      this.userAgent = userAgent;
    }

    public String getIpAddress() {
      return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
      this.ipAddress = ipAddress;
    }

    public Object getAttribute(String name) {
      return attributes.get(name);
    }

    public void setAttribute(String name, Object value) {
      if (value == null) {
        attributes.remove(name);
      } else {
        attributes.put(name, value);
      }
    }

    public void removeAttribute(String name) {
      attributes.remove(name);
    }

    /**
     * Verifica si la sesión ha expirado
     */
    public boolean isExpired() {
      return lastAccessedAt
        .plusMinutes(SESSION_TIMEOUT_MINUTES)
        .isBefore(LocalDateTime.now());
    }

    /**
     * Obtiene la duración de la sesión en minutos
     */
    public long getSessionDurationMinutes() {
      return java.time.Duration
        .between(createdAt, LocalDateTime.now())
        .toMinutes();
    }

    @Override
    public String toString() {
      return String.format(
        "SessionData{id='%s', username='%s', created=%s, lastAccess=%s}",
        sessionId,
        username,
        createdAt,
        lastAccessedAt
      );
    }
  }
}
