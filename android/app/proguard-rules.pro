# Shizuku constructs this UserService by name in its own app_process runtime.
-keep class com.shrey.androiddex.standalone.system.PrivilegedTaskService { public <init>(...); }
-keep class com.shrey.androiddex.standalone.system.IDesktopTaskBridge { *; }
-keep class com.shrey.androiddex.standalone.system.IDesktopTaskBridge$* { *; }
-keep class com.shrey.androiddex.standalone.system.DesktopTask { *; }
-keepclassmembers class com.shrey.androiddex.standalone.system.DesktopTask {
    public static final android.os.Parcelable$Creator CREATOR;
}
# The official provider artifact supplies its own BinderContainer keep rules.
