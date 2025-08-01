package com.futao.faketyping;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.util.xmlb.XmlSerializerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * FakeTyping插件的持久化设置
 */
@State(
        name = "com.faketyping.FakeTypingSettings",
        storages = {@Storage("FakeTypingSettings.xml")}
)
public class FakeTypingSettings implements PersistentStateComponent<FakeTypingSettings> {
    // 默认打字速度（毫秒/字符）
    public int typingSpeed = 50;
    // 最小打字速度
    public int minTypingSpeed = 1;
    // 最大打字速度
    public int maxTypingSpeed = 200;
    // 是否启用随机打字速度变化
    public boolean randomSpeedVariation = true;
    // 随机速度变化范围（百分比）
    public int randomVariationPercent = 30;

    /**
     * 获取设置实例
     *
     * @return FakeTypingSettings实例
     */
    public static FakeTypingSettings getInstance() {
        return ServiceManager.getService(FakeTypingSettings.class);
    }

    @Nullable
    @Override
    public FakeTypingSettings getState() {
        return this;
    }

    @Override
    public void loadState(@NotNull FakeTypingSettings state) {
        XmlSerializerUtil.copyBean(state, this);
    }
}
