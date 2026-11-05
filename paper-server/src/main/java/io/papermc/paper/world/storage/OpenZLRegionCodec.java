package io.papermc.paper.world.storage;

import io.github.hybledav.OpenZLCompressionLevel;
import io.github.hybledav.OpenZLCompressor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.jspecify.annotations.Nullable;

/**
 * EXPERIMENTAL OpenZL region compression through the openzl-jni bindings, used for benchmarking only.
 * OpenZL's frame format is not stable yet, so worlds written with it may become unreadable with other OpenZL versions.
 * <p>
 * Only touched (and its native library only loaded) when a region file actually uses {@code paper:openzl}.
 */
final class OpenZLRegionCodec {

    static final int LEVEL = Integer.getInteger("Paper.region-file-compression.openzl-level", 6);
    // Optional serialized (trained) compressor from `zli train`, loaded once per thread. It keeps the level it was
    // trained with, so openzl-level only applies without it.
    private static final byte @Nullable [] TRAINED = loadTrained(System.getProperty("Paper.region-file-compression.openzl-compressor"));

    // The default graph (a zstd node) compresses chunk NBT better than the "serial" profile (ratio 7.40 vs 6.48).
    private static final ThreadLocal<OpenZLCompressor> COMPRESSOR = ThreadLocal.withInitial(() -> {
        final OpenZLCompressor compressor = new OpenZLCompressor();
        if (TRAINED != null) {
            compressor.configureSerialized(TRAINED);
        } else {
            compressor.setCompressionLevel(OpenZLCompressionLevel.fromLevel(LEVEL));
        }
        return compressor;
    });

    private OpenZLRegionCodec() {
    }

    static byte[] compress(final byte[] data, final int length) {
        final byte[] input = length == data.length ? data : Arrays.copyOf(data, length);
        return COMPRESSOR.get().compress(input);
    }

    static byte[] decompress(final byte[] compressed) throws IOException {
        final OpenZLCompressor compressor = COMPRESSOR.get();
        final long size = compressor.getDecompressedSize(compressed);
        if (size < 0 || size > RegionFile.MAX_CHUNK_SIZE) {
            throw new IOException("Invalid OpenZL frame content size " + size);
        }
        return compressor.decompress(compressed);
    }

    private static byte @Nullable [] loadTrained(final @Nullable String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            return Files.readAllBytes(Path.of(path));
        } catch (final IOException e) {
            throw new UncheckedIOException("Unable to read OpenZL compressor " + path, e);
        }
    }
}
