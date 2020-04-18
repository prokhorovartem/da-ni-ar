package ru.ifmo.daniar.service;

import lombok.extern.log4j.Log4j2;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileWriter;

import java.io.IOException;
import java.util.*;
import java.util.List;

import static java.lang.System.getProperty;

@Log4j2
public final class Bot extends TelegramLongPollingCommandBot {

    private static final String BOT_NAME = "Da Ni Ar";
    private static final String BOT_TOKEN = "token";
    private static final String MODERATORS_CHAT_ID = "@daniar_moders";
    private static final String DESCRIPTIONS_FILE_NAME = "descriptions.txt";
    private static final String TICK_BUTTON_CODE = "\u2705";
    private static final String CROSS_BUTTON_CODE = "\u274C";
    private static List<String> memes = Arrays.asList("Сасный Вампус", "Запомните твари... а то забудите.",
            "Dr. Srars", "Подзезелья и гоблоны", "Миксаил Сольсе-Спайсович", "Я пить Грут", "Bruhforce", "У меня кончились сорцы");

    private ReplyKeyboardMarkup replyKeyboardMarkup;
    private List<KeyboardRow> menuKeyBoard;
    private List<KeyboardRow> cancelKeyBoard;
    private Set<String> descriptions;

    private boolean isWaitingForSignature = false;
    private boolean isWaitingForPhoto = false;

    public Bot(DefaultBotOptions botOptions) {
        super(botOptions, BOT_NAME);
        replyKeyboardMarkup = createReplyKeyBoardMarkup();
        menuKeyBoard = createMenu();
        cancelKeyBoard = createCancelMenu();
        descriptions = loadDescriptions();
    }

    private Set<String> loadDescriptions() {
        Set<String> descriptions = new HashSet<>();
        try (Scanner scanner = new Scanner(DESCRIPTIONS_FILE_NAME)) {
            while (scanner.hasNextLine()) {
                descriptions.add(scanner.nextLine());
            }
        }

        return descriptions;
    }

    private ReplyKeyboardMarkup createReplyKeyBoardMarkup() {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        replyKeyboardMarkup.setSelective(true);
        replyKeyboardMarkup.setResizeKeyboard(true);
        replyKeyboardMarkup.setOneTimeKeyboard(false);
        return replyKeyboardMarkup;
    }

    private List<KeyboardRow> createMenu() {
        KeyboardRow firstRow = new KeyboardRow(),
                secondRow = new KeyboardRow();

        firstRow.addAll(Arrays.asList("Зачем этот бот?", "Предложить подпись"));
        secondRow.addAll(Arrays.asList("Поддержать", "Статус бота"));
        return new ArrayList<>(Arrays.asList(firstRow, secondRow));
    }

    private List<KeyboardRow> createCancelMenu() {
        KeyboardRow firstRow = new KeyboardRow();
        firstRow.add("Отмена");
        return new ArrayList<>(Collections.singletonList(firstRow));
    }

    @Override
    public String getBotToken() {
        return BOT_TOKEN;
    }

