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

import org.cactoos.set.SetOf;
import org.eolang.maven.util.HmBase;
import org.eolang.maven.util.Walk;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

import javax.tools.*;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;


public class ByteCodeModificationMojoTest {

    private static final String OBJECT = "java/lang/Object";
    private static final String EXTENSION_JAVA = ".java";

    private static final String ORG_EOLANG_SRC = "org.eolang";
//    private static final String A_SRC = ORG_EOLANG_SRC + "A" +

    private final Set<String> includeBinaries = new SetOf<>("**/*.java");

    private static final String ORG_EOLANG_ASM_PATH = "org/eolang/";
    private static final String A_ASM_PATH = ORG_EOLANG_ASM_PATH + "A";
    private static final String CLASS_B_PATH_ASM = ORG_EOLANG_ASM_PATH + "B";
    private static final String CLASS_B_DESCRIPTOR = "L" + CLASS_B_PATH_ASM + ";";
    private static final String CLASS_C_PATH_ASM = "org" + File.separator + "eolang" + File.separator + "C";
    private static final String CLASS_C_DESCRIPTOR = "L" + CLASS_C_PATH_ASM + ";";


    private static final String INTERFACE_USAGE_ASM_PATH = ORG_EOLANG_ASM_PATH + "interfaces/InterfaceUsage";

    public static Path SRC = Paths.get("src/test/resources/org/eolang/maven/bytecode-modification/java-files");

    private static final Path RELATIVE_INPUT_DIR = Paths.get("target/classes");
    private static final Path RELATIVE_OUTPUT_DIR = Paths.get("target/modified-classes");
    private static final String HASH = "qwerty";

    private static final String SUPER_CLASS_DEFAULT_CHECK = "SUPER_CLASS_DEFAULT_CHECK";
    private static final String INTERFACES_DEFAULT_CHECK = "INTERFACES_DEFAULT_CHECK";

    @Test
    public void test(@TempDir final Path temp) throws Exception {

        Path inputDirPath = temp.resolve(RELATIVE_INPUT_DIR);
        Path outputDirPath = temp.resolve(RELATIVE_OUTPUT_DIR);

        compile(temp);

        new FakeMaven(temp)
                .with("inputDir", inputDirPath)
                .with("outputDir", outputDirPath)
                .with("hash", HASH)
                .execute(ByteCodeModificationMojo.class);





        // TODO: check number of files by deps in resource directory
    }

    private void saveClassFile(
            final Path temp,
            final byte[] content,
            final String className) throws IOException {
        new HmBase(temp.resolve(RELATIVE_INPUT_DIR))
                .save(content, Paths.get( className + ByteCodeModificationMojo.EXTENSION_CLASS));
    }

    private void compile(Path outputPath) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                null,
                null,
                null
        );
        List<File> outputDirList = new ArrayList<>();
        outputDirList.add(outputPath.toFile());
        fileManager.setLocation(StandardLocation.CLASS_OUTPUT, outputDirList);
        Collection<Path> pathsToCompile = new Walk(SRC)
                .includes(includeBinaries);
        List<File> filesToCompile = pathsToCompile.stream().map(Path::toFile).collect(Collectors.toList());
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(filesToCompile);
        List<String> javacOptions = new ArrayList<>();
        javacOptions.add("-d");
        javacOptions.add(outputPath.resolve(RELATIVE_INPUT_DIR).toAbsolutePath().toString());
        compiler.getTask(
                null,
                fileManager,
                null,
                javacOptions,
                null,
                compilationUnits
        ).call();
    }

    private void aCheck(HashSet<Path> outputFiles, Path outputDirPath) throws IOException {
        Set<String> defaultChecks = getAllDefaultChecks();
        doDefaultChecks(A_ASM_PATH, outputFiles, outputDirPath, defaultChecks, true);
    }

    private void interfaceUsageCheck(HashSet<Path> outputFiles, Path outputDirPath) throws IOException {
        Set<String> defaultChecks = getAllDefaultChecks();
        defaultChecks.remove(INTERFACES_DEFAULT_CHECK);
        doDefaultChecks(INTERFACE_USAGE_ASM_PATH, outputFiles, outputDirPath, defaultChecks, true);
    }

    private void doDefaultChecks(String inputAsmPath,
                                 HashSet<Path> outputFiles,
                                 Path outputDirPath,
                                 Set<String> checks,
                                 boolean hasVersionized) throws IOException {

        String asmPath = hasVersionized ? HASH + File.separator + inputAsmPath : inputAsmPath;
        Path path = outputDirPath.resolve(asmPath + ByteCodeModificationMojo.EXTENSION_CLASS);
        MatcherAssert.assertThat(
                "Can't find " + path,
                outputFiles.contains(path),
                Matchers.equalTo(true)
        );

        ClassNode classNode = getClassNode(path);
        MatcherAssert.assertThat(
                "The name in ASM format",
                classNode.name,
                Matchers.equalTo(asmPath)
        );

        if (checks.contains(SUPER_CLASS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    A_ASM_PATH + " has wrong superclass",
                    classNode.superName,
                    Matchers.equalTo(OBJECT)
            );
        }

        if (checks.contains(INTERFACES_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    A_ASM_PATH + " has irrelevant interfaces: " + classNode.interfaces,
                    classNode.interfaces.size(),
                    Matchers.equalTo(0)
            );
        }

        String sourceFile = A_ASM_PATH.substring(A_ASM_PATH.lastIndexOf(File.separator) + 1) + EXTENSION_JAVA;
        MatcherAssert.assertThat(
                A_ASM_PATH + ".class has wrong source file ",
                classNode.sourceFile,
                Matchers.equalTo(sourceFile)
        );
        MatcherAssert.assertThat(
                A_ASM_PATH + " has wrong module",
                classNode.module,
                Matchers.equalTo(null)
        );
    }

    private Set<String> getAllDefaultChecks() {
        Set<String> allDefaultsCheck = new HashSet<>();

        allDefaultsCheck.add(SUPER_CLASS_DEFAULT_CHECK);
        allDefaultsCheck.add(INTERFACES_DEFAULT_CHECK);

        return allDefaultsCheck;
    }

    private ClassNode getClassNode(Path path) throws IOException {
        ClassReader classReader = new ClassReader(Files.readAllBytes(path));
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);
        return classNode;
    }
}
