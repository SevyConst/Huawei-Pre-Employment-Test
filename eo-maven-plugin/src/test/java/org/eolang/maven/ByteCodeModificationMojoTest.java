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

import org.eolang.maven.name.ObjectName;
import org.eolang.maven.name.OnVersioned;
import org.eolang.maven.util.HmBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.*;

import javax.tools.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;


public class ByteCodeModificationMojoTest {

    private static final String CLASS_A_PATH_ASM = "org" + File.separator + "eolang" + File.separator + "A";
    private static final String CLASS_A_DESCRIPTOR = "L" + CLASS_A_PATH_ASM + ";";
    private static final String CLASS_B_PATH_ASM = "org" + File.separator + "eolang" + File.separator + "B";
    private static final String CLASS_B_DESCRIPTOR = "L" + CLASS_B_PATH_ASM + ";";
    private static final String CLASS_C_PATH_ASM = "org" + File.separator + "eolang" + File.separator + "C";
    private static final String CLASS_C_DESCRIPTOR = "L" + CLASS_C_PATH_ASM + ";";

    public static Path SRC = Paths.get("src/test/resources/org/eolang/maven/bytecode-modification/");


    private static final String RELATIVE_INPUT_DIR = "target" + File.separator + "classes";
    private static final String RELATIVE_OUTPUT_DIR = "target" + File.separator + "modified-classes";
    private static final String HASH = "qwerty";

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

        Path outPathClassA = outputDirPath.resolve(HASH).resolve(CLASS_A_PATH_ASM);
//        ClassReader classReader = new ClassReader(Files.readAllBytes(outPathClassA));
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

        List<File> filesToCompile = new ArrayList<>();
        filesToCompile.add(SRC.resolve("org").resolve("eolang").resolve("interfaces").resolve("InterfaceVersionized.java").toFile());
        filesToCompile.add(SRC.resolve("org").resolve("eolang").resolve("A.java").toFile());
        filesToCompile.add(SRC.resolve("org").resolve("eolang").resolve("B.java").toFile());
        filesToCompile.add(SRC.resolve("org").resolve("eolang").resolve("C.java").toFile());
        filesToCompile.add(SRC.resolve("org").resolve("eolang").resolve("Versionized.java").toFile());
        filesToCompile.add(SRC.resolve("org").resolve("eolang").resolve("annotations").resolve("AnnotationVersionized.java").toFile());
        filesToCompile.add(SRC.resolve("org").resolve("eolang").resolve("other").resolve("LocalVariable.java").toFile());
        filesToCompile.add(SRC.resolve("org").resolve("eolang").resolve("other").resolve("Generic.java").toFile());
        filesToCompile.add(SRC.resolve("org").resolve("eolang").resolve("other").resolve("MethodSignature.java").toFile());
        filesToCompile.add(SRC.resolve("org").resolve("eolang").resolve("other").resolve("VersionizedClass.java").toFile());

        Iterable<? extends JavaFileObject> compilationUnits = fileManager.getJavaFileObjectsFromFiles(filesToCompile);
        List<String> javacOptions = new ArrayList<>();
        javacOptions.add("-d");
        javacOptions.add(outputPath.resolve(RELATIVE_INPUT_DIR).toAbsolutePath().toString());
        compiler.getTask(null,
                fileManager,
                null,
                javacOptions,
                null,
                compilationUnits
        ).call();
    }


//    private class ClassFiller extends ClassVisitor {
//        public ClassFiller() {
//            super(ByteCodeModificationMojo.OPCODE_ASM_VERSION);
//        }
//
//        @Override
//        public void visit(int version,
//                          int access,
//                          String name,
//                          String signature,
//                          String superName,
//                          String[] interfaces
//        ) {
//
//            AnnotationVisitor av = visitAnnotation(
//                    ByteCodeModificationMojo.PATH_TO_VERSIONIZED,
//                    true
//            );
//            Object value = "the-value-to-set";
//
//            // This sets a parameter on the annotation
//            // it could be called more than once for multiple parameters
//            if (av != null) {
//                av.visit("value", value);
//            }
//            super.visit(version, access, name, signature, superName, interfaces);
//        }
//    }
}
