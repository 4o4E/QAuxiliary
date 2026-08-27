package io.github.qauxv.chainloader.api.emoticon;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;

import android.os.ParcelFileDescriptor;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class EmoticonProviderRegistryTest {

    @Test
    public void sameInstanceRegistrationIsIdempotent() {
        TestProvider provider = new TestProvider("test-idempotent");
        EmoticonProviderRegistration first = EmoticonProviderRegistry.register(1, provider);
        try {
            EmoticonProviderRegistration second = EmoticonProviderRegistry.register(1, provider);
            assertSame(first, second);
            assertEquals(Collections.singletonList(provider), EmoticonProviderRegistry.getProviders());
        } finally {
            first.close();
        }
    }

    @Test
    public void duplicateProviderIdIsRejected() {
        TestProvider firstProvider = new TestProvider("test-duplicate");
        EmoticonProviderRegistration registration = EmoticonProviderRegistry.register(1, firstProvider);
        try {
            assertThrows(IllegalStateException.class, () ->
                    EmoticonProviderRegistry.register(1, new TestProvider("test-duplicate")));
        } finally {
            registration.close();
        }
    }

    @Test
    public void registrationCloseRemovesOnlyCurrentProvider() {
        TestProvider provider = new TestProvider("test-close");
        EmoticonProviderRegistration registration = EmoticonProviderRegistry.register(1, provider);
        registration.close();
        registration.close();
        assertEquals(Collections.emptyList(), EmoticonProviderRegistry.getProviders());
    }

    private static final class TestProvider implements IEmoticonProvider {
        private final String id;

        private TestProvider(String id) {
            this.id = id;
        }

        @Override
        public String getProviderId() {
            return id;
        }

        @Override
        public String getDisplayName() {
            return id;
        }

        @Override
        public long getRevision() {
            return 1;
        }

        @Override
        public List<EmoticonPackInfo> listPacks() {
            return Collections.emptyList();
        }

        @Override
        public List<EmoticonItemInfo> listItems(String packId, int offset, int limit) {
            return Collections.emptyList();
        }

        @Override
        public ParcelFileDescriptor openItem(String packId, String itemId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void recordUse(String packId, String itemId, long usedAtMillis) {
        }
    }
}
