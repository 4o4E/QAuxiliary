package io.github.qauxv.chainloader.api.emoticon;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 单个 Provider 的幂等注销句柄。
 */
public final class EmoticonProviderRegistration implements AutoCloseable {

    private final String providerId;
    private final IEmoticonProvider provider;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    EmoticonProviderRegistration(String providerId, IEmoticonProvider provider) {
        this.providerId = providerId;
        this.provider = provider;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            EmoticonProviderRegistry.unregister(providerId, provider, this);
        }
    }
}
