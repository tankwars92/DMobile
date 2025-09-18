import javax.microedition.midlet.*;
import javax.microedition.lcdui.*;
import javax.microedition.io.*;
import java.io.*;
import javax.microedition.rms.*;

import java.util.Vector;

public class DMobile extends MIDlet implements Runnable, CommandListener {
    private Display display;
    private ChatCanvas canvas;
    private TextBox inputBox;
    private Command sendCmd, exitCmd, backCmd;

    private Form mainMenu;
    private Command connectCmd, settingsCmd;
    private Form settingsForm;
    private TextField serverField, portField, usernameField, passwordField;
    private Command saveSettingsCmd, cancelSettingsCmd;
    private Command exitAppCmd;

    private SocketConnection sc;
    private InputStream is;
    private OutputStream os;
    private Thread listenerThread;

    private boolean connected = false;
    private boolean running = true;

    private Vector messages = new Vector();

    private String serverAddress = "147.185.221.19";
    private int serverPort = 42439;
    private String username = "";
    private String password = "";

    public DMobile() {
        display = Display.getDisplay(this);

        mainMenu = new Form("DMconnect");
        mainMenu.append("DMconnect client for J2ME. Created by BitByByte on 18.09.2025.\nhttp://dmconnect.w10.site/"); 

        connectCmd = new Command("Connect", Command.OK, 1);
        settingsCmd = new Command("Settings", Command.SCREEN, 2);
        exitAppCmd = new Command("Exit", Command.EXIT, 3);

        mainMenu.addCommand(connectCmd);
        mainMenu.addCommand(settingsCmd);
        mainMenu.addCommand(exitAppCmd);
        mainMenu.setCommandListener(this);

        settingsForm = new Form("Settings");
        serverField = new TextField("Server", serverAddress, 64, TextField.ANY);
        portField = new TextField("Port", Integer.toString(serverPort), 6, TextField.NUMERIC);
        usernameField = new TextField("Username", username, 32, TextField.ANY);
        passwordField = new TextField("Password", password, 32, TextField.PASSWORD);
        settingsForm.append(serverField);
        settingsForm.append(portField);
        settingsForm.append(usernameField);
        settingsForm.append(passwordField);
        saveSettingsCmd = new Command("Save", Command.OK, 1);
        cancelSettingsCmd = new Command("Cancel", Command.CANCEL, 2);
        settingsForm.addCommand(saveSettingsCmd);
        settingsForm.addCommand(cancelSettingsCmd);
        settingsForm.setCommandListener(this);

        exitCmd = new Command("Exit", Command.EXIT, 2);

        canvas = new ChatCanvas();
        Command canvasSendCmd = new Command("Send", Command.OK, 1);
        canvas.addCommand(exitCmd);
        canvas.addCommand(canvasSendCmd);
        canvas.setCommandListener(this);

        sendCmd = new Command("Send", Command.OK, 2);
        backCmd = new Command("Back", Command.BACK, 1);
        inputBox = new TextBox("Message", " ", 128, TextField.ANY | TextField.NON_PREDICTIVE);
        inputBox.addCommand(backCmd);
        inputBox.addCommand(sendCmd);
        inputBox.setCommandListener(this);

    }

    public void startApp() {
        loadSettings();
        display.setCurrent(mainMenu);
    }

    public void pauseApp() {}

    public void destroyApp(boolean unconditional) {
        running = false;
        disconnect();
        saveSettings();

    }

