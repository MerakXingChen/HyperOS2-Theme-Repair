package com.merak;

interface IThemeService {
    void destroy() = 16777114;
    void exit() = 1;
    String exec(String command) = 2;
    boolean copyFile(String sourcePath, String targetPath) = 3;
}
