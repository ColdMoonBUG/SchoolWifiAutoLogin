package com.example.myapplication; // 确保包名正确

import android.content.Context;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import javax.net.ssl.HttpsURLConnection;

public class MainActivity extends AppCompatActivity {

    private EditText accountEditText;
    private EditText passwordEditText;
    private Spinner iTermTypeSpinner;
    private Button loginButton;
    private TextView loginStatusText;
    private TextView consoleTextView; // 控制台 TextView
    private String localIp;
    private static final String LOGIN_URL_TEMPLATE =
            "http://172.16.1.38:801/eportal/?c=ACSetting&a=Login&loginMethod=1&protocol=http%3A&hostname=172.16.1.38&port=&iTermType={iTermType}&wlanuserip={local_ip}&wlanacip=null&wlanacname=&redirect=null&session=null&vlanid=0&mac=00-00-00-00-00-00&ip={local_ip}&enAdvert=0&jsVersion=2.4.3&DDDDD=%2C0%2C{account}%40cmcc&upass={password}&R1=0&R2=0&R3=0&R6=0&para=00&0MKKey=123456&buttonClicked=&redirect_url=&err_flag=&username=&password=&user=&cmd=&Login=&v6ip=";
    private static final String LOGIN_SUCCESS_TITLE = "认证成功页"; // 认证成功页面的标题 (GB2312 编码)
    private Handler mainThreadHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        accountEditText = findViewById(R.id.accountEditText);
        passwordEditText = findViewById(R.id.passwordEditText);
        iTermTypeSpinner = findViewById(R.id.iTermTypeSpinner);
        loginButton = findViewById(R.id.loginButton);
        loginStatusText = findViewById(R.id.loginStatusText);
        consoleTextView = findViewById(R.id.consoleTextView); // 获取控制台 TextView

        // 设置默认账号和密码
        accountEditText.setText("此处填写你的学号"); // 设置默认账号
        passwordEditText.setText("147258");   // 设置默认密码

