package com.eoeshop.ziweibotmerchant;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private LinearLayout createCard;
    private LinearLayout verifyCard;
    private ProgressBar progress;
    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        createCard = findViewById(R.id.create_card);
        verifyCard = findViewById(R.id.verify_card);
        progress = findViewById(R.id.progress);
        status = findViewById(R.id.status);

        createCard.setOnClickListener(view -> request("/ziweibot/api/account/create", null, false));
        findViewById(R.id.create_h5_game_key_card).setOnClickListener(view -> showResult(
                "创建H5游戏KEY",
                "尊敬的用户您好：您购买的情侣互动小游戏学习工具资源包 KEY 已创建成功：SN-20261111, " +
                        "请尽快在7天内进行登录使用，多谢！\n网址：https://www.eoeshop.com/h5/001/index.html"));
        findViewById(R.id.create_novel_key_card).setOnClickListener(view -> showResult(
                "创建小说KEY",
                "尊敬的用户您好：您购买的情侣互动小游戏学习工具资源包 KEY 已创建成功：1234 , " +
                        "通过网盘分享的技能文件：novel-control-station 链接: " +
                        "https://pan.baidu.com/s/14lIqk-gjhRZkuhzMvbbdcg?pwd=1234 " +
                        "--来自百度网盘超级会员v1的分享"));
        verifyCard.setOnClickListener(view -> showVerifyDialog());
        findViewById(R.id.visit_card).setOnClickListener(view -> openWebsite());
    }

    private void showVerifyDialog() {
        EditText keyInput = new EditText(this);
        keyInput.setSingleLine(true);
        keyInput.setHint("请输入 KEY");
        int padding = (int) (24 * getResources().getDisplayMetrics().density);
        LinearLayout container = new LinearLayout(this);
        container.setPadding(padding, 0, padding, 0);
        container.addView(keyInput, new LinearLayout.LayoutParams(-1, -2));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("验证 KEY")
                .setView(container)
                .setNegativeButton("取消", null)
                .setPositiveButton("验证", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(view -> {
            String key = keyInput.getText().toString().trim();
            if (key.isEmpty()) {
                keyInput.setError("请输入 KEY");
                return;
            }
            dialog.dismiss();
            try {
                request("/ziweibot/api/account/verify", new JSONObject().put("key", key).toString(), true);
            } catch (JSONException error) {
                showResult("验证失败", error.getMessage());
            }
        }));
        dialog.show();
    }

    private void openWebsite() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse(getString(R.string.api_base_url) + "/ziweibot")));
        } catch (ActivityNotFoundException error) {
            showResult("无法打开网址", "请安装浏览器后重试。");
        }
    }

    private void request(String path, String jsonBody, boolean verify) {
        setLoading(true, verify ? "正在验证 KEY…" : "正在创建 KEY…");
        executor.execute(() -> {
            try {
                String response = post(path, jsonBody);
                String message = response.trim();
                String title;
                if (verify) {
                    JSONObject result = new JSONObject(response);
                    if (!result.has("valid") || result.isNull("valid")) {
                        throw new JSONException("服务器未返回验证结果");
                    }
                    title = result.getBoolean("valid") ? "验证结果：可以登录" : "验证结果：无法登录";
                } else {
                    if (message.isEmpty()) throw new IllegalStateException("服务器返回了空内容");
                    title = "创建成功";
                }
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    setLoading(false, "");
                    showResult(title, message);
                });
            } catch (Exception error) {
                runOnUiThread(() -> {
                    if (isDestroyed()) return;
                    setLoading(false, error.getMessage() == null ? "请求失败，请重试。" : error.getMessage());
                });
            }
        });
    }

    private String post(String path, String jsonBody) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                getString(R.string.api_base_url) + path).openConnection();
        try {
            connection.setRequestMethod("POST");
            connection.setRequestProperty("API_KEY", getString(R.string.api_key));
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            if (jsonBody != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(jsonBody.getBytes(StandardCharsets.UTF_8));
                }
            }
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String body = stream == null ? "" : readAll(stream);
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("请求失败 (HTTP " + code + ")" +
                        (body.isEmpty() ? "" : "：" + body));
            }
            return body;
        } finally {
            connection.disconnect();
        }
    }

    private String readAll(InputStream stream) throws Exception {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString("UTF-8");
        }
    }

    private void setLoading(boolean loading, String message) {
        createCard.setEnabled(!loading);
        verifyCard.setEnabled(!loading);
        createCard.setAlpha(loading ? 0.5f : 1f);
        verifyCard.setAlpha(loading ? 0.5f : 1f);
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
        status.setText(message);
    }

    private void showResult(String title, String message) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(title).setMessage(message)
                .setNeutralButton("复制", null)
                .setPositiveButton("确定", null).show();
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(view -> copyResult(message));
        TextView resultText = dialog.findViewById(android.R.id.message);
        if (resultText != null) {
            long[] lastTap = {0};
            resultText.setOnClickListener(view -> {
                long now = SystemClock.uptimeMillis();
                if (now - lastTap[0] < 400) {
                    copyResult(message);
                    lastTap[0] = 0;
                } else {
                    lastTap[0] = now;
                }
            });
        }
    }

    private void copyResult(String message) {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("紫微BOT返回结果", message));
        Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
