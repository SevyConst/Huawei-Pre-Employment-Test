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
import org.objectweb.asm.tree.FieldNode;

import javax.tools.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;

public class ModifyBytecodeMojoTest {

    private static final Path RELATIVE_INPUT_DIR = Paths.get("target/classes");
    private static final Path RELATIVE_OUTPUT_DIR = Paths.get("target/modified-classes");
    private static final String HASH = "qwerty";

    public static final Path SRC = Paths.get("src/test/resources/org/eolang/maven/bytecode-modification/java-files");

    public static final String EXTENSION_JAVA = ".java";
    private static final Set<String> GLOB_JAVA_FILES = new SetOf<>("**/*.java");

    private static final String SUPER_CLASS_DEFAULT_CHECK = "SUPER_CLASS_DEFAULT_CHECK";
    private static final String INTERFACES_DEFAULT_CHECK = "INTERFACES_DEFAULT_CHECK";
    private static final String MODULE_DEFAULT_CHECK = "MODULE_DEFAULT_CHECK";
    private static final String OUTER_CLASS_DEFAULT_CHECK = "OUTER_CLASS_DEFAULT_CHECK";
    private static final String VISIBLE_ANNOTATIONS_DEFAULT_CHECK = "VISIBLE_ANNOTATIONS_DEFAULT_CHECK";
    private static final String INVISIBLE_ANNOTATIONS_DEFAULT_CHECK = "INVISIBLE_ANNOTATIONS_DEFAULT_CHECK";
    private static final String VISIBLE_TYPE_ANNOTATIONS_DEFAULT_CHECK = "VISIBLE_TYPE_ANNOTATIONS_DEFAULT_CHECK";
    private static final String INVISIBLE_TYPE_ANNOTATIONS_DEFAULT_CHECK = "INVISIBLE_TYPE_ANNOTATIONS_DEFAULT_CHECK";
    private static final String ATTRS_DEFAULT_CHECK = "ATTRS_DEFAULT_CHECK";
    private static final String INNER_CLASSES_DEFAULT_CHECK = "INNER_CLASSES_DEFAULT_CHECK";
    private static final String NEST_HOST_CLASS_DEFAULT_CHECK = "NEST_HOST_CLASS_DEFAULT_CHECK";
    private static final String NEST_MEMBERS_DEFAULT_CHECK = "NEST_MEMBERS_DEFAULT_CHECK";

    private static final char ASM_SLASH = '/';
    private static final String ASM_OBJECT = "java/lang/Object";
    private static final String ASM_DEFAULT_PATH = "org/eolang/";
    private static final String ASM_A = HASH + ASM_DEFAULT_PATH + "A";
    private static final String ASM_DESC_A = asmNameToAsmDesc(ASM_A);
    private static final String ASM_B = HASH + ASM_DEFAULT_PATH + "B";
    private static final String ASM_DESC_B = asmNameToAsmDesc(ASM_B);
    private static final String ASM_C = ASM_DEFAULT_PATH + "C";
    private static final String ASM_DESC_C = asmNameToAsmDesc(ASM_C);
    private static final String ASM_VERSIONIZED = ASM_DEFAULT_PATH + "Versionized";
    private static final String ASM_DESC_VERSIONIZED = asmNameToAsmDesc(ASM_VERSIONIZED);
    private static final String ASM_

