package com.minecraft.gancity.client.config;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

@SuppressWarnings("null")
final class AdaptiveMobAiUiText {
    private AdaptiveMobAiUiText() {
    }

    static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }

    static String text(String key, Object... args) {
        return I18n.get(key, args);
    }

    static void setTooltip(Button button, String key, Object... args) {
        if (button != null) {
            button.setTooltip(Tooltip.create(tr(key, args)));
        }
    }

    static Component loadoutOptionLabel(String option) {
        if (option == null || option.isBlank() || option.equalsIgnoreCase("(empty)") || option.equalsIgnoreCase("(learned)")) {
            return tr("config.adaptivemobai.option.loadout.empty");
        }
        if (option.equalsIgnoreCase("default") || option.equalsIgnoreCase("preserve")) {
            return tr("config.adaptivemobai.option.loadout.default");
        }
        if (option.equalsIgnoreCase("none")) {
            return tr("config.adaptivemobai.option.loadout.none");
        }
        return Component.literal(option);
    }
}