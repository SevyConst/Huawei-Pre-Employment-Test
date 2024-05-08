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
import java.util.*;
import java.util.stream.Collectors;


public class ByteCodeModificationMojoTest {

    private static final String OBJECT = "java/lang/Object";

    private static final Set<String> GLOB_JAVA_FILES = new SetOf<>("**/*.java");

    private static final String ORG_EOLANG_ASM_PATH = "org/eolang/";
    private static final String A_ASM_PATH = ORG_EOLANG_ASM_PATH + "A";

    public static final Path SRC = Paths.get("src/test/resources/org/eolang/maven/bytecode-modification/java-files");

    private static final Path RELATIVE_INPUT_DIR = Paths.get("target/classes");
    private static final Path RELATIVE_OUTPUT_DIR = Paths.get("target/modified-classes");
    private static final String HASH = "qwerty";

    private static final String SUPER_CLASS_DEFAULT_CHECK = "SUPER_CLASS_DEFAULT_CHECK";
    private static final String INTERFACES_DEFAULT_CHECK = "INTERFACES_DEFAULT_CHECK";

    @Test
    public void integrationTest(@TempDir final Path temp) throws Exception {

        Path inputDirPath = temp.resolve(RELATIVE_INPUT_DIR);
        Path outputDirPath = temp.resolve(RELATIVE_OUTPUT_DIR);

        compile(inputDirPath);
        Map<String, Boolean> foundMap = new Walk(inputDirPath)
                .includes(ByteCodeModificationMojo.GLOB_CLASS_FILES)
                .stream()
                .map(path -> ByteCodeModificationMojo.pathToAsmName(path, inputDirPath))
                .collect(Collectors.toMap(s -> s, s -> false));

        new FakeMaven(temp)
                .with("inputDir", inputDirPath)
                .with("outputDir", outputDirPath)
                .with("hash", HASH)
                .execute(ByteCodeModificationMojo.class);

        Collection<Path> outputPaths = new Walk(outputDirPath)
                .includes(ByteCodeModificationMojo.GLOB_CLASS_FILES);


        for (Map.Entry<String, Boolean> foundClass : foundMap.entrySet()) {
            Set<String> defaultChecks = getAllDefaultChecks();
            String inputAsmName = foundClass.getKey();
            switch (inputAsmName.substring(inputAsmName.lastIndexOf("/") + 1)) {
                case "A":
                    doDefaultChecks(
                            foundClass,
                            outputPaths,
                            outputDirPath,
                            defaultChecks,
                            true
                    );
                    break;
                case "InterfaceUsage":
                    defaultChecks.remove(INTERFACES_DEFAULT_CHECK);
                    ClassNode classNode = doDefaultChecks(
                            foundClass,
                            outputPaths,
                            outputDirPath,
                            defaultChecks,
                            false);
                    MatcherAssert.assertThat(
                            "output class for "
                                    + inputAsmName
                                    + " has wrong number of interfaces"
                                    + classNode.interfaces,
                            classNode.interfaces.size(),
                            Matchers.equalTo(1)
                    );
                    break;
                default:
            }
        }

        // TODO: check number of files by deps in resource directory
    }

    private void compile(Path dirWithInputClassFiles) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                null,
                null,
                null
        );
        Collection<Path> pathsToCompile = new Walk(SRC)
                .includes(GLOB_JAVA_FILES);
        List<File> filesToCompile = pathsToCompile.stream().map(Path::toFile).collect(Collectors.toList());
        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(filesToCompile);
        List<String> javacOptions = new ArrayList<>();
        javacOptions.add("-d");
        javacOptions.add(dirWithInputClassFiles.toAbsolutePath().toString());
        compiler.getTask(
                null,
                fileManager,
                null,
                javacOptions,
                null,
                compilationUnits
        ).call();
    }

    private ClassNode doDefaultChecks(Map.Entry<String, Boolean> foundClass,
                                 Collection<Path> outputPaths,
                                 Path outputDirPath,
                                 Set<String> checks,
                                 boolean hasVersionized) throws IOException {
        String inputAsmName = foundClass.getKey();
        String correctOutputAsmName = hasVersionized ? HASH + File.separator + inputAsmName : inputAsmName;

        Set<String> outputAsmNames = outputPaths
                .stream()
                .map(path -> ByteCodeModificationMojo.pathToAsmName(path, outputDirPath))
                .collect(Collectors.toSet());
        MatcherAssert.assertThat(
                "Can't find output .class file with name in ASM format: " + correctOutputAsmName,
                outputAsmNames.contains(correctOutputAsmName),
                Matchers.equalTo(true)
        );
        foundClass.setValue(true);

        ClassNode classNode = getClassNode(correctOutputAsmName, outputDirPath);
        MatcherAssert.assertThat(
                "The name in ASM format",
                classNode.name,
                Matchers.equalTo(correctOutputAsmName)
        );

        if (checks.contains(SUPER_CLASS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    correctOutputAsmName + " has wrong superclass",
                    classNode.superName,
                    Matchers.equalTo(OBJECT)
            );
        }

        if (checks.contains(INTERFACES_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    correctOutputAsmName + " has irrelevant interfaces: " + classNode.interfaces,
                    classNode.interfaces.size(),
                    Matchers.equalTo(0)
            );
        }
//
//        String sourceFile = A_ASM_PATH.substring(A_ASM_PATH.lastIndexOf(File.separator) + 1) + EXTENSION_JAVA;
//        MatcherAssert.assertThat(
//                A_ASM_PATH + ".class has wrong source file ",
//                classNode.sourceFile,
//                Matchers.equalTo(sourceFile)
//        );
//        MatcherAssert.assertThat(
//                A_ASM_PATH + " has wrong module",
//                classNode.module,
//                Matchers.equalTo(null)
//        );

        return classNode;
    }

    private Set<String> getAllDefaultChecks() {
        Set<String> allDefaultsCheck = new HashSet<>();

        allDefaultsCheck.add(SUPER_CLASS_DEFAULT_CHECK);
        allDefaultsCheck.add(INTERFACES_DEFAULT_CHECK);

        return allDefaultsCheck;
    }

    private ClassNode getClassNode(String asmName, Path dir) throws IOException {
        Path path = asmNameToPath(asmName, dir);
        ClassReader classReader = new ClassReader(Files.readAllBytes(path));
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);
        return classNode;
    }

    private static Path asmNameToPath(String asmName, Path dir) {
        return dir.resolve(asmName + ByteCodeModificationMojo.EXTENSION_CLASS);
    }
} 