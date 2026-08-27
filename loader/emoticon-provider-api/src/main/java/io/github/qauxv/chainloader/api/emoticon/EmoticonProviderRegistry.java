package io.github.qauxv.chainloader.api.emoticon;

import androidx.annotation.NonNull;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 当前 QQ 进程中的外部表情 Provider 注册中心。
 */
public final class EmoticonProviderRegistry {

    public static final int API_VERSION = 1;

    private static final Object LOCK = new Object();
    private static final Map<String, Entry> PROVIDERS = new LinkedHashMap<>();

    private EmoticonProviderRegistry() {
        throw new AssertionError("no instance");
    }

    @NonNull
    public static EmoticonProviderRegistration register(int apiVersion,
            @NonNull IEmoticonProvider provider) {
        if (apiVersion != API_VERSION) {
            throw new IllegalArgumentException("unsupported emoticon provider API version: " + apiVersion);
        }
        IEmoticonProvider checkedProvider = Objects.requireNonNull(provider, "provider");
        String providerId = requireText(checkedProvider.getProviderId(), "providerId");
        requireText(checkedProvider.getDisplayName(), "displayName");
        synchronized (LOCK) {
            Entry existing = PROVIDERS.get(providerId);
            if (existing != null) {
                if (existing.provider == checkedProvider) {
                    return existing.registration;
                }
                throw new IllegalStateException("providerId is already registered: " + providerId);
            }
            EmoticonProviderRegistration registration =
                    new EmoticonProviderRegistration(providerId, checkedProvider);
            PROVIDERS.put(providerId, new Entry(checkedProvider, registration));
            return registration;
        }
    }

    @NonNull
    public static List<IEmoticonProvider> getProviders() {
        synchronized (LOCK) {
            List<IEmoticonProvider> snapshot = new ArrayList<>(PROVIDERS.size());
            for (Entry entry : PROVIDERS.values()) {
                snapshot.add(entry.provider);
            }
            return Collections.unmodifiableList(snapshot);
        }
    }

    static void unregister(String providerId, IEmoticonProvider provider,
            EmoticonProviderRegistration registration) {
        synchronized (LOCK) {
            Entry existing = PROVIDERS.get(providerId);
            if (existing != null && existing.provider == provider
                    && existing.registration == registration) {
                PROVIDERS.remove(providerId);
            }
        }
    }

    @NonNull
    private static String requireText(String value, String field) {
        String checked = Objects.requireNonNull(value, field);
        if (checked.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return checked;
    }

    private static final class Entry {
        private final IEmoticonProvider provider;
        private final EmoticonProviderRegistration registration;

        private Entry(IEmoticonProvider provider, EmoticonProviderRegistration registration) {
            this.provider = provider;
            this.registration = registration;
        }
    }
}
