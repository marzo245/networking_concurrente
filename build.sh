#!/bin/bash

# Colores para output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
PURPLE='\033[0;35m'
CYAN='\033[0;36m'
WHITE='\033[1;37m'
NC='\033[0m' # No Color

# Banner
echo -e "${CYAN}================================================="
echo -e "   Servidor Web Concurrente - Scripts de Build"
echo -e "=================================================${NC}"
echo

# Función para mostrar el menú
show_menu() {
    echo -e "${WHITE}Selecciona una opción:${NC}"
    echo -e "${YELLOW}1.${NC} Compilar proyecto"
    echo -e "${YELLOW}2.${NC} Ejecutar servidor"
    echo -e "${YELLOW}3.${NC} Ejecutar pruebas"
    echo -e "${YELLOW}4.${NC} Ejecutar pruebas de carga"
    echo -e "${YELLOW}5.${NC} Limpiar proyecto"
    echo -e "${YELLOW}6.${NC} Generar JAR ejecutable"
    echo -e "${YELLOW}7.${NC} Ver logs en tiempo real"
    echo -e "${YELLOW}8.${NC} Verificar puertos"
    echo -e "${YELLOW}9.${NC} Monitoreo de recursos"
    echo -e "${YELLOW}10.${NC} Salir"
    echo
}

# Función para compilar
compile_project() {
    echo -e "${BLUE}Compilando proyecto...${NC}"
    mvn clean compile
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Compilación exitosa${NC}"
    else
        echo -e "${RED}✗ Error en compilación${NC}"
    fi
}

# Función para ejecutar servidor
run_server() {
    echo -e "${BLUE}Iniciando servidor...${NC}"
    echo -e "${YELLOW}Presiona Ctrl+C para detener el servidor${NC}"
    echo
    mvn exec:java -Dexec.mainClass="com.networking.Main"
}

# Función para ejecutar pruebas
run_tests() {
    echo -e "${BLUE}Ejecutando pruebas...${NC}"
    mvn test
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Todas las pruebas pasaron${NC}"
    else
        echo -e "${RED}✗ Algunas pruebas fallaron${NC}"
    fi
}

# Función para ejecutar pruebas de carga
run_load_tests() {
    echo -e "${BLUE}Ejecutando pruebas de carga...${NC}"
    echo -e "${YELLOW}NOTA: Asegúrate de que el servidor esté corriendo en otra terminal${NC}"
    mvn test -Dtest=LoadTest
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Pruebas de carga completadas${NC}"
    else
        echo -e "${RED}✗ Pruebas de carga fallaron${NC}"
    fi
}

# Función para limpiar
clean_project() {
    echo -e "${BLUE}Limpiando proyecto...${NC}"
    mvn clean
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ Limpieza exitosa${NC}"
    else
        echo -e "${RED}✗ Error en limpieza${NC}"
    fi
}

# Función para generar JAR
build_jar() {
    echo -e "${BLUE}Generando JAR ejecutable...${NC}"
    mvn clean package
    if [ $? -eq 0 ]; then
        echo -e "${GREEN}✓ JAR generado exitosamente en target/${NC}"
    else
        echo -e "${RED}✗ Error generando JAR${NC}"
    fi
}

# Función para ver logs
view_logs() {
    echo -e "${BLUE}Abriendo logs en tiempo real...${NC}"
    if [ -f "logs/concurrent-server.log" ]; then
        tail -f logs/concurrent-server.log
    else
        echo -e "${YELLOW}No se encontraron logs. El servidor debe estar corriendo primero.${NC}"
    fi
}

# Función para verificar puertos
check_ports() {
    echo -e "${BLUE}Verificando puertos del servidor...${NC}"
    echo
    echo -e "${WHITE}Puerto 8080 (HTTP):${NC}"
    if command -v lsof &> /dev/null; then
        lsof -i :8080 || echo -e "${YELLOW}Puerto 8080 libre${NC}"
    elif command -v netstat &> /dev/null; then
        netstat -tlnp | grep :8080 || echo -e "${YELLOW}Puerto 8080 libre${NC}"
    else
        echo -e "${YELLOW}No se puede verificar (lsof/netstat no disponible)${NC}"
    fi
    
    echo
    echo -e "${WHITE}Puerto 8081 (WebSocket):${NC}"
    if command -v lsof &> /dev/null; then
        lsof -i :8081 || echo -e "${YELLOW}Puerto 8081 libre${NC}"
    elif command -v netstat &> /dev/null; then
        netstat -tlnp | grep :8081 || echo -e "${YELLOW}Puerto 8081 libre${NC}"
    else
        echo -e "${YELLOW}No se puede verificar (lsof/netstat no disponible)${NC}"
    fi
}

# Función para monitoreo de recursos
monitor_resources() {
    echo -e "${BLUE}Monitoreando recursos del sistema...${NC}"
    echo -e "${YELLOW}Presiona Ctrl+C para detener el monitoreo${NC}"
    echo
    
    while true; do
        clear
        echo -e "${CYAN}=== Monitor de Recursos ===${NC}"
        echo -e "${WHITE}Fecha: $(date)${NC}"
        echo
        
        # Uso de CPU
        if command -v top &> /dev/null; then
            echo -e "${WHITE}Procesos Java (top 5):${NC}"
            top -b -n1 | grep java | head -5
        fi
        
        echo
        
        # Uso de memoria
        if command -v free &> /dev/null; then
            echo -e "${WHITE}Uso de memoria:${NC}"
            free -h
        fi
        
        echo
        
        # Conexiones de red
        if command -v ss &> /dev/null; then
            echo -e "${WHITE}Conexiones en puertos 8080 y 8081:${NC}"
            ss -tuln | grep -E ':808[01]'
        elif command -v netstat &> /dev/null; then
            echo -e "${WHITE}Conexiones en puertos 8080 y 8081:${NC}"
            netstat -tln | grep -E ':808[01]'
        fi
        
        sleep 2
    done
}

# Bucle principal
while true; do
    show_menu
    read -p "Ingresa tu elección (1-10): " choice
    echo
    
    case $choice in
        1)
            compile_project
            ;;
        2)
            run_server
            ;;
        3)
            run_tests
            ;;
        4)
            run_load_tests
            ;;
        5)
            clean_project
            ;;
        6)
            build_jar
            ;;
        7)
            view_logs
            ;;
        8)
            check_ports
            ;;
        9)
            monitor_resources
            ;;
        10)
            echo -e "${GREEN}¡Gracias por usar el Servidor Web Concurrente!${NC}"
            exit 0
            ;;
        *)
            echo -e "${RED}Opción inválida. Intenta de nuevo.${NC}"
            ;;
    esac
    
    echo
    read -p "Presiona Enter para continuar..."
    echo
done
