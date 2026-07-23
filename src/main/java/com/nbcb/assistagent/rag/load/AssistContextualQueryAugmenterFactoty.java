package com.nbcb.assistagent.rag.load;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

public class AssistContextualQueryAugmenterFactoty {

    public static ContextualQueryAugmenter createInstance() {
        PromptTemplate emptyContextPromptTemplate = new PromptTemplate("""
                    你应该输出下面的内容：
                    抱歉, 我只能回答信贷合规相关的问题, 别的没办法帮到您哦,
                    有问题可以联系我司客服            
                """);
        return ContextualQueryAugmenter.builder()
                .allowEmptyContext(false)
                .emptyContextPromptTemplate(emptyContextPromptTemplate)
                .build();
    }


}
