package com.campamento.bot;

import com.campamento.dto.GroupRankingDTO;
import com.campamento.dto.KidRankingDTO;
import com.campamento.dto.LogDetailDTO;
import com.campamento.model.CampGroup;
import com.campamento.model.Config;
import com.campamento.model.Kid;
import com.campamento.model.Log;
import com.campamento.model.Monitor;
import com.campamento.service.CampamentoService;

import org.telegram.telegrambots.longpolling.util.LongPollingSingleThreadUpdateConsumer;
import org.telegram.telegrambots.meta.api.methods.AnswerCallbackQuery;
import org.telegram.telegrambots.meta.api.methods.send.SendDocument;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.DeleteMessage;
import org.telegram.telegrambots.meta.api.objects.InputFile;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardRow;
import org.telegram.telegrambots.meta.generics.TelegramClient;

import java.io.File;
import java.io.FileWriter;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CampamentoBot implements LongPollingSingleThreadUpdateConsumer {

    private final TelegramClient telegramClient;
    private final CampamentoService service;

    // Estado efímero de niños seleccionados por usuario (TelegramId -> Set<KidId>)
    private final Map<Long, Set<Long>> selectedKidsMap = new ConcurrentHashMap<>();

    // Estado efímero para el ID del grupo durante la creación masiva de niños
    private final Map<Long, Long> batchGroupIdMap = new ConcurrentHashMap<>();

    // --- Asistente de Creación de Monitores ---
    private static class PendingMonitor {
        Long groupId; // null significa "Monitor Flotante" (sin grupo)
        String name;
    }
    private final Map<Long, PendingMonitor> pendingMonitorMap = new ConcurrentHashMap<>();
    
    // Estado transitorio de entrada (ej. "ADD_MONITOR_NAME", "EDIT_KID_NAME_X", etc.)
    private final Map<Long, String> adminInputState = new ConcurrentHashMap<>();
    private final Map<Long, Integer> activeMenuIds = new ConcurrentHashMap<>();
    
    // Peticiones de acceso temporal
    private final Map<Long, String> accessRequests = new ConcurrentHashMap<>();

    public CampamentoBot(TelegramClient telegramClient, CampamentoService service) {
        this.telegramClient = telegramClient;
        this.service = service;
    }

    @Override
    public void consume(Update update) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                if (update.hasMessage() && update.getMessage().hasText()) {
                    handleTextMessage(update);
                } else if (update.hasCallbackQuery()) {
                    handleCallbackQuery(update);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    // --- MANEJO DE MENSAJES DE TEXTO ---
    private void handleTextMessage(Update update) throws Exception {
        long chatId = update.getMessage().getChatId();
        long telegramId = update.getMessage().getFrom().getId();
        String text = update.getMessage().getText().trim();
        int userMsgId = update.getMessage().getMessageId();

        if (text.equals("/start")) {
            Integer oldMenuId = activeMenuIds.remove(chatId);
            if (oldMenuId != null) {
                try {
                    telegramClient.executeAsync(DeleteMessage.builder().chatId(chatId).messageId(oldMenuId).build());
                } catch (Exception ignored) {}
            }
            adminInputState.remove(telegramId);
            selectedKidsMap.remove(telegramId);
            batchGroupIdMap.remove(telegramId);
        } else {
            try {
                telegramClient.executeAsync(DeleteMessage.builder().chatId(chatId).messageId(userMsgId).build());
            } catch (Exception ignored) {}
        }

        // --- Manejo de Estados de Creación del Admin ---
        if (adminInputState.containsKey(telegramId)) {
            String state = adminInputState.remove(telegramId);
            try {
                if ("ADD_GROUP".equals(state)) {
                    CampGroup group = service.addGroup(telegramId, text);
                    sendMainMenuSmart(chatId, telegramId, "✅ Групу **" + group.getName() + "** створено або вона вже існує.\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:");
                    return;
                } else if ("ADD_KID_NEW_GROUP_FOR_BATCH".equals(state)) {
                    CampGroup group = service.addGroup(telegramId, text.trim());
                    batchGroupIdMap.put(telegramId, group.getId());
                    adminInputState.put(telegramId, "ADD_KID_BATCH");
                    
                    List<InlineKeyboardRow> rows = new ArrayList<>();
                    rows.add(new InlineKeyboardRow(createButton("⬅️ Завершити", "batch_kid_finish")));
                    sendOrEditMenu(chatId, "⛺ **Додавання дітей до групи " + group.getName() + "**\n\nНапишіть ім'я дитини та надішліть. Повторіть для всіх, кого хочете додати.\n\nНатисніть **Завершити**, коли закінчите.", new InlineKeyboardMarkup(rows));
                    return;
                } else if ("ADD_KID_BATCH".equals(state)) {
                    Long groupId = batchGroupIdMap.get(telegramId);
                    if (groupId == null) throw new IllegalArgumentException("Error: no hay grupo seleccionado.");
                    service.addKidToExistingGroup(telegramId, text.trim(), groupId);
                    
                    adminInputState.put(telegramId, "ADD_KID_BATCH");
                    List<InlineKeyboardRow> rows = new ArrayList<>();
                    rows.add(new InlineKeyboardRow(createButton("⬅️ Завершити", "batch_kid_finish")));
                    sendOrEditMenu(chatId, "✅ **" + text.trim() + "** додано. Продовжуйте писати імена...", new InlineKeyboardMarkup(rows));
                    return;
                } else if ("REQUEST_ACCESS_NAME".equals(state)) {
                    accessRequests.put(telegramId, text.trim());
                    Integer oldMenuId = activeMenuIds.get(chatId);
                    if (oldMenuId != null) {
                        try {
                            editMessageText(chatId, oldMenuId, "✅ Ваш запит надіслано з іменем: *" + text.trim() + "*. Очікуйте на підтвердження адміністратором.", null);
                        } catch (Exception e) {
                            sendTextMessage(chatId, "✅ Ваш запит надіслано з іменем: *" + text.trim() + "*. Очікуйте на підтвердження адміністратором.");
                        }
                    } else {
                        sendTextMessage(chatId, "✅ Ваш запит надіслано з іменем: *" + text.trim() + "*. Очікуйте на підтвердження адміністратором.");
                    }
                    adminInputState.remove(telegramId);
                    return;
                } else if ("ADD_MONITOR_NEW_GROUP".equals(state)) {
                    CampGroup group = service.addGroup(telegramId, text.trim());
                    PendingMonitor pm = new PendingMonitor();
                    pm.groupId = group.getId();
                    pendingMonitorMap.put(telegramId, pm);
                    adminInputState.put(telegramId, "ADD_MONITOR_NAME");
                    
                    List<InlineKeyboardRow> rows = new ArrayList<>();
                    rows.add(new InlineKeyboardRow(createButton("⬅️ Скасувати", "admin_panel")));
                    sendOrEditMenu(chatId, "👨‍🏫 *Додати монітора до групи " + group.getName() + "*\n\nНадішліть ім'я монітора:", new InlineKeyboardMarkup(rows));
                    return;
                } else if ("ADD_MONITOR_NAME".equals(state)) {
                    PendingMonitor pm = pendingMonitorMap.get(telegramId);
                    if (pm == null) throw new IllegalArgumentException("Error: se perdieron los datos.");
                    pm.name = text.trim();
                    showMonitorRoleSelection(chatId, telegramId, pm.name);
                    return;
                } else if (state.startsWith("EDIT_MON_NAME_")) {
                    long monId = Long.parseLong(state.substring("EDIT_MON_NAME_".length()));
                    Monitor m = service.getMonitorById(monId);
                    if (m != null) {
                        m.setName(text.trim());
                        service.updateMonitor(telegramId, m);
                    }
                    sendMainMenuSmart(chatId, telegramId, "✅ Ім'я монітора оновлено.\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:");
                    return;
                } else if (state.startsWith("EDIT_KID_NAME_")) {
                    long kidId = Long.parseLong(state.substring("EDIT_KID_NAME_".length()));
                    Kid k = service.getKidById(kidId);
                    if (k != null) {
                        k.setName(text.trim());
                        service.updateKid(telegramId, k);
                    }
                    sendMainMenuSmart(chatId, telegramId, "✅ Ім'я дитини оновлено.\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:");
                    return;
                } else if (state.startsWith("EDIT_KID_PTS_")) {
                    long kidId = Long.parseLong(state.substring("EDIT_KID_PTS_".length()));
                    int pts = Integer.parseInt(text.trim());
                    service.assignManualPoints(telegramId, kidId, pts);
                    Kid k = service.getKidById(kidId);
                    String kidName = (k != null) ? k.getName() : "Дитина";
                    String confirmText = "✅ Вручну " + (pts > 0 ? "додано " : "віднято ") + Math.abs(pts) + " балів дитині **" + kidName + "**.\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:";
                    sendMainMenuSmart(chatId, telegramId, confirmText);
                    return;
                } else if ("EDIT_DAILY_LIMIT_".equals(state)) {
                    int newLimit = Integer.parseInt(text.trim());
                    if (newLimit < 0) throw new IllegalArgumentException("Ліміт не може бути від'ємним.");
                    Config cfg = service.getConfig();
                    service.updateConfig(telegramId, newLimit, cfg.isGlobalPointsEnable());
                    String confirmText = "✅ Денний ліміт успішно змінено на **" + newLimit + "** балів.\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:";
                    sendMainMenuSmart(chatId, telegramId, confirmText);
                    return;
                }
            } catch (IllegalArgumentException e) {
                sendTextMessage(chatId, "❌ " + e.getMessage());
            } catch (Exception e) {
                e.printStackTrace();
                sendTextMessage(chatId, "❌ Сталася помилка під час збереження в базу даних.");
            }
            sendMainMenuSmart(chatId, telegramId, "Повернення до меню...");
            return;
        }

        Monitor monitor = service.getMonitorByTelegramId(telegramId);
        if (monitor != null && monitor.getId() == 0L && service.isSuperAdmin(telegramId)) {
            List<Monitor> unassigned = service.getUnassignedMonitors();
            if (!unassigned.isEmpty()) {
                List<InlineKeyboardRow> rows = new ArrayList<>();
                rows.add(new InlineKeyboardRow(createButton("🙋‍♂️ Запросити доступ", "request_access")));
                sendMessageWithKeyboard(chatId, "⚠️ **Попередження Супер Адміна:** Ваш акаунт Telegram не прив'язаний до жодного профілю. Ви можете запросити доступ тут або призначити себе через Панель адміністратора.", new InlineKeyboardMarkup(rows));
            }
        } else if (monitor == null) {
            List<Monitor> unassigned = service.getUnassignedMonitors();
            if (unassigned.isEmpty()) {
                sendTextMessage(chatId, "⛔ *Ой, доступ закрито.* Наразі немає вільних місць для моніторів у базі.");
            } else {
                List<InlineKeyboardRow> rows = new ArrayList<>();
                rows.add(new InlineKeyboardRow(createButton("🙋‍♂️ Запросити доступ", "request_access")));
                sendMessageWithKeyboard(chatId, "👋 Привіт!\n\nВи не зареєстровані як монітор. Проте є вільні профілі. Якщо ви один із них, натисніть кнопку, щоб запросити доступ.", new InlineKeyboardMarkup(rows));
            }
            return;
        }
        sendMainMenuSmart(chatId, telegramId, "🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:");
    }

    private void handleCallbackQuery(Update update) throws Exception {
        String callbackId = update.getCallbackQuery().getId();
        long chatId = update.getCallbackQuery().getMessage().getChatId();
        int messageId = update.getCallbackQuery().getMessage().getMessageId();
        long telegramId = update.getCallbackQuery().getFrom().getId();
        String data = update.getCallbackQuery().getData();

        answerCallback(callbackId, null);

        if (data.equals("menu_main")) {
            selectedKidsMap.remove(telegramId);
            sendMainMenuEdit(chatId, messageId, telegramId, "Головне меню:");
        } else if (data.equals("request_access")) {
            adminInputState.put(telegramId, "REQUEST_ACCESS_NAME");
            editMessageText(chatId, messageId, "Будь ласка, введіть своє ім'я та прізвище для ідентифікації адміністратором:", null);
        } else if (data.equals("points_start")) {
            selectedKidsMap.remove(telegramId);
            showGroupSelectionOrKids(chatId, messageId, telegramId);
        } else if (data.equals("kids_cancel_selection")) {
            selectedKidsMap.remove(telegramId);
            editMessageText(chatId, messageId, "❌ Вибір скасовано.", getBackToMenuKeyboard());
        } else if (data.equals("historial_logs")) {
            showRecentLogs(chatId, messageId, telegramId);
        } else if (data.startsWith("group_select_")) {
            long groupId = Long.parseLong(data.substring("group_select_".length()));
            showKidsSelectionMenu(chatId, messageId, telegramId, groupId);
        } else if (data.startsWith("kid_toggle_")) {
            String[] parts = data.substring("kid_toggle_".length()).split("_");
            long kidId = Long.parseLong(parts[0]);
            long groupId = Long.parseLong(parts[1]);
            Set<Long> selected = selectedKidsMap.computeIfAbsent(telegramId, k -> ConcurrentHashMap.newKeySet());
            if (selected.contains(kidId)) {
                selected.remove(kidId);
            } else {
                selected.add(kidId);
            }
            showKidsSelectionMenu(chatId, messageId, telegramId, groupId);
        } else if (data.startsWith("kids_confirm_")) {
            Set<Long> selected = selectedKidsMap.get(telegramId);
            if (selected == null || selected.isEmpty()) {
                answerCallback(callbackId, "⚠️ Оберіть хоча б одну дитину.");
                return;
            }
            showPointDistributionMenu(chatId, messageId, telegramId);
        } else if (data.startsWith("give_pts_")) {
            int pts = Integer.parseInt(data.substring("give_pts_".length()));
            Set<Long> selected = selectedKidsMap.remove(telegramId);
            if (selected == null || selected.isEmpty()) {
                editMessageText(chatId, messageId, "⚠️ Вибір скасовано через таймаут. Почніть спочатку.", null);
                return;
            }
            try {
                service.assignPointsBatch(telegramId, new ArrayList<>(selected), pts);
                String confirmText = String.format("✅ **Клас!** Ми щойно додали **+%d балів** для %d учасників.", pts, selected.size());
                sendMainMenuEdit(chatId, messageId, telegramId, confirmText + "\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:");
            } catch (Exception e) {
                editMessageText(chatId, messageId, "❌ **Error:** " + e.getMessage(), getBackToMenuKeyboard());
            }
        } else if (data.equals("ranking_menu")) {
            showRankingMenu(chatId, messageId);
        } else if (data.equals("rank_top_global")) {
            showTopGlobalRanking(chatId, messageId, telegramId);
        } else if (data.equals("rank_groups")) {
            showGroupRankings(chatId, messageId, telegramId);
        } else if (data.equals("rank_kids_by_group")) {
            showRankKidsByGroupSelection(chatId, messageId);
        } else if (data.startsWith("rank_kids_grp_")) {
            long groupId = Long.parseLong(data.substring("rank_kids_grp_".length()));
            showTopKidsByGroup(chatId, messageId, telegramId, groupId);
        } else if (data.equals("annul_menu")) {
            showAnnulMenu(chatId, messageId, telegramId);
        } else if (data.startsWith("annul_log_")) {
            long logId = Long.parseLong(data.substring("annul_log_".length()));
            try {
                Log log = service.annulRecentLog(telegramId, logId);
                if (log != null) {
                    Kid k = service.getKidById(log.getKidId());
                    String kidName = (k != null) ? k.getName() : "Дитина";
                    String confirmText = "✅ Скасовано **" + log.getNumPoints() + "** балів дитині **" + kidName + "**.";
                    sendMainMenuEdit(chatId, messageId, telegramId, confirmText + "\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:");
                } else {
                    editMessageText(chatId, messageId, "❌ Не вдалося скасувати нарахування.", getBackToMenuKeyboard());
                }
            } catch (Exception e) {
                editMessageText(chatId, messageId, "❌ Error: " + e.getMessage(), getBackToMenuKeyboard());
            }
        } else if (data.equals("admin_panel")) {
            if (!service.isRealAdmin(telegramId)) {
                answerCallback(callbackId, "⛔ У вас немає прав адміністратора.");
                return;
            }
            showAdminPanel(chatId, messageId, telegramId);
        } else if (data.equals("admin_view_requests")) {
            showAccessRequestsList(chatId, messageId);
        } else if (data.startsWith("req_acc_sel_")) {
            long reqTelegramId = Long.parseLong(data.substring("req_acc_sel_".length()));
            showAccessRequestDetails(chatId, messageId, reqTelegramId);
        } else if (data.startsWith("req_acc_link_")) {
            String[] parts = data.substring("req_acc_link_".length()).split("_");
            long reqTelegramId = Long.parseLong(parts[0]);
            long monitorId = Long.parseLong(parts[1]);
            service.assignTelegramIdToMonitor(monitorId, reqTelegramId);
            accessRequests.remove(reqTelegramId);
            sendTextMessage(reqTelegramId, "✅ Ваш доступ підтверджено адміністратором. Напишіть /start, щоб почати!");
            editMessageText(chatId, messageId, "✅ Запит успішно прив'язано.", getBackToMenuKeyboard());
        } else if (data.startsWith("req_acc_rej_")) {
            long reqTelegramId = Long.parseLong(data.substring("req_acc_rej_".length()));
            accessRequests.remove(reqTelegramId);
            sendTextMessage(reqTelegramId, "❌ Ваш запит на доступ відхилено.");
            editMessageText(chatId, messageId, "❌ Запит відхилено та видалено.", getBackToMenuKeyboard());
        } else if (data.equals("admin_add_group")) {
            adminInputState.put(telegramId, "ADD_GROUP");
            editMessageText(chatId, messageId, "🏕️ *Додати групу*\n\nНадішліть назву групи.\nНаприклад: `Група А`", getBackToMenuKeyboard());
        } else if (data.equals("admin_add_kid")) {
            answerCallback(callbackId, "Завантаження груп...");
            List<CampGroup> groups = service.getAllGroups();
            List<InlineKeyboardRow> rows = new ArrayList<>();
            for (CampGroup g : groups) {
                rows.add(new InlineKeyboardRow(createButton("🟢 " + g.getName(), "batch_kid_grp_" + g.getId())));
            }
            rows.add(new InlineKeyboardRow(createButton("➕ Створити нову групу", "batch_kid_new_grp")));
            rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "admin_panel")));
            editMessageText(chatId, messageId, "👦 *Додати дітей (Масовий режим)*\n\nДо якої групи хочете додати дітей?", new InlineKeyboardMarkup(rows));
        } else if (data.startsWith("batch_kid_grp_")) {
            Long groupId = Long.parseLong(data.substring("batch_kid_grp_".length()));
            adminInputState.put(telegramId, "ADD_KID_BATCH");
            batchGroupIdMap.put(telegramId, groupId);
            answerCallback(callbackId, "Масовий режим увімкнено");
            List<InlineKeyboardRow> rows = new ArrayList<>();
            rows.add(new InlineKeyboardRow(createButton("⬅️ Завершити", "batch_kid_finish")));
            editMessageText(chatId, messageId, "⛺ **Додавання дітей до обраної групи**\n\nНапишіть ім'я дитини та надішліть. Повторіть для всіх.\n\nНатисніть **Завершити**, коли закінчите.", new InlineKeyboardMarkup(rows));
        } else if (data.equals("batch_kid_new_grp")) {
            adminInputState.put(telegramId, "ADD_KID_NEW_GROUP_FOR_BATCH");
            answerCallback(callbackId, "Створити нову групу");
            List<InlineKeyboardRow> rows = new ArrayList<>();
            rows.add(new InlineKeyboardRow(createButton("⬅️ Скасувати", "admin_panel")));
            editMessageText(chatId, messageId, "⛺ *Створити нову групу*\n\nНапишіть назву нової групи, куди будете додавати дітей:", new InlineKeyboardMarkup(rows));
        } else if (data.equals("batch_kid_finish")) {
            adminInputState.remove(telegramId);
            batchGroupIdMap.remove(telegramId);
            answerCallback(callbackId, "Масовий режим завершено");
            showAdminPanel(chatId, messageId, telegramId);
        } else if (data.equals("admin_add_monitor")) {
            showMonitorGroupSelection(chatId, messageId);
        } else if (data.startsWith("mon_add_grp_")) {
            String grpIdStr = data.substring("mon_add_grp_".length());
            Long groupId = "null".equals(grpIdStr) ? null : Long.parseLong(grpIdStr);
            PendingMonitor pm = new PendingMonitor();
            pm.groupId = groupId;
            pendingMonitorMap.put(telegramId, pm);
            adminInputState.put(telegramId, "ADD_MONITOR_NAME");
            String groupName = null;
            if (groupId != null) {
                CampGroup g = service.getAllGroups().stream().filter(grp -> grp.getId().equals(groupId)).findFirst().orElse(null);
                if (g != null) groupName = g.getName();
            }
            showMonitorNamePrompt(chatId, messageId, groupName);
        } else if (data.equals("mon_add_new_grp")) {
            adminInputState.put(telegramId, "ADD_MONITOR_NEW_GROUP");
            answerCallback(callbackId, "Створити нову групу");
            List<InlineKeyboardRow> rows = new ArrayList<>();
            rows.add(new InlineKeyboardRow(createButton("⬅️ Скасувати", "admin_panel")));
            editMessageText(chatId, messageId, "⛺ *Створити нову групу*\n\nНапишіть назву нової групи, до якої належатиме монітор:", new InlineKeyboardMarkup(rows));
        } else if (data.startsWith("mon_opts_")) {
            String[] parts = data.substring("mon_opts_".length()).split("_");
            boolean isAdmin = Boolean.parseBoolean(parts[0]);
            boolean isSolo = Boolean.parseBoolean(parts[1]);
            PendingMonitor pm = pendingMonitorMap.remove(telegramId);
            if (pm != null && pm.name != null) {
                service.createMonitorProfile(telegramId, pm.name, pm.groupId, isAdmin, isSolo);
                editMessageText(chatId, messageId, "✅ Монітора **" + pm.name + "** успішно створено. Очікується прив'язка.", getBackToMenuKeyboard());
            } else {
                editMessageText(chatId, messageId, "❌ Помилка: Втрачено дані монітора.", getBackToMenuKeyboard());
            }
        } else if (data.equals("admin_config")) {
            showConfigMenu(chatId, messageId, telegramId);
        } else if (data.startsWith("toggle_global_")) {
            boolean current = Boolean.parseBoolean(data.substring("toggle_global_".length()));
            Config cfg = service.getConfig();
            service.updateConfig(telegramId, cfg.getDailyLimit(), !current);
            showConfigMenu(chatId, messageId, telegramId);
        } else if (data.equals("admin_edit_limit")) {
            editMessageText(chatId, messageId, "Введіть новий щоденний ліміт балів (число):", getBackToMenuKeyboard());
            adminInputState.put(telegramId, "EDIT_DAILY_LIMIT_");
        } else if (data.equals("admin_audit_txt")) {
            exportAuditLogsFile(chatId, messageId, telegramId);
        } else if (data.equals("admin_audit_monitor")) {
            showAuditMonitorSelection(chatId, messageId);
        } else if (data.startsWith("audit_mon_")) {
            long monId = Long.parseLong(data.substring("audit_mon_".length()));
            exportAuditLogsForMonitor(chatId, messageId, telegramId, monId);
        } else if (data.equals("admin_manage_monitors")) {
            showManageMonitorsList(chatId, messageId);
        } else if (data.startsWith("mon_edit_roles_")) {
            long monId = Long.parseLong(data.substring("mon_edit_roles_".length()));
            Monitor m = service.getMonitorById(monId);
            if (m != null) {
                showEditMonitorRoleSelection(chatId, messageId, m);
            }
        } else if (data.startsWith("mon_edit_")) {
            long monId = Long.parseLong(data.substring("mon_edit_".length()));
            showManageMonitorDetails(chatId, messageId, monId);
        } else if (data.equals("admin_manage_kids")) {
            showManageKidsGroupSelection(chatId, messageId);
        } else if (data.startsWith("kids_manage_grp_")) {
            long grpId = Long.parseLong(data.substring("kids_manage_grp_".length()));
            showManageKidsList(chatId, messageId, grpId);
        } else if (data.startsWith("kid_edit_")) {
            long kId = Long.parseLong(data.substring("kid_edit_".length()));
            showManageKidDetails(chatId, messageId, kId);
        } else if (data.startsWith("mon_rename_")) {
            long monId = Long.parseLong(data.substring("mon_rename_".length()));
            adminInputState.put(telegramId, "EDIT_MON_NAME_" + monId);
            editMessageText(chatId, messageId, "Напишіть нове ім'я для цього монітора:", getBackToMenuKeyboard());
        } else if (data.startsWith("mon_update_opts_")) {
            String[] parts = data.substring("mon_update_opts_".length()).split("_");
            boolean isAdmin = Boolean.parseBoolean(parts[0]);
            boolean isSolo = Boolean.parseBoolean(parts[1]);
            long monId = Long.parseLong(parts[2]);
            Monitor m = service.getMonitorById(monId);
            if (m != null) {
                m.setAdmin(isAdmin);
                m.setSoloMonitor(isSolo);
                service.updateMonitor(telegramId, m);
                showManageMonitorDetails(chatId, messageId, monId);
            }
        } else if (data.startsWith("mon_del_")) {
            long monId = Long.parseLong(data.substring("mon_del_".length()));
            service.deleteMonitor(telegramId, monId);
            answerCallback(callbackId, "Монітора видалено");
            showManageMonitorsList(chatId, messageId);
        } else if (data.startsWith("kid_rename_")) {
            long kId = Long.parseLong(data.substring("kid_rename_".length()));
            adminInputState.put(telegramId, "EDIT_KID_NAME_" + kId);
            editMessageText(chatId, messageId, "Напишіть нове ім'я для цієї дитини:", getBackToMenuKeyboard());
        } else if (data.startsWith("admin_kid_pts_")) {
            long kId = Long.parseLong(data.substring("admin_kid_pts_".length()));
            editMessageText(chatId, messageId, "Введіть кількість балів для ручного нарахування/зняття (можна з мінусом):", getBackToMenuKeyboard());
            adminInputState.put(telegramId, "EDIT_KID_PTS_" + kId);
        } else if (data.startsWith("kid_del_")) {
            long kId = Long.parseLong(data.substring("kid_del_".length()));
            Kid k = service.getKidById(kId);
            Long gId = (k != null) ? k.getGroupId() : null;
            service.deleteKid(telegramId, kId);
            answerCallback(callbackId, "Дитину видалено");
            if (gId != null) {
                showManageKidsList(chatId, messageId, gId);
            } else {
                showManageKidsGroupSelection(chatId, messageId);
            }
        }
    }

    private void sendMainMenuSmart(long chatId, long telegramId, String text) throws Exception {
        Integer oldMenuId = activeMenuIds.get(chatId);
        if (oldMenuId != null) {
            try {
                sendMainMenuEdit(chatId, oldMenuId, telegramId, text);
                return;
            } catch (Exception e) {
                // Fallback
            }
        }
        sendMainMenu(chatId, telegramId, text);
    }

    private void sendMainMenu(long chatId, long telegramId, String text) throws Exception {
        boolean isAdmin = service.isRealAdmin(telegramId);
        int usedToday = service.getDailyPointsUsed(telegramId);
        String finalText = text.replace("Оберіть опцію:", "Оберіть опцію:\n\n📊 Витрачені бали сьогодні: " + usedToday);
        if (!finalText.contains("📊 Витрачені бали сьогодні:")) {
            finalText = finalText + "\n\n📊 Витрачені бали сьогодні: " + usedToday;
        }

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(createButton("🎯 Додати бали", "points_start")));
        rows.add(new InlineKeyboardRow(createButton("🏆 Рейтинги", "ranking_menu")));
        rows.add(new InlineKeyboardRow(createButton("📜 Останні дії", "historial_logs")));
        rows.add(new InlineKeyboardRow(createButton("↩️ Відмінити бали", "annul_menu")));
        if (isAdmin) rows.add(new InlineKeyboardRow(createButton("⚙️ Панель адміністратора", "admin_panel")));
        SendMessage msg = SendMessage.builder().chatId(chatId).text(finalText).replyMarkup(new InlineKeyboardMarkup(rows)).build();
        org.telegram.telegrambots.meta.api.objects.message.Message newMsg = telegramClient.execute(msg);
        activeMenuIds.put(chatId, newMsg.getMessageId());
    }

    private void sendMainMenuEdit(long chatId, int messageId, long telegramId, String text) throws Exception {
        boolean isAdmin = service.isRealAdmin(telegramId);
        int usedToday = service.getDailyPointsUsed(telegramId);
        String finalText = text.replace("Оберіть опцію:", "Оберіть опцію:\n\n📊 Витрачені бали сьогодні: " + usedToday);
        if (!finalText.contains("📊 Витрачені бали сьогодні:")) {
            finalText = finalText + "\n\n📊 Витрачені бали сьогодні: " + usedToday;
        }

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(createButton("🎯 Додати бали", "points_start")));
        rows.add(new InlineKeyboardRow(createButton("🏆 Рейтинги", "ranking_menu")));
        rows.add(new InlineKeyboardRow(createButton("📜 Останні дії", "historial_logs")));
        rows.add(new InlineKeyboardRow(createButton("↩️ Відмінити бали", "annul_menu")));
        if (isAdmin) rows.add(new InlineKeyboardRow(createButton("⚙️ Панель адміністратора", "admin_panel")));
        editMessageText(chatId, messageId, finalText, new InlineKeyboardMarkup(rows));
    }

    private void showGroupSelectionOrKids(long chatId, int messageId, long telegramId) throws Exception {
        Config config = service.getConfig();
        Monitor monitor = service.getMonitorByTelegramId(telegramId);
        if (config.isGlobalPointsEnable() || service.isRealAdmin(telegramId)) {
            List<CampGroup> groups = service.getAllGroups();
            List<InlineKeyboardRow> rows = new ArrayList<>();
            for (CampGroup g : groups) rows.add(new InlineKeyboardRow(createButton("📁 " + g.getName(), "group_select_" + g.getId())));
            rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "menu_main")));
            editMessageText(chatId, messageId, "Оберіть групу:", new InlineKeyboardMarkup(rows));
        } else {
            if (monitor != null && monitor.getGroupId() != null) showKidsSelectionMenu(chatId, messageId, telegramId, monitor.getGroupId());
            else editMessageText(chatId, messageId, "❌ У вас немає закріпленої групи.", getBackToMenuKeyboard());
        }
    }

    private void showKidsSelectionMenu(long chatId, int messageId, long telegramId, long groupId) throws Exception {
        List<Kid> kids = service.getKidsByGroup(groupId);
        Set<Long> selected = selectedKidsMap.getOrDefault(telegramId, Collections.emptySet());
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Kid k : kids) {
            boolean isChecked = selected.contains(k.getId());
            String label = (isChecked ? "✅ " : "⬜ ") + k.getName() + " (" + k.getPoints() + " pts)";
            rows.add(new InlineKeyboardRow(createButton(label, "kid_toggle_" + k.getId() + "_" + groupId)));
        }
        String confirmLabel = String.format("✅ 🟢 ПІДТВЕРДИТИ ВИБІР (%d) 🟢 ✅", selected.size());
        rows.add(new InlineKeyboardRow(createButton(confirmLabel, "kids_confirm_" + groupId)));
        if (!selected.isEmpty()) rows.add(new InlineKeyboardRow(createButton("❌ Скасувати вибір", "kids_cancel_selection")));
        
        Config config = service.getConfig();
        String backPayload = (config.isGlobalPointsEnable() || service.isRealAdmin(telegramId)) ? "points_start" : "menu_main";
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", backPayload)));
        
        editMessageTextAsync(chatId, messageId, "Відмітьте дітей, яким хочете нарахувати бали:", new InlineKeyboardMarkup(rows));
    }

    private void showPointDistributionMenu(long chatId, int messageId, long telegramId) throws Exception {
        Set<Long> selected = selectedKidsMap.getOrDefault(telegramId, Collections.emptySet());
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(createButton("🥇 +1 Бал", "give_pts_1"), createButton("🥈 +2 Бали", "give_pts_2"), createButton("🥉 +3 Бали", "give_pts_3")));
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад до вибору", "points_start")));
        String text = String.format("Скільки балів ви хочете нарахувати обраним дітя (**%d** )?", selected.size());
        editMessageText(chatId, messageId, text, new InlineKeyboardMarkup(rows));
    }

    private void showRankingMenu(long chatId, int messageId) throws Exception {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(createButton("🥇 Топ 10 дітей (Загальний)", "rank_top_global")));
        rows.add(new InlineKeyboardRow(createButton("🥇 Рейтинг дітей у групі", "rank_kids_by_group")));
        rows.add(new InlineKeyboardRow(createButton("📊 Рейтинг груп (Середній бал)", "rank_groups")));
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "menu_main")));
        editMessageText(chatId, messageId, "🏆 **Розділ Рейтингів**", new InlineKeyboardMarkup(rows));
    }

    private void showTopGlobalRanking(long chatId, int messageId, long telegramId) throws Exception {
        List<KidRankingDTO> rankings = service.getTopKidsGlobal(10);
        StringBuilder sb = new StringBuilder("🏆 **Топ 10 дітей (Загальний):**\n\n");
        if (rankings.isEmpty()) sb.append("Записів ще немає.");
        else {
            int pos = 1;
            for (KidRankingDTO r : rankings) sb.append(String.format("%d. **%s** — `%d бали`\n", pos++, r.getKidName(), r.getPoints()));
        }
        sb.append("\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:");
        sendMainMenuEdit(chatId, messageId, telegramId, sb.toString());
    }

    private void showRankKidsByGroupSelection(long chatId, int messageId) throws Exception {
        List<CampGroup> groups = service.getAllGroups();
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (CampGroup g : groups) rows.add(new InlineKeyboardRow(createButton("📁 " + g.getName(), "rank_kids_grp_" + g.getId())));
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "ranking_menu")));
        editMessageText(chatId, messageId, "🥇 **Рейтинг по групах**\nОберіть групу:", new InlineKeyboardMarkup(rows));
    }

    private void showTopKidsByGroup(long chatId, int messageId, long telegramId, long groupId) throws Exception {
        List<KidRankingDTO> rankings = service.getTopKidsByGroup(groupId, 50);
        StringBuilder sb = new StringBuilder();
        if (rankings.isEmpty()) sb.append("У цій групі ще немає записів.");
        else {
            sb.append("🏆 **Рейтинг: ").append(rankings.get(0).getGroupName()).append("**\n\n");
            int pos = 1;
            for (KidRankingDTO r : rankings) sb.append(String.format("%d. **%s** — `%d бали`\n", pos++, r.getKidName(), r.getPoints()));
        }
        sb.append("\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:");
        sendMainMenuEdit(chatId, messageId, telegramId, sb.toString());
    }

    private void showRecentLogs(long chatId, int messageId, long telegramId) throws Exception {
        List<LogDetailDTO> logs = service.getRecentGlobalLogs(20);
        StringBuilder sb = new StringBuilder("📜 **Останні 20 дій:**\n\n");
        if (logs.isEmpty()) sb.append("Ще не зареєстровано жодної дії.");
        else {
            java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter.ofPattern("HH:mm");
            for (LogDetailDTO l : logs) {
                String timeStr = l.getTime() != null ? l.getTime().format(formatter) : "--:--";
                String action = l.getNumPoints() >= 0 ? "додав" : "відняв";
                sb.append(String.format("🔸 %s - [%s] %s **%d бали** -> %s\n", timeStr, l.getMonitorName(), action, Math.abs(l.getNumPoints()), l.getKidName()));
            }
        }
        sb.append("\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:");
        sendMainMenuEdit(chatId, messageId, telegramId, sb.toString());
    }

    private void showGroupRankings(long chatId, int messageId, long telegramId) throws Exception {
        List<GroupRankingDTO> rankings = service.getGroupRankings();
        StringBuilder sb = new StringBuilder("📊 **Рейтинг груп (Середній бал):**\n\n");
        if (rankings.isEmpty()) sb.append("Немає зареєстрованих груп.");
        else {
            int pos = 1;
            for (GroupRankingDTO r : rankings) sb.append(String.format("%d. **%s** — `%.2f pts media`\n", pos++, r.getGroupName(), r.getAveragePoints()));
        }
        sb.append("\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:");
        sendMainMenuEdit(chatId, messageId, telegramId, sb.toString());
    }

    private void showAnnulMenu(long chatId, int messageId, long telegramId) throws Exception {
        List<LogDetailDTO> logs = service.getRecentLogsByMonitor(telegramId, 5);
        if (logs.isEmpty()) {
            editMessageText(chatId, messageId, "ℹ️ Немає недавніх нарахувань для скасування.", getBackToMenuKeyboard());
            return;
        }
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (LogDetailDTO l : logs) rows.add(new InlineKeyboardRow(createButton(String.format("❌ Скасувати %+d балів для %s", l.getNumPoints(), l.getKidName()), "annul_log_" + l.getLogId())));
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "menu_main")));
        editMessageText(chatId, messageId, "Оберіть дію для скасування:", new InlineKeyboardMarkup(rows));
    }

    private void showAdminPanel(long chatId, int messageId, long telegramId) throws Exception {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        int reqCount = accessRequests.size();
        rows.add(new InlineKeyboardRow(createButton(reqCount > 0 ? "🙋‍♂️ Запити на доступ (" + reqCount + ")" : "🙋‍♂️ Запити на доступ", "admin_view_requests")));
        rows.add(new InlineKeyboardRow(createButton("➕ Додати групу", "admin_add_group"), createButton("➕ Додати дитину", "admin_add_kid")));
        rows.add(new InlineKeyboardRow(createButton("➕ Додати монітора", "admin_add_monitor")));
        rows.add(new InlineKeyboardRow(createButton("📋 Керування дітьми", "admin_manage_kids"), createButton("📋 Керування моніторами", "admin_manage_monitors")));
        rows.add(new InlineKeyboardRow(createButton("⚙️ Загальні налаштування", "admin_config")));
        rows.add(new InlineKeyboardRow(createButton("🕵️‍♂️ Аудит моніторів", "admin_audit_monitor")));
        rows.add(new InlineKeyboardRow(createButton("📜 Завантажити аудит (.txt)", "admin_audit_txt")));
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "menu_main")));
        editMessageText(chatId, messageId, "⚙️ **Панель адміністратора**", new InlineKeyboardMarkup(rows));
    }

    private void showAccessRequestsList(long chatId, int messageId) throws Exception {
        if (accessRequests.isEmpty()) {
            editMessageText(chatId, messageId, "✅ Немає запитів на доступ.", getBackToMenuKeyboard());
            return;
        }
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Map.Entry<Long, String> entry : accessRequests.entrySet()) rows.add(new InlineKeyboardRow(createButton("Запит: " + entry.getValue(), "req_acc_sel_" + entry.getKey())));
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "admin_panel")));
        editMessageText(chatId, messageId, "Оберіть запит, щоб призначити монітора:", new InlineKeyboardMarkup(rows));
    }

    private void showAccessRequestDetails(long chatId, int messageId, long reqTelegramId) throws Exception {
        String name = accessRequests.get(reqTelegramId);
        if (name == null) {
            editMessageText(chatId, messageId, "⚠️ Цього запиту більше не існує.", getBackToMenuKeyboard());
            return;
        }
        List<Monitor> unassigned = service.getUnassignedMonitors();
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Monitor m : unassigned) rows.add(new InlineKeyboardRow(createButton("🔗 Прив'язати: " + m.getName(), "req_acc_link_" + reqTelegramId + "_" + m.getId())));
        rows.add(new InlineKeyboardRow(createButton("❌ Відхилити запит", "req_acc_rej_" + reqTelegramId)));
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "admin_view_requests")));
        editMessageText(chatId, messageId, String.format("🙋‍♂️ **Запит на доступ**\n\nІм'я: %s\nTelegram ID: `%d`\n\nОберіть вільний профіль:", name, reqTelegramId), new InlineKeyboardMarkup(rows));
    }

    private void showConfigMenu(long chatId, int messageId, long telegramId) throws Exception {
        Config config = service.getConfig();
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(createButton(config.isGlobalPointsEnable() ? "🌐 Глобальні бали: УВІМКНЕНО" : "🔒 Строгий режим по групах: УВІМКНЕНО", "toggle_global_" + config.isGlobalPointsEnable())));
        rows.add(new InlineKeyboardRow(createButton("✏️ Змінити ліміт балів", "admin_edit_limit")));
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "admin_panel")));
        editMessageText(chatId, messageId, String.format("⚙️ **Налаштування табору:**\n\n- Базовий денний ліміт: `%d балів`\n- Стан нарахування: **%s**", config.getDailyLimit(), config.isGlobalPointsEnable() ? "Вільно між групами" : "Тільки своя група"), new InlineKeyboardMarkup(rows));
    }

    private void exportAuditLogsFile(long chatId, int messageId, long telegramId) throws Exception {
        List<LogDetailDTO> logs = service.getLogsForAudit(LocalDateTime.now().minusDays(30), LocalDateTime.now().plusDays(1));
        if (logs.isEmpty()) {
            sendMainMenuEdit(chatId, messageId, telegramId, "ℹ️ Немає записів аудиту за останні 30 днів.\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:");
            return;
        }
        File tempFile = File.createTempFile("auditoria_", ".txt");
        try (FileWriter writer = new FileWriter(tempFile)) {
            for (LogDetailDTO l : logs) writer.write(l.toFormattedString() + "\n");
        }
        SendDocument doc = SendDocument.builder().chatId(chatId).document(new InputFile(tempFile)).caption("📜 Reporte de Auditoría").build();
        telegramClient.execute(doc);
        tempFile.delete();
        sendMainMenuEdit(chatId, messageId, telegramId, "✅ Файл аудиту згенеровано!\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:");
    }

    private void showAuditMonitorSelection(long chatId, int messageId) throws Exception {
        List<Monitor> all = service.getAllMonitors();
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Monitor m : all) rows.add(new InlineKeyboardRow(createButton("👨‍🏫 " + m.getName(), "audit_mon_" + m.getId())));
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "admin_panel")));
        editMessageText(chatId, messageId, "🕵️‍♂️ **Аудит монітора**", new InlineKeyboardMarkup(rows));
    }

    private void exportAuditLogsForMonitor(long chatId, int messageId, long adminTelegramId, long monitorId) throws Exception {
        List<LogDetailDTO> logs = service.getRecentLogsByMonitorIdForAdmin(adminTelegramId, monitorId, 100);
        if (logs.isEmpty()) {
            sendMainMenuEdit(chatId, messageId, adminTelegramId, "ℹ️ У цього монітора немає недавніх записів.");
            return;
        }
        Monitor m = service.getMonitorById(monitorId);
        String mName = m != null ? m.getName() : "Monitor";
        File tempFile = File.createTempFile("auditoria_" + mName.replaceAll("\\s+", "_") + "_", ".txt");
        try (FileWriter writer = new FileWriter(tempFile)) {
            writer.write("=====================================================\n");
            writer.write("          AUDITORÍA - " + mName.toUpperCase() + "\n");
            writer.write("=====================================================\n\n");
            for (LogDetailDTO l : logs) {
                writer.write(l.toFormattedString() + "\n");
            }
        }

        SendDocument doc = SendDocument.builder()
                .chatId(chatId)
                .document(new InputFile(tempFile))
                .caption("📜 Últimos 100 movimientos de " + mName)
                .build();
        telegramClient.execute(doc);
        tempFile.delete();

        sendMainMenuEdit(chatId, messageId, adminTelegramId, "✅ Файл аудиту згенеровано!\n\n🏕️ **Вітаємо в Системі Балів Табору**\nОберіть опцію:");
    }

    // --- HELPER METHODS ---
    private InlineKeyboardButton createButton(String text, String callbackData) {
        return InlineKeyboardButton.builder()
                .text(text)
                .callbackData(callbackData)
                .build();
    }

    private InlineKeyboardMarkup getBackToMenuKeyboard() {
        return new InlineKeyboardMarkup(List.of(new InlineKeyboardRow(createButton("⬅️ Головне меню", "menu_main"))));
    }

    private org.telegram.telegrambots.meta.api.objects.message.Message sendTextMessage(long chatId, String text) throws Exception {
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .build();
        return telegramClient.execute(message);
    }

    private void sendMessageWithKeyboard(long chatId, String text, InlineKeyboardMarkup keyboard) throws Exception {
        Integer oldMenuId = activeMenuIds.get(chatId);
        if (oldMenuId != null) {
            try {
                telegramClient.executeAsync(DeleteMessage.builder().chatId(chatId).messageId(oldMenuId).build());
            } catch (Exception ignored) {}
        }
        SendMessage message = SendMessage.builder()
                .chatId(chatId)
                .text(text)
                .parseMode("Markdown")
                .replyMarkup(keyboard)
                .build();
        org.telegram.telegrambots.meta.api.objects.message.Message newMsg = telegramClient.execute(message);
        activeMenuIds.put(chatId, newMsg.getMessageId());
    }

    private void editMessageText(long chatId, int messageId, String text, InlineKeyboardMarkup markup) throws Exception {
        EditMessageText edit = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .replyMarkup(markup)
                .build();
        try {
            telegramClient.execute(edit);
            activeMenuIds.put(chatId, messageId);
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException e) {
            // Ignorar errores de "message is not modified" o "Too Many Requests" por pulsar rápido
            if (!e.getApiResponse().contains("is not modified") && !e.getApiResponse().contains("Too Many Requests")) {
                throw e;
            }
        }
    }

    private void editMessageTextAsync(long chatId, int messageId, String text, InlineKeyboardMarkup markup) {
        EditMessageText edit = EditMessageText.builder()
                .chatId(chatId)
                .messageId(messageId)
                .text(text)
                .replyMarkup(markup)
                .build();
        try {
            telegramClient.executeAsync(edit).whenComplete((msg, ex) -> {
                if (ex != null && !ex.getMessage().contains("is not modified")) {
                    ex.printStackTrace();
                } else if (ex == null && msg instanceof org.telegram.telegrambots.meta.api.objects.message.Message) {
                    activeMenuIds.put(chatId, ((org.telegram.telegrambots.meta.api.objects.message.Message) msg).getMessageId());
                }
            });
        } catch (org.telegram.telegrambots.meta.exceptions.TelegramApiException e) {
            e.printStackTrace();
        }
    }
    // --- UI Gestión de Monitores ---
    private void showManageMonitorsList(long chatId, int messageId) throws Exception {
        List<Monitor> all = service.getAllMonitors();
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Monitor m : all) {
            String role = m.isAdmin() ? "👑" : "👨‍🏫";
            rows.add(new InlineKeyboardRow(createButton(role + " " + m.getName(), "mon_edit_" + m.getId())));
        }
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "admin_panel")));
        editMessageText(chatId, messageId, "📋 **Керування моніторами**\nОберіть для редагування:", new InlineKeyboardMarkup(rows));
    }

    private void showManageMonitorDetails(long chatId, int messageId, long monitorId) throws Exception {
        Monitor m = service.getMonitorById(monitorId);
        if (m == null) {
            editMessageText(chatId, messageId, "❌ Монітора не знайдено.", getBackToMenuKeyboard());
            return;
        }
        String groupName = "Немає (Вільний)";
        if (m.getGroupId() != null) {
            CampGroup g = service.getAllGroups().stream().filter(grp -> grp.getId().equals(m.getGroupId())).findFirst().orElse(null);
            if (g != null) groupName = g.getName();
        }
        
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(createButton("✏️ Перейменувати", "mon_rename_" + m.getId())));
        rows.add(new InlineKeyboardRow(createButton("⚙️ Редагувати права (Адмін/Сам)", "mon_edit_roles_" + m.getId())));
        rows.add(new InlineKeyboardRow(createButton("🗑️ Видалити монітора", "mon_del_" + m.getId())));
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "admin_manage_monitors")));
        
        String text = String.format("👨‍🏫 **Монітор:** %s\n**Група:** %s\n**Адмін:** %s\n**Сам:** %s\n\nЩо ви хочете зробити?",
                m.getName(), groupName, m.isAdmin() ? "Так" : "Ні", m.isSoloMonitor() ? "Так" : "Ні");
        editMessageText(chatId, messageId, text, new InlineKeyboardMarkup(rows));
    }

    // --- UI Gestión de Niños ---
    private void showManageKidsGroupSelection(long chatId, int messageId) throws Exception {
        List<CampGroup> groups = service.getAllGroups();
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (CampGroup g : groups) {
            rows.add(new InlineKeyboardRow(createButton("📁 " + g.getName(), "kids_manage_grp_" + g.getId())));
        }
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "admin_panel")));
        editMessageText(chatId, messageId, "📋 **Керування дітьми**\nОберіть групу:", new InlineKeyboardMarkup(rows));
    }

    private void showManageKidsList(long chatId, int messageId, long groupId) throws Exception {
        List<Kid> kids = service.getKidsByGroup(groupId);
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (Kid k : kids) {
            rows.add(new InlineKeyboardRow(createButton("👦 " + k.getName() + " (" + k.getPoints() + " pts)", "kid_edit_" + k.getId())));
        }
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "admin_manage_kids")));
        editMessageText(chatId, messageId, "👦 **Діти в групі**\nОберіть дитину для редагування:", new InlineKeyboardMarkup(rows));
    }

    private void showManageKidDetails(long chatId, int messageId, long kidId) throws Exception {
        Kid k = service.getKidById(kidId);
        if (k == null) {
            editMessageText(chatId, messageId, "❌ Дитину не знайдено.", getBackToMenuKeyboard());
            return;
        }
        String groupName = "Невідомо";
        CampGroup g = service.getAllGroups().stream().filter(grp -> grp.getId().equals(k.getGroupId())).findFirst().orElse(null);
        if (g != null) groupName = g.getName();

        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(createButton("✏️ Перейменувати", "kid_rename_" + k.getId())));
        rows.add(new InlineKeyboardRow(createButton("🔢 Нарахувати/Зняти бали вручну", "admin_kid_pts_" + k.getId())));
        rows.add(new InlineKeyboardRow(createButton("🗑️ Видалити дитину", "kid_del_" + k.getId())));
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "kids_manage_grp_" + k.getGroupId())));

        String text = String.format("👦 **Дитина:** %s\n**Група:** %s\n**Усього балів:** %d\n\nЩо ви хочете зробити?",
                k.getName(), groupName, k.getPoints());
        editMessageText(chatId, messageId, text, new InlineKeyboardMarkup(rows));
    }
    // --- UI Asistente de Monitores ---
    private void showMonitorGroupSelection(long chatId, int messageId) throws Exception {
        List<CampGroup> groups = service.getAllGroups();
        List<InlineKeyboardRow> rows = new ArrayList<>();
        for (CampGroup g : groups) {
            rows.add(new InlineKeyboardRow(createButton("🟢 " + g.getName(), "mon_add_grp_" + g.getId())));
        }
        rows.add(new InlineKeyboardRow(createButton("➕ Створити нову групу", "mon_add_new_grp")));
        rows.add(new InlineKeyboardRow(createButton("🌍 Жодної групи (Вільний монітор)", "mon_add_grp_null")));
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "admin_panel")));
        editMessageText(chatId, messageId, "👨‍🏫 *Додати монітора*\n\nОберіть групу, до якої він належатиме:", new InlineKeyboardMarkup(rows));
    }

    private void showMonitorNamePrompt(long chatId, int messageId, String groupName) throws Exception {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(createButton("⬅️ Скасувати", "admin_panel")));
        String text = groupName != null 
                ? "👨‍🏫 *Añadir Monitor al grupo " + groupName + "*\n\nEnvía el nombre del monitor:"
                : "🌍 *Додати вільного монітора*\n\nНадішліть ім'я монітора:";
        editMessageText(chatId, messageId, text, new InlineKeyboardMarkup(rows));
    }

    private void sendOrEditMenu(long chatId, String text, InlineKeyboardMarkup markup) throws Exception {
        Integer oldMenuId = activeMenuIds.get(chatId);
        if (oldMenuId != null) {
            try {
                editMessageText(chatId, oldMenuId, text, markup);
                return;
            } catch (Exception e) {
                // Fallback
            }
        }
        sendMessageWithKeyboard(chatId, text, markup);
    }

    private void showMonitorRoleSelection(long chatId, long telegramId, String monitorName) throws Exception {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(createButton("👑 Адмін + 👤 Сам", "mon_opts_true_true")));
        rows.add(new InlineKeyboardRow(createButton("👑 Адмін + 👥 З напарником", "mon_opts_true_false")));
        rows.add(new InlineKeyboardRow(createButton("👨‍🏫 Звичайний + 👤 Сам", "mon_opts_false_true")));
        rows.add(new InlineKeyboardRow(createButton("👨‍🏫 Звичайний + 👥 З напарником", "mon_opts_false_false")));
        
        sendOrEditMenu(chatId, "Налаштуйте ролі для **" + monitorName + "**:", new InlineKeyboardMarkup(rows));
    }

    private void showEditMonitorRoleSelection(long chatId, int messageId, Monitor m) throws Exception {
        List<InlineKeyboardRow> rows = new ArrayList<>();
        rows.add(new InlineKeyboardRow(createButton("👑 Адмін + 👤 Сам", "mon_update_opts_true_true_" + m.getId())));
        rows.add(new InlineKeyboardRow(createButton("👑 Адмін + 👥 З напарником", "mon_update_opts_true_false_" + m.getId())));
        rows.add(new InlineKeyboardRow(createButton("👨‍🏫 Звичайний + 👤 Сам", "mon_update_opts_false_true_" + m.getId())));
        rows.add(new InlineKeyboardRow(createButton("👨‍🏫 Звичайний + 👥 З напарником", "mon_update_opts_false_false_" + m.getId())));
        rows.add(new InlineKeyboardRow(createButton("⬅️ Назад", "mon_edit_" + m.getId())));
        
        editMessageText(chatId, messageId, "Зміна прав для **" + m.getName() + "**:", new InlineKeyboardMarkup(rows));
    }


    private void answerCallback(String callbackQueryId, String text) throws Exception {
        AnswerCallbackQuery answer = AnswerCallbackQuery.builder()
                .callbackQueryId(callbackQueryId)
                .text(text)
                .showAlert(text != null)
                .build();
        telegramClient.execute(answer);
    }
}