    /**
     * 1. Read special .java files from the resources path.
     * <p>
     * 2. Compile it to .class files and save binaries to the input directory.
     * <p>
     * 3. Create {@code Set<String>}. A key is a relative path to .class file without
     * file extension - this format is convenient for using ASM library.
     * <p>
     * 4. If an input class doesn't have {@code Versionized} annotation AND doesn't contain the usage of any class that
     * have {@code Versionized} annotation THEN remove corresponding item from the Set via method
     * {@link ModifyBytecodeMojoTest#removeUnmodifiedClasses(Set)}
     * <p>
     * 5. Execute the {@link ModifyBytecodeMojo}
     * <p>
     * 6. Create {@code Collection<Path>} with paths to all binaries in the output directory.
     * <p>
     * 7. Match the input and the output files. As soon as one match was defined remove the corresponding item from the
     * set and remove the corresponding item from the collection. In the same for-loop explore and check the output
     * binaries via ASM library.
     */
    @Test
    public void bigIntegrationTest(@TempDir final Path temp) throws Exception {
        Path inputDirPath = temp.resolve(RELATIVE_INPUT_DIR);
        Path outputDirPath = temp.resolve(RELATIVE_OUTPUT_DIR);

        compile(inputDirPath);

        Set<String> inputAsmNames = new Walk(inputDirPath)
                .includes(ModifyBytecodeMojo.GLOB_CLASS_FILES)
                .stream()
                .map(path -> ModifyBytecodeMojo.pathToAsmName(path, inputDirPath))
                .collect(Collectors.toSet());
        removeUnmodifiedClasses(inputAsmNames);

        new FakeMaven(temp)
                .with("inputDir", inputDirPath)
                .with("outputDir", outputDirPath)
                .with("hash", HASH)
                .execute(ModifyBytecodeMojo.class);

        Collection<Path> outputPaths = new Walk(outputDirPath).includes(ModifyBytecodeMojo.GLOB_CLASS_FILES);
        for (String inputAsmName : inputAsmNames) {
            switch (inputAsmName) {
                case ASM_A:
                    checkClassA(inputAsmName, outputPaths, outputDirPath);
                    break;
                case ASM_B:
                    checkClassB(inputAsmName, outputPaths, outputDirPath);
                    break;
                case ASM_C:
                    checkClassC(inputAsmName, outputPaths, outputDirPath);
                    break;
                case "Interface":
                    checkClassInterfaceUsage(inputAsmName, outputPaths, outputDirPath);
                    break;
                default:
                    MatcherAssert.
            }

            inputAsmNames.remove(inputAsmName);
        }

        MatcherAssert.assertThat(
                "Can't check input classes: " + inputAsmNames,
                inputAsmNames.size(),
                Matchers.equalTo(0)
        );

        MatcherAssert.assertThat(
                "Irrelevant output classes: " + outputPaths,
                outputPaths.size(),
                Matchers.equalTo(0)
        );
    }

