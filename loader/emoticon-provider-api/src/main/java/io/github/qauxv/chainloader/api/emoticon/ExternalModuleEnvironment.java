package io.github.qauxv.chainloader.api.emoticon;

import android.app.Application;
import androidx.annotation.NonNull;
import java.util.Objects;

/**
 * QAux 为外部 Provider 模块提供的宿主进程环境。
 */
public final class ExternalModuleEnvironment {

    private static volatile Application hostApplication;
    private static volatile String processName;

    private ExternalModuleEnvironment() {
        throw new AssertionError("no instance");
    }

    public static synchronized void initialize(@NonNull Application application,
            @NonNull String currentProcessName) {
        Application checkedApplication = Objects.requireNonNull(application, "application");
        String checkedProcessName = Objects.requireNonNull(currentProcessName, "currentProcessName");
        if (hostApplication != null && hostApplication != checkedApplication) {
            throw new IllegalStateException("external module environment is already initialized");
        }
        hostApplication = checkedApplication;
        processName = checkedProcessName;
    }

    @NonNull
    public static Application getHostApplication() {
        Application result = hostApplication;
        if (result == null) {
            throw new IllegalStateException("external module environment is not initialized");
        }
        return result;
    }

    @NonNull
    public static String getProcessName() {
        String result = processName;
        if (result == null) {
            throw new IllegalStateException("external module environment is not initialized");
        }
        return result;
    }
}
