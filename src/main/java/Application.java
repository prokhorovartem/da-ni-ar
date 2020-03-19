import org.telegram.telegrambots.ApiContextInitializer;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.meta.ApiContext;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.exceptions.TelegramApiRequestException;

public class Application {
    public static void main(String[] args) {
        try {
            ApiContextInitializer.init();

            TelegramBotsApi botsApi = new TelegramBotsApi();

            DefaultBotOptions botOptions = ApiContext.getInstance(DefaultBotOptions.class);

            botsApi.registerBot(new Bot(botOptions));

        } catch (TelegramApiRequestException ignored) {
        }
    }
}
