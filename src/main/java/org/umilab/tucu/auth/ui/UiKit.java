package org.umilab.tucu.auth.ui;

import com.formdev.flatlaf.extras.FlatSVGIcon;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JToggleButton;
import javax.swing.UIManager;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.util.function.Consumer;

/**
 * Shared building blocks for the "Quietud de ribera" design system.
 * All colors come from the theme tokens (flatdarklaf/flatlightlaf.properties);
 * semantic text colors are read from existing FlatLaf keys so nothing is invented:
 *   primary text   -> Label.foreground
 *   secondary text -> TableHeader.foreground
 *   tertiary text  -> TextField.placeholderText
 */
public final class UiKit {

    private static final Color TEXT_ON_BRAND_DARK = new Color(0x04, 0x20, 0x1B);
    private static final String MONO_FAMILY = pickMonoFamily();

    // Muted avatar palette (theme-independent, dark text on top).
    private static final Color[] AVATAR_COLORS = {
            new Color(0x5F, 0xC4, 0xAB), new Color(0x7F, 0xB2, 0xD9),
            new Color(0xD9, 0xA8, 0x6B), new Color(0xB7, 0x8F, 0xD6),
            new Color(0x6B, 0xBF, 0xD9), new Color(0xD9, 0x8C, 0x7F),
            new Color(0x9C, 0xC9, 0x8F), new Color(0xC9, 0xC4, 0x6B),
    };

    private UiKit() {}

    // ---- semantic colors ----

    public static Color text()        { return UIManager.getColor("Label.foreground"); }
    public static Color text2()       { return UIManager.getColor("TableHeader.foreground"); }
    public static Color text3()       { return UIManager.getColor("TextField.placeholderText"); }
    public static Color brand()       { return TucuTheme.token("Brand"); }
    public static Color surface()     { return TucuTheme.token("Surface.raised"); }
    public static Color hover()       { return TucuTheme.token("Surface.hover"); }
    public static Color line()        { return TucuTheme.token("Line.subtle"); }
    public static Color listBg()      { return UIManager.getColor("Table.background"); }
    public static Color panelBg()     { return UIManager.getColor("Panel.background"); }
    public static Color warm()        { return TucuTheme.token("Tide.warm"); }
    public static Color urgent()      { return TucuTheme.token("Tide.urgent"); }
    /** Readable text on the brand color (light theme brand is dark -> white text). */
    public static Color textOnBrand() {
        return TucuTheme.mode() == TucuTheme.Mode.DARK ? TEXT_ON_BRAND_DARK : Color.WHITE;
    }

    // ---- fonts ----

    private static String pickMonoFamily() {
        for (String name : new String[]{"Cascadia Mono", "Consolas", "JetBrains Mono"}) {
            for (String f : GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames()) {
                if (f.equalsIgnoreCase(name)) return f;
            }
        }
        return Font.MONOSPACED;
    }

    public static Font mono(float size, int style) {
        return new Font(MONO_FAMILY, style, Math.round(size));
    }

    public static Font ui(float size, int style) {
        Font base = UIManager.getFont("Label.font");
        return (base != null ? base : new JLabel().getFont()).deriveFont(style, size);
    }

    // ---- text labels ----

    public static JLabel title(String text, float size) {
        JLabel l = new JLabel(text);
        l.setFont(ui(size, Font.BOLD));
        return l;
    }

    public static JLabel body(String html, Color color, float size) {
        JLabel l = new JLabel("<html>" + html + "</html>");
        l.setFont(ui(size, Font.PLAIN));
        l.setForeground(color);
        return l;
    }

    public static JLabel secondary(String html, float size) { return body(html, text2(), size); }
    public static JLabel tertiary(String text, float size)  { return body(text, text3(), size); }

    // ---- icons ----

    /** SVG icon painted in a fixed color; recreate on theme change. */
    public static FlatSVGIcon icon(String name, int size, Color color) {
        FlatSVGIcon i = new FlatSVGIcon("icons/" + name + ".svg", size, size);
        i.setColorFilter(new FlatSVGIcon.ColorFilter(c -> color));
        return i;
    }

    // ---- buttons ----

    /** Primary action: brand background, readable text on top. */
    public static JButton primary(String text) {
        JButton b = new JButton(text) {
            @Override public void updateUI() {
                super.updateUI();
                setBackground(brand());
                setForeground(textOnBrand());
            }
        };
        b.setFont(ui(15, Font.BOLD));
        b.setFocusPainted(true);
        b.setBorder(BorderFactory.createEmptyBorder(9, 18, 9, 18));
        return b;
    }

    /** Ghost action: no fill, secondary text. */
    public static JButton ghost(String text) {
        JButton b = new JButton(text) {
            @Override public void updateUI() {
                super.updateUI();
                setForeground(text2());
            }
        };
        b.setFont(ui(15, Font.PLAIN));
        b.setContentAreaFilled(false);
        b.setBorder(BorderFactory.createEmptyBorder(9, 14, 9, 14));
        b.setFocusPainted(true);
        return b;
    }

    /** Icon-only toolbar button; re-renders its icon in the current secondary color. */
    public static JButton iconButton(String iconName, String tooltip) {
        JButton b = new JButton() {
            @Override public void updateUI() {
                super.updateUI();
                setIcon(icon(iconName, 19, text2()));
            }
        };
        b.setToolTipText(tooltip);
        b.setPreferredSize(new Dimension(38, 38));
        b.setContentAreaFilled(false);
        b.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
        b.setFocusPainted(true);
        b.getAccessibleContext().setAccessibleName(tooltip);
        return b;
    }

    // ---- logo mark (theme-aware) ----

