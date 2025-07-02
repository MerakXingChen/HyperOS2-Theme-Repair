package com.merak;

interface IUserService {
    /**
     * Shizuku服务端定义的销毁方法
     */
    void destroy() = 16777114;

    /**
     * 自定义的退出方法
     */
    void exit() = 1;

    /**
     * 执行命令
     */
    String exec(String command) = 2;

    /**
     * 复制文件
     */
    boolean copyFile(String sourcePath, String targetPath) = 3;
}
