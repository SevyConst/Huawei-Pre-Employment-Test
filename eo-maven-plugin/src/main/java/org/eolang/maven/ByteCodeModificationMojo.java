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
import org.cactoos.set.SetOf;
import org.eolang.maven.util.Walk;
import org.objectweb.asm.*;
import org.objectweb.asm.commons.ClassRemapper;
import org.objectweb.asm.commons.Remapper;
import org.objectweb.asm.commons.SimpleRemapper;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class ByteCodeModificationMojo extends SafeMojo{

    public static final String PATH_TO_VERSIONIZED =
            "Lorg" + File.separator + "eolang" + File.separator + "Versionized;";

    public static final Set<String> GLOB_CLASS_FILES = new SetOf<>("**/*.class");

    public static final String EXTENSION_CLASS = ".class";
    public static final int OPCODE_ASM_VERSION = Opcodes.ASM9;

    @Parameter(
            property = "inputDirectory")
    private Path inputDir;

    @Parameter(
            property = "outputDirectory")
    private Path outputDir;

    @Parameter(
            property = "hash")
    private String hash;

    // TODO write that algorithm consist of three steps - add links to three method
    @Override
    void exec() throws IOException {
        Walk inputWalk = new Walk(inputDir).includes(GLOB_CLASS_FILES);
        final Map<String, String> versionizedAsmMap = inputWalk
                .stream()
                .map(this::copyIfVersionized)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        inputWalk.forEach(path -> copyUsagesVersionized(path, versionizedAsmMap));

        new Walk(outputDir)
                .includes(GLOB_CLASS_FILES)
                .forEach(path -> renameUsagesInVersionized(path, versionizedAsmMap));
    }

    /**
     * Copy .class file if it has @Versionized annotation.
     *
     * @return input path and output paths of .class in ASM format (relative path without extension)
     */
    private Optional<Map.Entry<String, String>> copyIfVersionized(Path inputPath) {
        ClassReader classReader;
        try {
            classReader = new ClassReader(Files.readAllBytes(inputPath));
        } catch (IOException e) {
                throw new Error("Can't read file " + inputPath, e);
        }

        VisitorVersionized visitorVersionized = new VisitorVersionized();
        classReader.accept(visitorVersionized, 0);

        if (!visitorVersionized.hasVersionized) {
            return Optional.empty();
        }
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        String inputAsm = pathToAsmName(inputPath, this.inputDir);
        String outputAsm = this.hash + File.separator + inputAsm;
        // TODO: remove variable ClassRemapper
        ClassRemapper classRemapper =
                new ClassRemapper(classWriter, new SimpleRemapper(inputAsm, outputAsm));
        classReader.accept(classRemapper, 0);

        Path outputPath = this.outputDir.resolve(outputAsm + EXTENSION_CLASS);
        try {
            Files.createDirectories(outputPath.getParent());
            Files.write(outputPath, classWriter.toByteArray());
        } catch (IOException e) {
            throw new Error("can't write file " + outputPath, e);
        }

        return Optional.of(new AbstractMap.SimpleEntry<>(inputAsm, outputAsm));
    }

    static class VisitorVersionized extends ClassVisitor {
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
    public static String pathToAsmName(Path inputPath, Path inputDir) {
        String relativePath = inputDir.relativize(inputPath).toString();
        return relativePath.substring(0, relativePath.length() - EXTENSION_CLASS.length());
    }

    private void copyUsagesVersionized(Path inputPath, final Map<String, String> versionizedAsmMap) {
        ClassReader classReader;
        try {
            classReader = new ClassReader(Files.readAllBytes(inputPath));
        } catch (IOException e) {
            throw new Error("Can't read file " + inputPath, e);
        }

        String inputAsmName = pathToAsmName(inputPath, inputDir);
        if (null != versionizedAsmMap.get(inputAsmName)) {
            return;
        }
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        UsageRemapper usageRemapper = new UsageRemapper(versionizedAsmMap);
        ClassRemapper classRemapper = new ClassRemapper(classWriter, usageRemapper);
        classReader.accept(classRemapper, 0);

        if (usageRemapper.isChanged) {
            Path outputPath = this.outputDir.resolve(inputDir.relativize(inputPath));

            try {
                Files.createDirectories(outputPath.getParent());
                Files.write(outputPath, classWriter.toByteArray());
            } catch (IOException e) {
                throw new Error("can't write file " + outputPath, e);
            }
        }
    }

    private void renameUsagesInVersionized(Path outputPath, Map<String, String> versionizedAsmMap) {
        ClassReader classReader;
        try{
            classReader = new ClassReader(Files.readAllBytes(outputPath));
        } catch (IOException e) {
            throw new Error("Can't read file " + outputPath, e);
        }
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        UsageRemapper usageRemapper = new UsageRemapper(versionizedAsmMap);
        ClassRemapper classRemapper = new ClassRemapper(classWriter, usageRemapper);
        classReader.accept(classRemapper, 0);

        if (usageRemapper.isChanged) {
            try {
                Files.write(outputPath, classWriter.toByteArray());
            } catch (IOException e) {
                throw new Error("can't write file " + outputPath, e);
            }
        }
    }

    static class UsageRemapper extends Remapper{
        private final Map<String, String> versionizedAsmMap;
        private boolean isChanged;

        public UsageRemapper(final Map<String, String> versionizedAsmMap) {
            this.versionizedAsmMap = versionizedAsmMap;
        }

        @Override
        public String map(String typeName) {
            String outputPathAsm = versionizedAsmMap.get(typeName);
            if (null != outputPathAsm) {
                isChanged = true;
                return outputPathAsm;
            }
            return typeName;
        }
    }
}