package com.shrey.androiddex.standalone.system;
import com.shrey.androiddex.standalone.system.DesktopTask;

interface IDesktopTaskBridge {
    void destroy() = 16777114; // Shizuku's reserved UserService destruction transaction.
    int identity() = 1;
    List<DesktopTask> tasks(int maximum, int displayId) = 2;
    boolean bringTaskToFront(int taskId, int displayId) = 3;
    String readSetting(String key) = 4;
    String writeSetting(String key, String value) = 5;
    String launchFreeform(String component, int displayId) = 6;
    String launchDesktop(String component, int displayId) = 7;
    boolean removeTask(int taskId) = 8;
    void showRecents() = 9;
    // Native freeform window management. All calls require shell UID 2000 and a live task token.
    int[] taskBounds(int taskId) = 10;
    String setTaskWindowingMode(int taskId, int windowingMode) = 11;
    boolean setTaskHidden(int taskId, boolean hidden) = 12;
    String resizeTask(int taskId, int left, int top, int right, int bottom) = 13;
    String launchFreeformBounds(String component, int displayId, int left, int top, int right, int bottom) = 14;
    void back() = 15;
}
