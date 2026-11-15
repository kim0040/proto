package proto.ui.component;

import proto.ui.motion.Tween;

import javax.swing.Timer;

/**
 * [경계 boundary] 일정 행 하나의 목록 변화 모션: 새 자리로 미끄러지기, 나타나기(밑에서 6px 올라오며 서서히 보임).
 * OccurrenceRow가 하나씩 갖고, OccurrenceList가 변화를 계산해 시킨다. 미끄러짐은 "예전 자리와 새 자리의 차이"에서 0으로 줄어드는 세로 어긋남(offset)이라
 * 레이아웃은 이미 새 자리로 정해져 있고 화면에서만 천천히 따라온다. 오버슈트는 없다. 모든 값은 Tween이라 중간에 다시 바뀌어도 끊기지 않는다.
 */
final class RowMotion {

    private static final int GLIDE_MS = 160;
    private static final int FADE_MS = 140;

    private final Tween slide;
    private final Tween fade;
    private boolean active;

    RowMotion(Runnable onChange) {
        this.slide = new Tween(0f, onChange);
        this.fade = new Tween(1f, onChange);
    }

    /** 지금 레이아웃 자리에서 얼마나 어긋나 그려지는지 (px, 양수면 아래쪽) */
    int offset() {
        return Math.round(slide.value());
    }

    /** 지금 투명도 (0~1) */
    float alpha() {
        return Math.max(0f, Math.min(1f, fade.value()));
    }

    /** 예전 자리가 dy(px)만큼 어긋나 있었다: 거기서 새 자리로 미끄러진다 */
    void glideFrom(float dy) {
        active = true;
        slide.set(dy);
        slide.to(0f, GLIDE_MS);
    }

    /** 안 보이는 상태로 시작해 delayMs 뒤에 rise(px) 아래에서 올라오며 나타난다 */
    void appear(float rise, int delayMs) {
        active = true;
        fade.set(0f);
        slide.set(rise);
        if (delayMs <= 0) {
            play();
            return;
        }
        Timer t = new Timer(delayMs, e -> play());
        t.setRepeats(false);
        t.start();
    }

    private void play() {
        fade.to(1f, FADE_MS);
        slide.to(0f, GLIDE_MS);
    }

    /** 모션 없이 제자리·완전 불투명으로 맞춘다 */
    void settle() {
        if (active) {
            active = false;
            slide.set(0f);
            fade.set(1f);
        }
    }
}