    private void executeToUser(SendMessage message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error during sending message: {}", e.getMessage());
        }
    }

    private void executeToUser(EditMessageText message) {
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error during changing message: {}", e.getMessage());
        }
    }

    @Override
    public void processNonCommandUpdate(Update update) {
        log.debug("Message received: {}", update);
        Message inputMessage = update.getMessage();

        if (update.getCallbackQuery() != null && checkUserName(update)) {
            EditMessageText answer = createModerAnswer(update.getCallbackQuery());
            executeToUser(answer);
        } else {
            PartialBotApiMethod<Message> answer = createMessage(inputMessage);
            executeToUser((SendMessage) answer);
        }

    }

    private boolean checkUserName(Update update) {
        return MODERATORS_CHAT_ID.replace("@", "").equals(update.getCallbackQuery().getMessage().getChat().getUserName());
    }

    private EditMessageText createModerAnswer(CallbackQuery query) {
        int messageId = query.getMessage().getMessageId();
        String text = query.getMessage().getText();

        EditMessageText editMessageText = new EditMessageText();
        editMessageText
                .setMessageId(messageId)
                .setChatId(MODERATORS_CHAT_ID);
        String firstName = query.getFrom().getFirstName();
        if (query.getData().equals(TICK_BUTTON_CODE)) {
            String description = query.getMessage().getText();
            addDescription(description);
            writeDescription(description);
            editMessageText.setText(String.format("%s\n%s - %s", text, TICK_BUTTON_CODE, firstName));
        } else if (query.getData().equals(CROSS_BUTTON_CODE)) {
            editMessageText.setText(String.format("%s\n%s - %s", text, CROSS_BUTTON_CODE, firstName));
        }

        return editMessageText;
    }

    private PartialBotApiMethod<Message> createMessage(Message inputMsg) {
        long chatId = inputMsg.getChatId();
        String inputMsgText = inputMsg.getText();

        if (isWaitingForSignature) {
            SendMessage moderMsg = createModerMsg(inputMsgText);
            isWaitingForSignature = false;
            executeToUser(moderMsg);
            return createOutputMsg(
                    chatId,
                    "Подпись отправлена на рассмотрение!\nПришли мне следующую фотографию!",
                    menuKeyBoard);
        }

        if (hasPhotoInside(inputMsg)) {
            PartialBotApiMethod<Message> mem = processMem(inputMsg);
            log.debug("Mem has created for user: {}", inputMsg.getFrom().getId());
            isWaitingForPhoto = false;
            return mem;
        }

        switch (inputMsgText) {
            case "Предложить подпись":
                isWaitingForSignature = true;
                return createOutputMsg(chatId, "Напиши подпись, которую ты хочешь предложить", cancelKeyBoard);
            case "Меню":
                return createOutputMsg(chatId, "Выбрать...", menuKeyBoard);
            case "Зачем этот бот?":
                return createOutputMsg(chatId, "Создавая этого бота, мы хотели сохранить для тебя частичку постироничной культуры ушедших 2010-х годов. Часто мемы, которые ты получишь от бота, будут глупыми или абсурдными. Но время от времени он будет выдавать настоящие шедевры. Наслаждайся!", menuKeyBoard);
            case "Поддержать":
                return createOutputMsg(chatId, "Пример любую помощь: на хлеб, на энергетики, на духовное развитие.\n\nНомер:xxxx-xxxx-xxxx-xxxx", menuKeyBoard);
            case "Статус бота":
                return createOutputMsg(chatId, "Я еще живой:)", menuKeyBoard);
            case "Отмена":
                isWaitingForPhoto = false;
                isWaitingForSignature = false;
                return createOutputMsg(chatId, "Пришли мне фото", menuKeyBoard);
        }

        isWaitingForPhoto = true;
        return createOutputMsg(chatId, "Пришли мне фото", menuKeyBoard);
    }

    private SendMessage createModerMsg(String inputMsgText) {
        SendMessage sendMessage = new SendMessage(MODERATORS_CHAT_ID, inputMsgText);
        sendMessage.setReplyMarkup(createApprovingTable());
        return sendMessage;
    }

    private SendMessage createOutputMsg(Long chatId, String text, List<KeyboardRow> keyBoard) {
        SendMessage sendMessage = new SendMessage(chatId, text);
        replyKeyboardMarkup.setKeyboard(keyBoard);
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        return sendMessage;
    }

    private boolean hasPhotoInside(Message message) {
        return message.getPhoto() != null;
    }

    private PartialBotApiMethod<Message> processMem(Message message){
        try {
            String joke = memes.get((int)(Math.random() * memes.size()));
            File file = new File(message.getPhoto().get(0).getFileId());
            BufferedImage image = ImageIO.read(file);
            Graphics2D graphics = (Graphics2D) image.getGraphics();
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
            FontMetrics fontMetrics = graphics.getFontMetrics();
            Rectangle2D rect = fontMetrics.getStringBounds(joke, graphics);
            int centerX = (image.getWidth() - (int) rect.getWidth()) / 2;
            int centerY = image.getHeight() - (image.getWidth() / 20);
            graphics.drawString(joke, centerX, centerY);
            File sendFile = null;
            ImageIO.write(image,"jpg",sendFile);
            graphics.dispose();
            SendPhoto sendPhoto = new SendPhoto();
            sendPhoto.setChatId(message.getChatId());
            sendPhoto.setPhoto(sendFile);
            return sendPhoto;
        } catch (IOException e){
            return new SendMessage(message.getChatId(),"Не получилось открыть вашу картинку. Проверьте формат.");
        }
    }

    private SendMessage addMemDescription(Message message){
        if(message.getText() != null && !message.getText().equals("")) {
            memes.add(message.getText());
            return new SendMessage(message.getChatId(), "Ваше описание было добвалено");
        }
        else return new SendMessage(message.getChatId(), "Вы ввели неправильное описание");
    }

    private ReplyKeyboard createApprovingTable() {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        InlineKeyboardButton inlineKeyboardButton1 = new InlineKeyboardButton();
        InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();

        inlineKeyboardButton1.setText(TICK_BUTTON_CODE);
        inlineKeyboardButton1.setCallbackData(TICK_BUTTON_CODE);
        inlineKeyboardButton2.setText(CROSS_BUTTON_CODE);
        inlineKeyboardButton2.setCallbackData(CROSS_BUTTON_CODE);

        List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>(Arrays.asList(
                inlineKeyboardButton1,
                inlineKeyboardButton2
        ));

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>(Collections.singletonList(keyboardButtonsRow));
        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }
}