package ru.ifmo.daniar.service;

import lombok.extern.log4j.Log4j2;
import org.telegram.telegrambots.bots.DefaultBotOptions;
import org.telegram.telegrambots.extensions.bots.commandbot.TelegramLongPollingCommandBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.PartialBotApiMethod;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.methods.updatingmessages.EditMessageText;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboard;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import ru.ifmo.daniar.model.ResponseType;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.*;

import static java.lang.System.getProperty;
import static ru.ifmo.daniar.model.ResponseType.DESCRIPTION_RESPONSE;
import static ru.ifmo.daniar.model.ResponseType.USER_RESPONSE;

@Log4j2
public final class Bot extends TelegramLongPollingCommandBot {

    private static final String BOT_NAME = "Da Ni Ar";
    private static final String BOT_TOKEN = "961694457:AAHOgJxhxmdjYLHqhn4T66UKZqKYH9Ss0hw";
    private static final String MODERATORS_CHAT_ID = "@daniar_moders";
    private static final String DESCRIPTIONS_FILE_NAME = "descriptions.txt";
    private static final String AUTHORIZED_USERS_FILE_NAME = "authorizedUsers.txt";
    private static final String TICK_BUTTON_DESCRIPTION_CODE = "\u2705";
    private static final String TICK_BUTTON_USER_CODE = "\u2714";
    private static final String CROSS_BUTTON_DESCRIPTION_CODE = "\u274C";
    private static final String CROSS_BUTTON_USER_CODE = "\u2716";
    private static List<String> memes;

    private ReplyKeyboardMarkup replyKeyboardMarkup;
    private List<KeyboardRow> menuKeyBoard;
    private List<KeyboardRow> cancelKeyBoard;
    private Set<Integer> authorizedUsers;

    private boolean isWaitingForSignature = false;
    private boolean isWaitingForPhoto = false;

    public Bot(DefaultBotOptions botOptions) {
        super(botOptions, BOT_NAME);
        replyKeyboardMarkup = createReplyKeyBoardMarkup();
        menuKeyBoard = createMenu();
        cancelKeyBoard = createCancelMenu();
        memes = loadDescriptions();
        authorizedUsers = loadAuthorizedUsers();
    }

    private Set<Integer> loadAuthorizedUsers() {
        Set<Integer> authorizedUsers = new HashSet<>();
        try (Scanner scanner = new Scanner(new File(AUTHORIZED_USERS_FILE_NAME))) {
            while (scanner.hasNextInt()) {
                authorizedUsers.add(scanner.nextInt());
            }
        } catch (FileNotFoundException e) {
            log.error("{} file not found", AUTHORIZED_USERS_FILE_NAME, e);
        }

        return authorizedUsers;
    }

