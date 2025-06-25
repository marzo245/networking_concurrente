@echo off
echo =================================================
echo    Servidor Web Concurrente - Scripts de Build
echo =================================================
echo.

:menu
echo Selecciona una opcion:
echo 1. Compilar proyecto
echo 2. Ejecutar servidor
echo 3. Ejecutar pruebas
echo 4. Ejecutar pruebas de carga
echo 5. Limpiar proyecto
echo 6. Generar JAR ejecutable
echo 7. Ver logs en tiempo real
echo 8. Salir
echo.
set /p choice="Ingresa tu eleccion (1-8): "

if "%choice%"=="1" goto compile
if "%choice%"=="2" goto run_server
if "%choice%"=="3" goto run_tests
if "%choice%"=="4" goto run_load_tests
if "%choice%"=="5" goto clean
if "%choice%"=="6" goto build_jar
if "%choice%"=="7" goto view_logs
if "%choice%"=="8" goto exit

echo Opcion invalida. Intenta de nuevo.
goto menu

:compile
echo.
echo Compilando proyecto...
mvn clean compile
if %errorlevel%==0 (
    echo ✓ Compilacion exitosa
) else (
    echo ✗ Error en compilacion
)
pause
goto menu

:run_server
echo.
echo Iniciando servidor...
echo Presiona Ctrl+C para detener el servidor
echo.
mvn exec:java -Dexec.mainClass="com.networking.Main"
pause
goto menu

:run_tests
echo.
echo Ejecutando pruebas...
mvn test
if %errorlevel%==0 (
    echo ✓ Todas las pruebas pasaron
) else (
    echo ✗ Algunas pruebas fallaron
)
pause
goto menu

:run_load_tests
echo.
echo Ejecutando pruebas de carga...
echo NOTA: Asegurate de que el servidor este corriendo en otra terminal
mvn test -Dtest=LoadTest
if %errorlevel%==0 (
    echo ✓ Pruebas de carga completadas
) else (
    echo ✗ Pruebas de carga fallaron
)
pause
goto menu

:clean
echo.
echo Limpiando proyecto...
mvn clean
if %errorlevel%==0 (
    echo ✓ Limpieza exitosa
) else (
    echo ✗ Error en limpieza
)
pause
goto menu

:build_jar
echo.
echo Generando JAR ejecutable...
mvn clean package
if %errorlevel%==0 (
    echo ✓ JAR generado exitosamente en target/
) else (
    echo ✗ Error generando JAR
)
pause
goto menu

:view_logs
echo.
echo Abriendo logs en tiempo real...
if exist logs\concurrent-server.log (
    powershell -Command "Get-Content -Path 'logs\concurrent-server.log' -Wait"
) else (
    echo No se encontraron logs. El servidor debe estar corriendo primero.
)
pause
goto menu

:exit
echo.
echo ¡Gracias por usar el Servidor Web Concurrente!
exit /b 0
