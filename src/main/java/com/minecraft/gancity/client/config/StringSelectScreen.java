package com.minecraft.gancity.client.config;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Simple searchable selector screen for strings (used as a "dropdown" picker).
 */
public final class StringSelectScreen extends Screen {
    private final Screen parent;
    private final List<String> allOptions;
    private final Consumer<String> onSelected;

    private EditBox search;
    private OptionsList list;

    private String currentSelection;

    public StringSelectScreen(Screen parent, Component title, List<String> options, String currentSelection, Consumer<String> onSelected) {
        super(title);
        this.parent = parent;
        this.allOptions = options == null ? List.of() : new ArrayList<>(options);
        this.currentSelection = currentSelection;
        this.onSelected = onSelected;
    }

    @Override
    protected void init() {
        int w = Math.min(420, this.width - 20);
        int x = (this.width - w) / 2;

        int bottomButtonsY = this.height - 28;

        search = new EditBox(this.font, x, 18, w, 18, Component.literal("Search"));
        search.setMaxLength(128);
        search.setResponder(s -> refreshList());
        addRenderableWidget(search);

        list = new OptionsList(this.minecraft, w, this.height, 44, bottomButtonsY - 12, 18);
        list.setLeftPos(x);
        addRenderableWidget(list);

        addRenderableWidget(Button.builder(Component.literal("Cancel"), b -> onClose())
            .bounds(x, bottomButtonsY, 100, 20)
            .build());

        refreshList();
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    private void refreshList() {
        if (list == null) {
            return;
        }

        String q = search == null ? "" : search.getValue();
        String query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);

        List<String> filtered;
        if (query.isEmpty()) {
            filtered = allOptions;
        } else {
            filtered = allOptions.stream().filter(o -> o != null && o.toLowerCase(Locale.ROOT).contains(query)).toList();
        }

        list.replaceEntries(filtered);
    }

    private final class OptionsList extends ObjectSelectionList<OptionsList.Entry> {
        OptionsList(net.minecraft.client.Minecraft minecraft, int width, int height, int y0, int y1, int itemHeight) {
            super(minecraft, width, height, y0, y1, itemHeight);
            setRenderBackground(true);
        }

        void replaceEntries(List<String> options) {
            clearEntries();
            for (String option : options) {
                if (option == null) {
                    continue;
                }
                addEntry(new Entry(option));
            }
        }

        @Override
        public int getRowWidth() {
            return this.width - 12;
        }

        final class Entry extends ObjectSelectionList.Entry<Entry> {
            private final String option;

            Entry(String option) {
                this.option = option;
            }

            @Override
            public Component getNarration() {
                return Component.literal(option);
            }

            @Override
            public void render(net.minecraft.client.gui.GuiGraphics gfx, int index, int y, int x, int rowWidth, int rowHeight, int mouseX, int mouseY, boolean hovered, float partialTick) {
                int color = Objects.equals(currentSelection, option) ? 0xFFFFCC00 : 0xFFE0E0E0;
                gfx.drawString(font, option, x + 4, y + 4, color, false);
            }

            @Override
            public boolean mouseClicked(double mouseX, double mouseY, int button) {
                currentSelection = option;
                if (onSelected != null) {
                    onSelected.accept(option);
                }
                onClose();
                return true;
            }
        }
    }
}
