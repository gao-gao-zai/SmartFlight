package com.gaozay.smartflight.shizuku;

interface IShizukuCommandService {
    String runCommand(String command);
    void destroy();
}
