package com.luoch.cbt.graph;

import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.KeyStrategyFactoryBuilder;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;

public final class KeyStrategies {

    private KeyStrategies() {
    }

    public static KeyStrategyFactory build() {
        KeyStrategyFactoryBuilder b = new KeyStrategyFactoryBuilder();
        b.addStrategy(StateKeys.MESSAGES, new AppendStrategy());
        for (String key : StateKeys.ALL) {
            if (!StateKeys.MESSAGES.equals(key)) {
                b.addStrategy(key, KeyStrategy.REPLACE);
            }
        }
        return b.build();
    }
}
