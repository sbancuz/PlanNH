package com.sbancuz.plannh.gui;

import javax.annotation.Nonnull;

import com.cleanroommc.modularui.utils.Color;
import com.gtnewhorizon.gtnhlib.color.ColorResource;

public final class PlannhColors {

    private static final ColorResource.Factory C = new ColorResource.Factory("plannh");

    // spotless:off
    // ── Backgrounds ──
    public static final ColorResource
        SLOT_BAR_BG       = C.argb("slot_bar_bg",        "0x3C242428"),
        SUMMARY_BG        = C.argb("summary_bg",         "0x2D1E1E23"),
        SUMMARY_TITLE_BG  = C.argb("summary_title_bg",   "0x3C323237"),
        NODE_BG           = C.argb("node_bg",            "0x323232E6"),
        NOTE_BG           = C.argb("note_bg",            "0xC8FFF0A0"),
        NOTE_BG_EDITING   = C.argb("note_bg_editing",    "0xE6FFFAE0"),
        SETTINGS_PANEL_BG = C.argb("settings_panel_bg",  "0xAA202020");

    // ── Borders / Lines / Separators ──
    public static final ColorResource
        SLOT_BAR_LINE      = C.argb("slot_bar_line",       "0x6464A0DC"),
        SUMMARY_TITLE_LINE = C.argb("summary_title_line",  "0x5078A0DC"),
        NODE_BORDER        = C.argb("node_border",         "0x6496C8FF"),
        NODE_TITLE_LINE    = C.argb("node_title_line",     "0x3CFFFFFF"),
        NOTE_BORDER        = C.argb("note_border",         "0xA0B4A050"),
        NOTE_BORDER_EDIT   = C.argb("note_border_edit",    "0xC8B4A03C"),
        SEPARATOR_LIGHT    = C.argb("separator_light",     "0x3CC8C8C8"),
        SEPARATOR_DIM      = C.argb("separator_dim",       "0x32C8C8C8");

    // ── Section Headers (summary highlight bars) ──
    public static final ColorResource
        SECTION_PRODUCT   = C.argb("section_product",    "0x32B48C3C"),
        SECTION_INPUT     = C.argb("section_input",      "0x3250A050"),
        SECTION_FLUID_OUT = C.argb("section_fluid_out",  "0x323C8CB4"),
        SECTION_FLUID_IN  = C.argb("section_fluid_in",   "0x323C64B4"),
        SECTION_OPS       = C.argb("section_ops",        "0x326478C8");

    // ── Text Colors (opaque) ──
    public static final ColorResource
        TEXT_WHITE   = C.rgb("text_white",   "0xFFFFFF"),
        TEXT_LIGHT   = C.rgb("text_light",   "0xCCCCCC"),
        TEXT_MUTED   = C.rgb("text_muted",   "0xAAAAAA"),
        TEXT_DIM     = C.rgb("text_dim",     "0x888888"),
        TEXT_FAINT   = C.rgb("text_faint",   "0x666666"),
        TEXT_DARK    = C.rgb("text_dark",    "0x444444"),
        TEXT_NOTE    = C.rgb("text_note",    "0x555555"),
        TEXT_BADGE   = C.rgb("text_badge",   "0xAAFFFF");

    // ── Accent Text Colors (opaque) ──
    public static final ColorResource
        ACCENT_BLUE    = C.rgb("accent_blue",    "0x88AAFF"),
        ACCENT_GREEN   = C.rgb("accent_green",   "0x88FF88"),
        ACCENT_RED     = C.rgb("accent_red",     "0xFF8888"),
        ACCENT_RED_X   = C.rgb("accent_red_x",   "0xFF5555"),
        ACCENT_YELLOW  = C.rgb("accent_yellow",  "0xFFFFAA"),
        ACCENT_CYAN    = C.rgb("accent_cyan",    "0x77FFAA"),
        ACCENT_AMBER   = C.rgb("accent_amber",   "0xFFCC66"),
        ACCENT_GREEN2  = C.rgb("accent_green2",  "0x77DD77"),
        ACCENT_CYAN2   = C.rgb("accent_cyan2",   "0x77DDDD"),
        ACCENT_BLUE2   = C.rgb("accent_blue2",   "0x77AADD"),
        ACCENT_BLUE3   = C.rgb("accent_blue3",   "0x77AAFF");

    // ── Arrows & Preview ──
    public static final ColorResource
        ARROW_ITEM        = C.argb("arrow_item",         "0xDCC88C3C"),
        ARROW_FLUID       = C.argb("arrow_fluid",        "0xDC3C8CC8"),
        PREVIEW_HIGHLIGHT = C.argb("preview_highlight",  "0xB4FFC850");

    // ── Slot Pins ──
    public static final ColorResource
        PIN_OUTPUT       = C.argb("pin_output",        "0xDC64C864"),
        PIN_OUTPUT_HOVER = C.argb("pin_output_hover",  "0x3C64C864"),
        PIN_INPUT        = C.argb("pin_input",         "0xDC6464C8"),
        PIN_INPUT_HOVER  = C.argb("pin_input_hover",   "0x3C6464C8"),
        PIN_FLUID_OUT    = C.argb("pin_fluid_out",     "0xDC3C8CC8"),
        PIN_FLUID_OUT_H  = C.argb("pin_fluid_out_h",   "0x3C3C8CC8"),
        PIN_FLUID_IN     = C.argb("pin_fluid_in",      "0xDC3C64C8"),
        PIN_FLUID_IN_H   = C.argb("pin_fluid_in_h",    "0x3C3C64C8");

    // ── Close / Delete Buttons ──
    public static final ColorResource
        BTN_DELETE_SHADOW = C.argb("btn_delete_shadow",  "0x50C82828"),
        BTN_DELETE_BG     = C.argb("btn_delete_bg",      "0xC8B42828"),
        BTN_DELETE_INNER  = C.argb("btn_delete_inner",   "0xDC000000"),
        NOTE_CLOSE_BG     = C.argb("note_close_bg",      "0xB4C83C3C"),
        PORT_LABEL_BG     = C.argb("port_label_bg",      "0xC8141414");

    // ── Grid ──
    public static final ColorResource
        GRID_LINE      = C.argb("grid_line",       "0x18FFFFFF"),
        GRID_MAJOR     = C.argb("grid_major",      "0x30FFFFFF");

    // ── Context Menu ──
    public static final ColorResource
        CONTEXT_BG     = C.argb("context_bg",      "0xE6323237"),
        CONTEXT_BORDER = C.argb("context_border",  "0x9064A0DC"),
        CONTEXT_HOVER  = C.argb("context_hover",   "0x3C64C864");

    // ── Setting States (opaque) ──
    public static final ColorResource
        SETTING_ON  = C.rgb("setting_on",  "0x88FF88"),
        SETTING_OFF = C.rgb("setting_off", "0x888888");

    // spotless:on

    /**
     * Generates a deterministic title bar color from a recipe name hash.
     * Uses golden-ratio hue distribution for visual variety.
     */
    @Nonnull
    public static int titleColor(@Nonnull final String name) {
        final int hash = name.hashCode() % 1000;
        final float hue = hash * 0.618033988749895f;
        final int rgb = java.awt.Color.HSBtoRGB(hue, 0.45f, 0.55f);
        final int r = rgb >> 16 & 0xFF;
        final int g = rgb >> 8 & 0xFF;
        final int b = rgb & 0xFF;
        return Color.argb(r, g, b, 100);
    }
}
