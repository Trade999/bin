package com.accessdroid.termux;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * AccessDroid Settings — onboarding, status, quick actions, VLM config.
 *
 * Cards built around the current card container so all content nests inside
 * the rounded card (previously helpers appended to root and cards rendered
 * empty).  Built programmatically (no XML layouts) for the aapt2-only chain.
 */
public class SettingsActivity extends Activity {

    static final String PREFS = "accessdroid_prefs";
    static final String KEY_VLM_ENDPOINT = "vlm_endpoint";
    static final String KEY_VLM_API_KEY  = "vlm_api_key";
    static final String KEY_VLM_MODEL    = "vlm_model";
    static final String KEY_SECRET       = "am_secret";

    // UI references
    private TextView tvServiceStatus, tvSocketStatus, tvVlmStatus;
    private EditText etEndpoint, etApiKey, etModel, etSecret;
    private LinearLayout root, currentCard;

    // ── Palette ──
    private static final int BG_DARK       = 0xFF0F1117;
    private static final int BG_CARD       = 0xFF1A1D27;
    private static final int BG_INPUT      = 0xFF22263A;
    private static final int ACCENT_CYAN   = 0xFF00D2FF;
    private static final int ACCENT_GREEN  = 0xFF00E676;
    private static final int ACCENT_RED    = 0xFFFF5252;
    private static final int TEXT_PRIMARY  = 0xFFE8EAF6;
    private static final int TEXT_SECONDARY = 0xFF90A4AE;
    private static final int TEXT_DIM      = 0xFF546E7A;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG_DARK);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(16), dp(20), dp(24));
        root.setBackgroundColor(BG_DARK);

        // ── header ──
        TextView tvVersion = new TextView(this);
        tvVersion.setText(getVersionLabel());
        tvVersion.setTextColor(TEXT_DIM);
        tvVersion.setTextSize(11);
        tvVersion.setGravity(Gravity.END);
        root.addView(tvVersion);

        TextView title = new TextView(this);
        title.setText("AccessDroid");
        title.setTextColor(ACCENT_CYAN);
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, dp(4), 0, dp(4));
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Direct App Control from Termux");
        subtitle.setTextColor(TEXT_SECONDARY);
        subtitle.setTextSize(13);
        subtitle.setPadding(0, 0, 0, dp(16));
        root.addView(subtitle);

        // ── STATUS card ──
        buildCard("STATUS");
        tvServiceStatus = statusRow("Accessibility Service");
        tvSocketStatus  = statusRow("Termux AM Socket");
        tvVlmStatus     = statusRow("VLM Provider");
        endCard();

        // ── QUICK ACTIONS card ──
        buildCard("QUICK ACTIONS");
        actionButton("Open Accessibility Settings", 0xFF1B5E20, new View.OnClickListener() {
            public void onClick(View v) { openAccessibilitySettings(); }
        });
        actionButton("Enable Termux AM Socket", 0xFF0D47A1, new View.OnClickListener() {
            public void onClick(View v) { enableTermuxSocket(); }
        });
        actionButton("Open VLM Agent", 0xFF4A148C, new View.OnClickListener() {
            public void onClick(View v) {
                try { startActivity(new Intent(SettingsActivity.this, VlmAgentActivity.class)); }
                catch (Exception e) { Toast.makeText(SettingsActivity.this, "VLM Agent not available", Toast.LENGTH_SHORT).show(); }
            }
        });
        endCard();

        // ── VLM CONFIG card ──
        buildCard("VLM PROVIDER");
        etEndpoint = labeledInput("API Endpoint", "https://api.openai.com/v1/chat/completions");
        etApiKey   = labeledInput("API Key", "sk-...");
        etModel    = labeledInput("Vision Model", "gpt-4o");
        etSecret   = labeledInput("AccessDroid Secret", "termux-accessdroid-2025");
        actionButton("Save Configuration", 0xFF00838F, new View.OnClickListener() {
            public void onClick(View v) { saveConfig(); }
        });
        endCard();

        // ── footer ──
        TextView footer = new TextView(this);
        footer.setText("github.com/yashas-13/accessdroid-termux");
        footer.setTextColor(TEXT_DIM);
        footer.setTextSize(11);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0, dp(20), 0, dp(8));
        root.addView(footer);

        scroll.addView(root);
        setContentView(scroll);
        refreshStatus();
    }

    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
        loadSavedConfig();
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         STATUS CHECKS
    // ═══════════════════════════════════════════════════════════════════

    private void refreshStatus() {
        setIndicator(tvServiceStatus, isAccessibilityEnabled());
        setIndicator(tvSocketStatus, isTermuxSocketEnabled());
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        boolean vlmOk = prefs.getString(KEY_VLM_ENDPOINT, "").length() > 5
                     && prefs.getString(KEY_VLM_API_KEY, "").length() > 5;
        setIndicator(tvVlmStatus, vlmOk);
    }

    private void setIndicator(TextView tv, boolean ok) {
        tv.setText(ok ? "● ENABLED" : "● DISABLED");
        tv.setTextColor(ok ? ACCENT_GREEN : ACCENT_RED);
    }

    private boolean isAccessibilityEnabled() {
        String svc = getApplicationContext().getPackageName() + "/.AccessibilityServiceImpl";
        AccessibilityManager am = (AccessibilityManager) getSystemService(ACCESSIBILITY_SERVICE);
        List<AccessibilityServiceInfo> list = am.getEnabledAccessibilityServiceList(
                AccessibilityServiceInfo.FEEDBACK_GENERIC);
        for (AccessibilityServiceInfo info : list) {
            if (info.getId().equals(svc)) return true;
        }
        try {
            String raw = Settings.Secure.getString(getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return raw != null && raw.contains("accessdroid");
        } catch (Exception e) { return false; }
    }

    private boolean isTermuxSocketEnabled() {
        try {
            Process p = Runtime.getRuntime().exec(
                    new String[]{"sh", "-c",
                        "cat $HOME/.termux/termux.properties 2>/dev/null | grep -q run-termux-am-socket-server=true"});
            p.waitFor();
            return p.exitValue() == 0;
        } catch (Exception e) { return false; }
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         QUICK ACTIONS
    // ═══════════════════════════════════════════════════════════════════

    private void openAccessibilitySettings() {
        Toast.makeText(this, "Enable AccessDroid in Accessibility settings", Toast.LENGTH_LONG).show();
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            Toast.makeText(this, "Could not open Accessibility settings", Toast.LENGTH_SHORT).show();
        }
    }

    private void enableTermuxSocket() {
        try {
            String cmd = "sed -i 's/run-termux-am-socket-server=false/run-termux-am-socket-server=true/' "
                + "$HOME/.termux/termux.properties 2>/dev/null; "
                + "grep -q run-termux-am-socket-server=true $HOME/.termux/termux.properties "
                + "|| echo 'run-termux-am-socket-server=true' >> $HOME/.termux/termux.properties; "
                + "termux-reload-settings 2>/dev/null";
            Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd}).waitFor();
            Toast.makeText(this, "AM socket enabled. Restart Termux to apply.", Toast.LENGTH_LONG).show();
            refreshStatus();
        } catch (Exception e) {
            Toast.makeText(this, "Could not enable socket", Toast.LENGTH_SHORT).show();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //                         SAVE / LOAD CONFIG
    // ═══════════════════════════════════════════════════════════════════

    private void saveConfig() {
        SharedPreferences.Editor ed = getSharedPreferences(PREFS, MODE_PRIVATE).edit();
        ed.putString(KEY_VLM_ENDPOINT, etEndpoint.getText().toString().trim());
        ed.putString(KEY_VLM_API_KEY,  etApiKey.getText().toString().trim());
        ed.putString(KEY_VLM_MODEL,    etModel.getText().toString().trim());
        ed.putString(KEY_SECRET,       etSecret.getText().toString().trim());
        ed.apply();
        Toast.makeText(this, "Configuration saved", Toast.LENGTH_SHORT).show();
        refreshStatus();
    }

    private void loadSavedConfig() {
        SharedPreferences prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        etEndpoint.setText(prefs.getString(KEY_VLM_ENDPOINT, etEndpoint.getText().toString()));
        etApiKey.setText(prefs.getString(KEY_VLM_API_KEY, ""));
        etModel.setText(prefs.getString(KEY_VLM_MODEL, etModel.getText().toString()));
        etSecret.setText(prefs.getString(KEY_SECRET, etSecret.getText().toString()));
    }

    // ═══════════════════════════════════════════════════════════════════
    //                     UI BUILDER HELPERS (card-aware)
    // ═══════════════════════════════════════════════════════════════════

    private void buildCard(String title) {
        currentCard = new LinearLayout(this);
        currentCard.setOrientation(LinearLayout.VERTICAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BG_CARD);
        bg.setCornerRadius(dp(12));
        currentCard.setBackground(bg);
        currentCard.setPadding(dp(16), dp(14), dp(16), dp(12));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        currentCard.setLayoutParams(lp);

        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextColor(ACCENT_CYAN);
        tv.setTextSize(12);
        tv.setTypeface(Typeface.DEFAULT_BOLD);
        tv.setLetterSpacing(0.08f);
        tv.setPadding(0, 0, 0, dp(8));
        currentCard.addView(tv);

        root.addView(currentCard);
    }

    private void endCard() { currentCard = null; }

    private LinearLayout destination() {
        return currentCard != null ? currentCard : root;
    }

    private TextView statusRow(String label) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(TEXT_SECONDARY);
        lbl.setTextSize(13);
        lbl.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(lbl);

        TextView val = new TextView(this);
        val.setTextSize(12);
        val.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(val);

        destination().addView(row);
        return val;
    }

    private void actionButton(String text, int bgColor, View.OnClickListener onClick) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setTextColor(TEXT_PRIMARY);
        btn.setTextSize(13);
        btn.setTypeface(Typeface.DEFAULT_BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setOnClickListener(onClick);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(bgColor);
        bg.setCornerRadius(dp(8));
        btn.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        btn.setLayoutParams(lp);
        destination().addView(btn);
    }

    private EditText labeledInput(String label, String hint) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));

        TextView lbl = new TextView(this);
        lbl.setText(label);
        lbl.setTextColor(TEXT_SECONDARY);
        lbl.setTextSize(11);
        row.addView(lbl);

        EditText et = new EditText(this);
        et.setHint(hint);
        et.setTextColor(TEXT_PRIMARY);
        et.setHintTextColor(TEXT_DIM);
        et.setTextSize(13);
        et.setSingleLine(true);
        et.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_NORMAL);

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(BG_INPUT);
        bg.setCornerRadius(dp(8));
        et.setBackground(bg);
        et.setPadding(dp(12), dp(10), dp(12), dp(10));

        row.addView(et);
        destination().addView(row);
        return et;
    }

    private String getVersionLabel() {
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), 0);
            return "v" + pi.versionName + "  (" + pi.versionCode + ")";
        } catch (Exception e) { return ""; }
    }

    private int dp(int px) {
        return (int)(px * getResources().getDisplayMetrics().density + 0.5f);
    }
}