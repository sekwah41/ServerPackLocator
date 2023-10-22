package cpw.mods.forge.serverpacklocator;

import com.mojang.logging.LogUtils;
import cpw.mods.modlauncher.api.LamdbaExceptionUtils;
import net.minecraftforge.forgespi.language.IModFileInfo;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.forgespi.locating.IModFile;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PackBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static Map<IModFile, IModFileInfo> infos = Map.of();
    @Nullable
    private static Field modInfoParser;
    @Nullable
    private static Method modFileParser;

    private final Set<String> excludedModIds;

    public PackBuilder(final Set<String> excludedModIds) {
        this.excludedModIds = excludedModIds;
    }

    public static List<IModInfo> getModInfos(final IModFile modFile) {
        if (modInfoParser == null) {
            final Class<?> mfClass = LamdbaExceptionUtils.uncheck(() -> Class.forName("net.minecraftforge.fml.loading.moddiscovery.ModFile"));
            modInfoParser = LamdbaExceptionUtils.uncheck(() -> mfClass.getDeclaredField("parser"));
            modInfoParser.setAccessible(true);
            final Class<?> mfpClass = LamdbaExceptionUtils.uncheck(() -> Class.forName("net.minecraftforge.fml.loading.moddiscovery.ModFileParser"));
            modFileParser = Arrays.stream(mfpClass.getMethods()).filter(m -> m.getName().equals("readModList")).findAny().orElseThrow(() -> new RuntimeException("BARFY!"));
            infos = new HashMap<>();
        }
        final IModFileInfo info = infos.computeIfAbsent(modFile, LamdbaExceptionUtils.rethrowFunction(junk -> (IModFileInfo) modFileParser.invoke(null, modFile, modInfoParser.get(modFile))));
        return info.getMods();
    }

    public static String getRootModId(final IModFile modFile) {
        return modFile.getType() == IModFile.Type.MOD ? getModInfos(modFile).get(0).getModId() : modFile.getFileName();
    }

    public List<IModFile> buildModList(final List<IModFile> files) {
        final Map<String, List<IModFile>> filesByRootId = files.stream().collect(Collectors.groupingBy(PackBuilder::getRootModId));
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
                .max(Comparator.comparing(PackBuilder::getRootVersion))
                .orElseThrow();
        LOGGER.debug("Newest file by artifact version for modid {} is {} ({})", entry.getKey(), newestFile.getFileName(), getRootVersion(newestFile));
        return Stream.of(newestFile);
    }

    private static ArtifactVersion getRootVersion(IModFile file) {
        return getModInfos(file).get(0).getVersion();
    }

}
