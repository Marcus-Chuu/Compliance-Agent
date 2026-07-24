package com.nbcb.assistagent.rag.load;

import org.junit.jupiter.api.Test;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AssistContextualQueryAugmenterFactoryTest {

    @Test
    void preservesOriginalQueryWhenRetrievalContextIsEmpty() {
        ContextualQueryAugmenter augmenter = AssistContextualQueryAugmenterFactory.createInstance();
        Query original = new Query("计算50万贷款，年利率4%，期限3年");

        Query augmented = augmenter.augment(original, List.of());

        assertEquals(original.text(), augmented.text());
    }
}
