package proto.ui.component;

import java.util.Map;

/**
 * [경계 boundary] 카테고리 색 키("blue" 등)를 사용자에게 보이는 한국어 이름("파랑" 등)으로 바꾼다.
 * ColorSwatchPopover의 툴팁과 접근성 이름, 색 칩의 설명이 쓴다 (F7). 모르는 키는 키 그대로 보여 준다.
 */
public final class ColorLabels {

    private static final Map<String, String> NAMES = Map.ofEntries(
            Map.entry("blue", "파랑"), Map.entry("orange", "주황"), Map.entry("teal", "청록"),
            Map.entry("rose", "분홍"), Map.entry("green", "초록"), Map.entry("purple", "보라"),
            Map.entry("sky", "하늘"), Map.entry("brown", "갈색"), Map.entry("red", "빨강"),
            Map.entry("yellow", "노랑"), Map.entry("indigo", "남색"), Map.entry("gray", "회색"));

    private ColorLabels() {
    }

    public static String of(String key) {
        return NAMES.getOrDefault(key, key);
    }
}