    public void commandAction(Command c, Displayable d) {
        if (d == mainMenu) {
            if (c == connectCmd) {
                connect();
                display.setCurrent(canvas);
            } else if (c == settingsCmd) {
                serverField.setString(serverAddress);
                portField.setString(Integer.toString(serverPort));
                usernameField.setString(username);
                passwordField.setString(password);
                display.setCurrent(settingsForm);
            } else if (c == exitAppCmd) {
                saveSettings();
                destroyApp(true);
                notifyDestroyed();
            }
        }

        else if (d == settingsForm) {
            if (c == saveSettingsCmd) {
                serverAddress = serverField.getString().trim();
                try {
                    serverPort = Integer.parseInt(portField.getString().trim());
                } catch (NumberFormatException e) {
                    serverPort = 42439;
                }
                username = usernameField.getString().trim();
                password = passwordField.getString().trim();
                saveSettings();
                display.setCurrent(mainMenu);
            } else if (c == cancelSettingsCmd) {
                display.setCurrent(mainMenu);
            }
        }

        else if (d == canvas) {
            if (c.getLabel().equals("Send")) {
                display.setCurrent(inputBox);
            } else if (c == exitCmd) {
                disconnect();
                display.setCurrent(mainMenu);
            }
        }

        else if (d == inputBox) {
            if (c == sendCmd) {
                String msg = inputBox.getString();

                if (msg != null && msg.startsWith(" ")) {
                    msg = msg.substring(1);
                }

                if (msg != null && msg.length() > 0 && connected) {
                    if (msg.startsWith("/login ")) {
                        int firstSpace = msg.indexOf(' ');
                        int secondSpace = msg.indexOf(' ', firstSpace + 1);
                        if (firstSpace > 0 && secondSpace > firstSpace) {
                            username = msg.substring(firstSpace + 1, secondSpace);
                            password = msg.substring(secondSpace + 1);
                            usernameField.setString(username);
                            passwordField.setString(password);
                        }
                    }
                    sendMessage(msg);
                }

                inputBox.setString(" ");
                display.setCurrent(canvas);
            } else if (c == backCmd) {
                inputBox.setString(" ");
                display.setCurrent(canvas);
            }
        }

    }

    private void connect() {
        try {
            sc = (SocketConnection) Connector.open("socket://" + serverAddress + ":" + serverPort);
            is = sc.openInputStream();
            os = sc.openOutputStream();
            connected = true;

            listenerThread = new Thread(this);
            listenerThread.start();

            addMessage("** Connected to " + serverAddress + ":" + serverPort + " **");

            if (username.length() > 0 && password.length() > 0) {
                new Thread(new Runnable() {
                    public void run() {
                        try { Thread.sleep(200); } catch (InterruptedException e) {}
                        sendMessage("/login " + username + " " + password);
                    }
                }).start();
            }

            Thread autoSendThread = new Thread(new Runnable() {
                public void run() {
                    try {
                        Thread.sleep(5000);
                    } catch (InterruptedException e) {}

                    while (running && connected) {
                        try {
                            sendMessage("/");
                            Thread.sleep(5000);
                        } catch (Exception e) {}
                    }
                }
            });
            autoSendThread.start();

        } catch (Exception e) {
            addMessage("Error: " + e.getMessage());
        }
    }

    private StringBuffer recvBuffer = new StringBuffer();

    public void run() {
        byte[] buffer = new byte[512];
        while (running && connected) {
            try {
                int len = is.read(buffer);
                if (len > 0) {
                    String chunk = new String(buffer, 0, len, "UTF-8");
                    recvBuffer.append(chunk);

                    String bufStr = recvBuffer.toString();
                    int nl;
                    while ((nl = bufStr.indexOf('\n')) != -1) {
                        String line = bufStr.substring(0, nl).trim();
                        if (!line.equals("*Ping!*") && line.length() > 0) {
                            addMessage(line);
                        }

                        bufStr = bufStr.substring(nl + 1);
                    }

                    recvBuffer.setLength(0);
                    recvBuffer.append(bufStr);
                }
            } catch (IOException e) {
                break;
            }
        }
        disconnect();
    }



    private void disconnect() {
        try {
            if (is != null) is.close();
            if (os != null) os.close();
            if (sc != null) sc.close();
        } catch (Exception e) {}
        connected = false;
    }

    private void sendMessage(String msg) {
        try {
            byte[] data = msg.concat("\n").getBytes("UTF-8");
            os.write(data);
            os.flush();
        } catch (Exception e) {
            addMessage("Send error: " + e.getMessage());
        }
    }

    private void addMessage(String msg) {
        messages.addElement(msg);
        canvas.repaint();
    }

    private void saveSettings() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore("DMconnectSettings", true);

            RecordEnumeration re = rs.enumerateRecords(null, null, false);
            while (re.hasNextElement()) {
                int id = re.nextRecordId();
                rs.deleteRecord(id);
            }
            re.destroy();

