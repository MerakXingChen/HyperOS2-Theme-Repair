package xyz.xxin.adbshellutils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rikka.shizuku.Shizuku;

public class MainActivity extends AppCompatActivity {
    private final static int PERMISSION_CODE = 10001;
    private boolean shizukuServiceState = false;
    
    // 原有控件
    private Button judge_permission;
    private Button request_permission;
    private Button connect_shizuku;
    private Button execute_command;
    private EditText input_command;
    private TextView execute_result;
    private IUserService iUserService;
    
    // 新增主题安装控件
    private Button btnSelectTheme;
    private TextView tvSelectedFile;
    private Button btnMoveTheme;
    private TextView tvMoveStatus;
    private EditText etPassword;
    private Button btnInstall;
    
    // 主题安装相关变量
    private String selectedThemeFile = null;
    private final String correctPassword = "656100875";
    private final String targetThemePath = "/sdcard/Android/data/com.android.thememanager/files/temp.mtz";
    
    // 文件选择器
    private ActivityResultLauncher<Intent> filePickerLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findView();
        setupFilePickerLauncher();
        addEvent();
        initShizuku();
    }

    private void setupFilePickerLauncher() {
        filePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                    Uri uri = result.getData().getData();
                    if (uri != null) {
                        handleSelectedFile(uri);
                    }
                }
            }
        );
    }

    private void initShizuku() {
        // 添加权限申请监听
        Shizuku.addRequestPermissionResultListener(onRequestPermissionResultListener);

        // Shiziku服务启动时调用该监听
        Shizuku.addBinderReceivedListenerSticky(onBinderReceivedListener);

        // Shiziku服务终止时调用该监听
        Shizuku.addBinderDeadListener(onBinderDeadListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // 移除权限申请监听
        Shizuku.removeRequestPermissionResultListener(onRequestPermissionResultListener);

        Shizuku.removeBinderReceivedListener(onBinderReceivedListener);

        Shizuku.removeBinderDeadListener(onBinderDeadListener);

        if (iUserService != null) {
            Shizuku.unbindUserService(userServiceArgs, serviceConnection, true);
        }
    }

    private final Shizuku.OnBinderReceivedListener onBinderReceivedListener = () -> {
        shizukuServiceState = true;
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "Shizuku服务已启动", Toast.LENGTH_SHORT).show();
        });
    };

    private final Shizuku.OnBinderDeadListener onBinderDeadListener = () -> {
        shizukuServiceState = false;
        iUserService = null;
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "Shizuku服务被终止", Toast.LENGTH_SHORT).show();
        });
    };

    private void addEvent() {
        // 原有事件处理（保持不变）
        judge_permission.setOnClickListener(view -> {
            if (!shizukuServiceState) {
                Toast.makeText(this, "Shizuku服务状态异常", Toast.LENGTH_SHORT).show();
                return;
            }

            if (checkPermission()) {
                Toast.makeText(this, "已拥有权限", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "未拥有权限", Toast.LENGTH_SHORT).show();
            }
        });

        request_permission.setOnClickListener(view -> {
            if (!shizukuServiceState) {
                Toast.makeText(this, "Shizuku服务状态异常", Toast.LENGTH_SHORT).show();
                return;
            }

            requestShizukuPermission();
        });

        connect_shizuku.setOnClickListener(view -> {
            if (!shizukuServiceState) {
                Toast.makeText(this, "Shizuku服务状态异常", Toast.LENGTH_SHORT).show();
                return;
            }

            if (!checkPermission()) {
                Toast.makeText(this, "没有Shizuku权限", Toast.LENGTH_SHORT).show();
                return;
            }

            if (iUserService != null) {
                Toast.makeText(MainActivity.this, "已连接Shizuku服务", Toast.LENGTH_SHORT).show();
                return;
            }

            Shizuku.bindUserService(userServiceArgs, serviceConnection);
        });

        execute_command.setOnClickListener(view -> {
            if (iUserService == null) {
                Toast.makeText(this, "请先连接Shizuku服务", Toast.LENGTH_SHORT).show();
                return;
            }

            String command = input_command.getText().toString().trim();

            if (TextUtils.isEmpty(command)) {
                Toast.makeText(this, "命令不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                String result = exec(command);

                if (result == null) {
                    result = "返回结果为null";
                } else if (TextUtils.isEmpty(result.trim())) {
                    result = "返回结果为空";
                }

                execute_result.setText(result);
            } catch (Exception e) {
                execute_result.setText(e.toString());
                e.printStackTrace();
            }
        });

        // 新增主题安装事件处理
        btnSelectTheme.setOnClickListener(view -> openFilePicker());
        
        btnMoveTheme.setOnClickListener(view -> moveThemeFile());
        
        btnInstall.setOnClickListener(view -> installTheme());
        
        etPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                updateInstallButtonState();
            }
        });
    }

    // 新增主题安装相关方法
    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        filePickerLauncher.launch(intent);
    }

    private void handleSelectedFile(Uri uri) {
        try {
            String fileName = getFileName(uri);
            
            if (fileName != null && fileName.toLowerCase().endsWith(".mtz")) {
                String realPath = copyFileToCache(uri, fileName);
                if (realPath != null) {
                    selectedThemeFile = realPath;
                    tvSelectedFile.setText("已选择: " + fileName);
                    updateMoveButtonState();
                } else {
                    Toast.makeText(this, "无法处理文件", Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(this, "请选择 .mtz 格式的主题文件", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "文件选择失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if ("content".equals(uri.getScheme())) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        if (result == null) {
            result = uri.getPath();
            if (result != null) {
                int cut = result.lastIndexOf('/');
                if (cut != -1) {
                    result = result.substring(cut + 1);
                }
            }
        }
        return result;
    }

    private String copyFileToCache(Uri uri, String fileName) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            if (inputStream == null) return null;
            
            File tempFile = new File(getCacheDir(), fileName);
            
            try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                byte[] buffer = new byte[1024];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            inputStream.close();
            return tempFile.getAbsolutePath();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void updateMoveButtonState() {
        boolean hasFile = selectedThemeFile != null;
        boolean canMove = hasFile && iUserService != null;
        btnMoveTheme.setEnabled(canMove);
    }

    private void updateInstallButtonState() {
        boolean hasPassword = etPassword.getText() != null && !etPassword.getText().toString().trim().isEmpty();
        boolean themeExists = new File(targetThemePath).exists();
        btnInstall.setEnabled(hasPassword && themeExists);
    }

    private void moveThemeFile() {
        if (selectedThemeFile == null) {
            Toast.makeText(this, "请先选择主题文件", Toast.LENGTH_SHORT).show();
            return;
        }

        if (iUserService == null) {
            Toast.makeText(this, "Shizuku 服务未连接", Toast.LENGTH_SHORT).show();
            return;
        }

        tvMoveStatus.setText("移动中...");
        btnMoveTheme.setEnabled(false);

        new Thread(() -> {
            try {
                // 创建目标目录
                String mkdirCommand = "mkdir -p /sdcard/Android/data/com.android.thememanager/files/";
                iUserService.execLine(mkdirCommand);

                // 复制文件
                String copyCommand = "cp \"" + selectedThemeFile + "\" \"" + targetThemePath + "\"";
                iUserService.execLine(copyCommand);

                runOnUiThread(() -> {
                    if (new File(targetThemePath).exists()) {
                        tvMoveStatus.setText("✅ 移动成功");
                        Toast.makeText(this, "主题文件移动成功", Toast.LENGTH_SHORT).show();
                        updateInstallButtonState();
                    } else {
                        tvMoveStatus.setText("❌ 移动失败");
                        Toast.makeText(this, "主题文件移动失败", Toast.LENGTH_SHORT).show();
                    }
                    btnMoveTheme.setEnabled(true);
                });
            } catch (Exception e) {
                runOnUiThread(() -> {
                    tvMoveStatus.setText("❌ 移动失败: " + e.getMessage());
                    Toast.makeText(this, "移动失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    btnMoveTheme.setEnabled(true);
                });
            }
        }).start();
    }

    private void installTheme() {
        String password = etPassword.getText().toString().trim();
        
        if (!password.equals(correctPassword)) {
            Toast.makeText(this, "密码错误，无法安装主题", Toast.LENGTH_SHORT).show();
            return;
        }
        
        if (!new File(targetThemePath).exists()) {
            Toast.makeText(this, "主题文件不存在，请先移动主题文件", Toast.LENGTH_SHORT).show();
            return;
        }
        
        try {
            Intent intent = new Intent();
            intent.setClassName(
                "com.android.thememanager",
                "com.android.thememanager.ApplyThemeForScreenshot"
            );
            intent.putExtra("theme_file_path", targetThemePath);
            intent.putExtra("api_called_from", "ThemeEditor");
            intent.putExtra("ver2_step", "ver2_step_apply");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            
            startActivity(intent);
            Toast.makeText(this, "正在启动主题安装...", Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Toast.makeText(this, "启动主题管理器失败: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // 原有方法保持不变
    private String exec(String command) throws RemoteException {
        Pattern pattern = Pattern.compile("\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(command);

        if (matcher.find()) {
            ArrayList<String> list = new ArrayList<>();
            Pattern pattern2 = Pattern.compile("\"([^\"]*)\"|(\\S+)");
            Matcher matcher2 = pattern2.matcher(command);

            while (matcher2.find()) {
                if (matcher2.group(1) != null) {
                    list.add(matcher2.group(1));
                } else {
                    list.add(matcher2.group(2));
                }
            }

            return iUserService.execArr(list.toArray(new String[0]));
        } else {
            return iUserService.execLine(command);
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Toast.makeText(MainActivity.this, "Shizuku服务连接成功", Toast.LENGTH_SHORT).show();

            if (iBinder != null && iBinder.pingBinder()) {
                iUserService = IUserService.Stub.asInterface(iBinder);
                updateMoveButtonState(); // 更新按钮状态
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Toast.makeText(MainActivity.this, "Shizuku服务连接断开", Toast.LENGTH_SHORT).show();
            iUserService = null;
            updateMoveButtonState(); // 更新按钮状态
        }
    };

    private final Shizuku.UserServiceArgs userServiceArgs =
            new Shizuku.UserServiceArgs(new ComponentName(BuildConfig.APPLICATION_ID, UserService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("adb_service")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE);

    private void requestShizukuPermission() {
        if (Shizuku.isPreV11()) {
            Toast.makeText(this, "当前shizuku版本不支持动态申请权限", Toast.LENGTH_SHORT).show();
            return;
        }

        if (checkPermission()) {
            Toast.makeText(this, "已拥有Shizuku权限", Toast.LENGTH_SHORT).show();
            return;
        }

        Shizuku.requestPermission(MainActivity.PERMISSION_CODE);
    }

    private final Shizuku.OnRequestPermissionResultListener onRequestPermissionResultListener = (requestCode, grantResult) -> {
        boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
        if (granted) {
            Toast.makeText(MainActivity.this, "Shizuku授权成功", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(MainActivity.this, "Shizuku授权失败", Toast.LENGTH_SHORT).show();
        }
    };

    private boolean checkPermission() {
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    private void findView() {
        // 原有控件
        judge_permission = findViewById(R.id.judge_permission);
        request_permission = findViewById(R.id.request_permission);
        connect_shizuku = findViewById(R.id.connect_shizuku);
        execute_command = findViewById(R.id.execute_command);
        input_command = findViewById(R.id.input_command);
        execute_result = findViewById(R.id.execute_result);
        
        // 新增主题安装控件
        btnSelectTheme = findViewById(R.id.btnSelectTheme);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        btnMoveTheme = findViewById(R.id.btnMoveTheme);
        tvMoveStatus = findViewById(R.id.tvMoveStatus);
        etPassword = findViewById(R.id.etPassword);
        btnInstall = findViewById(R.id.btnInstall);
    }
}
