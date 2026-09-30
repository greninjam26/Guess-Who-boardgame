package com.guesswho.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.JTextField;

/** Shared visual language for the desktop interface. */
final class UiTheme {
    static final Color CANVAS = new Color(244, 247, 246);
    static final Color SURFACE = Color.WHITE;
    static final Color TEXT = new Color(29, 42, 48);
    static final Color MUTED_TEXT = new Color(75, 91, 99);
    static final Color PRIMARY = new Color(20, 90, 102);
    static final Color ERROR = new Color(143, 38, 38);
    static final Color BORDER = new Color(210, 220, 219);

    private UiTheme() {
    }

    static JPanel screen() {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setBackground(CANVAS);
        panel.setBorder(BorderFactory.createEmptyBorder(32, 32, 32, 32));
        return panel;
    }

    static JPanel card() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(32, 40, 32, 40)));
        return panel;
    }

    static void addCentered(JPanel screen, JPanel card) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.weightx = 1;
        constraints.weighty = 1;
        constraints.fill = GridBagConstraints.NONE;
        constraints.insets = new Insets(8, 8, 8, 8);
        screen.add(card, constraints);
    }

    static JLabel title(String text) {
        JLabel label = label(text, TEXT, Font.BOLD, 32f);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    static JLabel subtitle(String text) {
        JLabel label = label(text, MUTED_TEXT, Font.PLAIN, 16f);
        label.setHorizontalAlignment(SwingConstants.CENTER);
        return label;
    }

    static JButton primaryButton(String text) {
        JButton button = new JButton(text);
        button.setBackground(PRIMARY);
        button.setForeground(Color.WHITE);
        button.setOpaque(true);
        button.setBorder(BorderFactory.createEmptyBorder(11, 24, 11, 24));
        button.setFocusPainted(true);
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
        Dimension preferred = button.getPreferredSize();
        button.setPreferredSize(new Dimension(Math.max(150, preferred.width),
                Math.max(42, preferred.height)));
        return button;
    }

    static JButton secondaryButton(String text) {
        JButton button = new JButton(text);
        styleSecondaryButton(button);
        return button;
    }

    static void styleSecondaryButton(JButton button) {
        button.setForeground(TEXT);
        button.setBackground(SURFACE);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)));
        button.setAlignmentX(Component.CENTER_ALIGNMENT);
    }

    static void styleToolbarButton(JButton button) {
        styleSecondaryButton(button);
        Dimension preferred = button.getPreferredSize();
        button.setPreferredSize(new Dimension(preferred.width, Math.max(38, preferred.height)));
    }

    static void styleToolbar(JPanel panel) {
        panel.setBackground(SURFACE);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
                BorderFactory.createEmptyBorder(8, 14, 8, 14)));
    }

    static JButton choiceButton(String text) {
        JButton button = secondaryButton(text);
        styleChoiceButton(button);
        return button;
    }

    static void styleChoiceButton(JButton button) {
        Dimension size = new Dimension(360, 44);
        button.setPreferredSize(size);
        button.setMaximumSize(size);
    }

    static void styleInput(JTextField field) {
        Dimension size = new Dimension(360, 38);
        field.setPreferredSize(size);
        field.setMaximumSize(size);
        field.setAlignmentX(Component.CENTER_ALIGNMENT);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(7, 10, 7, 10)));
    }

    static void alignCenter(Component component) {
        if (component instanceof javax.swing.JComponent swingComponent) {
            swingComponent.setAlignmentX(Component.CENTER_ALIGNMENT);
        }
    }

    static Component gap(int height) {
        return Box.createVerticalStrut(height);
    }

    private static JLabel label(String text, Color color, int style, float size) {
        JLabel label = new JLabel(text);
        label.setForeground(color);
        label.setFont(label.getFont().deriveFont(style, size));
        label.setAlignmentX(Component.CENTER_ALIGNMENT);
        return label;
    }
}
