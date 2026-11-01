package me.iynotuo.judge.judge.sandbox;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * File helpers shared by the judge engines and by {@link me.iynotuo.judge.judge.answer.ProgramAnswerChecker}.
 *
 * <p>Files inside a working directory are created world-readable/writable on purpose: the sandboxed
 * user has a different UID than the judge process.</p>
 */
public final class SandboxFiles {

    /**
     * Files the sandbox itself creates inside a working directory; they are never copied from a compile
     * directory into a run directory.
     */
    private static final List<String> SANDBOX_FILES = List.of("stdout.txt", "stderr.txt", "empty.txt");

    private SandboxFiles() {
    }

    /**
     * Creates a directory including its parents and widens its permissions for the sandboxed user.
     *
     * @param dir directory to create
     * @return the same path, which allows chaining
     * @throws IOException when the directory cannot be created
     */
    public static Path createDir(Path dir) throws IOException {
        Files.createDirectories(dir);
        setPermissions(dir, "rwxrwxrwx");
        return dir;
    }

    /**
     * Writes a UTF-8 text file, treating {@code null} content as an empty string.
     *
     * @param file        target file; its parent directory must already exist
     * @param content     text to write, {@code null} becomes {@code ""}
     * @param permissions POSIX permissions applied afterwards; ignored on non-POSIX file systems
     * @throws IOException when the file cannot be written
     */
    public static void writeFile(Path file, String content, String permissions) throws IOException {
        Files.writeString(file, content == null ? "" : content, StandardCharsets.UTF_8);
        setPermissions(file, permissions);
    }

    /**
     * Applies POSIX permissions on a best-effort basis.
     *
     * @param path        file or directory to adjust
     * @param permissions symbolic permissions such as {@code "rwxrwxrwx"}; failures are ignored on file
     *                    systems without POSIX permissions
     */
    public static void setPermissions(Path path, String permissions) {
        try {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        } catch (IOException | UnsupportedOperationException ignored) {
            // Non-POSIX file systems keep their defaults.
        }
    }

    /**
     * Copies the compile output into a fresh per-case directory.
     *
     * @param from       compile directory
     * @param to         per-case directory that becomes the working directory of the run
     * @param sourceFile source file name, excluded when {@code skipSource} is set
     * @param skipSource {@code true} for compiled languages, where the produced binary is used instead of
     *                   the source; {@code false} for interpreted languages
     * @throws IOException when a file cannot be copied
     */
    public static void copyArtifacts(Path from, Path to, String sourceFile, boolean skipSource) throws IOException {
        try (Stream<Path> files = Files.list(from)) {
            for (Path file : files.toList()) {
                String name = file.getFileName().toString();
                if (!Files.isRegularFile(file) || SANDBOX_FILES.contains(name)
                        || (skipSource && name.equals(sourceFile))) {
                    continue;
                }
                Path target = to.resolve(name);
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                setPermissions(target, "rwxr-xr-x");
            }
        }
    }

    /**
     * Reads at most {@code limit} bytes of a UTF-8 text file.
     *
     * @param file  file to read; {@code null} or a missing file yields an empty string
     * @param limit upper bound in bytes, so a program producing huge output cannot exhaust judge memory
     * @return the decoded prefix of the file, or {@code ""} when the file is missing or unreadable
     */
    public static String readText(Path file, int limit) {
        if (file == null || !Files.isRegularFile(file)) {
            return "";
        }
        try {
            long length = Files.size(file);
            int toRead = (int) Math.min(length, Math.max(0, limit));
            byte[] data = new byte[toRead];
            try (InputStream in = Files.newInputStream(file)) {
                int offset = 0;
                while (offset < toRead) {
                    int read = in.read(data, offset, toRead - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                }
                return new String(data, 0, offset, StandardCharsets.UTF_8);
            }
        } catch (IOException ex) {
            return "";
        }
    }

    /**
     * Returns the size of a file without throwing.
     *
     * @param file file to inspect
     * @return size in bytes, or {@code 0} when the file is missing or unreadable
     */
    public static long size(Path file) {
        try {
            return Files.size(file);
        } catch (IOException ex) {
            return 0;
        }
    }

    /**
     * Deletes a directory tree, ignoring failures.
     *
     * @param dir root of the tree, normally the temporary work directory of one submission
     */
    public static void deleteRecursively(Path dir) {
        if (dir == null) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best effort cleanup; the directory lives under the system temp directory.
                }
            });
        } catch (IOException ignored) {
            // The tree is already gone or unreadable; nothing useful can be done here.
        }
    }
}
