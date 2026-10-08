package it.filtrogruppo.app;

import android.Manifest;
import android.app.Activity;
import android.app.NotificationManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collections;
import java.util.List;

public class MainActivity extends Activity {
    private SharedPreferences sp;
    private EditText etGroup;
    private EditText etPersons;
    private Switch swEnabled;
    private Switch swAlert;
    private TextView tvAccess;
    private TextView tvPost;
    private TextView tvStats;
    private TextView tvLast;
    private TextView tvSendersEmpty;
    private TextView tvGroupsEmpty;
    private Button btnPost;
    private LinearLayout boxGroups;
    private LinearLayout boxSenders;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sp = Prefs.sp(this);
        FilterService.ensureChannels(this);

        etGroup = findViewById(R.id.et_group);
        etPersons = findViewById(R.id.et_persons);
        swEnabled = findViewById(R.id.sw_enabled);
        swAlert = findViewById(R.id.sw_alert);
        tvAccess = findViewById(R.id.tv_access);
        tvPost = findViewById(R.id.tv_post);
        tvStats = findViewById(R.id.tv_stats);
        tvLast = findViewById(R.id.tv_last);
        tvSendersEmpty = findViewById(R.id.tv_senders_empty);
        tvGroupsEmpty = findViewById(R.id.tv_groups_empty);
        btnPost = findViewById(R.id.btn_post);
        boxGroups = findViewById(R.id.box_groups);
        boxSenders = findViewById(R.id.box_senders);

        etGroup.setText(sp.getString(Prefs.KEY_GROUP, ""));
        etPersons.setText(String.join("\n", Prefs.persons(sp)));
        swEnabled.setChecked(sp.getBoolean(Prefs.KEY_ENABLED, true));
        swAlert.setChecked(sp.getBoolean(Prefs.KEY_ALERT_MODE, false));

        findViewById(R.id.btn_access).setOnClickListener(v -> openListenerSettings());
        findViewById(R.id.btn_app_info).setOnClickListener(v -> openAppInfo());
        btnPost.setOnClickListener(v -> askPostPermission());
        findViewById(R.id.btn_wa_settings).setOnClickListener(v -> openWhatsAppNotificationSettings());
        findViewById(R.id.btn_sound).setOnClickListener(v -> openChannelSettings());
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            save();
            Toast.makeText(this, R.string.saved, Toast.LENGTH_SHORT).show();
            refreshSenders();
        });
        swEnabled.setOnCheckedChangeListener((b, checked) -> save());
        swAlert.setOnCheckedChangeListener((b, checked) -> save());
        etGroup.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { refreshSenders(); }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        refreshGroups();
        refreshSenders();
    }

    @Override
    protected void onPause() {
        super.onPause();
        save();
    }

    private void save() {
        sp.edit()
                .putString(Prefs.KEY_GROUP, etGroup.getText().toString().trim())
                .putString(Prefs.KEY_PERSONS, String.join("\n", Prefs.lines(etPersons.getText().toString())))
                .putBoolean(Prefs.KEY_ENABLED, swEnabled.isChecked())
                .putBoolean(Prefs.KEY_ALERT_MODE, swAlert.isChecked())
                .apply();
    }

    private void refreshStatus() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean access = nm != null && nm.isNotificationListenerAccessGranted(
                new ComponentName(this, FilterService.class));
        tvAccess.setText(access ? R.string.access_on : R.string.access_off);
        tvAccess.setTextColor(getColor(access ? R.color.ok : R.color.warn));

        boolean post = canPostNotifications();
        tvPost.setText(post ? R.string.post_on : R.string.post_off);
        tvPost.setTextColor(getColor(post ? R.color.ok : R.color.warn));
        btnPost.setVisibility(post ? View.GONE : View.VISIBLE);

        tvStats.setText(getString(R.string.stats_fmt, sp.getInt(Prefs.KEY_FILTERED, 0)));
        String last = sp.getString(Prefs.KEY_LAST_EVENT, "");
        tvLast.setText(last == null || last.isEmpty() ? getString(R.string.last_event_none) : last);
    }

    private boolean canPostNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        NotificationManager nm = getSystemService(NotificationManager.class);
        return nm != null && nm.areNotificationsEnabled();
    }

    private void refreshGroups() {
        boxGroups.removeAllViews();
        List<String> groups = Prefs.list(sp, Prefs.KEY_RECENT_GROUPS);
        tvGroupsEmpty.setVisibility(groups.isEmpty() ? View.VISIBLE : View.GONE);
        for (String g : groups) {
            boxGroups.addView(chip(g, false, v -> {
                etGroup.setText(g);
                etGroup.setSelection(g.length());
                save();
            }));
        }
    }

    private void refreshSenders() {
        boxSenders.removeAllViews();
        String group = etGroup.getText().toString().trim();
        List<String> senders = group.isEmpty()
                ? Collections.emptyList()
                : Prefs.list(sp, Prefs.sendersKey(group));
        tvSendersEmpty.setVisibility(senders.isEmpty() ? View.VISIBLE : View.GONE);
        List<String> current = Prefs.lines(etPersons.getText().toString());
        for (String s : senders) {
            boolean filtered = SenderMatcher.senderMatches(s, current);
            String label = (filtered ? getString(R.string.chip_filtered_prefix) : getString(R.string.chip_add_prefix)) + s;
            boxSenders.addView(chip(label, filtered, v -> {
                List<String> now = Prefs.lines(etPersons.getText().toString());
                if (SenderMatcher.senderMatches(s, now)) return;
                now.add(s);
                etPersons.setText(String.join("\n", now));
                save();
                Toast.makeText(this, getString(R.string.added_fmt, s), Toast.LENGTH_SHORT).show();
                refreshSenders();
            }));
        }
    }

    private TextView chip(String text, boolean muted, View.OnClickListener onClick) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tv.setTextColor(getColor(muted ? R.color.text2 : R.color.text));
        int padH = dp(14), padV = dp(10);
        tv.setPadding(padH, padV, padH, padV);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(dp(12));
        bg.setColor(getColor(R.color.chip_bg));
        tv.setBackground(bg);
        tv.setOnClickListener(onClick);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        tv.setLayoutParams(lp);
        return tv;
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private void openListenerSettings() {
        ComponentName cn = new ComponentName(this, FilterService.class);
        if (Build.VERSION.SDK_INT >= 30) {
            Intent detail = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                    .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, cn.flattenToString());
            if (tryStart(detail)) return;
        }
        tryStart(new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS));
    }

    private void openAppInfo() {
        tryStart(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)));
    }

    private void askPostPermission() {
        if (Build.VERSION.SDK_INT >= 33 && !sp.getBoolean(Prefs.KEY_ASKED_POST, false)
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            sp.edit().putBoolean(Prefs.KEY_ASKED_POST, true).apply();
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            return;
        }
        tryStart(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        refreshStatus();
    }

    private void openWhatsAppNotificationSettings() {
        if (!tryStart(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, "com.whatsapp"))) {
            Toast.makeText(this, R.string.wa_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void openChannelSettings() {
        FilterService.ensureChannels(this);
        tryStart(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName())
                .putExtra(Settings.EXTRA_CHANNEL_ID, FilterService.CH_ALERT));
    }

    private boolean tryStart(Intent i) {
        try {
            startActivity(i);
            return true;
        } catch (ActivityNotFoundException | SecurityException e) {
            return false;
        }
    }
}
