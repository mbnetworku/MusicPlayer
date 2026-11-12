package com.musicplayer.lite;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.RadioGroup;

/**
 * Shown when a widget is added, and again from the widget's settings: the widget's look is
 * Follow my device, Light or Dark. Leaving without saving cancels adding the widget.
 */
public class WidgetConfigActivity extends BoundActivity {
    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        Intent intent = getIntent();
        if (intent != null && intent.getExtras() != null) {
            widgetId = intent.getExtras().getInt(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
        }
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        setContentView(R.layout.activity_widget_config);
        prepareWindow();
        final Prefs prefs = Prefs.get(this);
        final RadioGroup group = findViewById(R.id.widget_style);
        int style = prefs.widgetStyle(widgetId);
        group.check(style == Appearance.LIGHT ? R.id.widget_light
                : style == Appearance.DARK ? R.id.widget_dark : R.id.widget_follow);
        findViewById(R.id.btn_back).setOnClickListener(v -> finish());
        findViewById(R.id.widget_save).setOnClickListener(v -> {
            int id = group.getCheckedRadioButtonId();
            prefs.setWidgetStyle(widgetId, id == R.id.widget_light ? Appearance.LIGHT
                    : id == R.id.widget_dark ? Appearance.DARK : Appearance.FOLLOW_DEVICE);
            AppWidgetManager.getInstance(this).updateAppWidget(widgetId, PlayerWidget.build(this, widgetId));
            setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId));
            finish();
        });
    }
}
