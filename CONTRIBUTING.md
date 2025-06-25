# Contributing to Servidor Web Concurrente

¡Gracias por tu interés en contribuir al proyecto! Este documento proporciona guidelines para contribuir al servidor web concurrente.

## 🚀 Comenzando

### Prerequisites
- Java 11 o superior
- Maven 3.6+
- Git
- IDE recomendado: IntelliJ IDEA o Visual Studio Code

### Setup del Proyecto
1. Fork el repositorio
2. Clona tu fork localmente:
   ```bash
   git clone https://github.com/tu-usuario/networking_concurrente.git
   cd networking_concurrente
   ```
3. Compila el proyecto:
   ```bash
   mvn clean compile
   ```
4. Ejecuta las pruebas:
   ```bash
   mvn test
   ```

## 📋 Guidelines de Contribución

### Código
- Sigue las convenciones de naming de Java
- Documenta métodos públicos con JavaDoc
- Mantén métodos pequeños y con responsabilidad única
- Usa logging apropiado (SLF4J)
- Incluye pruebas para nueva funcionalidad

### Commits
- Usa mensajes descriptivos en español
- Formato: `tipo: descripción breve`
- Tipos: `feat`, `fix`, `docs`, `style`, `refactor`, `test`, `chore`

Ejemplos:
```
feat: añadir soporte para SSL/TLS
fix: corregir memory leak en pool de hilos
docs: actualizar README con nuevas instrucciones
test: añadir pruebas de concurrencia para WebSocket
```

### Pull Requests
1. Crea una rama para tu feature: `git checkout -b feature/nueva-funcionalidad`
2. Haz commits atómicos y bien documentados
3. Ejecuta todas las pruebas antes de hacer push
4. Crea un Pull Request con descripción detallada
5. Incluye evidencia de testing (screenshots, logs, etc.)

## 🧪 Testing

### Tipos de Pruebas
- **Unit Tests**: Pruebas de componentes individuales
- **Integration Tests**: Pruebas de interacción entre componentes
- **Concurrency Tests**: Pruebas específicas de concurrencia
- **Load Tests**: Pruebas de carga y rendimiento

### Ejecutar Pruebas
```bash
# Todas las pruebas
mvn test

# Solo pruebas de concurrencia
mvn test -Dtest=ConcurrencyTest

# Solo pruebas de carga
mvn test -Dtest=LoadTest

# Pruebas específicas del chat
mvn test -Dtest=ChatTest
```

## 🔍 Code Review

### Checklist para Reviews
- [ ] El código sigue las convenciones del proyecto
- [ ] Hay pruebas adecuadas para la nueva funcionalidad
- [ ] La documentación está actualizada
- [ ] No hay memory leaks o problemas de concurrencia
- [ ] El rendimiento es aceptable
- [ ] Los logs son apropiados

## 📚 Áreas de Contribución

### Funcionalidades Deseadas
- [ ] Soporte para HTTPS/SSL
- [ ] Autenticación de usuarios
- [ ] Persistencia de mensajes
- [ ] Salas de chat múltiples
- [ ] Rate limiting
- [ ] Métricas avanzadas (Prometheus/Grafana)
- [ ] Balanceador de carga
- [ ] Clustering

### Mejoras de Rendimiento
- [ ] Optimización de pools de hilos
- [ ] Caching de respuestas estáticas
- [ ] Compresión HTTP
- [ ] Keep-alive connections
- [ ] Non-blocking I/O

### Calidad de Código
- [ ] Aumentar cobertura de pruebas
- [ ] Añadir más pruebas de edge cases
- [ ] Mejorar manejo de errores
- [ ] Refactoring de código duplicado

## 🐛 Reportar Bugs

### Template de Bug Report
```markdown
**Descripción del Bug**
Descripción clara y concisa del problema.

**Pasos para Reproducir**
1. Ir a '...'
2. Hacer clic en '....'
3. Scroll hasta '....'
4. Ver error

**Comportamiento Esperado**
Qué esperabas que pasara.

**Comportamiento Actual**
Qué pasó realmente.

**Screenshots/Logs**
Si es aplicable, añade screenshots o logs.

**Entorno**
- OS: [e.g. Windows 10, Ubuntu 20.04]
- Java Version: [e.g. 11.0.2]
- Maven Version: [e.g. 3.8.1]
```

## 💡 Sugerir Features

### Template de Feature Request
```markdown
**¿El feature resuelve un problema? Describe.**
Descripción clara del problema que el feature resolvería.

**Describe la solución que te gustaría**
Descripción clara de lo que quieres que pase.

**Describe alternativas consideradas**
Descripción de soluciones alternativas que consideraste.

**Contexto adicional**
Cualquier otro contexto o screenshots sobre el feature request.
```

## 📞 Contacto

- **Issues**: Usa GitHub Issues para bugs y feature requests
- **Discussions**: Usa GitHub Discussions para preguntas generales
- **Email**: [tu-email@ejemplo.com] para temas sensibles

## 📄 Licencia

Al contribuir, aceptas que tus contribuciones serán licenciadas bajo la misma licencia del proyecto (MIT License).

---

¡Gracias por contribuir al proyecto! 🎉
