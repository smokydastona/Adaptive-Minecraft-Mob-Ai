package com.minecraft.gancity.ai;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * GZIP Compression utility for network data
 * Based on PlayerSync-Plus optimizations
 * 
 * Features:
 * - Smart compression (only if it saves space)
 * - 40-70% typical compression ratio
 * - Reduces network bandwidth
 * - Automatic fallback to uncompressed
 */
public class CompressionUtil {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int COMPRESSION_THRESHOLD = 512; // Only compress if larger than 512 bytes
    
    /**
     * Compress string data using GZIP
     * Only compresses if result is smaller than original
     * 
     * @param data String to compress
     * @return Compressed bytes, or null if compression not beneficial
     */
    public static byte[] compress(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        
        byte[] original = data.getBytes(StandardCharsets.UTF_8);
        
        // Don't compress small data
        if (original.length < COMPRESSION_THRESHOLD) {
            return null;
        }
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(baos)) {
            
            gzipOut.write(original);
            gzipOut.finish();
            
            byte[] compressed = baos.toByteArray();
            
            // Only use compression if it actually saves space
            if (compressed.length < original.length) {
                float ratio = (1.0f - ((float) compressed.length / original.length)) * 100;
                LOGGER.debug("Compressed {} bytes -> {} bytes ({:.1f}% reduction)", 
                    original.length, compressed.length, ratio);
                return compressed;
            } else {
                LOGGER.debug("Compression not beneficial ({} -> {} bytes), using uncompressed", 
                    original.length, compressed.length);
                return null;
            }
            
        } catch (IOException e) {
            LOGGER.warn("Compression failed, using uncompressed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Decompress GZIP data
     * 
     * @param compressed Compressed bytes
     * @return Decompressed string
     */
    public static String decompress(byte[] compressed) {
        if (compressed == null || compressed.length == 0) {
            return null;
        }
        
        try (ByteArrayInputStream bais = new ByteArrayInputStream(compressed);
             GZIPInputStream gzipIn = new GZIPInputStream(bais);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            
            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzipIn.read(buffer)) > 0) {
                baos.write(buffer, 0, len);
            }
            
            return baos.toString(StandardCharsets.UTF_8);
            
        } catch (IOException e) {
            LOGGER.error("Decompression failed: {}", e.getMessage());
            return null;
        }
    }
    
    /**
     * Check if compression would be beneficial
     */
    public static boolean shouldCompress(String data) {
        return data != null && data.getBytes(StandardCharsets.UTF_8).length >= COMPRESSION_THRESHOLD;
    }
    
    /**
     * Get compression statistics
     */
    public static String getCompressionStats(byte[] original, byte[] compressed) {
        if (original == null || compressed == null) {
            return "N/A";
        }
        float ratio = (1.0f - ((float) compressed.length / original.length)) * 100;
        return String.format("%d -> %d bytes (%.1f%% reduction)", 
            original.length, compressed.length, ratio);
    }
}
