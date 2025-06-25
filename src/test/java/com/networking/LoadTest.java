package com.networking;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Pruebas de carga intensiva para validar el rendimiento del servidor
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LoadTest {

  private static final Logger logger = LoggerFactory.getLogger(LoadTest.class);

  private static final String SERVER_HOST = "localhost";
  private static final int HTTP_PORT = 8080;
  private static final int TEST_TIMEOUT_SECONDS = 60;

  @Test
  @Order(1)
  @DisplayName("Prueba de carga alta - 100 usuarios simultáneos")
  void testHighLoadSimultaneousUsers() throws InterruptedException {
    logger.info(
      "Iniciando prueba de alta carga con 100 usuarios simultáneos..."
    );

    final int NUM_USERS = 100;
    final int REQUESTS_PER_USER = 20;
    final ExecutorService executor = Executors.newFixedThreadPool(NUM_USERS);
    final CountDownLatch latch = new CountDownLatch(NUM_USERS);

    final AtomicInteger totalRequests = new AtomicInteger(0);
    final AtomicInteger successfulRequests = new AtomicInteger(0);
    final AtomicInteger failedRequests = new AtomicInteger(0);
    final AtomicLong totalResponseTime = new AtomicLong(0);
    final List<Long> responseTimes = Collections.synchronizedList(
      new ArrayList<>()
    );

    long startTime = System.currentTimeMillis();

    for (int i = 0; i < NUM_USERS; i++) {
      final int userId = i;
      executor.submit(() -> {
        try {
          for (int j = 0; j < REQUESTS_PER_USER; j++) {
            long requestStart = System.currentTimeMillis();
            totalRequests.incrementAndGet();

            if (makeHttpRequest("/api/stats")) {
              successfulRequests.incrementAndGet();
              long responseTime = System.currentTimeMillis() - requestStart;
              totalResponseTime.addAndGet(responseTime);
              responseTimes.add(responseTime);
            } else {
              failedRequests.incrementAndGet();
            }

            // Pequeña variación en el timing para simular comportamiento real
            Thread.sleep(50 + (userId % 100));
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

    long totalTime = System.currentTimeMillis() - startTime;

    Assertions.assertTrue(
      completed,
      "La prueba de alta carga no se completó en el tiempo esperado"
    );

    // Calcular estadísticas
    double successRate = (double) successfulRequests.get() /
    totalRequests.get() *
    100;
    double avgResponseTime = totalResponseTime.get() > 0
      ? (double) totalResponseTime.get() / successfulRequests.get()
      : 0;
    double requestsPerSecond = (double) totalRequests.get() /
    (totalTime / 1000.0);

    // Calcular percentiles
    responseTimes.sort(Long::compareTo);
    long p50 = getPercentile(responseTimes, 50);
    long p95 = getPercentile(responseTimes, 95);
    long p99 = getPercentile(responseTimes, 99);

    logger.info("Resultados de prueba de alta carga:");
    logger.info("- Usuarios simultáneos: {}", NUM_USERS);
    logger.info("- Requests por usuario: {}", REQUESTS_PER_USER);
    logger.info("- Total requests: {}", totalRequests.get());
    logger.info("- Successful: {}", successfulRequests.get());
    logger.info("- Failed: {}", failedRequests.get());
    logger.info("- Success rate: {:.2f}%", successRate);
    logger.info("- Total time: {:.2f}s", totalTime / 1000.0);
    logger.info("- Requests per second: {:.2f}", requestsPerSecond);
    logger.info("- Average response time: {:.2f}ms", avgResponseTime);
    logger.info("- P50 response time: {}ms", p50);
    logger.info("- P95 response time: {}ms", p95);
    logger.info("- P99 response time: {}ms", p99);

    // Validaciones
    Assertions.assertTrue(
      successRate >= 90.0,
      "Success rate too low under high load: " + successRate + "%"
    );
    Assertions.assertTrue(
      avgResponseTime < 2000,
      "Average response time too high: " + avgResponseTime + "ms"
    );
    Assertions.assertTrue(
      p95 < 3000,
      "P95 response time too high: " + p95 + "ms"
    );
    Assertions.assertTrue(
      requestsPerSecond >= 50,
      "Throughput too low: " + requestsPerSecond + " req/s"
    );
  }

  @Test
  @Order(2)
  @DisplayName("Prueba de escalamiento gradual")
  void testGradualScaling() throws InterruptedException {
    logger.info("Iniciando prueba de escalamiento gradual...");

    final int[] userCounts = { 5, 10, 20, 30, 50 };
    final int DURATION_PER_STEP = 10; // segundos

    for (int userCount : userCounts) {
      logger.info("Probando con {} usuarios simultáneos...", userCount);

      final ExecutorService executor = Executors.newFixedThreadPool(userCount);
      final AtomicInteger requests = new AtomicInteger(0);
      final AtomicInteger successes = new AtomicInteger(0);
      final AtomicLong responseTime = new AtomicLong(0);
      final CountDownLatch startLatch = new CountDownLatch(1);
      final CountDownLatch endLatch = new CountDownLatch(userCount);

      // Iniciar usuarios
      for (int i = 0; i < userCount; i++) {
        executor.submit(() -> {
          try {
            startLatch.await(); // Esperar señal de inicio

            long endTime =
              System.currentTimeMillis() + (DURATION_PER_STEP * 1000);
            while (System.currentTimeMillis() < endTime) {
              long start = System.currentTimeMillis();
              requests.incrementAndGet();

              if (makeHttpRequest("/")) {
                successes.incrementAndGet();
                responseTime.addAndGet(System.currentTimeMillis() - start);
              }

              Thread.sleep(200); // 5 requests por segundo por usuario
            }
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
          } finally {
            endLatch.countDown();
          }
        });
      }

      long stepStart = System.currentTimeMillis();
      startLatch.countDown(); // Iniciar todos los usuarios

      boolean completed = endLatch.await(
        DURATION_PER_STEP + 5,
        TimeUnit.SECONDS
      );
      executor.shutdown();

      long stepTime = System.currentTimeMillis() - stepStart;
      double successRate = requests.get() > 0
        ? (double) successes.get() / requests.get() * 100
        : 0;
      double avgResponseTime = successes.get() > 0
        ? (double) responseTime.get() / successes.get()
        : 0;
      double throughput = (double) requests.get() / (stepTime / 1000.0);

      logger.info(
        "- {} usuarios: {} req, {:.1f}% éxito, {:.1f}ms avg, {:.1f} req/s",
        userCount,
        requests.get(),
        successRate,
        avgResponseTime,
        throughput
      );

      Assertions.assertTrue(
        completed,
        "Step with " + userCount + " users did not complete in time"
      );
      Assertions.assertTrue(
        successRate >= 85.0,
        "Success rate too low with " +
        userCount +
        " users: " +
        successRate +
        "%"
      );

      // Pausa entre steps
      Thread.sleep(2000);
    }
  }

  @Test
  @Order(3)
  @DisplayName("Prueba de resistencia a picos de tráfico")
  void testTrafficSpikes() throws InterruptedException {
    logger.info("Iniciando prueba de resistencia a picos de tráfico...");

    final int NORMAL_LOAD = 10;
    final int SPIKE_LOAD = 50;
    final int SPIKE_DURATION = 5; // segundos
    final int TEST_CYCLES = 3;

    for (int cycle = 1; cycle <= TEST_CYCLES; cycle++) {
      logger.info("Ciclo {} de {}", cycle, TEST_CYCLES);

      // Fase 1: Carga normal
      logger.info("- Carga normal ({} usuarios)", NORMAL_LOAD);
      LoadResult normalResult = runLoadPhase(NORMAL_LOAD, 5);

      // Fase 2: Pico de carga
      logger.info("- Pico de carga ({} usuarios)", SPIKE_LOAD);
      LoadResult spikeResult = runLoadPhase(SPIKE_LOAD, SPIKE_DURATION);

      // Fase 3: Vuelta a carga normal
      logger.info("- Recuperación ({} usuarios)", NORMAL_LOAD);
      LoadResult recoveryResult = runLoadPhase(NORMAL_LOAD, 5);

      // Validaciones
      Assertions.assertTrue(
        normalResult.successRate >= 95.0,
        "Normal load success rate too low in cycle " + cycle
      );
      Assertions.assertTrue(
        spikeResult.successRate >= 80.0,
        "Spike load success rate too low in cycle " + cycle
      );
      Assertions.assertTrue(
        recoveryResult.successRate >= 90.0,
        "Recovery success rate too low in cycle " + cycle
      );

      logger.info(
        "- Ciclo {}: Normal {:.1f}%, Pico {:.1f}%, Recuperación {:.1f}%",
        cycle,
        normalResult.successRate,
        spikeResult.successRate,
        recoveryResult.successRate
      );

      // Pausa entre ciclos
      if (cycle < TEST_CYCLES) {
        Thread.sleep(3000);
      }
    }
  }

  @Test
  @Order(4)
  @DisplayName("Prueba de estabilidad a largo plazo")
  void testLongTermStability() throws InterruptedException {
    logger.info(
      "Iniciando prueba de estabilidad a largo plazo (30 segundos)..."
    );

    final int CONCURRENT_USERS = 15;
    final int TEST_DURATION = 30; // segundos
    final ExecutorService executor = Executors.newFixedThreadPool(
      CONCURRENT_USERS
    );
    final AtomicInteger totalRequests = new AtomicInteger(0);
    final AtomicInteger successfulRequests = new AtomicInteger(0);
    final AtomicLong totalResponseTime = new AtomicLong(0);
    final CountDownLatch startLatch = new CountDownLatch(1);
    final CountDownLatch endLatch = new CountDownLatch(CONCURRENT_USERS);

    // Métricas por intervalos
    final List<LoadResult> intervalResults = Collections.synchronizedList(
      new ArrayList<>()
    );

    // Monitor de métricas cada 5 segundos
    ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor();
    final AtomicInteger lastRequests = new AtomicInteger(0);
    final AtomicInteger lastSuccesses = new AtomicInteger(0);

    monitor.scheduleAtFixedRate(
      () -> {
        int currentRequests = totalRequests.get();
        int currentSuccesses = successfulRequests.get();
        int intervalRequests = currentRequests - lastRequests.get();
        int intervalSuccesses = currentSuccesses - lastSuccesses.get();

        double intervalSuccessRate = intervalRequests > 0
          ? (double) intervalSuccesses / intervalRequests * 100
          : 0;

        intervalResults.add(
          new LoadResult(intervalSuccessRate, 0, intervalRequests)
        );

        lastRequests.set(currentRequests);
        lastSuccesses.set(currentSuccesses);

        logger.info(
          "- Intervalo: {} req, {:.1f}% éxito",
          intervalRequests,
          intervalSuccessRate
        );
      },
      5,
      5,
      TimeUnit.SECONDS
    );

    // Usuarios haciendo requests continuamente
    for (int i = 0; i < CONCURRENT_USERS; i++) {
      executor.submit(() -> {
        try {
          startLatch.await();

          long endTime = System.currentTimeMillis() + (TEST_DURATION * 1000);
          while (System.currentTimeMillis() < endTime) {
            long start = System.currentTimeMillis();
            totalRequests.incrementAndGet();

            if (makeHttpRequest("/api/stats")) {
              successfulRequests.incrementAndGet();
              totalResponseTime.addAndGet(System.currentTimeMillis() - start);
            }

            Thread.sleep(300); // ~3 requests por segundo por usuario
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          endLatch.countDown();
        }
      });
    }

    long testStart = System.currentTimeMillis();
    startLatch.countDown();

    boolean completed = endLatch.await(TEST_DURATION + 10, TimeUnit.SECONDS);
    executor.shutdown();
    monitor.shutdown();

    long testTime = System.currentTimeMillis() - testStart;

    // Resultados finales
    double overallSuccessRate = totalRequests.get() > 0
      ? (double) successfulRequests.get() / totalRequests.get() * 100
      : 0;
    double avgResponseTime = successfulRequests.get() > 0
      ? (double) totalResponseTime.get() / successfulRequests.get()
      : 0;
    double throughput = (double) totalRequests.get() / (testTime / 1000.0);

    // Calcular estabilidad (variación en success rate)
    double minSuccessRate = intervalResults
      .stream()
      .mapToDouble(r -> r.successRate)
      .filter(rate -> rate > 0)
      .min()
      .orElse(0);
    double maxSuccessRate = intervalResults
      .stream()
      .mapToDouble(r -> r.successRate)
      .max()
      .orElse(0);
    double stabilityVariation = maxSuccessRate - minSuccessRate;

    logger.info("Resultados de estabilidad a largo plazo:");
    logger.info("- Duración: {:.1f}s", testTime / 1000.0);
    logger.info("- Total requests: {}", totalRequests.get());
    logger.info("- Success rate general: {:.2f}%", overallSuccessRate);
    logger.info("- Average response time: {:.2f}ms", avgResponseTime);
    logger.info("- Throughput: {:.2f} req/s", throughput);
    logger.info(
      "- Success rate min/max: {:.1f}% / {:.1f}%",
      minSuccessRate,
      maxSuccessRate
    );
    logger.info("- Stability variation: {:.1f}%", stabilityVariation);

    Assertions.assertTrue(completed, "Long term test did not complete in time");
    Assertions.assertTrue(
      overallSuccessRate >= 90.0,
      "Overall success rate too low: " + overallSuccessRate + "%"
    );
    Assertions.assertTrue(
      minSuccessRate >= 80.0,
      "Minimum interval success rate too low: " + minSuccessRate + "%"
    );
    Assertions.assertTrue(
      stabilityVariation <= 20.0,
      "Success rate too unstable: " + stabilityVariation + "% variation"
    );
    Assertions.assertTrue(
      avgResponseTime < 1000,
      "Average response time too high: " + avgResponseTime + "ms"
    );
  }

  /**
   * Ejecuta una fase de carga específica
   */
  private LoadResult runLoadPhase(int userCount, int durationSeconds)
    throws InterruptedException {
    final ExecutorService executor = Executors.newFixedThreadPool(userCount);
    final AtomicInteger requests = new AtomicInteger(0);
    final AtomicInteger successes = new AtomicInteger(0);
    final AtomicLong responseTime = new AtomicLong(0);
    final CountDownLatch startLatch = new CountDownLatch(1);
    final CountDownLatch endLatch = new CountDownLatch(userCount);

    for (int i = 0; i < userCount; i++) {
      executor.submit(() -> {
        try {
          startLatch.await();

          long endTime = System.currentTimeMillis() + (durationSeconds * 1000);
          while (System.currentTimeMillis() < endTime) {
            long start = System.currentTimeMillis();
            requests.incrementAndGet();

            if (makeHttpRequest("/")) {
              successes.incrementAndGet();
              responseTime.addAndGet(System.currentTimeMillis() - start);
            }

            Thread.sleep(150);
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          endLatch.countDown();
        }
      });
    }

    long start = System.currentTimeMillis();
    startLatch.countDown();
    endLatch.await(durationSeconds + 5, TimeUnit.SECONDS);
    executor.shutdown();
    long duration = System.currentTimeMillis() - start;

    double successRate = requests.get() > 0
      ? (double) successes.get() / requests.get() * 100
      : 0;
    double avgResponseTime = successes.get() > 0
      ? (double) responseTime.get() / successes.get()
      : 0;

    return new LoadResult(successRate, avgResponseTime, requests.get());
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
   * Calcula el percentil de una lista ordenada
   */
  private long getPercentile(List<Long> sortedValues, int percentile) {
    if (sortedValues.isEmpty()) return 0;

    int index = (int) Math.ceil(percentile / 100.0 * sortedValues.size()) - 1;
    index = Math.max(0, Math.min(index, sortedValues.size() - 1));

    return sortedValues.get(index);
  }

  /**
   * Clase para encapsular resultados de carga
   */
  private static class LoadResult {

    final double successRate;
    final double avgResponseTime;
    final int totalRequests;

    LoadResult(double successRate, double avgResponseTime, int totalRequests) {
      this.successRate = successRate;
      this.avgResponseTime = avgResponseTime;
      this.totalRequests = totalRequests;
    }
  }
}
