package com.campamento;

import com.campamento.bot.CampamentoBot;
import com.campamento.dao.*;
import com.campamento.dao.impl.*;
import com.campamento.service.CampamentoService;
import org.telegram.telegrambots.client.okhttp.OkHttpTelegramClient;
import org.telegram.telegrambots.longpolling.TelegramBotsLongPollingApplication;
import org.telegram.telegrambots.meta.generics.TelegramClient;

public class Main {

    public static void main(String[] args) {
        String botToken = System.getenv("BOT_TOKEN");
        if (botToken == null || botToken.isBlank()) {
            System.err.println("⚠️ ATENCIÓN: La variable de entorno BOT_TOKEN no está definida.");
            System.err.println("Por favor arranca el bot definiendo BOT_TOKEN.");
            return;
        }

        String superAdminId = System.getenv("SUPER_ADMIN_TELEGRAM_ID");
        if (superAdminId != null && !superAdminId.isBlank()) {
            System.out.println("👑 Super Admin configurado para Telegram ID: " + superAdminId);
        } else {
            System.out.println("ℹ️ Nota: No se ha definido SUPER_ADMIN_TELEGRAM_ID.");
        }

        try (TelegramBotsLongPollingApplication botsApplication = new TelegramBotsLongPollingApplication()) {
            TelegramClient telegramClient = new OkHttpTelegramClient(botToken);

            CampGroupDAO groupDAO = new CampGroupDAOMySQL();
            KidDAO kidDAO = new KidDAOMySQL();
            MonitorDAO monitorDAO = new MonitorDAOMySQL();
            LogDAO logDAO = new LogDAOMySQL();
            ConfigDAO configDAO = new ConfigDAOMySQL();

            CampamentoService service = new CampamentoService(groupDAO, kidDAO, monitorDAO, logDAO, configDAO);
            CampamentoBot bot = new CampamentoBot(telegramClient, service);

            botsApplication.registerBot(botToken, bot);
            System.out.println("🤖 Bot de Campamento arrancado con éxito y escuchando peticiones en Telegram...");
            
            // --- SERVIDOR HTTP PARA RENDER ---
            int port = 8080;
            String portEnv = System.getenv("PORT");
            if (portEnv != null && !portEnv.isBlank()) {
                try {
                    port = Integer.parseInt(portEnv);
                } catch (NumberFormatException ignored) {}
            }
            
            com.sun.net.httpserver.HttpServer server = com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress(port), 0);
            server.createContext("/", exchange -> {
                String response = "Bot de Campamento Activo";
                exchange.sendResponseHeaders(200, response.getBytes().length);
                try (java.io.OutputStream os = exchange.getResponseBody()) {
                    os.write(response.getBytes());
                }
            });
            server.setExecutor(null);
            server.start();
            System.out.println("🌐 Servidor HTTP nativo escuchando en el puerto " + port + " (Para Render/UptimeRobot)");
            // ---------------------------------
            
            // Mantener el hilo vivo
            Thread.currentThread().join();
        } catch (Exception e) {
            System.err.println("❌ Error crítico en el servidor del bot:");
            e.printStackTrace();
        }
    }
}
