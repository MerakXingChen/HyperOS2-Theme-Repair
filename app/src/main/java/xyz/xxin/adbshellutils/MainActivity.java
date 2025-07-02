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
import androidx.core.content.ContextCompat;

import com.google.android.material.textfield.TextInputEditText;

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
    
    // 主题安装器控件
    private TextView tvShizukuStatus;
    private Button btnSelectTheme;
    private TextView tvSelectedFile;
    private Button btnMoveTheme;
    private TextView tvMoveStatus;
    private TextInputEditText etPassword;
    private Button btnInstall;
    
    // 主题安装器相关变量
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
        updateUIState();
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
            updateUIState();
        });
    };

    private final Shizuku.OnBinderDeadListener onBinderDeadListener = () -> {
        shizukuServiceState = false;
        iUserService = null;
        runOnUiThread(() -> {
            Toast.makeText(MainActivity.this, "Shizuku服务被终止", Toast.LENGTH_SHORT).show();
            updateUIState();
        });
    };

    private void addEvent() {
        // 原有事件处理
        setupOriginalEvents();
        
        // 主题安装器事件处理
        setupThemeEvents();
    }
    
    private void setupOriginalEvents() {
        // 判断权限
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

        // 动态申请权限
        request_permission.setOnClickListener(view -> {
            if (!shizukuServiceState) {
                Toast.makeText(this, "Shizuku服务状态异常", Toast.LENGTH_SHORT).show();
                return;
            }

            requestShizukuPermission();
        });

        // 连接Shizuku服务
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

            // 绑定shizuku服务
            Shizuku.bindUserService(userServiceArgs, serviceConnection);
        });

        // 执行命令
        execute_command.setOnClickListener(view -> {
            if (iUserService == null) {
                Toast.makeText(this, "请先连接Shizuku服务", Toast.LENGTH_SHORT).show();
                return;
            }

            String command = input_command.getText().toString().trim();

            // 命令不能为空
            if (TextUtils.isEmpty(command)) {
                Toast.makeText(this, "命令不能为空", Toast.LENGTH_SHORT).show();
                return;
            }

            try {
                // 执行命令，返回执行结果
                String result = exec(command);

                if (result == null) {
                    result = "返回结果为null";
                } else if (TextUtils.isEmpty(result.trim())) {
                    result = "返回结果为空";
                }

                // 将执行结果显示
                execute_result.setText(result);
            } catch (Exception e) {
                execute_result.setText(e.toString());
                e.printStackTrace();
            }
        });
    }
    
    private void setupThemeEvents() {
        // 选择主题文件
        btnSelectTheme.setOnClickListener(view -> openFilePicker());
        
        // 移动主题文件
        btnMoveTheme.setOnClickListener(view -> moveThemeFile());
        
        // 安装主题
        btnInstall.setOnClickListener(view -> installTheme());
        
        // 密码输入监听
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

    private void updateUIState() {
        boolean hasShizukuPermission = checkPermission();
        boolean shizukuRunning = Shizuku.pingBinder();
        boolean serviceConnected = iUserService != null;
        
        // 更新 Shizuku 状态显示
        if (!shizukuRunning) {
            tvShizukuStatus.setText("❌ Shizuku 服务未运行");
            tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
        } else if (!hasShizukuPermission) {
            tvShizukuStatus.setText("⚠️ Shizuku 权限未授予");
            tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_orange_dark));
        } else if (!serviceConnected) {
            tvShizukuStatus.setText("🔄 Shizuku 服务未连接");
            tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_blue_dark));
        } else {
            tvShizukuStatus.setText("✅ Shizuku 服务已就绪");
            tvShizukuStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
        }

        // 更新按钮状态
        updateMoveButtonState();
        updateInstallButtonState();
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

    private void openFilePicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"*/*"});
        filePickerLauncher.launch(intent);
    }

    private void handleSelectedFile(Uri uri) {
        try {
            String fileName = getFileName(uri);
            
            if (fileName != null && fileName.toLowerCase().endsWith(".mtz")) {
                String realPath = getRealPathFromUri(uri);
                if (realPath != null) {
                    selectedThemeFile = realPath;
                    tvSelectedFile.setText("已选择: " + fileName);
                    updateMoveButtonState();
                } else {
                    showToast("无法获取文件路径");
                }
            } else {
                showToast("请选择 .mtz 格式的主题文件");
            }
        } catch (Exception e) {
            showToast("文件选择失败: " + e.getMessage());
        }
    }

    private String getFileName(Uri uri) {
        String result = null;
        if (uri.getScheme().equals("content")) {
            try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex);
                    }
                }
            }
        }
        if (result == null) {
            result = uri.getPath();
            int cut = result.lastIndexOf('/');
            if (cut != -1) {
                result = result.substring(cut + 1);
            }
        }
        return result;
    }

    private String getRealPathFromUri(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);
            String fileName = getFileName(uri);
            if (fileName == null) fileName = "temp.mtz";
            
            File tempFile = new File(getCacheDir(), fileName);
            
            if (inputStream != null) {
                try (FileOutputStream outputStream = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[1024];
                    int bytesRead;
                    while ((bytesRead = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, bytesRead);
                    }
                }
                inputStream.close();
                return tempFile.getAbsolutePath();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private void moveThemeFile() {
        if (selectedThemeFile == null || selectedThemeFile.isEmpty()) {
            showToast("请先选择主题文件");
            return;
        }

        if (iUserService == null) {
            showToast("Shizuku 服务未连接");
            return;
        }

        try {
            tvMoveStatus.setText("移动中...");
            btnMoveTheme.setEnabled(false);

            // 使用后台线程执行文件移动
            new Thread(() -> {
                try {
                    // 创建目标目录
                    String mkdirCommand = "mkdir -p /sdcard/Android/data/com.android.thememanager/files/";
                    iUserService.execLine(mkdirCommand);

                    // 复制文件
                    String copyCommand = "cp \"" + selectedThemeFile + "\" \"" + targetThemePath + "\"";
                    String result = iUserService.execLine(copyCommand);

                    runOnUiThread(() -> {
                        if (new File(targetThemePath).exists()) {
                            tvMoveStatus.setText("✅ 移动成功");
                            tvMoveStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark));
                            showToast("主题文件移动成功");
                            updateInstallButtonState();
                        } else {
                            tvMoveStatus.setText("❌ 移动失败");
                            tvMoveStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                            showToast("主题文件移动失败");
                        }
                        btnMoveTheme.setEnabled(true);
                    });
                } catch (RemoteException e) {
                    runOnUiThread(() -> {
                        tvMoveStatus.setText("❌ 移动失败: " + e.getMessage());
                        tvMoveStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
                        showToast("移动文件时发生错误: " + e.getMessage());
                        btnMoveTheme.setEnabled(true);
                    });
                }
            }).start();

        } catch (Exception e) {
            tvMoveStatus.setText("❌ 移动失败: " + e.getMessage());
            tvMoveStatus.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark));
            showToast("移动文件失败: " + e.getMessage());
            btnMoveTheme.setEnabled(true);
        }
    }

    private void installTheme() {
        String password = etPassword.getText().toString().trim();
        
        // 验证密码
        if (!password.equals(correctPassword)) {
            showToast("密码错误，无法安装主题");
            return;
        }
        
        // 检查主题文件是否存在
        if (!new File(targetThemePath).exists()) {
            showToast("主题文件不存在，请先移动主题文件");
            return;
        }
        
        try {
            // 创建 Intent 调用小米主题管理器
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
            showToast("正在启动主题安装...");
            
        } catch (Exception e) {
            showToast("启动主题管理器失败: " + e.getMessage());
        }
    }

    private String exec(String command) throws RemoteException {
        // 检查是否存在包含任意内容的双引号
        Pattern pattern = Pattern.compile("\"([^\"]*)\"");
        Matcher matcher = pattern.matcher(command);

        // 下面展示了两种不同的命令执行方法
        if (matcher.find()) {
            ArrayList<String> list = new ArrayList<>();
            Pattern pattern2 = Pattern.compile("\"([^\"]*)\"|(\\S+)");
            Matcher matcher2 = pattern2.matcher(command);

            while (matcher2.find()) {
                if (matcher2.group(1) != null) {
                    // 如果是引号包裹的内容，取group(1)
                    list.add(matcher2.group(1));
                } else {
                    // 否则取group(2)，即普通的单词
                    list.add(matcher2.group(2));
                }
            }

            // 这种方法可用于执行路径中带空格的命令，例如 ls /storage/0/emulated/temp dir/
            // 当然也可以执行不带空格的命令，实际上是要强于另一种执行方式的
            return iUserService.execArr(list.toArray(new String[0]));
        } else {
            // 这种方法仅用于执行路径中不包含空格的命令，例如 ls /storage/0/emulated/
            return iUserService.execLine(command);
        }
    }

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Toast.makeText(MainActivity.this, "Shizuku服务连接成功", Toast.LENGTH_SHORT).show();

            if (iBinder != null && iBinder.pingBinder()) {
                iUserService = IUserService.Stub.asInterface(iBinder);
                updateUIState();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Toast.makeText(MainActivity.this, "Shizuku服务连接断开", Toast.LENGTH_SHORT).show();
            iUserService = null;
            updateUIState();
        }
    };

    private final Shizuku.UserServiceArgs userServiceArgs =
            new Shizuku.UserServiceArgs(new ComponentName(BuildConfig.APPLICATION_ID, UserService.class.getName()))
                    .daemon(false)
                    .processNameSuffix("adb_service")
                    .debuggable(BuildConfig.DEBUG)
                    .version(BuildConfig.VERSION_CODE);


    /**
     * 动态申请Shizuku adb shell权限
     */
    private void requestShizukuPermission() {
        if (Shizuku.isPreV11()) {
            Toast.makeText(this, "当前shizuku版本不支持动态申请权限", Toast.LENGTH_SHORT).show();
            return;
        }

        if (checkPermission()) {
            Toast.makeText(this, "已拥有Shizuku权限", Toast.LENGTH_SHORT).show();
            return;
        }

        // 动态申请权限
        Shizuku.requestPermission(MainActivity.PERMISSION_CODE);
    }

    private final Shizuku.OnRequestPermissionResultListener onRequestPermissionResultListener = new Shizuku.OnRequestPermissionResultListener() {
        @Override
        public void onRequestPermissionResult(int requestCode, int grantResult) {
            boolean granted = grantResult == PackageManager.PERMISSION_GRANTED;
            if (granted) {
                Toast.makeText(MainActivity.this, "Shizuku授权成功", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(MainActivity.this, "Shizuku授权失败", Toast.LENGTH_SHORT).show();
            }
            updateUIState();
        }
    };

    /**
     * 判断是否拥有shizuku adb shell权限
     */
    private boolean checkPermission() {
        return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED;
    }

    private void showToast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private void findView() {
        // 原有控件
        judge_permission = findViewById(R.id.judge_permission);
        request_permission = findViewById(R.id.request_permission);
        connect_shizuku = findViewById(R.id.connect_shizuku);
        execute_command = findViewById(R.id.execute_command);
        input_command = findViewById(R.id.input_command);
        execute_result = findViewById(R.id.execute_result);
        
        // 主题安装器控件
        tvShizukuStatus = findViewById(R.id.tvShizukuStatus);
        btnSelectTheme = findViewById(R.id.btnSelectTheme);
        tvSelectedFile = findViewById(R.id.tvSelectedFile);
        btnMoveTheme = findViewById(R.id.btnMoveTheme);
        tvMoveStatus = findViewById(R.id.tvMoveStatus);
        etPassword = findViewById(R.id.etPassword);
        btnInstall = findViewById(R.id.btnInstall);
    }
}
