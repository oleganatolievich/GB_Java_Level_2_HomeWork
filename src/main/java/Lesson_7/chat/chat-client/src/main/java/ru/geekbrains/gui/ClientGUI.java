package ru.geekbrains.gui;

import ru.geekbrains.chat.common.MessageLibrary;
import ru.geekbrains.net.MessageSocketThread;
import ru.geekbrains.net.MessageSocketThreadListener;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.Socket;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Map;

public class ClientGUI extends JFrame implements ActionListener, Thread.UncaughtExceptionHandler, MessageSocketThreadListener {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 300;

    private final JTextArea chatArea = new JTextArea();
    private final JPanel panelTop = new JPanel(new GridLayout(2, 3));
    private final JTextField ipAddressField = new JTextField("127.0.0.1");
    private final JTextField portField = new JTextField("8181");
    private final JCheckBox cbAlwaysOnTop = new JCheckBox("Always on top", true);
    private final JTextField loginField = new JTextField("login");
    private final JPasswordField passwordField = new JPasswordField("123");
    private final JButton buttonLogin = new JButton("Login");

    private final JPanel panelBottom = new JPanel(new BorderLayout());
    private final JButton buttonDisconnect = new JButton("<html><b>Disconnect</b></html>");
    private final JTextField messageField = new JTextField();
    private final JButton buttonSend = new JButton("Send");

    private final JList<String> listUsers = new JList<>();
    private SimpleDateFormat sdf = new SimpleDateFormat("dd.MM.yyyy HH:mm:ss");
    private MessageSocketThread socketThread;
    private boolean isLoggedIn = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
                new ClientGUI();
            }
        });
    }

    ClientGUI() {
        Thread.setDefaultUncaughtExceptionHandler(this);
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("Chat");
        setSize(WIDTH, HEIGHT);
        setAlwaysOnTop(true);

        //listUsers.setListData();
        JScrollPane scrollPaneUsers = new JScrollPane(listUsers);
        JScrollPane scrollPaneChatArea = new JScrollPane(chatArea);
        scrollPaneUsers.setPreferredSize(new Dimension(100, 0));

        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setEditable(false);

        panelTop.add(ipAddressField);
        panelTop.add(portField);
        panelTop.add(cbAlwaysOnTop);
        panelTop.add(loginField);
        panelTop.add(passwordField);
        panelTop.add(buttonLogin);
        panelBottom.add(buttonDisconnect, BorderLayout.WEST);
        panelBottom.add(messageField, BorderLayout.CENTER);
        panelBottom.add(buttonSend, BorderLayout.EAST);

        add(scrollPaneChatArea, BorderLayout.CENTER);
        add(scrollPaneUsers, BorderLayout.EAST);
        add(panelTop, BorderLayout.NORTH);
        add(panelBottom, BorderLayout.SOUTH);

        cbAlwaysOnTop.addActionListener(this);
        buttonSend.addActionListener(this);
        messageField.addActionListener(this);
        buttonLogin.addActionListener(this);
        buttonDisconnect.addActionListener(this);

        setVisible(true);
        controlVisibility();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        Object src = e.getSource();
        if (src == cbAlwaysOnTop) {
            setAlwaysOnTop(cbAlwaysOnTop.isSelected());
        } else if (src == buttonSend || src == messageField) {
            sendMessage(loginField.getText(), messageField.getText());
        } else if (src == buttonLogin) {
            Socket socket = null;
            try {
                socket = new Socket(ipAddressField.getText(), Integer.parseInt(portField.getText()));
                socketThread = new MessageSocketThread(this, "Client" + loginField.getText(), socket);
            } catch (IOException ioException) {
                showError(ioException.getMessage());
            }
        } else if (src == buttonDisconnect) {
            socketThread.close();
        } else {
            throw new RuntimeException("Unsupported action: " + src);
        }
    }

    @Override
    public void uncaughtException(Thread t, Throwable e) {
        e.printStackTrace();
        StackTraceElement[] ste = e.getStackTrace();
        String msg = String.format("Exception in \"%s\": %s %s%n\t %s",
                t.getName(), e.getClass().getCanonicalName(), e.getMessage(), ste[0]);
        showError(msg);
    }

    /*
     * Отправка сообщений в сторону сервера
     */
    public void sendMessage(String user, String msg) {
        if (msg.isEmpty()) {
            return;
        }
        socketThread.sendMessage(MessageLibrary.getCommonMessage(user, msg));
    }

    public void putMessageInChatArea(String user, String messageToChat) {
        putIntoFileHistory(user, messageToChat);
        chatArea.append(messageToChat);
        messageField.setText("");
        messageField.grabFocus();
    }

    private void putIntoFileHistory(String user, String msg) {
        try (PrintWriter pw = new PrintWriter(new FileOutputStream(user + "-history.txt", true))) {
            pw.print(msg);
        } catch (FileNotFoundException e) {
            showError(msg);
        }
    }

    private void showError(String errorMsg) {
        JOptionPane.showMessageDialog(this, errorMsg, "Exception!", JOptionPane.ERROR_MESSAGE);
    }

    @Override
    public void onSocketReady() {
        socketThread.sendMessage(MessageLibrary.getAuthRequestMessage(loginField.getText(), new String(passwordField.getPassword())));
    }

    @Override
    public void onSocketClosed() {
        isLoggedIn = false;
        controlVisibility();
    }

    private void controlVisibility() {
        panelTop.setVisible(!isLoggedIn);
        panelBottom.setVisible(isLoggedIn);
        chatArea.setVisible(isLoggedIn);
        listUsers.setVisible(isLoggedIn);
    }
    /*
    * Получение сообщений от сервера
     */
    @Override
    public void onMessageReceived(Socket socket, String msg) {
        Map<String, Object> decodedMessage = MessageLibrary.decodeMessage(msg);
        if (decodedMessage == null) {
            showError("Incorrect server response: " + msg);
            return;
        }
        Boolean success = (Boolean) (decodedMessage.get("success"));
        String login. = loginField.getText();
        String message = (String) (decodedMessage.get("message"));
        if (success == Boolean.FALSE) {
            showError(message);
            return;
        }
        String messageType = (String) (decodedMessage.get("type"));
        if (messageType.equals(MessageLibrary.AUTH_TYPE)) {
            isLoggedIn = true;
            controlVisibility();
            putMessageInChatArea(login, message);
        }
        if (messageType.equals(MessageLibrary.COMMON_MESSAGE_TYPE)) {
            if (!message.isEmpty()) {
                String sender = (String) (decodedMessage.get("login"));
                String messageToChat = String.format("%s <%s>: %s%n", sdf.format(Calendar.getInstance().getTime()), sender, message);
                putMessageInChatArea(login, messageToChat);
            }
        }
    }

    @Override
    public void onException(Throwable throwable) {
        throwable.printStackTrace();
        showError(throwable.getMessage());
    }
}
