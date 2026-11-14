package com.shrey.androiddex.standalone.system;

import android.os.Parcel;
import android.os.Parcelable;

/** Stable, minimal IPC model; no intents, thumbnails, titles or user content cross this interface. */
public final class DesktopTask implements Parcelable {
    public final int taskId;
    public final int displayId;
    public final String packageName;
    public final String component;
    public final boolean running;
    public final boolean visible;
    public final boolean focused;
    public final boolean resizable;
    public final int windowingMode;

    public DesktopTask(int taskId, int displayId, String packageName, String component,
                       boolean running, boolean visible, boolean focused, boolean resizable, int windowingMode) {
        this.taskId = taskId;
        this.displayId = displayId;
        this.packageName = packageName == null ? "" : packageName;
        this.component = component == null ? "" : component;
        this.running = running;
        this.visible = visible;
        this.focused = focused;
        this.resizable = resizable;
        this.windowingMode = windowingMode;
    }

    private DesktopTask(Parcel parcel) {
        this(parcel.readInt(), parcel.readInt(), parcel.readString(), parcel.readString(),
                parcel.readInt() != 0, parcel.readInt() != 0, parcel.readInt() != 0,
                parcel.readInt() != 0, parcel.readInt());
    }

    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(taskId);
        dest.writeInt(displayId);
        dest.writeString(packageName);
        dest.writeString(component);
        dest.writeInt(running ? 1 : 0);
        dest.writeInt(visible ? 1 : 0);
        dest.writeInt(focused ? 1 : 0);
        dest.writeInt(resizable ? 1 : 0);
        dest.writeInt(windowingMode);
    }

    public static final Creator<DesktopTask> CREATOR = new Creator<DesktopTask>() {
        @Override public DesktopTask createFromParcel(Parcel source) { return new DesktopTask(source); }
        @Override public DesktopTask[] newArray(int size) { return new DesktopTask[size]; }
    };
}
