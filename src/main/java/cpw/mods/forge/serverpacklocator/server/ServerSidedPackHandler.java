package cpw.mods.forge.serverpacklocator.server;

import com.electronwill.nightconfig.core.ConfigFormat;
import com.electronwill.nightconfig.core.file.FileConfig;
import cpw.mods.forge.serverpacklocator.SidedPackHandler;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import net.minecraftforge.forgespi.locating.IModFile;
import net.minecraftforge.forgespi.locating.IModLocator;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public class ServerSidedPackHandler extends SidedPackHandler {
    private static final Logger LOGGER = LogManager.getLogger();
    private ServerFileManager serverFileManager;

    public ServerSidedPackHandler(final Path serverModsDir) {
        super(serverModsDir);
    }

    @Override
    protected boolean validateConfig() {
        final OptionalInt port = getConfig().getOptionalInt("server.port");

        if (port.isPresent()) {
            return true;
        } else {
            LOGGER.fatal("Invalid configuration file found: {}, please delete or correct before trying again", getConfig().getNioPath());
            throw new IllegalStateException("Invalid configuration found");
        }
    }

    @Override
    protected boolean handleMissing(final Path path, final ConfigFormat<?> configFormat) throws IOException {
        Files.copy(Objects.requireNonNull(getClass().getResourceAsStream("/defaultserverconfig.toml")), path);
        return true;
    }

    @Override
    protected boolean waitForDownload() {
        return true;
    }

    @Override
    protected List<IModFile> processModList(List<IModFile> scannedMods) {
        serverFileManager.handleModList(scannedMods);
        return serverFileManager.getModList();
    }

    @Override
    public void initialize(final IModLocator dirLocator) {
        FileConfig config = getConfig();
        int port = config.getOptionalInt("server.port").orElse(8443);
        List<String> excludedModIds = config.<List<String>>getOptional("server.excludedModIds").orElse(List.of());
        SslContext sslContext = buildSslContext(config.get("server.ssl.certificateChainFile"), config.get("server.ssl.keyFile"));

        serverFileManager = new ServerFileManager(this, excludedModIds);
        SimpleHttpServer.run(serverFileManager, port, sslContext);
    }

    @Nullable
    private static SslContext buildSslContext(@Nullable final String certificateChainFile, @Nullable final String keyFile) {
        if (certificateChainFile == null || keyFile == null) {
            return null;
        }
        try (
                final InputStream certificateChain = Files.newInputStream(Path.of(certificateChainFile));
                final InputStream key = Files.newInputStream(Path.of(keyFile))
        ) {
            return SslContextBuilder.forServer(certificateChain, key).build();
        } catch (final Exception e) {
            LOGGER.error("Failed to initialize SSL context for server", e);
        }
        return null;
    }
}
