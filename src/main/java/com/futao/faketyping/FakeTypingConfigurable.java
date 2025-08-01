package com.futao.faketyping;

import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBTextField;
import com.intellij.util.ui.FormBuilder;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

/**
 * FakeTyping插件的设置界面
 */
public class FakeTypingConfigurable implements Configurable {
    private JPanel myMainPanel;
    private JBTextField typingSpeedField;
    private JBTextField minTypingSpeedField;
    private JBTextField maxTypingSpeedField;
    private JBCheckBox randomSpeedVariationCheckBox;
    private JBTextField randomVariationPercentField;

    @Nls(capitalization = Nls.Capitalization.Title)
    @Override
    public String getDisplayName() {
        return "FakeTyping设置";
    }

    @Nullable
    @Override
    public JComponent createComponent() {
        typingSpeedField = new JBTextField();
        minTypingSpeedField = new JBTextField();
        maxTypingSpeedField = new JBTextField();
        randomSpeedVariationCheckBox = new JBCheckBox("启用随机打字速度变化");
        randomVariationPercentField = new JBTextField();

        // 创建设置面板
        myMainPanel = FormBuilder.createFormBuilder()
                .addLabeledComponent(new JBLabel("默认打字速度 (毫秒/字符):"), typingSpeedField, 1, false)
                .addLabeledComponent(new JBLabel("最小打字速度 (毫秒/字符):"), minTypingSpeedField, 1, false)
                .addLabeledComponent(new JBLabel("最大打字速度 (毫秒/字符):"), maxTypingSpeedField, 1, false)
                .addComponent(randomSpeedVariationCheckBox, 1)
                .addLabeledComponent(new JBLabel("随机速度变化范围 (%):"), randomVariationPercentField, 1, false)
                .addComponentFillVertically(new JPanel(), 0)
                .getPanel();

        // 设置面板大小
        myMainPanel.setPreferredSize(new Dimension(400, 200));

        // 加载当前设置
        reset();

        return myMainPanel;
    }

    @Override
    public boolean isModified() {
        FakeTypingSettings settings = FakeTypingSettings.getInstance();
        try {
            int typingSpeed = Integer.parseInt(typingSpeedField.getText());
            int minTypingSpeed = Integer.parseInt(minTypingSpeedField.getText());
            int maxTypingSpeed = Integer.parseInt(maxTypingSpeedField.getText());
            boolean randomSpeedVariation = randomSpeedVariationCheckBox.isSelected();
            int randomVariationPercent = Integer.parseInt(randomVariationPercentField.getText());

            return typingSpeed != settings.typingSpeed ||
                   minTypingSpeed != settings.minTypingSpeed ||
                   maxTypingSpeed != settings.maxTypingSpeed ||
                   randomSpeedVariation != settings.randomSpeedVariation ||
                   randomVariationPercent != settings.randomVariationPercent;
        } catch (NumberFormatException e) {
            return true;
        }
    }

    @Override
    public void apply() throws ConfigurationException {
        FakeTypingSettings settings = FakeTypingSettings.getInstance();
        try {
            settings.typingSpeed = Integer.parseInt(typingSpeedField.getText());
            settings.minTypingSpeed = Integer.parseInt(minTypingSpeedField.getText());
            settings.maxTypingSpeed = Integer.parseInt(maxTypingSpeedField.getText());
            settings.randomSpeedVariation = randomSpeedVariationCheckBox.isSelected();
            settings.randomVariationPercent = Integer.parseInt(randomVariationPercentField.getText());

            // 验证设置值的合理性
            if (settings.typingSpeed < 1 || settings.minTypingSpeed < 1 || settings.maxTypingSpeed < 1 ||
                settings.randomVariationPercent < 0 || settings.randomVariationPercent > 100) {
                throw new ConfigurationException("请输入有效的数值");
            }

            if (settings.minTypingSpeed > settings.maxTypingSpeed) {
                throw new ConfigurationException("最小打字速度不能大于最大打字速度");
            }

            if (settings.typingSpeed < settings.minTypingSpeed || settings.typingSpeed > settings.maxTypingSpeed) {
                throw new ConfigurationException("默认打字速度必须在最小和最大打字速度之间");
            }
        } catch (NumberFormatException e) {
            throw new ConfigurationException("请输入有效的数字");
        }
    }

    @Override
    public void reset() {
        FakeTypingSettings settings = FakeTypingSettings.getInstance();
        typingSpeedField.setText(String.valueOf(settings.typingSpeed));
        minTypingSpeedField.setText(String.valueOf(settings.minTypingSpeed));
        maxTypingSpeedField.setText(String.valueOf(settings.maxTypingSpeed));
        randomSpeedVariationCheckBox.setSelected(settings.randomSpeedVariation);
        randomVariationPercentField.setText(String.valueOf(settings.randomVariationPercent));
    }

    @Nullable
    @Override
    public JComponent getPreferredFocusedComponent() {
        return typingSpeedField;
    }
}
