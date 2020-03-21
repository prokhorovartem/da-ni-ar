package ru.ifmo.daniar.service;

import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public final class Bot extends TelegramLongPollingCommandBot {

    private static final String BOT_NAME = "Da Ni Ar";
    private static final String BOT_TOKEN = "961694457:AAHrgGPDkBsvWzQ-mNWQr9smAzzx9j8XKaI";

    public Bot(DefaultBotOptions botOptions) {
        super(botOptions, BOT_NAME);
    }

    @Override
    public void processNonCommandUpdate(Update update) {
        Message message = update.getMessage();
        SendMessage answer = new SendMessage();
        answer.setText("Привет");
        answer.setChatId(message.getChatId());
        replyToUser(answer);
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    private void replyToUser(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException ignored) {
        }
    }
}
