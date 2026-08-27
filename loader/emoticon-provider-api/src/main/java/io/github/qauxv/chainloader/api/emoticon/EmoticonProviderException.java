package io.github.qauxv.chainloader.api.emoticon;

import androidx.annotation.NonNull;
import java.util.Objects;

/**
 * Provider 可以安全展示给用户的可预期错误。
 */
public class EmoticonProviderException extends Exception {

    public static final String CODE_INVALID_ARGUMENT = "invalid_argument";
    public static final String CODE_UNAVAILABLE = "unavailable";
    public static final String CODE_IO_ERROR = "io_error";
    public static final String CODE_SECURITY = "security";

    private final String code;

    public EmoticonProviderException(@NonNull String code, @NonNull String message) {
        super(Objects.requireNonNull(message, "message"));
        this.code = requireCode(code);
    }

    public EmoticonProviderException(@NonNull String code, @NonNull String message, Throwable cause) {
        super(Objects.requireNonNull(message, "message"), cause);
        this.code = requireCode(code);
    }

    @NonNull
    public String getCode() {
        return code;
    }

    @NonNull
    private static String requireCode(@NonNull String value) {
        String checked = Objects.requireNonNull(value, "code");
        if (checked.trim().isEmpty()) {
            throw new IllegalArgumentException("code must not be blank");
        }
        return checked;
    }
}
