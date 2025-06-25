package com.networking.server;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gestor de pools de hilos optimizado para el servidor web concurrente
 */
public class ThreadPoolManager {

  private static final Logger logger = LoggerFactory.getLogger(
    ThreadPoolManager.class
  );

  private static final int CORE_POOL_SIZE = 10;
  private static final int MAX_POOL_SIZE = 50;
  private static final long KEEP_ALIVE_TIME = 60L;
  private static final int QUEUE_CAPACITY = 100;

  private final ThreadPoolExecutor executor;
  private final ScheduledExecutorService scheduledExecutor;
  private final AtomicInteger taskCounter = new AtomicInteger(0);

  public ThreadPoolManager() {
    // Pool principal para manejar requests HTTP
    BlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>(
      QUEUE_CAPACITY
    );

    this.executor =
      new ThreadPoolExecutor(
        CORE_POOL_SIZE,
        MAX_POOL_SIZE,
        KEEP_ALIVE_TIME,
        TimeUnit.SECONDS,
        workQueue,
        new CustomThreadFactory("HttpWorker"),
        new ThreadPoolExecutor.CallerRunsPolicy() // Política de rechazo
      );

    // Pool para tareas programadas (limpieza, métricas, etc.)
    this.scheduledExecutor =
      Executors.newScheduledThreadPool(2, new CustomThreadFactory("Scheduler"));

    // Habilitar métricas del pool
    this.executor.allowCoreThreadTimeOut(true);

    // Programar reporte de métricas cada 30 segundos
    scheduleMetricsReport();

    logger.info(
      "ThreadPoolManager inicializado - Core: {}, Max: {}, Queue: {}",
      CORE_POOL_SIZE,
      MAX_POOL_SIZE,
      QUEUE_CAPACITY
    );
  }

  /**
   * Ejecuta una tarea en el pool de hilos
   */
  public void execute(Runnable task) {
    int taskId = taskCounter.incrementAndGet();
    executor.execute(() -> {
      String originalName = Thread.currentThread().getName();
      Thread.currentThread().setName(originalName + "-Task-" + taskId);
      try {
        task.run();
      } finally {
        Thread.currentThread().setName(originalName);
      }
    });
  }

  /**
   * Ejecuta una tarea con un timeout específico
   */
  public Future<?> submitWithTimeout(
    Runnable task,
    long timeout,
    TimeUnit unit
  ) {
    return executor.submit(() -> {
      try {
        task.run();
      } catch (Exception e) {
        logger.error("Error ejecutando tarea con timeout", e);
        throw new RuntimeException(e);
      }
    });
  }

  /**
   * Programa una tarea para ejecutarse después de un delay
   */
  public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
    return scheduledExecutor.schedule(task, delay, unit);
  }

  /**
   * Programa una tarea para ejecutarse periódicamente
   */
  public ScheduledFuture<?> scheduleAtFixedRate(
    Runnable task,
    long initialDelay,
    long period,
    TimeUnit unit
  ) {
    return scheduledExecutor.scheduleAtFixedRate(
      task,
      initialDelay,
      period,
      unit
    );
  }

  /**
   * Obtiene estadísticas del pool de hilos
   */
  public ThreadPoolStats getStats() {
    return new ThreadPoolStats(
      executor.getPoolSize(),
      executor.getActiveCount(),
      executor.getCompletedTaskCount(),
      executor.getTaskCount(),
      executor.getQueue().size(),
      executor.getQueue().remainingCapacity()
    );
  }

  /**
   * Verifica si el pool puede aceptar más tareas
   */
  public boolean canAcceptMoreTasks() {
    return (
      executor.getQueue().remainingCapacity() > 0 ||
      executor.getActiveCount() < executor.getMaximumPoolSize()
    );
  }

  /**
   * Cierra el pool de hilos de manera elegante
   */
  public void shutdown() {
    logger.info("Iniciando shutdown del ThreadPoolManager...");

    executor.shutdown();
    scheduledExecutor.shutdown();

    try {
      if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
        logger.warn("Pool no terminó en 10 segundos, forzando shutdown...");
        executor.shutdownNow();
      }

      if (!scheduledExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
        scheduledExecutor.shutdownNow();
      }

      logger.info("ThreadPoolManager cerrado exitosamente");
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      executor.shutdownNow();
      scheduledExecutor.shutdownNow();
      logger.error("Shutdown interrumpido", e);
    }
  }

  private void scheduleMetricsReport() {
    scheduleAtFixedRate(
      () -> {
        ThreadPoolStats stats = getStats();
        logger.info(
          "Pool Stats - Active: {}, Pool Size: {}, Queue: {}/{}, " +
          "Completed: {}, Total: {}",
          stats.activeCount,
          stats.poolSize,
          stats.queueSize,
          (stats.queueSize + stats.queueRemainingCapacity),
          stats.completedTaskCount,
          stats.taskCount
        );
      },
      30,
      30,
      TimeUnit.SECONDS
    );
  }

  /**
   * Factory personalizada para crear hilos con nombres descriptivos
   */
  private static class CustomThreadFactory implements ThreadFactory {

    private final AtomicInteger threadNumber = new AtomicInteger(1);
    private final String namePrefix;

    CustomThreadFactory(String namePrefix) {
      this.namePrefix = namePrefix + "-";
    }

    @Override
    public Thread newThread(Runnable r) {
      Thread t = new Thread(r, namePrefix + threadNumber.getAndIncrement());
      if (t.isDaemon()) {
        t.setDaemon(false);
      }
      if (t.getPriority() != Thread.NORM_PRIORITY) {
        t.setPriority(Thread.NORM_PRIORITY);
      }
      return t;
    }
  }

  /**
   * Clase para encapsular estadísticas del pool
   */
  public static class ThreadPoolStats {

    public final int poolSize;
    public final int activeCount;
    public final long completedTaskCount;
    public final long taskCount;
    public final int queueSize;
    public final int queueRemainingCapacity;

    public ThreadPoolStats(
      int poolSize,
      int activeCount,
      long completedTaskCount,
      long taskCount,
      int queueSize,
      int queueRemainingCapacity
    ) {
      this.poolSize = poolSize;
      this.activeCount = activeCount;
      this.completedTaskCount = completedTaskCount;
      this.taskCount = taskCount;
      this.queueSize = queueSize;
      this.queueRemainingCapacity = queueRemainingCapacity;
    }

    @Override
    public String toString() {
      return String.format(
        "ThreadPoolStats{poolSize=%d, active=%d, completed=%d, " +
        "total=%d, queueSize=%d, queueCapacity=%d}",
        poolSize,
        activeCount,
        completedTaskCount,
        taskCount,
        queueSize,
        queueRemainingCapacity
      );
    }
  }
}