        // 设置 iTermType Spinner 的适配器
        ArrayAdapter<Integer> spinnerAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, new Integer[]{1, 2, 3});
        iTermTypeSpinner.setAdapter(spinnerAdapter);
        iTermTypeSpinner.setSelection(1);
        // 初始状态为 "Not Logged In"
        loginStatusText.setText("Not Logged In");
        appendToConsole("App started."); // 添加启动日志到控制台

        // 在后台线程中获取本地 IP 地址
        getLocalIpAddressInBackground();

        loginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loginStatusText.setText("Logging in...");
                appendToConsole("Logging in..."); // 添加登录开始日志到控制台
                String account = accountEditText.getText().toString();
                String password = passwordEditText.getText().toString();
                int selectedITermType = (Integer) iTermTypeSpinner.getSelectedItem();

                performLoginInBackground(account, password, selectedITermType);
            }
        });
    }

    private void getLocalIpAddressInBackground() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 获取 WLAN Manager
                    WifiManager wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
                    if (wifiManager != null && wifiManager.isWifiEnabled()) { // 检查 WiFi 是否启用
                        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
                        int ipAddress = wifiInfo.getIpAddress();

                        // 将 int IP 地址转换为点分十进制字符串格式
                        if (ipAddress != 0) {
                            localIp = String.format("%d.%d.%d.%d",
                                    (ipAddress & 0xff),
                                    (ipAddress >> 8 & 0xff),
                                    (ipAddress >> 16 & 0xff),
                                    (ipAddress >> 24 & 0xff));
                            Log.d("NetworkLoginDebug", "Local IP Address (WLAN): " + localIp); // 输出 WLAN IP
                            appendToConsole("Local IP Address (WLAN): " + localIp); // 添加 IP 获取日志到控制台
                        } else {
                            localIp = null;
                            Log.e("NetworkLogin", "Failed to get WLAN IP address: IP address is 0");
                            appendToConsole("Failed to get WLAN IP address: IP address is 0"); // 添加 IP 获取失败日志到控制台
                        }
                    } else {
                        localIp = null;
                        Log.e("NetworkLogin", "WiFi is not enabled");
                        appendToConsole("WiFi is not enabled"); // 添加 WiFi 未启用日志到控制台
                    }
                } catch (Exception e) {
                    localIp = null;
                    Log.e("NetworkLogin", "获取 WLAN IP 地址失败", e);
                    appendToConsole("Error getting WLAN IP address: " + e.getMessage()); // 添加 IP 异常日志到控制台
                    appendToConsole("Exception details (IP fetch): " + Log.getStackTraceString(e)); // 添加 IP 异常堆栈信息到控制台
                }
            }
        }).start();
    }

    private void performLoginInBackground(final String account, final String password, final int iTermType) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean success = performLogin(account, password, iTermType);
                mainThreadHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        loginStatusText.setText(success ? "Login Successful!" : "Login Failed!");
                        appendToConsole(success ? "Login Successful!" : "Login Failed!"); // 添加登录结果日志到控制台
                    }
                });
            }
        }).start();
    }

    private boolean performLogin(String account, String password, int iTermType) {
        if (localIp == null || localIp.isEmpty()) {
            appendToConsole("Error: Could not get local IP address."); // 添加 IP 地址错误日志到控制台
            Log.e("NetworkLogin", "Error: Could not get local IP address.");
            return false;
        }
        try {
            String loginUrl = LOGIN_URL_TEMPLATE.replace("{iTermType}", String.valueOf(iTermType))
                    .replace("{local_ip}", localIp)
                    .replace("{account}", account)
                    .replace("{password}", password);
            URL url = new URL(loginUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET"); // **显式设置为 GET**
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36"); // **设置 User-Agent**
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; MSIE 9.0; Windows NT 6.1; Trident/5.0)");
            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);

            appendToConsole("Request URL: " + loginUrl); // 添加请求 URL 日志到控制台

            // **添加日志输出 - 连接信息**
            // **添加日志输出 - 连接信息 (更正后)**
            if (connection instanceof HttpsURLConnection) { // 检查是否是 HTTPS 连接
                HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
                Log.d("NetworkLoginDebug", "HostnameVerifier: " + httpsConnection.getHostnameVerifier());
            } else {
                Log.d("NetworkLoginDebug", "Not a HTTPS connection");
                appendToConsole("Not a HTTPS connection"); // 添加连接类型日志到控制台
            }

            Log.d("NetworkLoginDebug", "Login URL: " + loginUrl); // 输出 Logcat 日志
            int responseCode = connection.getResponseCode();
            appendToConsole("Response Code: " + responseCode); // 添加响应码日志到控制台
            Log.d("NetworkLoginDebug", "Response Code: " + responseCode); // 输出 Logcat 日志


            if (responseCode == HttpURLConnection.HTTP_OK) {
                String response = convertStreamToString(connection.getInputStream());
                appendToConsole("Response Body: " + response); // 添加响应体日志到控制台
                Log.d("NetworkLoginDebug", "请求完毕 Response Body: " + response); // 输出 Logcat 日志
                return response.contains("认证成功页"); // 使用中文标题判断登录成功
            } else {
                String errorResponse = convertStreamToString(connection.getErrorStream());
                appendToConsole("Error Response Body: " + errorResponse); // 添加错误响应体日志到控制台
                appendToConsole("Login failed with response code: " + responseCode); // 添加登录失败日志到控制台
                Log.e("NetworkLoginDebug", "Failed Response Body: " + errorResponse); // 输出 Logcat 错误日志
                Log.e("NetworkLogin", "Login failed with response code: " + responseCode); // 输出 Logcat 错误日志
                return false;
            }

        } catch (Exception e) {
            appendToConsole("Network error during login: " + e.getMessage()); // 添加网络错误日志到控制台
            appendToConsole("Exception details: " + Log.getStackTraceString(e)); // 添加异常堆栈信息到控制台
            Log.e("NetworkLoginDebug", "Network error during login", e); // 输出 Logcat 错误日志
            Log.e("NetworkLoginDebug", "Exception details: ", e); // 输出 Logcat 错误日志
            return false;
        }
    }

    java.util.Scanner scanner = new java.util.Scanner(System.in).useDelimiter("\\A");
    private String convertStreamToString(java.io.InputStream is) {
        java.util.Scanner s = new java.util.Scanner(is, "GB2312").useDelimiter("\\A"); // **指定 GB2312 编码**
        return s.hasNext() ? s.next() : "";
    }

    // **新增: appendToConsole 方法，用于在控制台 TextView 中追加信息**
    private void appendToConsole(final String message) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                consoleTextView.append(message + "\n"); // 追加信息和换行符
            }
        });
    }
}