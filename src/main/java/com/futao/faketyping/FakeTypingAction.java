package com.futao.faketyping;

import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.awt.RelativePoint;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * FakeTyping动作类
 * 在编辑器中右键点击文件，选择"FakeTyping"选项，将会清空当前文件内容并模拟打字机效果重新输入文件内容
 *
 * @author FakeTyping
 * @version 1.0
 */
public class FakeTypingAction extends AnAction {
    // 加载图标
    private static final Icon FAKE_TYPING_ICON = IconLoader.getIcon("/icons/fakeTypingIcon.svg", FakeTypingAction.class);

    /**
     * 构造函数
     */
    public FakeTypingAction() {
        super("FakeTyping", "模拟打字机效果", FAKE_TYPING_ICON);
    }
    private final Random random = new Random();
    // 用于控制定时任务的变量
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledFuture;
    private boolean isPaused = false;
    private String originalContent;
    // 用于暂停/继续功能
    private volatile boolean isRunning = true;
    
    // 重置状态变量
    private void resetState() {
        isPaused = false;
        isRunning = true;
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(false);
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
        executor = null;
        scheduledFuture = null;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 重置状态变量
        resetState();
        
        // 获取当前项目、编辑器和文档
        final Project project = e.getRequiredData(CommonDataKeys.PROJECT);
        final Editor editor = e.getRequiredData(CommonDataKeys.EDITOR);
        final Document document = editor.getDocument();

        // 获取当前文档的全部内容
        originalContent = document.getText();

        // 如果文档为空，则不执行操作
        if (originalContent.isEmpty()) {
            Messages.showWarningDialog("当前文件为空，无法执行FakeTyping操作。", "FakeTyping警告");
            return;
        }

        // 获取设置
        FakeTypingSettings settings = FakeTypingSettings.getInstance();

        // 使用滑动组件让用户选择打字速度
        int typingSpeed = showSpeedSliderDialog(project, settings);

        // 创建控制按钮
        createControlPanel(project, editor, document, typingSpeed, settings);
    }