    void removeUnmodifiedClasses(Set<String> inputAsmNames) {
        inputAsmNames.remove(ASM_VERSIONIZED);
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

    private Set<String> getAllDefaultClassChecks() {
        Set<String> allDefaultsClassChecks = new HashSet<>();

        allDefaultsClassChecks.add(SUPER_CLASS_DEFAULT_CHECK);
        allDefaultsClassChecks.add(INTERFACES_DEFAULT_CHECK);
        allDefaultsClassChecks.add(MODULE_DEFAULT_CHECK);
        allDefaultsClassChecks.add(OUTER_CLASS_DEFAULT_CHECK);
        allDefaultsClassChecks.add(VISIBLE_ANNOTATIONS_DEFAULT_CHECK);
        allDefaultsClassChecks.add(INVISIBLE_ANNOTATIONS_DEFAULT_CHECK);
        allDefaultsClassChecks.add(VISIBLE_TYPE_ANNOTATIONS_DEFAULT_CHECK);
        allDefaultsClassChecks.add(INVISIBLE_TYPE_ANNOTATIONS_DEFAULT_CHECK);
        allDefaultsClassChecks.add(ATTRS_DEFAULT_CHECK);
        allDefaultsClassChecks.add(INNER_CLASSES_DEFAULT_CHECK);
        allDefaultsClassChecks.add(NEST_HOST_CLASS_DEFAULT_CHECK);
        allDefaultsClassChecks.add(NEST_MEMBERS_DEFAULT_CHECK);

        return allDefaultsClassChecks;
    }

    private Set<String> getAllDefaultFieldChecks() {
        Set<String> allDefaultFieldChecks = new HashSet<>();

        allDefaultFieldChecks.add(VISIBLE_ANNOTATIONS_DEFAULT_CHECK);
        allDefaultFieldChecks.add(INVISIBLE_ANNOTATIONS_DEFAULT_CHECK);
        allDefaultFieldChecks.add(VISIBLE_TYPE_ANNOTATIONS_DEFAULT_CHECK);
        allDefaultFieldChecks.add(INVISIBLE_TYPE_ANNOTATIONS_DEFAULT_CHECK);
        allDefaultFieldChecks.add(ATTRS_DEFAULT_CHECK);

        return allDefaultFieldChecks;
    }

    /**
     * @return ClassNode for creating custom checks
     */
    private ClassNode doDefaultClassChecks(
            String inputAsmName,
            Collection<Path> outputPaths,
            Path outputDirPath,
            Set<String> defaultClassChecks,
            boolean isVersionized
    ) throws IOException {

        String correctOutputAsmName = isVersionized ? HASH + File.separator + inputAsmName : inputAsmName;

        Optional<Path> outputPathOptional = outputPaths
                .stream()
                .filter(path -> ModifyBytecodeMojo.pathToAsmName(path, outputDirPath).equals(inputAsmName))
                .findFirst();
        MatcherAssert.assertThat(
                "Can't find output .class file with name in ASM format: " + correctOutputAsmName,
                outputPathOptional.isPresent(),
                Matchers.equalTo(true)
        );
        outputPaths.remove(outputPathOptional.get());

        ClassNode classNode = getClassNode(correctOutputAsmName, outputDirPath);
        MatcherAssert.assertThat(
                "Wrong name in ASM format",
                classNode.name,
                Matchers.equalTo(correctOutputAsmName)
        );

        if (defaultClassChecks.contains(SUPER_CLASS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    correctOutputAsmName + " has wrong superclass",
                    classNode.superName,
                    Matchers.equalTo(ASM_OBJECT)
            );
        }

        if (defaultClassChecks.contains(INTERFACES_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    correctOutputAsmName + " has irrelevant interfaces: " + classNode.interfaces,
                    classNode.interfaces.size(),
                    Matchers.equalTo(0)
            );
        }

        String sourceFile = correctOutputAsmName.substring(
                correctOutputAsmName.lastIndexOf(ASM_SLASH) + 1) + EXTENSION_JAVA;
        MatcherAssert.assertThat(
                correctOutputAsmName + "has wrong name inside bytecode: ",
                classNode.sourceFile,
                Matchers.equalTo(sourceFile)
        );

        if (defaultClassChecks.contains(MODULE_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    correctOutputAsmName + " has wrong module: " + classNode.module,
                    classNode.module,
                    Matchers.equalTo(null)
            );
        }

        if (defaultClassChecks.contains(OUTER_CLASS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    correctOutputAsmName + " has outer class",
                    classNode.outerClass,
                    Matchers.equalTo(null)
            );
        }

        doDefaultClassChecksAnnotations(
                classNode,
                defaultClassChecks,
                correctOutputAsmName,
                isVersionized
        );

        if (defaultClassChecks.contains(ATTRS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    correctOutputAsmName + " has irrelevant attributes" + classNode.attrs,
                    classNode.attrs,
                    Matchers.equalTo(null)
            );
        }

        if (defaultClassChecks.contains(INNER_CLASSES_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    correctOutputAsmName + "has irrelevant inner classes" + classNode.innerClasses,
                    classNode.innerClasses.size(),
                    Matchers.equalTo(0)
            );
        }

        if (defaultClassChecks.contains(NEST_HOST_CLASS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    correctOutputAsmName + "has nest host class",
                    classNode.nestHostClass,
                    Matchers.equalTo(null)
            );
        }

        if (defaultClassChecks.contains(NEST_MEMBERS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    correctOutputAsmName + " has irrelevant nest members" + classNode.nestMembers,
                    classNode.nestMembers,
                    Matchers.equalTo(null)
            );
        }

        return classNode;
    }

    /**
     * The definitions of visible and invisible annotations could be found in the ASM library doc
     */
    private void doDefaultClassChecksAnnotations(
            ClassNode classNode,
            Set<String> defaultClassChecks,
            String correctOutputAsmName,
            boolean isVersionized
    ) {

        if (defaultClassChecks.contains(VISIBLE_ANNOTATIONS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    correctOutputAsmName + " has irrelevant visible annotations: "
                            + classNode.visibleAnnotations,
                    classNode.visibleAnnotations,
                    Matchers.equalTo(null)
            );
        }

        if (defaultClassChecks.contains(INVISIBLE_ANNOTATIONS_DEFAULT_CHECK)) {

            if (isVersionized) {
                MatcherAssert.assertThat(
                        correctOutputAsmName + " has wrong number of invisible annotations: "
                                + classNode.invisibleAnnotations,
                        classNode.invisibleAnnotations.size(),
                        Matchers.equalTo(1)
                );
                MatcherAssert.assertThat(
                        correctOutputAsmName + " does not have Versionized annotation",
                        classNode.invisibleAnnotations
                                .stream()
                                .map(a -> a.desc)
                                .anyMatch(ASM_DESC_VERSIONIZED::equals),
                        Matchers.equalTo(true)
                );


            } else {
                MatcherAssert.assertThat(
                        correctOutputAsmName + " has irrelevant invisible annotations: "
                                + classNode.invisibleAnnotations,
                        classNode.invisibleAnnotations,
                        Matchers.equalTo(null)
                );
            }
        }



        if (defaultClassChecks.contains(VISIBLE_TYPE_ANNOTATIONS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    correctOutputAsmName + " has irrelevant visible type annotations: "
                            + classNode.visibleTypeAnnotations,
                    classNode.visibleTypeAnnotations,
                    Matchers.equalTo(null)
            );
        }

        if (defaultClassChecks.contains(INVISIBLE_TYPE_ANNOTATIONS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    correctOutputAsmName + " has irrelevant invisible type annotations: "
                            + classNode.invisibleTypeAnnotations,
                    classNode.invisibleTypeAnnotations,
                    Matchers.equalTo(null)
            );
        }
    }

    private void doDefaultFieldChecks(
            FieldNode fieldNode,
            Set<String> defaultFieldChecks,
            String inputAsmName
    ) {

        if (defaultFieldChecks.contains(VISIBLE_ANNOTATIONS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    "The output class for " + inputAsmName + " has field " + fieldNode.desc
                            + " with irrelevant visible annotations: "
                            + fieldNode.visibleAnnotations,
                    fieldNode.visibleAnnotations,
                    Matchers.equalTo(null)
            );
        }

        if (defaultFieldChecks.contains(INVISIBLE_ANNOTATIONS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    "The output class for " + inputAsmName + " has field " + fieldNode.desc
                            + " with irrelevant invisible annotations: " + fieldNode.visibleAnnotations
                            + fieldNode.invisibleAnnotations,
                    fieldNode.invisibleAnnotations,
                    Matchers.equalTo(null)
            );
        }

        if (defaultFieldChecks.contains(VISIBLE_TYPE_ANNOTATIONS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    "The output class for " + inputAsmName + " has field " + fieldNode.desc
                            + " with irrelevant visible type annotations: "
                            + fieldNode.visibleAnnotations,
                    fieldNode.visibleTypeAnnotations,
                    Matchers.equalTo(null)
            );
        }

        if (defaultFieldChecks.contains(INVISIBLE_TYPE_ANNOTATIONS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    "The output class for " + inputAsmName + " has field " + fieldNode.desc
                            + " with irrelevant invisible type annotations: "
                            + fieldNode.invisibleAnnotations,
                    fieldNode.invisibleTypeAnnotations,
                    Matchers.equalTo(null)
            );
        }

        if (defaultFieldChecks.contains(ATTRS_DEFAULT_CHECK)) {
            MatcherAssert.assertThat(
                    "The output class for " + inputAsmName + " has field " + fieldNode.desc
                            + " with irrelevant attributes: ",
                    fieldNode.attrs,
                    Matchers.equalTo(null)
            );
        }

    }

    private ClassNode getClassNode(String asmName, Path dir) throws IOException {
        Path path = asmNameToPath(asmName, dir);
        ClassReader classReader = new ClassReader(Files.readAllBytes(path));
        ClassNode classNode = new ClassNode();
        classReader.accept(classNode, 0);
        return classNode;
    }

    private Path asmNameToPath(String asmName, Path dir) {
        return dir.resolve(asmName + ModifyBytecodeMojo.EXTENSION_CLASS);
    }

    private void checkClassA(
            String inputAsmName,
            Collection <Path> outputPaths,
            Path outputDirPath
    ) throws IOException {

        Set<String> defaultClassChecks = getAllDefaultClassChecks();
        ClassNode classNode = doDefaultClassChecks(
                inputAsmName,
                outputPaths,
                outputDirPath,
                defaultClassChecks,
                true
        );

        MatcherAssert.assertThat(
                "The output class for " + inputAsmName + " has wrong number of fields",
                classNode.fields.size(),
                Matchers.equalTo(2)
        );
        MatcherAssert.assertThat(
                "The output class for " + inputAsmName + "doesn't have field " + ASM_DESC_B,
                classNode.fields.stream().map(f -> f.desc).anyMatch(ASM_DESC_B::equals),
                Matchers.equalTo(true)
        );
        MatcherAssert.assertThat(
                "The output class for " + inputAsmName + "doesn't have field " + ASM_DESC_C,
                classNode.fields.stream().map(f -> f.desc).anyMatch(ASM_DESC_C::equals),
                Matchers.equalTo(true)
        );

        classNode.fields.forEach(field -> doDefaultFieldChecks(
                field, getAllDefaultFieldChecks(),
                inputAsmName
        ));

        MatcherAssert.assertThat(
                "The output class for " + inputAsmName
                        + " has irrelevant number of methods/constructors: " + classNode.methods,
                classNode.methods.size(),
                Matchers.equalTo(1)
        );
    }

    private void checkClassB(
            String inputAsmName,
            Collection<Path> outputPaths,
            Path outputDirPath
    ) throws IOException {

        Set<String> defaultClassChecks = getAllDefaultClassChecks();
        ClassNode classNode = doDefaultClassChecks(
                inputAsmName,
                outputPaths,
                outputDirPath,
                defaultClassChecks,
                true
        );

        MatcherAssert.assertThat(
                "The output class for " + inputAsmName + "doesn't have field " + ASM_DESC_C,
                classNode.fields.stream().map(f -> f.desc).anyMatch(ASM_DESC_C::equals),
                Matchers.equalTo(true)
        );
        MatcherAssert.assertThat(
                "The output class for " + inputAsmName + " has wrong number of fields",
                classNode.fields.size(),
                Matchers.equalTo(1)
        );

        doDefaultFieldChecks(classNode.fields.get(0), getAllDefaultFieldChecks(), inputAsmName);

        MatcherAssert.assertThat(
                "The output class for " + inputAsmName
                        + " has irrelevant number of methods/constructors: " + classNode.methods,
                classNode.methods.size(),
                Matchers.equalTo(1)
        );
    }

    private void checkClassC(
            String inputAsmName,
            Collection<Path> outputPaths,
            Path outputDirPath
    ) throws IOException {

        Set<String> defaultClassChecks = getAllDefaultClassChecks();
        ClassNode classNode = doDefaultClassChecks(
                inputAsmName,
                outputPaths,
                outputDirPath,
                defaultClassChecks,
                false
        );

        MatcherAssert.assertThat(
                "The output class for " + inputAsmName + "doesn't have field " + ASM_DESC_A,
                classNode.fields.stream().map(f -> f.desc).anyMatch(ASM_DESC_A::equals),
                Matchers.equalTo(true)
        );
        MatcherAssert.assertThat(
                "The output class for " + inputAsmName + " has wrong number of fields",
                classNode.fields.size(),
                Matchers.equalTo(1)
        );

        classNode.fields.forEach(field -> doDefaultFieldChecks(
                field, getAllDefaultFieldChecks(),
                inputAsmName
        ));

        MatcherAssert.assertThat(
                "The output class for " + inputAsmName
                        + " has irrelevant number of methods/constructors: " + classNode.methods,
                classNode.methods.size(),
                Matchers.equalTo(1)
        );
    }

    private void checkClassInterfaceUsage(
        String inputAsmName,
        Collection<Path> outputPaths,
        Path outputDirPath
    ) throws IOException {

        Set<String> defaultClassChecks = getAllDefaultClassChecks();
        ClassNode classNode = doDefaultClassChecks(
                inputAsmName,
                outputPaths,
                outputDirPath,
                defaultClassChecks,
                true
        );

        MatcherAssert.assertThat(
                "The output class for " + inputAsmName + " has wrong number of fields",
                classNode.fields.size(),
                Matchers.equalTo(0)
        );
        MatcherAssert.assertThat(
                "The output class for " + inputAsmName
                        + " has irrelevant number of methods/constructors: " + classNode.methods,
                classNode.methods.size(),
                Matchers.equalTo(1)
        );
    }

    private static String asmNameToAsmDesc(String asmClass) {
        return "L" + asmClass + ";";
    }
}