    private List<String> loadDescriptions() {
        List<String> descriptions = new ArrayList<>();
        try (Scanner scanner = new Scanner(new File(DESCRIPTIONS_FILE_NAME))) {
            while (scanner.hasNextLine()) {
                descriptions.add(scanner.nextLine());
            }
        } catch (FileNotFoundException e) {
            log.error("Error during loading descriptions", e);
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

    private void executeToUser(SendPhoto message) {
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

        CallbackQuery callbackQuery = update.getCallbackQuery();
        if (callbackQuery != null && checkUserName(update)) {
            if (isUserConfirmation(callbackQuery.getData())) {
                EditMessageText answer = createModerAnswer(update.getCallbackQuery(), USER_RESPONSE);
                executeToUser(answer);
            } else {
                EditMessageText answer = createModerAnswer(update.getCallbackQuery(), DESCRIPTION_RESPONSE);
                executeToUser(answer);
            }
        } else {
            User user = update.getMessage().getFrom();
            if (!authorizedUsers.contains(user.getId())) {
                SendMessage userSuggestMessage;
                if (user.getUserName() == null) {
                    userSuggestMessage = createModerMsg(
                            String.format("Добавить пользователя @%s (id %d) в список разрешенных?",
                                    user.getFirstName(), user.getId()), USER_RESPONSE);
                } else {
                    userSuggestMessage = createModerMsg(
                            String.format("Добавить пользователя %s (id %d) в список разрешенных?",
                                    user.getUserName(), user.getId()), USER_RESPONSE);
                }

                executeToUser(userSuggestMessage);

                executeToUser(new SendMessage(update.getMessage().getChatId(), "Вы не зарегистрированы. Дождитесь решения модераторов"));
                return;
            }

            PartialBotApiMethod<Message> answer = createMessage(inputMessage);
            if (answer instanceof SendPhoto) {
                executeToUser((SendPhoto) answer);
            } else {
                executeToUser((SendMessage) answer);
            }
        }

    }

    private boolean isUserConfirmation(String data) {
        return data.equals(TICK_BUTTON_USER_CODE) || data.equals(CROSS_BUTTON_USER_CODE);
    }

    private boolean checkUserName(Update update) {
        return MODERATORS_CHAT_ID.replace("@", "").equals(update.getCallbackQuery().getMessage().getChat().getUserName());
    }

    private EditMessageText createModerAnswer(CallbackQuery query, ResponseType descriptionResponse) {
        int messageId = query.getMessage().getMessageId();
        String text = query.getMessage().getText();

        EditMessageText editMessageText = new EditMessageText();
        editMessageText
                .setMessageId(messageId)
                .setChatId(MODERATORS_CHAT_ID);
        String firstName = query.getFrom().getFirstName();
        if (descriptionResponse == DESCRIPTION_RESPONSE) {
            if (query.getData().equals(TICK_BUTTON_DESCRIPTION_CODE)) {
                String description = query.getMessage().getText();
                addDescription(description);
                writeDescription(description);
                editMessageText.setText(String.format("%s\n%s - %s", text, TICK_BUTTON_DESCRIPTION_CODE, firstName));
            } else if (query.getData().equals(CROSS_BUTTON_DESCRIPTION_CODE)) {
                editMessageText.setText(String.format("%s\n%s - %s", text, CROSS_BUTTON_DESCRIPTION_CODE, firstName));
            }
        } else {
            if (query.getData().equals(TICK_BUTTON_USER_CODE)) {
                addUser(query);
                editMessageText.setText(String.format("%s\n%s - %s", text, TICK_BUTTON_USER_CODE, firstName));
            } else if (query.getData().equals(CROSS_BUTTON_USER_CODE)) {
                editMessageText.setText(String.format("%s\n%s - %s", text, CROSS_BUTTON_USER_CODE, firstName));
            }
        }

        return editMessageText;
    }

    private void addUser(CallbackQuery query) {
        String text = query.getMessage().getText().replace("@", "");
        String userId = text.substring(text.indexOf("id") + 3, text.indexOf(")"));
        authorizedUsers.add(Integer.parseInt(userId));

        try (FileWriter writer = new FileWriter(AUTHORIZED_USERS_FILE_NAME)) {
            writer.write(userId + "\n");
        } catch (IOException e) {
            log.error("Error during writing new userId", e);
        }
    }

    private void addDescription(String description) {
        memes.add(description);
        try (FileWriter writer = new FileWriter(DESCRIPTIONS_FILE_NAME, true)) {
            writer.write(description + "\n");
        } catch (IOException e) {
            log.error("Error during writing description", e);
        }
    }

    private void writeDescription(String description) {
        try (FileWriter fileWriter = new FileWriter(getProperty("user.dir") + DESCRIPTIONS_FILE_NAME, true)) {
            fileWriter.write(description);
        } catch (IOException e) {
            log.error("Error occurred during writing description: {}", e.getMessage());
        }
    }

    private PartialBotApiMethod<Message> createMessage(Message inputMsg) {
        long chatId = inputMsg.getChatId();

        String inputMsgText = inputMsg.getText();

        if (isWaitingForSignature) {
            SendMessage moderMsg = createModerMsg(inputMsgText, DESCRIPTION_RESPONSE);
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

    private SendMessage createModerMsg(String inputMsgText, ResponseType responseType) {
        SendMessage sendMessage = new SendMessage(MODERATORS_CHAT_ID, inputMsgText);
        sendMessage.setReplyMarkup(createApprovingTable(responseType));
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

    private PartialBotApiMethod<Message> processMem(Message message) {
        try {
            String joke = memes.get((int) (Math.random() * memes.size()));
            PhotoSize photo = message.getPhoto().stream()
                    .max(Comparator.comparing(PhotoSize::getFileSize))
                    .orElse(null);
            if (photo != null) {
                File file = downloadFile(Objects.requireNonNull(getFilePath(photo)));
                BufferedImage image = ImageIO.read(file);
                Graphics2D graphics = (Graphics2D) image.getGraphics();
                graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 26));
                FontMetrics fontMetrics = graphics.getFontMetrics();
                Rectangle2D rect = fontMetrics.getStringBounds(joke, graphics);
                int centerX = (image.getWidth() - (int) rect.getWidth()) / 2;
                int centerY = image.getHeight() - (image.getWidth() / 20);
                graphics.drawString(joke, centerX, centerY);
                File sendFile = new File("image.jpg");
                ImageIO.write(image, "jpg", sendFile);
                graphics.dispose();
                SendPhoto sendPhoto = new SendPhoto();
                sendPhoto.setChatId(message.getChatId());
                sendPhoto.setPhoto(sendFile);
                return sendPhoto;
            }
        } catch (IOException | TelegramApiException e) {
            return new SendMessage(message.getChatId(), "Не получилось открыть вашу картинку. Проверьте формат.");
        }
        return null;
    }

    public String getFilePath(PhotoSize photo) {
        Objects.requireNonNull(photo);

        if (photo.hasFilePath()) { // If the file_path is already present, we are done!
            return photo.getFilePath();
        } else { // If not, let find it
            // We create a GetFile method and set the file_id from the photo
            GetFile getFileMethod = new GetFile();
            getFileMethod.setFileId(photo.getFileId());
            try {
                // We execute the method using AbsSender::execute method.
                org.telegram.telegrambots.meta.api.objects.File file = execute(getFileMethod);
                // We now have the file_path
                return file.getFilePath();
            } catch (TelegramApiException e) {
                e.printStackTrace();
            }
        }

        return null; // Just in case
    }

    private SendMessage addMemDescription(Message message) {
        if (message.getText() != null && !message.getText().equals("")) {
            memes.add(message.getText());
            return new SendMessage(message.getChatId(), "Ваше описание было добвалено");
        } else return new SendMessage(message.getChatId(), "Вы ввели неправильное описание");
    }

    private ReplyKeyboard createApprovingTable(ResponseType responseType) {
        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
        InlineKeyboardButton inlineKeyboardButton1 = new InlineKeyboardButton();
        InlineKeyboardButton inlineKeyboardButton2 = new InlineKeyboardButton();

        if (responseType == DESCRIPTION_RESPONSE) {
            inlineKeyboardButton1.setText(TICK_BUTTON_DESCRIPTION_CODE);
            inlineKeyboardButton1.setCallbackData(TICK_BUTTON_DESCRIPTION_CODE);
            inlineKeyboardButton2.setText(CROSS_BUTTON_DESCRIPTION_CODE);
            inlineKeyboardButton2.setCallbackData(CROSS_BUTTON_DESCRIPTION_CODE);
        } else {
            inlineKeyboardButton1.setText(TICK_BUTTON_USER_CODE);
            inlineKeyboardButton1.setCallbackData(TICK_BUTTON_USER_CODE);
            inlineKeyboardButton2.setText(CROSS_BUTTON_USER_CODE);
            inlineKeyboardButton2.setCallbackData(CROSS_BUTTON_USER_CODE);
        }

        List<InlineKeyboardButton> keyboardButtonsRow = new ArrayList<>(Arrays.asList(
                inlineKeyboardButton1,
                inlineKeyboardButton2
        ));

        List<List<InlineKeyboardButton>> rowList = new ArrayList<>(Collections.singletonList(keyboardButtonsRow));
        inlineKeyboardMarkup.setKeyboard(rowList);
        return inlineKeyboardMarkup;
    }
}