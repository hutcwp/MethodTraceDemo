package com.hutcwp.plugin;

import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * author : kevin
 * date : 2021/9/19 3:46 AM
 * description :
 */
public class MethodTraceClassVisitor extends ClassVisitor implements Opcodes {


    private boolean needHook = true;

    private String mClassName = "";
    private boolean isABSClass = false;


    public MethodTraceClassVisitor(ClassVisitor classVisitor) {
        super(Opcodes.ASM7, classVisitor);
    }

    @Override
    public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
        super.visit(version, access, name, signature, superName, interfaces);
        this.mClassName = name;
        if ((access & Opcodes.ACC_ABSTRACT) > 0 || (access & Opcodes.ACC_INTERFACE) > 0) {
            this.isABSClass = true;
        }
    }


    @Override
    public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
        if (isABSClass) {
            return super.visitMethod(access, name, descriptor, signature, exceptions);
        }

        MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
        String methodName = mClassName + "#" + name;
        System.out.println("visitMethod: name=" + methodName);
        if (canHook(name)) {
            System.out.println("hook this");
            return new TraceMethodVisitor(methodName, Opcodes.ASM7, access, descriptor, methodVisitor);
//            return new TimeCostMethodVisitor(Opcodes.ASM7, access, descriptor, methodVisitor);
        }

        return methodVisitor;
    }

    private boolean canHook(String methodName) {
        return needHook && isValidMethod(methodName);
    }

    private boolean isValidMethod(String methodName) {
        return !"<init>".equals(methodName);
    }

}