            String data = serverAddress + "|" + serverPort + "|" + username + "|" + password;
            rs.addRecord(data.getBytes(), 0, data.length());
        } catch (Exception e) {
            addMessage("Settings save error: " + e.getMessage());
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    private void loadSettings() {
        RecordStore rs = null;
        try {
            rs = RecordStore.openRecordStore("DMconnectSettings", true);
            if (rs.getNumRecords() > 0) {
                RecordEnumeration re = rs.enumerateRecords(null, null, false);
                if (re.hasNextElement()) {
                    int id = re.nextRecordId();
                    byte[] record = rs.getRecord(id);
                    String data = new String(record);

                    String[] parts = new String[4];
                    int pos = 0;
                    for (int i = 0; i < 4; i++) {
                        int next = data.indexOf('|', pos);
                        if (next == -1) next = data.length();
                        parts[i] = data.substring(pos, next);
                        pos = next + 1;
                    }

                    if (parts[0] != null) serverAddress = parts[0];
                    if (parts[1] != null) {
                        try { serverPort = Integer.parseInt(parts[1]); } catch (NumberFormatException e) { serverPort = 42439; }
                    }
                    if (parts[2] != null) username = parts[2];
                    if (parts[3] != null) password = parts[3];
                }
                re.destroy();
            }
        } catch (Exception e) {
            addMessage("Settings load error: " + e.getMessage());
        } finally {
            try { if (rs != null) rs.closeRecordStore(); } catch (Exception e) {}
        }
    }

    class ChatCanvas extends Canvas {
        ChatCanvas() {
            setFullScreenMode(true);
        }

        protected void paint(Graphics g) {
            int w = getWidth();
            int h = getHeight();
            int lineHeight = g.getFont().getHeight();

            g.setColor(0x000000);
            g.fillRect(0, 0, w, h);

            g.setColor(0x00FF00);
            g.fillRect(0, 0, w, lineHeight + 4);
            g.setColor(0x000000);
            g.drawString("Press FIRE to type", 2, 2, Graphics.TOP | Graphics.LEFT);

            int maxLines = (h - (lineHeight + 4)) / lineHeight;
            Vector wrappedLines = new Vector();
            Vector firstLineFlags = new Vector(); 

            for (int i = 0; i < messages.size(); i++) {
                String msg = (String) messages.elementAt(i);
                int offset = 0;
                boolean firstLine = true;
                while (offset < msg.length()) {
                    int end = offset;
                    while (end < msg.length() && g.getFont().stringWidth(msg.substring(offset, end + 1)) <= w - 4) {
                        end++;
                    }
                    if (end == offset) end++;
                    wrappedLines.addElement(msg.substring(offset, end));
                    firstLineFlags.addElement(firstLine ? Boolean.TRUE : Boolean.FALSE);
                    offset = end;
                    firstLine = false; 
                }
            }

            int start = Math.max(0, wrappedLines.size() - maxLines);
            int y = lineHeight + 6;
            for (int i = start; i < wrappedLines.size(); i++) {
                String line = (String) wrappedLines.elementAt(i);
                boolean isFirstLine = ((Boolean) firstLineFlags.elementAt(i)).booleanValue();

                if (isFirstLine) {
                    int colonIndex = line.indexOf(":");
                    if (colonIndex > 0 && !line.startsWith("**")) {
                        String nickPart = line.substring(0, colonIndex);
                        String msgPart = line.substring(colonIndex);

                        if (nickPart.indexOf(' ') == -1) {
                            if (nickPart.equals(username)) {
                                g.setColor(0x00FFFF);
                            } else {
                                g.setColor(0xFF0000);
                            }
                            g.drawString(nickPart + ":", 2, y, Graphics.TOP | Graphics.LEFT);

                            int nickWidth = g.getFont().stringWidth(nickPart + ":");
                            g.setColor(0xFFFFFF);
                            g.drawString(msgPart.length() > 1 ? msgPart.substring(1) : "", 2 + nickWidth, y, Graphics.TOP | Graphics.LEFT);
                        } else {
                            g.setColor(0xFFFFFF);
                            g.drawString(line, 2, y, Graphics.TOP | Graphics.LEFT);
                        }
                    } else {
                        g.setColor(0xFFFFFF);
                        g.drawString(line, 2, y, Graphics.TOP | Graphics.LEFT);
                    }
                } else {
                    g.setColor(0xFFFFFF);
                    g.drawString(line, 2, y, Graphics.TOP | Graphics.LEFT);
                }
                y += lineHeight;
            }
        }
        protected void keyPressed(int keyCode) {
            int ga = getGameAction(keyCode);
            if (ga == FIRE) {
                display.setCurrent(inputBox);
            }
        }
    }
}
