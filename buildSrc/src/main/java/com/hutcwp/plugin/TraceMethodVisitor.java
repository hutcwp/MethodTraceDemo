package com.hutcwp.plugin;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.commons.AdviceAdapter;
import org.objectweb.asm.commons.LocalVariablesSorter;

/**
 * author : kevin date : 2021/9/19 11:37 PM description :
 */
public class TraceMethodVisitor extends AdviceAdapter {

    private String methodName = "defaultName";

    protected TraceMethodVisitor(String methodName, int api, int access, String descriptor,
        MethodVisitor methodVisitor) {
        super(api, methodVisitor, access, methodName, descriptor);
        this.methodName = methodName;
    }

    @Override
    protected void onMethodEnter() {
        mv.visitLdcInsn(methodName);
        mv.visitMethodInsn(INVOKESTATIC, "android/os/Trace", "beginSection", "(Ljava/lang/String;)V", false);
    }

    @Override
    protected void onMethodExit(int opcode) {
        mv.visitMethodInsn(INVOKESTATIC, "android/os/Trace", "endSection", "()V", false);
    }
}
