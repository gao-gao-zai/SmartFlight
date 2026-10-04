# Shizuku creates this Binder service reflectively, outside the application process.
# Retain both the pre-v13 default constructor and the v13 Context constructor.
-keep class com.gaozay.smartflight.shizuku.ShizukuCommandService {
    public <init>();
    public <init>(android.content.Context);
}
