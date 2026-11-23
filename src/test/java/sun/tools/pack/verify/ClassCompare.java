/*
 * Copyright (c) 2010, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package sun.tools.pack.verify;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.objectweb.asm.Attribute;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Label;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.FrameNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.LineNumberNode;
import org.objectweb.asm.tree.LocalVariableAnnotationNode;
import org.objectweb.asm.tree.LocalVariableNode;
import org.objectweb.asm.tree.LookupSwitchInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.RecordComponentNode;
import org.objectweb.asm.tree.TableSwitchInsnNode;
import org.objectweb.asm.tree.TryCatchBlockNode;
import org.objectweb.asm.util.Textifier;
import org.objectweb.asm.util.TextifierSupport;
import org.objectweb.asm.util.TraceClassVisitor;

/**
 * Compares the classes of two jars, ignoring the differences the packer is allowed to introduce.
 */
public class ClassCompare {

    public static void compareClass(String jar1, String jar2, String className) throws IOException {
        boolean result = true;
        try (JarFile jf1 = new JarFile(jar1); JarFile jf2 = new JarFile(jar2)) {
            if (className == null) {
                for (JarEntry je : Collections.list(jf1.entries())) {
                    if (je.getName().endsWith(".class")) {
                        result &= compareClass(jf1, jf2, je);
                    }
                }
            } else {
                result = compareClass(jf1, jf2, jf1.getJarEntry(className));
            }
        }
        if (!result) {
            throw new RuntimeException("Class structural comparison failure");
        }
    }

    private static boolean compareClass(JarFile jf1, JarFile jf2, JarEntry je1) throws IOException {
        String name = je1.getName();
        JarEntry je2 = jf2.getJarEntry(name);
        if (je2 == null) {
            Globals.println("+++" + name + "+++\tFAIL");
            Globals.log(name + " not found in " + jf2.getName());
            return false;
        }

        byte[] class1 = read(jf1, je1);
        byte[] class2 = read(jf2, je2);
        if (Arrays.equals(class1, class2)) {
            Globals.println("+++" + name + "+++\tb/b:PASS");
            return true;
        }

        String text1 = describe(class1);
        String text2 = describe(class2);
        if (text1.equals(text2)) {
            Globals.println("+++" + name + "+++\tPASS");
            return true;
        }

        Globals.println("+++" + name + "+++\tFAIL");
        Globals.log("Classes differ: " + name);
        logDifference(text1, text2);
        return false;
    }

    /**
     * Returns a textual description of the class, independent of the constant pool
     * and of the order of the attributes.
     */
    static String describe(byte[] classfile) throws IOException {
        ClassReader reader = new ClassReader(classfile);
        ClassNode node = new ClassNode();
        int flags = Globals.ignoreDebugAttributes() ? ClassReader.SKIP_DEBUG : 0;
        reader.accept(node, unknownAttributePrototypes(classfile), flags);
        normalize(node);

        StringWriter out = new StringWriter();
        node.accept(new TraceClassVisitor(null, new Textifier(), new PrintWriter(out)));
        return out.toString();
    }

    /**
     * Sorts the InnerClasses entries and the unknown attributes, which the unpacker
     * reorders, and removes the unknown attributes if they are ignored. The order of
     * the standard attributes isn't significant since ClassNode visits them in a fixed order.
     */
    private static void normalize(ClassNode node) {
        if (node.innerClasses != null) {
            node.innerClasses.sort(Comparator.comparing(ic -> ic.name + " " + ic.outerName + " " + ic.innerName + " " + ic.access));
        }
        node.attrs = normalize(node.attrs);
        for (FieldNode field : node.fields) {
            field.attrs = normalize(field.attrs);
        }
        for (MethodNode method : node.methods) {
            method.attrs = normalize(method.attrs);
            removeUnusedLabels(method);
        }
        if (node.recordComponents != null) {
            for (RecordComponentNode component : node.recordComponents) {
                component.attrs = normalize(component.attrs);
            }
        }
    }

