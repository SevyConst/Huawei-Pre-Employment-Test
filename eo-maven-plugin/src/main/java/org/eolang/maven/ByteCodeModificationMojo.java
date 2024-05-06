/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2016-2024 Objectionary.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included
 * in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.eolang.maven;

import org.apache.maven.plugins.annotations.Parameter;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

public class ByteCodeModificationMojo extends SafeMojo{

    public static final String PATH_TO_VERSIONIZED =
            "Lorg" + File.separator + "eolang" + File.separator + "Versionized;";

    public static final String EXTENSION_CLASS = ".class";
    public static final int OPCODE_ASM_VERSION = Opcodes.ASM9;

    public static final String OBJECT_PATH_ASM = "java" + File.separator + "lang" + File.separator + "Object";

    @Parameter(
            property = "inputDirectory")
    private Path inputDir;

    @Parameter(
            property = "outputDirectory")
    private Path outputDir;

    @Parameter(
            property = "hash")
    private String hash;

    @Override
    void exec() throws IOException {
        PathsTable pathsTable = copyVersionizedClasses();
        copyUsagesVersionized(pathsTable.inputSet, pathsTable.inOutAsmMap);
        renameUsagesInVersionizedClasses(pathsTable.inOutAsmMap);
    }

    PathsTable copyVersionizedClasses() throws IOException {
        PathsTable pathsTable = new PathsTable();
        Files.walkFileTree(this.inputDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path inputPath, BasicFileAttributes attrs) throws IOException {
                if (hasExtensionClass(inputPath)) {

                    Optional<Map.Entry<String, String>> pathIOForAsm = copyIfVersionized(inputPath);
                    if (pathIOForAsm.isPresent()) {
                        String inputPathForAsm = pathIOForAsm.get().getKey();
                        String outputPathForAsm = pathIOForAsm.get().getValue();

                        pathsTable.inputSet.add(inputPath);
                        pathsTable.inOutAsmMap.put(inputPathForAsm, outputPathForAsm);
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return pathsTable;
    }

    void copyUsagesVersionized(Set<Path> inputSet, Map<String, String> inOutAsmMap) throws IOException {
        Files.walkFileTree(this.inputDir, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path inputPath, BasicFileAttributes attrs) throws IOException {
                if (hasExtensionClass(inputPath) && !inputSet.contains(inputPath)) {
                    copyUsageVersionized(inputPath, inOutAsmMap);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    void renameUsagesInVersionizedClasses(Map<String, String> inOutAsmMap) throws IOException {
        Files.walkFileTree(this.outputDir.resolve(this.hash), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path inputPath, BasicFileAttributes attrs) throws IOException {
                if (hasExtensionClass(inputPath)) {
                    renameUsagesInVersionizedOneFile(inputPath, inOutAsmMap);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean hasExtensionClass(Path path) {
        if (Files.isDirectory(path)) {
            return false;
        }

        String fileName = path.toString();
        return EXTENSION_CLASS.equals(fileName.substring(fileName.lastIndexOf(".")));
    }

    /**
     * Copy .class file if it has @Versionized annotation.
     *
     * @return input path and output paths of .class in ASM format (relative path without extension)
     */
    private Optional<Map.Entry<String, String>> copyIfVersionized(Path inputPath) throws IOException {
        ClassReader classReader = new ClassReader(Files.readAllBytes(inputPath));
        VisitorVersionized visitorVersionized = new VisitorVersionized();
        classReader.accept(visitorVersionized, 0);

        if (!visitorVersionized.hasVersionized) {
            return Optional.empty();
        }
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        String inputPathAsm = getInputPathAsm(inputPath);
        String outputPathAsm = this.hash + File.separator + inputPathAsm;
        // TODO: remove variable ClassRemapper
        ClassRemapper classRemapper =
                new ClassRemapper(classWriter, new SimpleRemapper(inputPathAsm, outputPathAsm));
        classReader.accept(classRemapper, 0);

        Path outputPath = this.outputDir.resolve(outputPathAsm + EXTENSION_CLASS);
        Files.createDirectories(outputPath.getParent());
        Files.write(outputPath, classWriter.toByteArray());

        return Optional.of(new AbstractMap.SimpleEntry<>(inputPathAsm, outputPathAsm));
    }

    private class VisitorVersionized extends ClassVisitor {
        public VisitorVersionized() {
            super(OPCODE_ASM_VERSION);
        }

        private boolean hasVersionized;

        @Override
        public AnnotationVisitor visitAnnotation(String desc, boolean visible) {
            if (desc.equals(PATH_TO_VERSIONIZED)) {
                hasVersionized = true;
            }
            return super.visitAnnotation(desc, visible);
        }
    }

    /**
     * Obtain relative path without file extension.
     */
    private String getInputPathAsm(Path inputPath) {
        String relativePath = this.inputDir.relativize(inputPath).toString();
        return relativePath.substring(0, relativePath.length() - EXTENSION_CLASS.length());
    }

    private void copyUsageVersionized(Path inputPath, Map<String, String> inOutAsmMap) throws IOException {
        ClassReader classReader = new ClassReader(Files.readAllBytes(inputPath));
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        UsageRemapper usageRemapper = new UsageRemapper(inOutAsmMap);
        ClassRemapper classRemapper = new ClassRemapper(classWriter, usageRemapper);
        classReader.accept(classRemapper, 0);

        if (usageRemapper.isChanged) {
            Path outPath = this.outputDir.resolve(inputDir.relativize(inputPath));
            Files.createDirectories(outPath.getParent());
            Files.write(outPath, classWriter.toByteArray());
        }
    }

    // TODO: changed class must not change package
    private void renameUsagesInVersionizedOneFile(Path outPath, Map<String, String> inOutAsmMap) throws IOException {
        ClassReader classReader = new ClassReader(Files.readAllBytes(outPath));
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        UsageRemapper usageRemapper = new UsageRemapper(inOutAsmMap);
        ClassRemapper classRemapper = new ClassRemapper(classWriter, usageRemapper);
        classReader.accept(classRemapper, 0);

        if (usageRemapper.isChanged) {
            Files.write(outPath, classWriter.toByteArray());
        }
    }

    private class UsageRemapper extends Remapper{
        private final Map<String, String> inOutAsmMap;
        private boolean isChanged;

        public UsageRemapper(final Map<String, String> inOutAsmMap) {
            this.inOutAsmMap = inOutAsmMap;
        }

        @Override
        public String map(String typeName) {
            String outputPathAsm = inOutAsmMap.get(typeName);
            if (null != outputPathAsm) {
                isChanged = true;
                return outputPathAsm;
            }
            return typeName;
        }
    }

    class PathsTable {
        final Map<String, String> inOutAsmMap = new HashMap<>();
        final Set<Path> inputSet = new HashSet<>();
    }
}