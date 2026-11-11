package com.musicplayer.lite;

import android.content.Intent;
import android.media.audiofx.AudioEffect;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/** Equalizer: presets, a band slider per frequency, bass boost, surround and volume boost. */
public class EqualizerActivity extends BoundActivity implements EqSliderView.Listener {

    /** A label, a slider and a value readout for one sound effect. */
    private final class EffectRow {
        final View root;
        final SeekBar seek;
        final TextView value;
        final IntFunction<String> format;

        EffectRow(int rowId, int labelRes, int max, final IntConsumer onChange, IntFunction<String> format) {
            this.root = findViewById(rowId);
            this.seek = root.findViewById(R.id.fx_seek);
            this.value = root.findViewById(R.id.fx_value);
            this.format = format;
            ((TextView) root.findViewById(R.id.fx_label)).setText(labelRes);
            seek.setMax(max);
            seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                    value.setText(EffectRow.this.format.apply(progress));
                    if (fromUser) {
                        ensureOn();
                        onChange.accept(progress);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar bar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar bar) {
                    fx.commit();
                }
            });
        }

        void set(int v) {
            seek.setProgress(v);
            value.setText(format.apply(v));
        }
    }

    private EffectsController fx;
    private boolean built;
    private boolean syncing;

    private Switch toggle;
    private View content;
    private TextView unavailable;
    private ViewGroup presetRow;
    private LinearLayout bandRow;
    private EqCurveView curve;
    private final List<EqSliderView> sliders = new ArrayList<>();
    private final List<TextView> bandValues = new ArrayList<>();
    private final List<TextView> chips = new ArrayList<>();
    private EffectRow bassRow;
    private EffectRow virtRow;
    private EffectRow loudRow;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_equalizer);
        applyInsets();
        toggle = findViewById(R.id.eq_switch);
        content = findViewById(R.id.eq_content);
        unavailable = findViewById(R.id.eq_unavailable);
        presetRow = findViewById(R.id.eq_presets);
        bandRow = findViewById(R.id.eq_bands);
        curve = findViewById(R.id.eq_curve);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
    }

    @Override
    protected void onServiceReady() {
        if (built) return;
        built = true;
        fx = service.effects();
        build();
    }

    @Override
    protected void onStop() {
        if (fx != null) fx.commit();
        super.onStop();
    }

    private void build() {
        final boolean hasEq = fx.hasEq();
        final boolean anything = hasEq || fx.hasBass() || fx.hasVirtualizer() || fx.hasLoudness();
        unavailable.setVisibility(hasEq ? View.GONE : View.VISIBLE);
        content.setVisibility(anything ? View.VISIBLE : View.GONE);
        toggle.setEnabled(anything);

        // Presets, curve and bands only exist when the device has an equalizer.
        View presetHolder = presetRow.getParent() instanceof HorizontalScrollView
                ? (View) presetRow.getParent() : presetRow;
        presetHolder.setVisibility(hasEq ? View.VISIBLE : View.GONE);
        curve.setVisibility(hasEq ? View.VISIBLE : View.GONE);
        bandRow.setVisibility(hasEq ? View.VISIBLE : View.GONE);

        if (hasEq) {
            for (int i = 0; i < EqPresets.COUNT; i++) {
                final int preset = i;
                chips.add(addChip(getString(EqPresets.NAMES[i]), v -> {
                    ensureOn();
                    fx.usePreset(preset);
                    syncFromFx();
                }));
            }
            chips.add(addChip(getString(R.string.eq_custom), null));

            LayoutInflater inflater = LayoutInflater.from(this);
            for (int b = 0; b < fx.bandCount(); b++) {
                View col = inflater.inflate(R.layout.item_eq_band, bandRow, false);
                EqSliderView slider = col.findViewById(R.id.band_slider);
                TextView value = col.findViewById(R.id.band_value);
                TextView freq = col.findViewById(R.id.band_freq);
                freq.setText(Fmt.freq(fx.centerFreq(b)));
                slider.setRange(fx.minLevel(), fx.maxLevel());
                slider.setTag(b);
                slider.setContentDescription(freq.getText());
                slider.setListener(this);
                bandRow.addView(col);
                sliders.add(slider);
                bandValues.add(value);
            }
        }

        bassRow = new EffectRow(R.id.fx_bass, R.string.eq_bass, EffectsController.STRENGTH_MAX,
                v -> fx.setBass(v), v -> (v / 10) + "%");
        virtRow = new EffectRow(R.id.fx_virt, R.string.eq_virtualizer, EffectsController.STRENGTH_MAX,
                v -> fx.setVirtualizer(v), v -> (v / 10) + "%");
        loudRow = new EffectRow(R.id.fx_loud, R.string.eq_loudness, EffectsController.LOUDNESS_MAX,
                v -> fx.setLoudness(v), Fmt::db);
        bassRow.root.setVisibility(fx.hasBass() ? View.VISIBLE : View.GONE);
        virtRow.root.setVisibility(fx.hasVirtualizer() ? View.VISIBLE : View.GONE);
        loudRow.root.setVisibility(fx.hasLoudness() ? View.VISIBLE : View.GONE);

        toggle.setOnCheckedChangeListener((b, on) -> {
            if (syncing) return;
            fx.setEnabled(on);
            applyEnabledState();
        });
        findViewById(R.id.eq_reset).setOnClickListener(v -> {
            fx.reset();
            syncFromFx();
        });

        final Intent panel = new Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL)
                .putExtra(AudioEffect.EXTRA_AUDIO_SESSION, service.audioSession())
                .putExtra(AudioEffect.EXTRA_PACKAGE_NAME, getPackageName())
                .putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC);
        View system = findViewById(R.id.eq_system);
        if (panel.resolveActivity(getPackageManager()) != null) {
            system.setOnClickListener(v -> startActivity(panel));
        } else {
            system.setVisibility(View.GONE);
        }
        syncFromFx();
    }

    private TextView addChip(String text, View.OnClickListener onClick) {
        TextView chip = new TextView(this);
        chip.setText(text);
        chip.setTextColor(getColorStateList(R.color.chip_text));
        chip.setTextSize(14);
        chip.setGravity(android.view.Gravity.CENTER);
        chip.setBackgroundResource(R.drawable.bg_chip);
        chip.setPadding(dp(20), 0, dp(20), 0);
        if (onClick != null) chip.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                getResources().getDimensionPixelSize(R.dimen.eq_chip_height));
        lp.setMarginEnd(dp(8));
        lp.bottomMargin = dp(8);
        presetRow.addView(chip, lp);
        return chip;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /** Touching a preset or an effect turns the equalizer on. */
    private void ensureOn() {
        if (!fx.isEnabled()) {
            fx.setEnabled(true);
            syncing = true;
            toggle.setChecked(true);
            syncing = false;
            applyEnabledState();
        }
    }

    private void syncFromFx() {
        syncing = true;
        toggle.setChecked(fx.isEnabled());
        syncing = false;
        if (fx.hasEq()) {
            int[] levels = new int[fx.bandCount()];
            for (int b = 0; b < levels.length; b++) {
                levels[b] = fx.level(b);
                sliders.get(b).setLevel(levels[b]);
                bandValues.get(b).setText(Fmt.db(levels[b]));
            }
            curve.setData(levels, fx.minLevel(), fx.maxLevel());
            int selected = fx.preset() >= 0 ? fx.preset() : EqPresets.COUNT;
            for (int i = 0; i < chips.size(); i++) chips.get(i).setSelected(i == selected);
            revealChip(chips.get(selected));
        }
        bassRow.set(fx.bass());
        virtRow.set(fx.virtualizer());
        loudRow.set(fx.loudness());
        applyEnabledState();
    }

    /** Scrolls the preset row (phone layout) so the chosen preset is not hidden off-screen. */
    private void revealChip(final TextView chip) {
        if (!(presetRow.getParent() instanceof HorizontalScrollView)) return;
        final HorizontalScrollView scroller = (HorizontalScrollView) presetRow.getParent();
        scroller.post(() -> {
            if (chip.getLeft() < scroller.getScrollX()
                    || chip.getRight() > scroller.getScrollX() + scroller.getWidth()) {
                scroller.smoothScrollTo(Math.max(0, chip.getLeft() - dp(24)), 0);
            }
        });
    }

    private void applyEnabledState() {
        final boolean on = fx.isEnabled();
        for (EqSliderView s : sliders) s.setEnabled(on);
        curve.setActive(on);
        bandRow.setAlpha(on ? 1f : 0.5f);
        curve.setAlpha(on ? 1f : 0.7f);
    }

    // ---- EqSliderView.Listener ------------------------------------------------------------------------

    @Override
    public void onLevelChanged(EqSliderView slider, int millibels) {
        int band = (Integer) slider.getTag();
        fx.setBand(band, millibels);
        bandValues.get(band).setText(Fmt.db(millibels));
        int[] levels = new int[fx.bandCount()];
        for (int b = 0; b < levels.length; b++) levels[b] = fx.level(b);
        curve.setData(levels, fx.minLevel(), fx.maxLevel());
        for (int i = 0; i < chips.size(); i++) chips.get(i).setSelected(i == EqPresets.COUNT);
    }

    @Override
    public void onDragEnd(EqSliderView slider) {
        fx.commit();
    }
}