    /**
     * 创建控制面板
     * @param project 当前项目
     * @param editor 当前编辑器
     * @param document 当前文档
     * @param typingSpeed 打字速度
     * @param settings 设置
     */
    private void createControlPanel(Project project, Editor editor, Document document, int typingSpeed, FakeTypingSettings settings) {
        // 创建一个定时任务执行器
        executor = Executors.newSingleThreadScheduledExecutor();

        // 在写入命令中清空文档
        WriteCommandAction.runWriteCommandAction(project, () -> {
            document.setText("");
        });

        // 字符索引
        final int[] charIndex = {0};
        final int finalTypingSpeed = typingSpeed;

        // 创建悬浮控制面板
        JPanel controlPanel = new JPanel();
        controlPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 5, 0));
        
        // 暂停/继续按钮
        JButton pauseResumeButton = new JButton("暂停");
        pauseResumeButton.setToolTipText("暂停/继续打字效果");
        
        // 还原按钮
        JButton restoreButton = new JButton("还原");
        restoreButton.setToolTipText("还原文件原始内容");
        
        // 添加按钮到面板
        controlPanel.add(pauseResumeButton);
        controlPanel.add(restoreButton);
        
        // 显示控制面板
        var popup = JBPopupFactory.getInstance()
                .createComponentPopupBuilder(controlPanel, pauseResumeButton)
                .setTitle("FakeTyping 控制")
                .setMovable(true)
                .setRequestFocus(false)
                .setCancelOnClickOutside(false)
                .setCancelOnWindowDeactivation(false)
                .createPopup();
        
        popup.show(new RelativePoint(
                editor.getComponent(),
                new Point(editor.getComponent().getWidth() - 150, 10)
        ));
        
        // 暂停/继续按钮点击事件
        pauseResumeButton.addActionListener(e -> {
            if (isPaused) {
                // 继续执行
                isPaused = false;
                isRunning = true;
                pauseResumeButton.setText("暂停");
                // 从当前位置继续执行打字任务
                startTypingTask(project, editor, document, charIndex, originalContent, finalTypingSpeed, settings);
            } else {
                // 暂停执行
                isPaused = true;
                isRunning = false;
                pauseResumeButton.setText("继续");
                if (scheduledFuture != null) {
                    scheduledFuture.cancel(false);
                }
            }
        });
        
        // 还原按钮点击事件
        restoreButton.addActionListener(e -> {
            // 取消当前任务
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
            if (executor != null) {
                executor.shutdownNow();
            }
            
            // 还原文件内容
            WriteCommandAction.runWriteCommandAction(project, () -> {
                document.setText(originalContent);
            });
            
            // 显示通知
            Notifications.Bus.notify(
                    new Notification(
                            "FakeTyping",
                            "FakeTyping已还原",
                            "文件内容已还原为原始状态",
                            NotificationType.INFORMATION
                    )
            );
            
            // 关闭悬浮弹窗
            popup.cancel();
        });
        
        // 开始打字任务
        startTypingTask(project, editor, document, charIndex, originalContent, finalTypingSpeed, settings);
    }
    
    /**
     * 开始打字任务
     */
    private void startTypingTask(Project project, Editor editor, Document document, int[] charIndex, 
                                String content, int typingSpeed, FakeTypingSettings settings) {
        // 如果已经有任务在运行，先取消它
        if (scheduledFuture != null && !scheduledFuture.isDone()) {
            scheduledFuture.cancel(false);
        }
        
        // 确保执行器存在
        if (executor == null || executor.isShutdown()) {
            executor = Executors.newSingleThreadScheduledExecutor();
        }
        
        // 设置为运行状态
        isRunning = true;
        isPaused = false;
        
        // 确保速度值至少为1毫秒
        int actualSpeed = Math.max(1, typingSpeed);
        
        // 对于极低的速度值，使用不同的调度策略
        if (actualSpeed <= 5) {
            // 使用递归调度而不是固定速率
            Runnable typingTask = new Runnable() {
                @Override
                public void run() {
                    // 如果暂停了，就不执行
                    if (!isRunning) {
                        return;
                    }
                    
                    if (charIndex[0] < content.length()) {
                        // 获取下一个字符
                        char nextChar = content.charAt(charIndex[0]);
        
                        // 在写入命令中添加字符并移动光标
                        WriteCommandAction.runWriteCommandAction(project, () -> {
                            document.insertString(charIndex[0], String.valueOf(nextChar));
                            // 移动光标到插入位置之后
                            editor.getCaretModel().moveToOffset(charIndex[0] + 1);
                        });
        
                        // 增加索引
                        charIndex[0]++;
        
                        // 计算下一次执行的延迟
                        int nextDelay = actualSpeed;
                        if (settings.randomSpeedVariation && charIndex[0] < content.length()) {
                            int variation = (int) (actualSpeed * settings.randomVariationPercent / 100.0);
                            nextDelay = actualSpeed + random.nextInt(variation * 2 + 1) - variation;
                            nextDelay = Math.max(1, Math.min(settings.maxTypingSpeed, nextDelay));
                        }
        
                        // 调度下一个字符
                        if (charIndex[0] < content.length() && isRunning) {
                            scheduledFuture = executor.schedule(this, nextDelay, TimeUnit.MILLISECONDS);
                        } else if (charIndex[0] >= content.length()) {
                            // 完成后关闭执行器
                            executor.shutdown();
        
                            // 显示完成通知
                            Notifications.Bus.notify(
                                    new Notification(
                                            "FakeTyping",
                                            "FakeTyping完成",
                                            "文件内容已成功以打字机效果重新输入",
                                            NotificationType.INFORMATION
                                    )
                            );
                        }
                    }
                }
            };
            
            // 开始第一次执行
            scheduledFuture = executor.schedule(typingTask, 0, TimeUnit.MILLISECONDS);
        } else {
            // 对于正常速度，使用固定速率调度
            scheduledFuture = executor.scheduleAtFixedRate(() -> {
                // 如果暂停了，就不执行
                if (!isRunning) {
                    return;
                }
                
                if (charIndex[0] < content.length()) {
                    // 获取下一个字符
                    char nextChar = content.charAt(charIndex[0]);
    
                    // 在写入命令中添加字符并移动光标
                    WriteCommandAction.runWriteCommandAction(project, () -> {
                        document.insertString(charIndex[0], String.valueOf(nextChar));
                        // 移动光标到插入位置之后
                        editor.getCaretModel().moveToOffset(charIndex[0] + 1);
                    });
    
                    // 增加索引
                    charIndex[0]++;
    
                    // 如果启用随机速度变化，则调整下一次执行的延迟
                    if (settings.randomSpeedVariation && charIndex[0] < content.length() && isRunning) {
                        int variation = (int) (actualSpeed * settings.randomVariationPercent / 100.0);
                        int nextDelay = actualSpeed + random.nextInt(variation * 2) - variation;
    
                        // 确保延迟在合理范围内
                        nextDelay = Math.max(1, Math.min(settings.maxTypingSpeed, nextDelay));
    
                        // 取消当前任务
                        if (scheduledFuture != null) {
                            scheduledFuture.cancel(false);
                        }
    
                        // 重新调度下一次执行
                        executor.schedule(() -> {
                            if (!isRunning) {
                                return;
                            }
                            if (charIndex[0] < content.length()) {
                                char nextCharInner = content.charAt(charIndex[0]);
                                WriteCommandAction.runWriteCommandAction(project, () -> {
                                    document.insertString(charIndex[0], String.valueOf(nextCharInner));
                                    // 移动光标到插入位置之后
                                    editor.getCaretModel().moveToOffset(charIndex[0] + 1);
                                });
                                charIndex[0]++;
                                
                                // 继续执行后续字符的输入
                                startTypingTask(project, editor, document, charIndex, content, actualSpeed, settings);
                            }
                        }, nextDelay, TimeUnit.MILLISECONDS);
                        return; // 退出当前任务
                    }
                } else {
                    // 完成后关闭执行器
                    if (scheduledFuture != null) {
                        scheduledFuture.cancel(false);
                    }
                    executor.shutdown();
    
                    // 显示完成通知
                    Notifications.Bus.notify(
                            new Notification(
                                    "FakeTyping",
                                    "FakeTyping完成",
                                    "文件内容已成功以打字机效果重新输入",
                                    NotificationType.INFORMATION
                            )
                    );
                }
            }, 0, settings.randomSpeedVariation ? settings.maxTypingSpeed : actualSpeed, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 显示速度滑动条对话框
     * @param project 当前项目
     * @param settings 设置
     * @return 用户选择的打字速度
     */
    private int showSpeedSliderDialog(Project project, FakeTypingSettings settings) {
        // 创建滑动条
        JSlider slider = new JSlider(JSlider.HORIZONTAL, settings.minTypingSpeed, settings.maxTypingSpeed, settings.typingSpeed);
        slider.setMajorTickSpacing(50);
        slider.setMinorTickSpacing(10);
        slider.setPaintTicks(true);
        slider.setPaintLabels(true);
        slider.setPreferredSize(new Dimension(300, 50));
        
        // 设置滑动条颜色渐变
        slider.setUI(new javax.swing.plaf.basic.BasicSliderUI(slider) {
            @Override
            public void paintTrack(Graphics g) {
                Graphics2D g2d = (Graphics2D) g;
                Rectangle trackBounds = trackRect;
                
                // 创建渐变色
                GradientPaint gradient = new GradientPaint(
                        trackBounds.x, trackBounds.y, new Color(0, 200, 0), // 绿色（快）
                        trackBounds.x + trackBounds.width, trackBounds.y, new Color(200, 0, 0) // 红色（慢）
                );
                
                g2d.setPaint(gradient);
                g2d.fillRect(trackBounds.x, trackBounds.y, trackBounds.width, trackBounds.height);
            }
        });
        
        // 添加当前值显示标签
        JLabel valueLabel = new JLabel("当前速度: " + slider.getValue() + " 毫秒/字符");
        valueLabel.setHorizontalAlignment(SwingConstants.CENTER);
        
        // 添加滑动条监听器，更新显示的值
        slider.addChangeListener(e -> {
            int value = slider.getValue();
            valueLabel.setText("当前速度: " + value + " 毫秒/字符");
            
            // 根据速度值设置标签颜色
            float ratio = (float)(value - settings.minTypingSpeed) / (settings.maxTypingSpeed - settings.minTypingSpeed);
            Color color = new Color(
                    Math.min(255, (int)(ratio * 200)),  // 红色分量
                    Math.min(255, (int)((1 - ratio) * 200)),  // 绿色分量
                    0  // 蓝色分量
            );
            valueLabel.setForeground(color);
        });
        
        // 创建面板并添加组件
        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        
        JLabel titleLabel = new JLabel("请选择打字速度");
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14));
        
        JLabel descLabel = new JLabel("数值越小打字速度越快，数值越大打字速度越慢");
        
        JPanel labelPanel = new JPanel(new BorderLayout());
        labelPanel.add(titleLabel, BorderLayout.NORTH);
        labelPanel.add(descLabel, BorderLayout.CENTER);
        
        mainPanel.add(labelPanel, BorderLayout.NORTH);
        mainPanel.add(slider, BorderLayout.CENTER);
        mainPanel.add(valueLabel, BorderLayout.SOUTH);
        
        // 创建按钮面板
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton okButton = new JButton("确定");
        JButton cancelButton = new JButton("取消");
        
        buttonPanel.add(okButton);
        buttonPanel.add(cancelButton);
        
        // 创建完整的内容面板
        JPanel contentPanel = new JPanel(new BorderLayout());
        contentPanel.add(mainPanel, BorderLayout.CENTER);
        contentPanel.add(buttonPanel, BorderLayout.SOUTH);
        
        // 使用对话框构建器创建对话框
        final int[] result = new int[] { settings.typingSpeed }; // 默认使用当前设置值
        final boolean[] confirmed = new boolean[] { false };
        
        JDialog dialog = new JDialog((Dialog)null, "FakeTyping速度设置", true);
        dialog.setContentPane(contentPanel);
        
        okButton.addActionListener(e -> {
            result[0] = slider.getValue();
            confirmed[0] = true;
            dialog.dispose();
        });
        
        cancelButton.addActionListener(e -> {
            dialog.dispose();
        });
        
        // 设置对话框大小和位置
        dialog.pack();
        dialog.setLocationRelativeTo(null);
        dialog.setResizable(false);
        dialog.setVisible(true);
        
        // 返回用户选择的值或默认值
        return confirmed[0] ? result[0] : settings.typingSpeed;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 获取项目和编辑器
        Project project = e.getProject();
        Editor editor = e.getData(CommonDataKeys.EDITOR);

        // 只有在编辑器中且有项目打开时才启用此操作
        e.getPresentation().setEnabledAndVisible(project != null && editor != null);
    }
}