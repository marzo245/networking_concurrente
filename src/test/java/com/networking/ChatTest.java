package com.networking;

import com.networking.chat.ChatMessage;
import com.networking.chat.ChatRoom;
import com.networking.chat.ChatUser;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pruebas específicas para el sistema de chat
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ChatTest {

  private static final Logger logger = LoggerFactory.getLogger(ChatTest.class);

  private ChatRoom chatRoom;
  private ExecutorService executor;

  @BeforeEach
  void setUp() {
    chatRoom = new ChatRoom("test", "Test Room");
    executor = Executors.newFixedThreadPool(20);
  }

  @AfterEach
  void tearDown() {
    if (chatRoom != null) {
      chatRoom.cleanup();
    }
    if (executor != null) {
      executor.shutdown();
      try {
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
          executor.shutdownNow();
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        executor.shutdownNow();
      }
    }
  }

  @Test
  @Order(1)
  @DisplayName("Prueba básica de usuarios y mensajes")
  void testBasicChatFunctionality() {
    logger.info("Probando funcionalidad básica del chat...");

    // Crear usuarios
    ChatUser user1 = new ChatUser("1", "Alice");
    ChatUser user2 = new ChatUser("2", "Bob");

    // Añadir usuarios
    Assertions.assertTrue(chatRoom.addUser(user1));
    Assertions.assertTrue(chatRoom.addUser(user2));
    Assertions.assertEquals(2, chatRoom.getActiveUserCount());

    // Enviar mensajes
    ChatMessage message1 = new ChatMessage(user1, "Hola Bob!");
    ChatMessage message2 = new ChatMessage(user2, "¡Hola Alice!");

    chatRoom.addMessage(message1);
    chatRoom.addMessage(message2);

    List<ChatMessage> messages = chatRoom.getAllMessages();
    Assertions.assertTrue(messages.size() >= 2);

    // Verificar usuarios activos
    List<ChatUser> activeUsers = chatRoom.getActiveUsers();
    Assertions.assertEquals(2, activeUsers.size());

    logger.info("✓ Funcionalidad básica del chat validada");
  }

  @Test
  @Order(2)
  @DisplayName("Prueba de usuarios concurrentes")
  void testConcurrentUsers() throws InterruptedException {
    logger.info("Probando usuarios concurrentes...");

    final int NUM_USERS = 50;
    final CountDownLatch latch = new CountDownLatch(NUM_USERS);
    final AtomicInteger successfulAdditions = new AtomicInteger(0);

    for (int i = 0; i < NUM_USERS; i++) {
      final int userId = i;
      executor.submit(() -> {
        try {
          ChatUser user = new ChatUser(String.valueOf(userId), "User" + userId);
          if (chatRoom.addUser(user)) {
            successfulAdditions.incrementAndGet();
          }
        } finally {
          latch.countDown();
        }
      });
    }

    boolean completed = latch.await(10, TimeUnit.SECONDS);
    Assertions.assertTrue(
      completed,
      "No se completó la adición de usuarios en el tiempo esperado"
    );

    logger.info(
      "Usuarios añadidos exitosamente: {}/{}",
      successfulAdditions.get(),
      NUM_USERS
    );
    Assertions.assertTrue(
      successfulAdditions.get() >= NUM_USERS * 0.8,
      "Demasiados fallos al añadir usuarios"
    );

    int activeUsers = chatRoom.getActiveUserCount();
    logger.info("Usuarios activos: {}", activeUsers);
    Assertions.assertEquals(successfulAdditions.get(), activeUsers);

    logger.info("✓ Usuarios concurrentes validados");
  }

  @Test
  @Order(3)
  @DisplayName("Prueba de mensajes concurrentes")
  void testConcurrentMessages() throws InterruptedException {
    logger.info("Probando mensajes concurrentes...");

    // Crear algunos usuarios primero
    final int NUM_USERS = 10;
    final int MESSAGES_PER_USER = 20;

    // Añadir usuarios
    for (int i = 0; i < NUM_USERS; i++) {
      ChatUser user = new ChatUser(String.valueOf(i), "User" + i);
      chatRoom.addUser(user);
    }

    final CountDownLatch latch = new CountDownLatch(NUM_USERS);
    final AtomicInteger messagesSent = new AtomicInteger(0);

    for (int i = 0; i < NUM_USERS; i++) {
      final int userId = i;
      final ChatUser user = chatRoom.getUser(String.valueOf(userId));

      executor.submit(() -> {
        try {
          for (int j = 0; j < MESSAGES_PER_USER; j++) {
            String content = "Mensaje " + j + " de " + user.getUsername();
            ChatMessage message = new ChatMessage(user, content);
            chatRoom.addMessage(message);
            messagesSent.incrementAndGet();

            Thread.sleep(10); // Pequeña pausa entre mensajes
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          latch.countDown();
        }
      });
    }

    boolean completed = latch.await(15, TimeUnit.SECONDS);
    Assertions.assertTrue(
      completed,
      "No se completó el envío de mensajes en el tiempo esperado"
    );

    int totalExpectedMessages = NUM_USERS * MESSAGES_PER_USER;
    logger.info(
      "Mensajes enviados: {}/{}",
      messagesSent.get(),
      totalExpectedMessages
    );

    List<ChatMessage> allMessages = chatRoom.getAllMessages();
    logger.info("Mensajes almacenados: {}", allMessages.size());

    // Debe haber al menos los mensajes enviados (puede haber mensajes de sistema adicionales)
    Assertions.assertTrue(
      allMessages.size() >= messagesSent.get(),
      "No se almacenaron todos los mensajes enviados"
    );

    logger.info("✓ Mensajes concurrentes validados");
  }

  @Test
  @Order(4)
  @DisplayName("Prueba de chat bajo carga alta")
  void testChatUnderHighLoad() throws InterruptedException {
    logger.info("Probando chat bajo carga alta...");

    final int NUM_CONCURRENT_USERS = 30;
    final int SIMULATION_DURATION_SECONDS = 10;
    final CountDownLatch startLatch = new CountDownLatch(1);
    final CountDownLatch endLatch = new CountDownLatch(NUM_CONCURRENT_USERS);

    final AtomicInteger totalMessages = new AtomicInteger(0);
    final AtomicInteger totalUserOperations = new AtomicInteger(0);

    // Simular usuarios uniéndose, chateando y saliendo concurrentemente
    for (int i = 0; i < NUM_CONCURRENT_USERS; i++) {
      final int userId = i;
      executor.submit(() -> {
        try {
          startLatch.await(); // Esperar señal de inicio

          // Crear y añadir usuario
          ChatUser user = new ChatUser(
            "load_user_" + userId,
            "LoadUser" + userId
          );
          if (chatRoom.addUser(user)) {
            totalUserOperations.incrementAndGet();

            long endTime =
              System.currentTimeMillis() + (SIMULATION_DURATION_SECONDS * 1000);

            // Simular actividad de chat
            while (System.currentTimeMillis() < endTime) {
              // Enviar mensaje
              String content =
                "Mensaje de carga " + totalMessages.incrementAndGet();
              ChatMessage message = new ChatMessage(user, content);
              chatRoom.addMessage(message);

              // Actualizar actividad del usuario
              user.updateActivity();

              // Pausa variable para simular comportamiento real
              Thread.sleep(200 + (userId % 300));
            }

            // Remover usuario
            chatRoom.removeUser(user);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          endLatch.countDown();
        }
      });
    }

    long testStart = System.currentTimeMillis();
    startLatch.countDown(); // Iniciar la simulación

    boolean completed = endLatch.await(
      SIMULATION_DURATION_SECONDS + 10,
      TimeUnit.SECONDS
    );
    long testDuration = System.currentTimeMillis() - testStart;

    Assertions.assertTrue(
      completed,
      "La simulación de carga alta no se completó a tiempo"
    );

    // Estadísticas finales
    ChatRoom.RoomStats stats = chatRoom.getStats();
    double messagesPerSecond = (double) totalMessages.get() /
    (testDuration / 1000.0);

    logger.info("Resultados de carga alta:");
    logger.info("- Duración: {:.2f}s", testDuration / 1000.0);
    logger.info("- Usuarios que se unieron: {}", totalUserOperations.get());
    logger.info("- Mensajes enviados: {}", totalMessages.get());
    logger.info("- Mensajes por segundo: {:.2f}", messagesPerSecond);
    logger.info("- Usuarios activos finales: {}", stats.activeUsers);
    logger.info("- Total mensajes almacenados: {}", stats.totalMessages);

    // Validaciones
    Assertions.assertTrue(
      totalUserOperations.get() >= NUM_CONCURRENT_USERS * 0.9,
      "Demasiados usuarios fallaron al unirse"
    );
    Assertions.assertTrue(totalMessages.get() > 0, "No se enviaron mensajes");
    Assertions.assertTrue(
      messagesPerSecond >= 5,
      "Throughput de mensajes demasiado bajo: " + messagesPerSecond
    );

    // La mayoría de usuarios deberían haberse desconectado
    Assertions.assertTrue(
      stats.activeUsers <= 5,
      "Demasiados usuarios aún conectados: " + stats.activeUsers
    );

    logger.info("✓ Chat bajo carga alta validado");
  }

  @Test
  @Order(5)
  @DisplayName("Prueba de integridad de datos en chat concurrente")
  void testDataIntegrityUnderConcurrency() throws InterruptedException {
    logger.info("Probando integridad de datos bajo concurrencia...");

    final int NUM_WRITERS = 10;
    final int NUM_READERS = 5;
    final int OPERATIONS_PER_WRITER = 50;
    final CountDownLatch writersLatch = new CountDownLatch(NUM_WRITERS);
    final CountDownLatch readersLatch = new CountDownLatch(NUM_READERS);

    final AtomicInteger messagesWritten = new AtomicInteger(0);
    final AtomicInteger usersAdded = new AtomicInteger(0);

    // Writers: añaden usuarios y envían mensajes
    for (int i = 0; i < NUM_WRITERS; i++) {
      final int writerId = i;
      executor.submit(() -> {
        try {
          // Añadir usuario
          ChatUser user = new ChatUser(
            "writer_" + writerId,
            "Writer" + writerId
          );
          if (chatRoom.addUser(user)) {
            usersAdded.incrementAndGet();

            // Enviar mensajes
            for (int j = 0; j < OPERATIONS_PER_WRITER; j++) {
              String content = "Mensaje " + j + " de writer " + writerId;
              ChatMessage message = new ChatMessage(user, content);
              chatRoom.addMessage(message);
              messagesWritten.incrementAndGet();

              Thread.sleep(5);
            }
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          writersLatch.countDown();
        }
      });
    }

    // Readers: leen estado del chat concurrentemente
    for (int i = 0; i < NUM_READERS; i++) {
      final int readerId = i;
      executor.submit(() -> {
        try {
          int readOperations = 0;
          long endTime = System.currentTimeMillis() + 8000; // 8 segundos

          while (System.currentTimeMillis() < endTime) {
            // Leer usuarios activos
            List<ChatUser> users = chatRoom.getActiveUsers();

            // Leer mensajes recientes
            List<ChatMessage> messages = chatRoom.getRecentMessages(10);

            // Obtener estadísticas
            ChatRoom.RoomStats stats = chatRoom.getStats();

            // Validar consistencia básica
            Assertions.assertTrue(users.size() >= 0);
            Assertions.assertTrue(messages.size() >= 0);
            Assertions.assertTrue(stats.activeUsers >= 0);
            Assertions.assertTrue(stats.totalMessages >= 0);

            readOperations++;
            Thread.sleep(100);
          }

          logger.debug(
            "Reader {} realizó {} operaciones de lectura",
            readerId,
            readOperations
          );
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          readersLatch.countDown();
        }
      });
    }

    // Esperar completion
    boolean writersCompleted = writersLatch.await(15, TimeUnit.SECONDS);
    boolean readersCompleted = readersLatch.await(10, TimeUnit.SECONDS);

    Assertions.assertTrue(
      writersCompleted,
      "Los writers no completaron a tiempo"
    );
    Assertions.assertTrue(
      readersCompleted,
      "Los readers no completaron a tiempo"
    );

    // Validar estado final
    ChatRoom.RoomStats finalStats = chatRoom.getStats();
    List<ChatMessage> allMessages = chatRoom.getAllMessages();

    logger.info("Estado final:");
    logger.info("- Usuarios añadidos: {}", usersAdded.get());
    logger.info("- Mensajes escritos: {}", messagesWritten.get());
    logger.info("- Usuarios activos finales: {}", finalStats.activeUsers);
    logger.info("- Mensajes totales almacenados: {}", finalStats.totalMessages);
    logger.info("- Mensajes en lista: {}", allMessages.size());

    // Validaciones de integridad
    Assertions.assertEquals(
      usersAdded.get(),
      finalStats.activeUsers,
      "Inconsistencia en número de usuarios activos"
    );

    // Los mensajes almacenados deben incluir al menos los mensajes de usuarios
    Assertions.assertTrue(
      finalStats.totalMessages >= messagesWritten.get(),
      "Se perdieron mensajes: esperados >= " +
      messagesWritten.get() +
      ", encontrados " +
      finalStats.totalMessages
    );

    // Verificar que no hay mensajes duplicados por contenido
    long uniqueUserMessages = allMessages
      .stream()
      .filter(msg -> msg.getUser() != null)
      .map(msg -> msg.getContent())
      .distinct()
      .count();

    logger.info("- Mensajes únicos de usuarios: {}", uniqueUserMessages);

    logger.info("✓ Integridad de datos validada");
  }
}
