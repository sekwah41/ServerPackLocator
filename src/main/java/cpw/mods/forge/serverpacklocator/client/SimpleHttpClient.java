package cpw.mods.forge.serverpacklocator.client;

import cpw.mods.forge.serverpacklocator.FileChecksumValidator;
import cpw.mods.forge.serverpacklocator.LaunchEnvironmentHandler;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraft.Util;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SimpleHttpClient {
    private static final Logger LOGGER = LogManager.getLogger();
    private final Path outputDir;
    private ServerManifest serverManifest;
    private Iterator<ServerManifest.ModFileData> fileDownloaderIterator;
    private final Future<Boolean> downloadJob;
    private final String       passwordHash;
    private final List<String> excludedModIds;

    public SimpleHttpClient(final ClientSidedPackHandler packHandler, final String password, final List<String> excludedModIds) {
        this.outputDir = packHandler.getServerModsDir();
        this.excludedModIds = excludedModIds;

        try
        {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update(password.getBytes());
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(Integer.toHexString(b & 0xff));
            }
            this.passwordHash = sb.toString().toUpperCase(Locale.ROOT);
        }
        catch (NoSuchAlgorithmException e)
        {
            throw new IllegalStateException("Missing MD5 hashing algorithm", e);
        }

        final Optional<String> remoteServer = packHandler.getConfig().getOptional("client.remoteServer");
        downloadJob = Executors.newSingleThreadExecutor().submit(() -> remoteServer
          .map(server -> server.endsWith("/") ? server.substring(0, server.length() - 1) : server)
          .map(this::connectAndDownload).orElse(false));
    }

    private boolean connectAndDownload(final String server) {
        try {
            downloadManifest(server);
            downloadNextFile(server);
            return true;
        } catch (Exception ex) {
            LOGGER.error("Failed to download modpack from server: " + server, ex);
            return false;
        }
    }

    protected void downloadManifest(final String serverHost) throws IOException
    {
        var address = serverHost + "/servermanifest.json";

        LOGGER.info("Requesting server manifest from: " + serverHost);
        LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Requesting server manifest from: " + serverHost);

        var url = new URL(address);
        var connection = url.openConnection();
        connection.setRequestProperty("Authentication", this.passwordHash);

        try (BufferedInputStream in = new BufferedInputStream(connection.getInputStream())) {
            this.serverManifest = Util.getOrThrow(ServerManifest.loadFromStream(in), error -> new IllegalStateException("Manifest was malformed: " + error));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to download manifest", e);
        }
        LOGGER.debug("Received manifest");
        buildFileFetcher();
    }

    private void downloadFile(final String server, final ServerManifest.ModFileData next) throws IOException
    {
        final String existingChecksum = FileChecksumValidator.computeChecksumFor(outputDir.resolve(next.fileName()));
        if (Objects.equals(next.checksum(), existingChecksum)) {
            LOGGER.debug("Found existing file {} - skipping", next.fileName());
            downloadNextFile(server);
            return;
        }

        final String nextFile = next.fileName();
        LOGGER.info("Requesting file {}", nextFile);
        LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Requesting file "+nextFile);
        final String requestUri = server + LamdbaExceptionUtils.rethrowFunction((String f) -> URLEncoder.encode(f, StandardCharsets.UTF_8))
          .andThen(s -> s.replaceAll("\\+", "%20"))
          .andThen(s -> "/files/"+s)
          .apply(nextFile);

        try
        {
            URLConnection connection = new URL(requestUri).openConnection();
            connection.setRequestProperty("Authentication", this.passwordHash);

            File file = outputDir.resolve(next.fileName()).toFile();

            FileChannel download = new FileOutputStream(file).getChannel();

            long totalBytes = connection.getContentLengthLong(), time = System.nanoTime(), between, length;
            int percent;

            ReadableByteChannel channel = Channels.newChannel(connection.getInputStream());

            while (download.transferFrom(channel, file.length(), 1024) > 0)
            {
                between = System.nanoTime() - time;

                if (between < 1000000000) continue;

                length = file.length();

                percent = (int) ((double) length / ((double) totalBytes == 0.0 ? 1.0 : (double) totalBytes) * 100.0);

                LOGGER.info("Downloaded {}% of {}", percent, nextFile);
                LaunchEnvironmentHandler.INSTANCE.addProgressMessage("Downloaded " + percent + "% of " + nextFile);

                time = System.nanoTime();
            }

            downloadNextFile(server);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to download file: " + nextFile, ex);
        }
    }

    private void downloadNextFile(final String server) throws IOException
    {
        final Iterator<ServerManifest.ModFileData> fileDataIterator = fileDownloaderIterator;
        if (fileDataIterator.hasNext()) {
            downloadFile(server, fileDataIterator.next());
        } else {
            LOGGER.info("Finished downloading closing channel");
        }
    }

    private void buildFileFetcher() {
        if (this.excludedModIds.isEmpty())
        {
            fileDownloaderIterator = serverManifest.files().iterator();
        }
        else
        {
            fileDownloaderIterator = serverManifest.files()
                                   .stream()
                                   .filter(modFileData -> !this.excludedModIds.contains(modFileData.rootModId()))
                                   .iterator();
        }

    }

    boolean waitForResult() throws ExecutionException {
        try {
            return downloadJob.get();
        } catch (InterruptedException e) {
            return false;
        }
    }

    public ServerManifest getManifest() {
        return this.serverManifest;
    }
}
