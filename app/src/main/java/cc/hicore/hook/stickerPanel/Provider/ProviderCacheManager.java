package cc.hicore.hook.stickerPanel.Provider;

import android.os.ParcelFileDescriptor;
import androidx.annotation.NonNull;
import cc.ioctl.util.HostInfo;
import io.github.qauxv.chainloader.api.emoticon.EmoticonItemInfo;
import io.github.qauxv.chainloader.api.emoticon.EmoticonProviderException;
import io.github.qauxv.chainloader.api.emoticon.IEmoticonProvider;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 把 Provider 返回的原始文件缓存到 QQ 私有缓存，保留 GIF 等动画的完整字节。
 */
public final class ProviderCacheManager {

    private static final long MAX_CACHE_BYTES = 128L * 1024L * 1024L;
    private static final ConcurrentHashMap<String, Object> FILE_LOCKS = new ConcurrentHashMap<>();

    private ProviderCacheManager() {
        throw new AssertionError("no instance");
    }

    @NonNull
    public static File getOrCreate(@NonNull IEmoticonProvider provider, @NonNull String packId,
            @NonNull EmoticonItemInfo item) throws EmoticonProviderException {
        return getOrCreate(provider, packId, item.getId(), item.getFileName());
    }

    @NonNull
    public static File getOrCreate(@NonNull IEmoticonProvider provider, @NonNull String packId,
            @NonNull String itemId, @NonNull String fileName) throws EmoticonProviderException {
        long revision = provider.getRevision();
        String key = provider.getProviderId() + '\n' + packId + '\n' + itemId + '\n' + revision;
        File directory = new File(HostInfo.getApplication().getCacheDir(), "qauxv/emoticon_provider");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new EmoticonProviderException(EmoticonProviderException.CODE_IO_ERROR,
                    "无法创建 QAux 外部表情缓存");
        }
        File target = new File(directory, sha256(key) + extensionOf(fileName));
        Object lock = FILE_LOCKS.computeIfAbsent(target.getName(), ignored -> new Object());
        try {
            synchronized (lock) {
                if (target.isFile() && target.length() > 0) {
                    touch(target);
                    return target;
                }
                copyProviderFile(provider, packId, itemId, target);
                trim(directory, target);
                return target;
            }
        } finally {
            FILE_LOCKS.remove(target.getName(), lock);
        }
    }

    private static void copyProviderFile(IEmoticonProvider provider, String packId, String itemId,
            File target) throws EmoticonProviderException {
        File temporary = new File(target.getParentFile(), target.getName() + ".part");
        if (temporary.exists() && !temporary.delete()) {
            throw new EmoticonProviderException(EmoticonProviderException.CODE_IO_ERROR,
                    "无法清理未完成的外部表情缓存");
        }
        try (ParcelFileDescriptor descriptor = provider.openItem(packId, itemId);
             InputStream input = new ParcelFileDescriptor.AutoCloseInputStream(descriptor);
             FileOutputStream output = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                if (read > 0) {
                    output.write(buffer, 0, read);
                }
            }
            output.getFD().sync();
        } catch (EmoticonProviderException error) {
            throw error;
        } catch (IOException error) {
            throw new EmoticonProviderException(EmoticonProviderException.CODE_IO_ERROR,
                    "读取外部表情失败", error);
        }
        if (temporary.length() <= 0 || !temporary.renameTo(target)) {
            temporary.delete();
            throw new EmoticonProviderException(EmoticonProviderException.CODE_IO_ERROR,
                    "写入外部表情缓存失败");
        }
        touch(target);
    }

    private static void trim(File directory, File protectedFile) {
        File[] entries = directory.listFiles(file -> file.isFile() && !file.getName().endsWith(".part"));
        if (entries == null) {
            return;
        }
        long total = 0;
        List<File> files = new ArrayList<>();
        for (File entry : entries) {
            total += entry.length();
            files.add(entry);
        }
        if (total <= MAX_CACHE_BYTES) {
            return;
        }
        files.sort(Comparator.comparingLong(File::lastModified));
        for (File file : files) {
            if (total <= MAX_CACHE_BYTES) {
                break;
            }
            if (file.equals(protectedFile)) {
                continue;
            }
            long length = file.length();
            if (file.delete()) {
                total -= length;
            }
        }
    }

    private static void touch(File file) {
        if (!file.setLastModified(System.currentTimeMillis())) {
            // 访问时间更新失败只会降低 LRU 精度，不影响文件可用性。
        }
    }

    private static String extensionOf(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return ".bin";
        }
        String extension = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        return extension.matches("[a-z0-9]{1,8}") ? "." + extension : ".bin";
    }

    private static String sha256(String value) throws EmoticonProviderException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                result.append(String.format(Locale.ROOT, "%02x", item & 0xff));
            }
            return result.toString();
        } catch (NoSuchAlgorithmException error) {
            throw new EmoticonProviderException(EmoticonProviderException.CODE_IO_ERROR,
                    "设备不支持 SHA-256", error);
        }
    }
}
