package com.hutcwp.plugin;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.LocalVariablesSorter;

/**
 * author : kevin
 * date : 2021/9/19 11:37 PM
 * description :
 */
public class TraceMethodVisitor extends LocalVariablesSorter implements Opcodes {


    protected TraceMethodVisitor(int api, int access, String descriptor, MethodVisitor methodVisitor) {
        super(api, access, descriptor, methodVisitor);
    }


    @Override
    public void visitCode() {
//        mv.visitLdcInsn("TraceMainActivity");
//        // 调用该方法
//        mv.visitMethodInsn(INVOKEVIRTUAL, "android/os/Trace", "beginSection", "(Ljava/lang/String;)V", false);
        mv.visitLdcInsn("TraceMainActivity");
        mv.visitMethodInsn(INVOKESTATIC, "android/os/Trace", "beginSection", "(Ljava/lang/String;)V", false);

        super.visitCode();
    }

    @Override
    public void visitInsn(int opcode) {
        if (opcode == RETURN) {
            mv.visitMethodInsn(INVOKESTATIC, "android/os/Trace", "endSection", "()V", false);
        }
        super.visitInsn(opcode);
    }

}