    /** The Tucu mark as vector art — crisp at any size/DPI; swaps per theme. */
    public static JComponent mark(int size) {
        JLabel l = new JLabel() {
            @Override public void updateUI() {
                super.updateUI();
                setIcon(markIcon(size));
            }
        };
        l.setIcon(markIcon(size));
        l.setPreferredSize(new Dimension(size, size));
        return l;
    }

    private static javax.swing.Icon markIcon(int size) {
        // The v2 mark stays legible down to 16 px, so one artwork per theme
        // covers all sizes. Colors are baked into each SVG (light mark for
        // dark theme and vice-versa), so no color filter is needed.
        boolean dark = TucuTheme.mode() == TucuTheme.Mode.DARK;
        String res = dark ? "logo/tucu-mark.svg" : "logo/tucu-mark-light.svg";
        return new FlatSVGIcon(res, size, size);
    }

    // ---- note box (raised surface with colored left rail) ----

    public static JComponent noteBox(String iconName, Color railColor, String html) {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(surface());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.setColor(railColor);
                g2.fillRect(0, 0, 3, getHeight());
                g2.dispose();
                super.paintComponent(g);
            }
        };
        p.setOpaque(false);
        p.setLayout(new BoxLayout(p, BoxLayout.X_AXIS));
        p.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 16));

        JLabel ic = new JLabel() {
            @Override public void updateUI() {
                super.updateUI();
                setIcon(icon(iconName, 19, railColor));
            }
        };
        ic.setIcon(icon(iconName, 19, railColor));
        ic.setAlignmentY(Component.TOP_ALIGNMENT);
        p.add(ic);
        p.add(Box.createHorizontalStrut(12));

        JLabel txt = body(html, text2(), 15);
        txt.setAlignmentY(Component.TOP_ALIGNMENT);
        p.add(txt);
        return p;
    }

    // ---- segmented control (theme / language pickers) ----

    public static final class SegmentedControl extends JPanel {
        public SegmentedControl(String[] labels, int selected, Consumer<Integer> onSelect) {
            setLayout(new BoxLayout(this, BoxLayout.X_AXIS));
            setBorder(BorderFactory.createLineBorder(line(), 1, true));
            ButtonGroup group = new ButtonGroup();
            for (int i = 0; i < labels.length; i++) {
                int idx = i;
                JToggleButton t = new JToggleButton(labels[i]) {
                    @Override public void updateUI() {
                        super.updateUI();
                        restyle(this);
                    }
                };
                restyle(t);
                t.setSelected(i == selected);
                t.addActionListener(e -> onSelect.accept(idx));
                t.getModel().addChangeListener(e -> paint(t));
                t.setFocusPainted(true);
                group.add(t);
                add(t);
            }
        }

        private static void restyle(JToggleButton t) {
            t.setFont(ui(15, Font.PLAIN));
            t.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            t.setContentAreaFilled(false);
            t.setOpaque(true);
            paint(t);
        }

        private static void paint(JToggleButton t) {
            t.setBackground(t.isSelected() ? brand() : surface());
            t.setForeground(t.isSelected() ? textOnBrand() : text2());
        }
    }

    // ---- password strength meter: 4 rounded segments ----

    public static final class StrengthMeter extends JPanel {
        private int score = 0;

        public StrengthMeter() {
            setOpaque(false);
            setLayout(new java.awt.GridLayout(1, 4, 6, 0));
            setPreferredSize(new Dimension(200, 5));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 5));
            for (int i = 0; i < 4; i++) add(new Segment(i));
        }

        public void setScore(int s) { score = s; repaint(); }

        private class Segment extends JComponent {
            private final int index;
            Segment(int index) { this.index = index; }
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                boolean on = index < score;
                Color c = !on ? surface()
                        : score <= 1 ? urgent()
                        : score == 2 ? warm()
                        : brand();
                g2.setColor(c);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
            }
        }
    }

    // ---- avatar: rounded chip with the account's initial ----

    public static final class Avatar extends JComponent {
        private final String initial;
        private final Color bg;

        public Avatar(String seed, int size) {
            this.initial = (seed == null || seed.isBlank() ? "?"
                    : seed.trim().substring(0, 1).toUpperCase(java.util.Locale.ROOT));
            this.bg = AVATAR_COLORS[Math.floorMod(seed == null ? 0 : seed.hashCode(), AVATAR_COLORS.length)];
            setPreferredSize(new Dimension(size, size));
        }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
            g2.setColor(TEXT_ON_BRAND_DARK);
            g2.setFont(ui(17, Font.BOLD));
            var fm = g2.getFontMetrics();
            g2.drawString(initial, (getWidth() - fm.stringWidth(initial)) / 2,
                    (getHeight() + fm.getAscent() - fm.getDescent()) / 2);
            g2.dispose();
        }
    }

    // ---- misc ----

    /** "824193" -> "824 193" (grouped so codes are easy to compare). */
    public static String groupCode(String code) {
        if (code == null || code.length() <= 3) return code;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length(); i++) {
            if (i > 0 && i % 3 == 0) sb.append(' ');
            sb.append(code.charAt(i));
        }
        return sb.toString();
    }

    /** 1px horizontal rule in the subtle line color. */
    public static JComponent rule() {
        return new JComponent() {
            { setPreferredSize(new Dimension(1, 1)); setMaximumSize(new Dimension(Integer.MAX_VALUE, 1)); }
            @Override protected void paintComponent(Graphics g) {
                g.setColor(line());
                g.drawLine(0, 0, getWidth(), 0);
            }
        };
    }

    /** Thin vertical gap between toolbar items. */
    public static Component gap(int w) { return Box.createHorizontalStrut(w); }
}
