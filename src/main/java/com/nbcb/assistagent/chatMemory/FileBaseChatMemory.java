package com.nbcb.assistagent.chatMemory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.objenesis.strategy.StdInstantiatorStrategy;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Slf4j
public class FileBaseChatMemory implements ChatMemory {

    private final String BASE_DIR;

    private static final Kryo kryo = new Kryo();

    static {
        kryo.setRegistrationRequired(false);
        kryo.setInstantiatorStrategy(new StdInstantiatorStrategy());
    }

    public FileBaseChatMemory(String dir) {
        this.BASE_DIR = dir;
        File baseDir = new File(dir);
        if (!baseDir.exists()) {
            boolean res = baseDir.mkdir();
            if (!res) {
                log.error("目录 {} 创建失败", dir);
            }
        }
    }


    @Override
    public void add(@NotNull String conversationId, @NotNull List<Message> messages) {
        List<Message> conversationMessages = getOrCreateConversation(conversationId);
        conversationMessages.addAll(messages);
        saveConversation(conversationId, conversationMessages);
    }


    @NotNull
    @Override
    public List<Message> get(@NotNull String conversationId) {
        return List.copyOf(getOrCreateConversation(conversationId));
    }


    @Override
    public void clear(@NotNull String conversationId){
        File file = getConversationFile(conversationId);
        if (file.exists()) {
            file.deleteOnExit();
        }
    }


    /**
     * 获取对话历史
     * @param conversationId 对话 ID
     * @return List<Message>
     */
    private List<Message> getOrCreateConversation(String conversationId) {
        File file = getConversationFile(conversationId);
        List<Message> messages = new ArrayList<>();
        if (file.exists()) {
            try (Input input = new Input(new FileInputStream(file))) {
                messages = kryo.readObject(input, ArrayList.class);
            } catch (IOException e) {
                log.error("消息列表读取报错 | ", e);
            }
        }
        return messages;
    }


    /**
     * 持久化对话内容
     * @param conversationId 对话 ID
     * @param messageList 消息列表
     */
    private void saveConversation(String conversationId, List<Message> messageList) {
        File file = getConversationFile(conversationId);
        try (Output output = new Output(new FileOutputStream(file))) {
            kryo.writeObject(output, messageList);
        } catch (IOException e) {
            log.error("消息列表写入报错 | ", e);
        }
    }


    /**
     * 创建保存对话信息的 File 对象
     * @param conversationId 对话 ID
     * @return File
     */
    private File getConversationFile(String conversationId) {
        return new File(BASE_DIR, conversationId + ".kryo");
    }

}
