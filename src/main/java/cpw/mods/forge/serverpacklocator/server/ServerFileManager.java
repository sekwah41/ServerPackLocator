package cpw.mods.forge.serverpacklocator.server;

import com.google.common.hash.HashCode;
import cpw.mods.forge.serverpacklocator.FileChecksumValidator;
import cpw.mods.forge.serverpacklocator.ServerManifest;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.artifact.versioning.ArtifactVersion;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ServerFileManager {
    private static final Logger LOGGER = LogManager.getLogger();
    private static Map<IModFile, IModFileInfo> infos;
    private ServerManifest manifest;
    private final Path modsDir;
    private List<IModFile> modList;
    private final Path manifestFile;
    private final ServerSidedPackHandler serverSidedPackHandler;
    private final List<String> excludedModIds;

    ServerFileManager(ServerSidedPackHandler packHandler, final List<String> excludedModIds) {
        modsDir = packHandler.getServerModsDir();
        this.excludedModIds = excludedModIds;
        manifestFile = modsDir.resolve("servermanifest.json");
        this.serverSidedPackHandler = packHandler;
    }

    private String getForgeVersion()
    {
        return serverSidedPackHandler.getMcVersion() + "-" + serverSidedPackHandler.getForgeVersion();
    }

    String buildManifest() {
        return manifest.toJson();
    }

    byte[] findFile(final String fileName) {
        try {
            return Files.readAllBytes(modsDir.resolve(fileName));
        } catch (IOException e) {
            LOGGER.warn("Failed to read file {}", fileName);
            return null;
        }
    }

    private static Field modInfoParser;
    private static Method modFileParser;
    public static List<IModInfo> getModInfos(final IModFile modFile) {
        if (modInfoParser == null) {
            Class<?> mfClass = LamdbaExceptionUtils.uncheck(() -> Class.forName("net.minecraftforge.fml.loading.moddiscovery.ModFile"));
            modInfoParser = LamdbaExceptionUtils.uncheck(() -> mfClass.getDeclaredField("parser"));
            modInfoParser.setAccessible(true);
            Class<?> mfpClass = LamdbaExceptionUtils.uncheck(() -> Class.forName("net.minecraftforge.fml.loading.moddiscovery.ModFileParser"));
            modFileParser = Arrays.stream(mfpClass.getMethods()).filter(m -> m.getName().equals("readModList")).findAny().orElseThrow(() -> new RuntimeException("BARFY!"));
            infos = new HashMap<>();
        }
        IModFileInfo info = infos.computeIfAbsent(modFile, LamdbaExceptionUtils.rethrowFunction(junk->(IModFileInfo)modFileParser.invoke(null, modFile, modInfoParser.get(modFile))));
        return info.getMods();
    }

    private static String getRootModId(IModFile modFile) {
        return modFile.getType() == IModFile.Type.MOD ? getModInfos(modFile).get(0).getModId() : modFile.getFileName();
    }

    void handleModList(final List<IModFile> modList) {
        generateManifest(modList);
    }

    private void generateManifest(final List<IModFile> files) {
        LOGGER.debug("Generating manifest");

        modList = buildModList(files);

        final ServerManifest.Builder manifest = new ServerManifest.Builder()
                .setForgeVersion(LamdbaExceptionUtils.uncheck(this::getForgeVersion));

        for (IModFile file : modList) {
            if (file.getFileName().equals("serverpackutility.jar")) {
                continue;
            }
            HashCode checksum = FileChecksumValidator.computeChecksumFor(file.getFilePath());
            if (checksum == null) {
                throw new IllegalArgumentException("Invalid checksum for file " + file.getFileName());
            }
            manifest.add(getRootModId(file), checksum, file.getFileName());
        }

        this.manifest = manifest.build();
        this.manifest.save(manifestFile);
    }

    private List<IModFile> buildModList(List<IModFile> files) {
        final Map<String, List<IModFile>> filesByRootId = files.stream().collect(Collectors.groupingBy(ServerFileManager::getRootModId));
        excludedModIds.forEach(filesByRootId::remove);

        return filesByRootId.entrySet().stream()
                .flatMap(this::selectNewest)
                .toList();
    }

    private Stream<IModFile> selectNewest(final Map.Entry<String, List<IModFile>> entry) {
        List<IModFile> files = entry.getValue();
        if (files.isEmpty()) {
            return Stream.empty();
        } else if (files.size() == 1) {
            return Stream.of(files.get(0));
        }
        if (!files.stream().allMatch(file -> file.getType() == IModFile.Type.MOD)) {
            return files.stream();
        }

        LOGGER.debug("Selecting newest by artifact version for modid {}", entry.getKey());
        IModFile newestFile = files.stream()
                .max(Comparator.comparing(ServerFileManager::getRootVersion))
                .orElseThrow();
        LOGGER.debug("Newest file by artifact version for modid {} is {} ({})", entry.getKey(), newestFile.getFileName(), getRootVersion(newestFile));
        return Stream.of(newestFile);
    }

    private static ArtifactVersion getRootVersion(IModFile file) {
        return getModInfos(file).get(0).getVersion();
    }

    List<IModFile> getModList() {
        return this.modList;
    }
}
