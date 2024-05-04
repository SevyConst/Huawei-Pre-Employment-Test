package org.eolang.maven;

import org.eolang.maven.util.HmBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ByteCodeModificationMojoTest {

    private static final String CLASS_A_PATH_ASM = "org" + File.separator + "eolang" + File.separator + "A";
    private static final String CLASS_A_DESCRIPTOR = "L" + CLASS_A_PATH_ASM + ";";
    private static final String CLASS_B_PATH_ASM = "org" + File.separator + "eolang" + File.separator + "B";
    private static final String CLASS_B_DESCRIPTOR = "L" + CLASS_B_PATH_ASM + ";";
    private static final String CLASS_C_PATH_ASM = "org" + File.separator + "eolang" + File.separator + "C";
    private static final String CLASS_C_DESCRIPTOR = "L" + CLASS_C_PATH_ASM + ";";

    private static final String RELATIVE_INPUT_DIR = "target" + File.separator + "classes";
    private static final String RELATIVE_OUTPUT_DIR = "target" + File.separator + "modified-classes";

    @Test
    public void test(@TempDir final Path temp) throws Exception {
        createByteCodeClassA(temp);
        createByteCodeClassB(temp);
        createByteCodeClassC(temp);

        new FakeMaven(temp)
                .with("inputDir", temp.resolve(RELATIVE_INPUT_DIR))
                .with("outputDir", temp.resolve(RELATIVE_OUTPUT_DIR))
                .with("hash", "qwerty1")
                .execute(ByteCodeModificationMojo.class);
    }

    private void createByteCodeClassA(Path temp) throws IOException {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                CLASS_A_PATH_ASM,
                null,
                ByteCodeModificationMojo.OBJECT_PATH_ASM,
                null
        );
        classWriter.visitAnnotation(ByteCodeModificationMojo.PATH_TO_VERSIONIZED, true);
        classWriter.visitField(
                Opcodes.ACC_PRIVATE,
                "b",
                CLASS_B_DESCRIPTOR,
                null,
                null
        );
        classWriter.visitField(
                Opcodes.ACC_PRIVATE,
                "c",
                CLASS_C_DESCRIPTOR,
                null,
                null
        );

        saveClassFile(temp, classWriter.toByteArray(), CLASS_A_PATH_ASM);
    }

    private void createByteCodeClassB(Path temp) throws IOException {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                CLASS_B_PATH_ASM,
                null,
                ByteCodeModificationMojo.OBJECT_PATH_ASM,
                null
        );
        classWriter.visitAnnotation(ByteCodeModificationMojo.PATH_TO_VERSIONIZED, true);
        classWriter.visitField(
                Opcodes.ACC_PRIVATE,
                "c",
                CLASS_C_DESCRIPTOR,
                null,
                null
        );

        saveClassFile(temp, classWriter.toByteArray(), CLASS_B_PATH_ASM);
    }

    private void createByteCodeClassC(Path temp) throws IOException {
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        classWriter.visit(Opcodes.V1_8,
                Opcodes.ACC_PUBLIC,
                CLASS_C_PATH_ASM,
                null,
                ByteCodeModificationMojo.OBJECT_PATH_ASM,
                null
        );
        classWriter.visitField(
                Opcodes.ACC_PRIVATE,
                "a",
                CLASS_A_DESCRIPTOR,
                null,
                null
        );

        saveClassFile(temp, classWriter.toByteArray(), CLASS_C_PATH_ASM);
    }

    private void saveClassFile(
            final Path temp,
            final byte[] content,
            final String className) throws IOException {
        new HmBase(temp.resolve(RELATIVE_INPUT_DIR))
                .save(content, Paths.get( className + ByteCodeModificationMojo.EXTENSION_CLASS));
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
