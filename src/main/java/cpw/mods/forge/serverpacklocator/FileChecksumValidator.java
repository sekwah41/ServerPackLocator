package cpw.mods.forge.serverpacklocator;

import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.Path;

public class FileChecksumValidator {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    public static HashCode computeChecksumFor(Path file) {
        if (!Files.exists(file)) {
            return null;
        }
        Hasher hasher = Hashing.sha256().newHasher();
        ByteBuffer buffer = ByteBuffer.allocate(1024);
        try (SeekableByteChannel channel = Files.newByteChannel(file)) {
            while (channel.read(buffer) > 0) {
                hasher.putBytes(buffer.rewind());
                buffer.rewind();
            }
        } catch (IOException e) {
            LOGGER.warn("Failed to compute hash for {}", file, e);
            return null;
        }
        return hasher.hash();
    }
}
