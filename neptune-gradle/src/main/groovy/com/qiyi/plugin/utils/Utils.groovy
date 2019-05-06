package com.qiyi.plugin.utils

import com.android.builder.model.Version
import com.google.common.io.Files
import org.gradle.util.VersionNumber

import java.security.MessageDigest

class Utils {

    public static boolean  isAgpAbove34() {
        String version = Version.ANDROID_GRADLE_PLUGIN_VERSION
        VersionNumber agpVersion = VersionNumber.parse(version)
        return agpVersion >= VersionNumber.parse("3.4")
    }

    public static boolean isAgpAbove3() {
        String version = Version.ANDROID_GRADLE_PLUGIN_VERSION
        VersionNumber agpVersion = VersionNumber.parse(version)
        return agpVersion >= VersionNumber.parse("3.0")
    }

    public static int compareVersion(String v1, String v2) {
        String[] va1 = v1.split("\\.")
        String[] va2 = v2.split("\\.")

        int idx = 0
        int minLen = Math.max(va1.length, va2.length)
        int diff = 0
        while (idx < minLen
                && (diff = va1[idx].length() - va2[idx].length()) == 0
                && (diff = va1[idx].compareTo(va2[idx])) == 0) {
            ++idx
        }

        return (diff != 0) ? diff : va1.length - va2.length
    }


    public static byte[] toByteArray(final InputStream input) throws IOException {
        final ByteArrayOutputStream output = new ByteArrayOutputStream()
        final byte[] buffer = new byte[8024]
        int n = 0
        long count = 0
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n)
            count += n
        }
        return output.toByteArray()
    }


    public static void renameFile(File originFile, File targetFile) {
        if (targetFile.exists()) {
            targetFile.delete()
        }
        targetFile.parentFile.mkdirs()
        if (!originFile.renameTo(targetFile)) {
            throw new RuntimeException("${originFile} rename to ${targetFile} failed ")
        }
    }


    public static void copy(File src, File dst) {
        if (!src.exists()) {
            return
        }

        if (src.isFile()) {
            if (!dst.getParentFile().exists()) {
                dst.getParentFile().mkdirs()
            }
            dst.delete()
            dst.createNewFile()
            // copy
            Files.copy(src, dst)
            return
        }

        if (!dst.exists()) {
            dst.mkdirs()
        }

        src.list().each {name ->
            File srcFile = new File(src, name)
            File dstFile = new File(dst, name)
            copy(srcFile, dstFile)
        }
    }

    private static final char[] HEX_DIGITS = ['0', '1', '2', '3', '4',
                                              '5', '6', '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f']

    public static String md5(String str) {

        MessageDigest messageDigest = MessageDigest.getInstance("MD5")
        messageDigest.reset()
        messageDigest.update(str.getBytes("UTF-8"))
        byte[] byteArray = messageDigest.digest()

        StringBuilder hexString = new StringBuilder()
        for (byte b : byteArray) {
            hexString.append(HEX_DIGITS[b >> 4 & 0xf])
            hexString.append(HEX_DIGITS[b & 0xf])
        }

        return hexString.toString()
    }
}
