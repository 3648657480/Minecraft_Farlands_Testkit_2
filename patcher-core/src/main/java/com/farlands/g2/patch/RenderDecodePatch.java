package com.farlands.g2.patch;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * B-render of {@code docs/RENDER-WIDE-KEYS.md}: inside the client renderer,
 * replace the packed-long decoders {@code SectionPos.of/x/y/z(long)} with
 * {@code RenderKeys.pos/x/y/z(long)}.
 *
 * <p>The descriptors are unchanged, so no call site shape changes: only the
 * owner of the static call. {@code RenderKeys} resolves the long through the
 * wide {@code SectionKey} table and falls back to {@code SectionPos} for longs
 * that were not produced by the render pipeline, so normal worlds are
 * unaffected.</p>
 *
 * <p>Scope is limited to {@code net/minecraft/client/renderer/**}; entity and
 * lighting code keeps the packed layout.</p>
 */
public final class RenderDecodePatch implements ClassPatch {

    private static final String PREFIX = "net/minecraft/client/renderer/";
    private static final String SECTION_POS = "net/minecraft/core/SectionPos";
    private static final String RENDER_KEYS = "com/farlands/g2/client/RenderKeys";

    @Override
    public boolean matches(String internalName) {
        return internalName.startsWith(PREFIX);
    }

    @Override
    public byte[] apply(byte[] original) {
        ClassNode node = new ClassNode();
        new ClassReader(original).accept(node, 0);
        boolean changed = false;
        for (MethodNode method : node.methods) {
            for (AbstractInsnNode insn : method.instructions) {
                if (!(insn instanceof MethodInsnNode call)) {
                    continue;
                }
                if (call.getOpcode() != Opcodes.INVOKESTATIC
                    || !SECTION_POS.equals(call.owner)) {
                    continue;
                }
                if ("(J)Lnet/minecraft/core/SectionPos;".equals(call.desc)
                    && "of".equals(call.name)) {
                    call.owner = RENDER_KEYS;
                    call.name = "pos";
                    changed = true;
                } else if ("(J)I".equals(call.desc)
                    && ("x".equals(call.name) || "y".equals(call.name) || "z".equals(call.name))) {
                    call.owner = RENDER_KEYS;
                    changed = true;
                }
            }
        }
        if (!changed) {
            return original;
        }
        ClassWriter cw = new SafeClassWriter(ClassWriter.COMPUTE_FRAMES);
        node.accept(cw);
        return cw.toByteArray();
    }
}
