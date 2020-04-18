package ru.ifmo.daniar;

import lombok.extern.log4j.Log4j2;
import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.ApiContext;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;
import ru.ifmo.daniar.service.Bot;

@Log4j2
public class Daniar {
    public static void main(String[] args) {
        registerBot();
        System.out.println("Bot has started");
    }

    private static void registerBot() {
        ApiContextInitializer.init();
        TelegramBotsApi botsApi = new TelegramBotsApi();
        DefaultBotOptions botOptions = ApiContext.getInstance(DefaultBotOptions.class);

        try {
            botsApi.registerBot(new Bot(botOptions));
        } catch (TelegramApiRequestException e) {
            log.error("Error during registration bot: ", e);
        }
    }
}
