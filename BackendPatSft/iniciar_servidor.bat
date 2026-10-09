@echo off
chcp 65001 > nul
title PoolGuard - Lanzador
echo ============================================================
echo           PoolGuard - Sistema de Monitoreo de Piscinas
echo ============================================================
echo.

cd /d "%~dp0"

echo [1/3] Verificando Java y javac...
where java >nul 2>&1
if %ERRORLEVEL% neq 0 (
    echo [ERROR] No se encontro Java instalado en el sistema.
    echo Por favor instala Java JDK 8 o superior y vuelve a intentar.
    pause
    exit /b 1
)

echo [2/3] Compilando el proyecto...
if not exist "backend\out" mkdir "backend\out"
powershell -NoProfile -Command "$files = (Get-ChildItem -Path backend\src, database -Filter *.java -Recurse).FullName; javac -encoding UTF-8 -d backend/out $files"
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Error durante la compilacion.
    pause
    exit /b 1
)
echo [OK] Compilacion exitosa.

echo.
echo [3/3] Iniciando el servidor Java...
start "PoolGuard Servidor Java (NO CERRAR)" cmd /c "title PoolGuard Servidor Java (NO CERRAR) & java -cp backend/out com.poolguard.Main 8080 & pause"

echo Esperando 3 segundos a que el servidor este listo...
timeout /t 3 /nobreak > nul

echo Abriendo navegador en http://localhost:8080 ...
start "" http://localhost:8080

echo.
echo ============================================================
echo   ✔ PoolGuard iniciado con exito en http://localhost:8080
echo   IMPORTANTE: Deja abierta la ventana negra "PoolGuard Servidor Java"
echo               mientras uses la aplicacion.
echo ============================================================
echo.
pause
