package keystrokesmod.client.module.modules.world;

import com.google.common.eventbus.Subscribe;
import keystrokesmod.client.event.impl.ForgeEvent;
import keystrokesmod.client.module.Module;
import net.minecraftforge.client.event.ClientChatReceivedEvent;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ChatLogger extends Module {
    private final File dir;
    private File chatLog;
    public String fileName;
    public String extension;

    public ChatLogger() {
        super("Chat Logger", ModuleCategory.world);

        extension = "txt";
        if (mc != null && mc.mcDataDir != null) {
            dir = new File(mc.mcDataDir, "keystrokes" + File.separator + "logs");
            if (!dir.exists()) {
                dir.mkdirs();
            }
        } else {
            dir = null;
        }
    }

    @Override
    public void onEnable() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd-MM-yyyy_HH.mm.ss");
        LocalDateTime now = LocalDateTime.now();
        fileName = dtf.format(now) + "." + extension;
        File logDir = dir != null ? dir : new File("keystrokes" + File.separator + "logs");
        if (!logDir.exists()) {
            logDir.mkdirs();
        }
        this.chatLog = new File(logDir, fileName);
        if (!chatLog.exists()) {
            try {
                chatLog.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        super.onEnable();
    }

    @Subscribe
    public void onMessageRecieved(ForgeEvent fe) {
        if (fe != null && fe.getEvent() instanceof ClientChatReceivedEvent && this.chatLog != null) {
            ClientChatReceivedEvent chatEvent = (ClientChatReceivedEvent) fe.getEvent();
            if (chatEvent.message == null) {
                return;
            }
            try (FileWriter fw = new FileWriter(this.chatLog.getPath(), true);
                    BufferedWriter bw = new BufferedWriter(fw);
                    PrintWriter out = new PrintWriter(bw)) {
                out.println(chatEvent.message.getUnformattedText());
            } catch (IOException e) {
                // shit
            }
        }
    }
}
