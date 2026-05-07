package com.gaozay.smartflight.shizuku;

interface IShizukuCommandService {
    String runReadonlyCommand(String command);
    void destroy();
}
