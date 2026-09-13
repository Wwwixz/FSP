@echo off
rem DocHelper: запуск бэка, фронта и публичного туннеля одной кнопкой.
chcp 65001 >nul
cd /d "%~dp0"

echo [1/3] Бэкенд (порт 8080)...
start "DocHelper backend" /min cmd /c ".tools\apache-maven-3.9.9\bin\mvn.cmd" -q -o -f backend\pom.xml spring-boot:run

echo [2/3] Фронтенд (порт 4321)...
cd frontend
start "DocHelper frontend" /min cmd /c npm run preview
cd ..

echo [3/3] Публичный туннель (ссылка появится в окне туннеля)...
start "DocHelper tunnel - СКОПИРУЙ ССЫЛКУ ИЗ ЭТОГО ОКНА" .tools\cloudflared.exe tunnel --url http://localhost:4321 --no-autoupdate --protocol http2

timeout /t 3 >nul
start http://localhost:4321
echo Готово. Локально: http://localhost:4321
pause
