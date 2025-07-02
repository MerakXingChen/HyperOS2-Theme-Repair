package com.merak;

interface IThemeService {
    void destroy();
    void exit();
    String exec(String command);
    boolean copyFile(String sourcePath, String targetPath);
}