    /**
     * Removes the labels nothing refers to. ASM creates a label for the uninitialized types
     * of the stack map frames with a heuristic on the raw bytes of the StackMapTable attribute,
     * which may produce false positives depending on the constant pool indexes in the frames.
     */
    private static void removeUnusedLabels(MethodNode method) {
        Set<LabelNode> used = Collections.newSetFromMap(new IdentityHashMap<>());
        for (AbstractInsnNode insn : method.instructions) {
            if (insn instanceof JumpInsnNode) {
                used.add(((JumpInsnNode) insn).label);
            } else if (insn instanceof TableSwitchInsnNode) {
                used.add(((TableSwitchInsnNode) insn).dflt);
                used.addAll(((TableSwitchInsnNode) insn).labels);
            } else if (insn instanceof LookupSwitchInsnNode) {
                used.add(((LookupSwitchInsnNode) insn).dflt);
                used.addAll(((LookupSwitchInsnNode) insn).labels);
            } else if (insn instanceof LineNumberNode) {
                used.add(((LineNumberNode) insn).start);
            } else if (insn instanceof FrameNode) {
                addLabels(used, ((FrameNode) insn).local);
                addLabels(used, ((FrameNode) insn).stack);
            }
        }
        for (TryCatchBlockNode block : method.tryCatchBlocks) {
            used.add(block.start);
            used.add(block.end);
            used.add(block.handler);
        }
        if (method.localVariables != null) {
            for (LocalVariableNode variable : method.localVariables) {
                used.add(variable.start);
                used.add(variable.end);
            }
        }
        for (List<LocalVariableAnnotationNode> annotations : Arrays.asList(method.visibleLocalVariableAnnotations, method.invisibleLocalVariableAnnotations)) {
            if (annotations != null) {
                for (LocalVariableAnnotationNode annotation : annotations) {
                    used.addAll(annotation.start);
                    used.addAll(annotation.end);
                }
            }
        }

        for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; ) {
            AbstractInsnNode next = insn.getNext();
            if (insn instanceof LabelNode && !used.contains(insn)) {
                method.instructions.remove(insn);
            }
            insn = next;
        }
    }

    private static void addLabels(Set<LabelNode> used, List<Object> types) {
        if (types != null) {
            for (Object type : types) {
                if (type instanceof LabelNode) {
                    used.add((LabelNode) type);
                }
            }
        }
    }

    private static List<Attribute> normalize(List<Attribute> attributes) {
        if (attributes == null || Globals.ignoreUnknownAttributes()) {
            return null;
        }
        List<Attribute> sorted = new ArrayList<>(attributes);
        sorted.sort(Comparator.comparing(Attribute::toString));
        return sorted;
    }

    /**
     * Returns prototypes for all the attributes ASM doesn't know, to read their content.
     * The names of these attributes are necessarily among the Utf8 constants of the class.
     */
    private static Attribute[] unknownAttributePrototypes(byte[] classfile) throws IOException {
        List<Attribute> prototypes = new ArrayList<>();
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(classfile));
        in.skipBytes(8); // magic, minor and major versions
        int count = in.readUnsignedShort();
        for (int i = 1; i < count; i++) {
            int tag = in.readUnsignedByte();
            switch (tag) {
                case 1:  prototypes.add(new RawAttribute(in.readUTF(), null)); break; // Utf8
                case 5: case 6:  in.skipBytes(8); i++; break;                        // Long, Double
                case 7: case 8: case 16: case 19: case 20:  in.skipBytes(2); break;  // Class, String, MethodType, Module, Package
                case 15:  in.skipBytes(3); break;                                    // MethodHandle
                default:  in.skipBytes(4); break;                                    // Integer, Float, refs, NameAndType, Dynamic, InvokeDynamic
            }
        }
        return prototypes.toArray(new Attribute[0]);
    }

    /**
     * An attribute unknown to ASM, described by its raw content.
     */
    private static class RawAttribute extends Attribute implements TextifierSupport {
        private final byte[] content;

        RawAttribute(String type, byte[] content) {
            super(type);
            this.content = content;
        }

        @Override
        protected Attribute read(ClassReader classReader, int offset, int length, char[] charBuffer, int codeAttributeOffset, Label[] labels) {
            byte[] bytes = new byte[length];
            for (int i = 0; i < length; i++) {
                bytes[i] = (byte) classReader.readByte(offset + i);
            }
            return new RawAttribute(type, bytes);
        }

        @Override
        public void textify(StringBuilder output, Map<Label, String> labelNames) {
            output.append(" : ").append(toString()).append('\n');
        }

        @Override
        public String toString() {
            StringBuilder hex = new StringBuilder(type).append(' ');
            for (byte b : content) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        }
    }

    private static void logDifference(String text1, String text2) {
        String[] lines1 = text1.split("\n");
        String[] lines2 = text2.split("\n");
        int i = 0;
        while (i < lines1.length && i < lines2.length && lines1[i].equals(lines2[i])) {
            i++;
        }
        Globals.println("First difference at line " + (i + 1) + " (" + lines1.length + " and " + lines2.length + " lines)");
        for (int k = Math.max(0, i - 2); k < Math.min(lines1.length, i + 8); k++) {
            Globals.println("< " + lines1[k]);
        }
        for (int k = Math.max(0, i - 2); k < Math.min(lines2.length, i + 8); k++) {
            Globals.println("> " + lines2[k]);
        }
    }

    private static byte[] read(JarFile jar, JarEntry entry) throws IOException {
        byte[] bytes = new byte[(int) entry.getSize()];
        try (DataInputStream in = new DataInputStream(jar.getInputStream(entry))) {
            in.readFully(bytes);
        }
        return bytes;
    }
}
