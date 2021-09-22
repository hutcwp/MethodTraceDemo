package com.hutcwp.plugin;


import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;

import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.QualifiedContent.Scope;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.zip.ZipEntry;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;


/**
 * author : kevin date : 2021/9/19 12:48 AM description :
 */
public class MethodTraceTransform extends Transform {

    private Project mProject;

    private static final String TYPE_DIR = "dir";
    private static final String TYPE_JAR = "jar";


    public MethodTraceTransform(Project p) {
        this.mProject = p;
    }

    @Override
    public String getName() {
        return "MethodTraceTransform";
    }

    @Override
    public Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS;
    }

    @Override
    public Set<? super QualifiedContent.Scope> getScopes() {
        return ImmutableSet.of(Scope.PROJECT, Scope.SUB_PROJECTS);
    }

    @Override
    public boolean isIncremental() {
        return false;
    }

    @Override
    public void transform(TransformInvocation transformInvocation) throws IOException {
        TransformOutputProvider outputProvider = transformInvocation.getOutputProvider();
        Collection<TransformInput> inputs = transformInvocation.getInputs();

        boolean isIncremental = transformInvocation.isIncremental();
        //如果不是增量，就删除之前所有产生的输出，重头来过
        if (!isIncremental) {
            outputProvider.deleteAll();
        }

        for (TransformInput input : inputs) {
            Collection<DirectoryInput> directoryInputs = input.getDirectoryInputs();
            Collection<JarInput> jarInputs = input.getJarInputs();
            for (JarInput jarInput : jarInputs) {
                if (isIncremental) {
                    //增量处理Jar文件
                    handleJarIncremental(jarInput, outputProvider);
                } else {
                    //非增量处理Jar文件
                    handJarInput(jarInput, outputProvider);
                }
            }

            for (DirectoryInput directoryInput : directoryInputs) {
                if (isIncremental) {
                    //增量处理目录文件
                    handleDirectoryIncremental(directoryInput, outputProvider);
                } else {
                    //非增量处理目录文件
                    handleDirectory(directoryInput, outputProvider);
                }
            }
        }
    }

    private void handleDirectory(DirectoryInput directoryInput, TransformOutputProvider outputProvider)
        throws IOException {
        System.out.println("handleDirectory");
        File srcDirFile = directoryInput.getFile();
        File destDir = outputProvider.getContentLocation(
            directoryInput.getName(),
            directoryInput.getContentTypes(),
            directoryInput.getScopes(),
            Format.DIRECTORY);

        if (srcDirFile.isDirectory()) {
            //列出目录所有文件（包含子文件夹，子文件夹内文件
            for (File file : FileUtils.getAllFiles(srcDirFile)) {
                String name = file.getName();
                MLog.debug("遍历dir class=" + name);

                if (isValidClass(name)) {
                    printHandClassLog(TYPE_DIR, name);
                    ClassReader classReader = new ClassReader(Files.readAllBytes(file.toPath()));
                    ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
                    ClassVisitor methodTraceClassVisitor = new MethodTraceClassVisitor(classWriter);
                    classReader.accept(methodTraceClassVisitor, EXPAND_FRAMES);
                    byte[] code = classWriter.toByteArray();
                    FileOutputStream fos = new FileOutputStream(
                        file.getParentFile().getAbsoluteFile() + File.separator + name);
                    fos.write(code);
                    fos.close();
                }
            }
        }

        FileUtils.copyDirectory(srcDirFile, destDir);
    }

    private void handleDirectoryIncremental(DirectoryInput directoryInput, TransformOutputProvider outputProvider)
        throws IOException {
        //通过DirectoryInput的getChangedFiles方法获取改变过的文件集合，每一个文件对应一个Status
        Map<File, Status> changedFileMap = directoryInput.getChangedFiles();
        //遍历所有改变过的文件
        for (Map.Entry<File, Status> entry : changedFileMap.entrySet()) {
            File file = entry.getKey();
            Status status = entry.getValue();
            //根据文件的Status做出不同的操作
            switch (status) {
                case ADDED:
                case CHANGED:
                    handleDirectory(directoryInput, outputProvider);
                    break;
                case REMOVED:
                    outputProvider.deleteAll();
                    break;
                case NOTCHANGED:
                    //do nothing
                    break;
                default:
            }
        }
    }

    private static void handJarInput(JarInput jarInput, TransformOutputProvider outputProvider) throws IOException {
        if (jarInput.getFile().getAbsolutePath().endsWith(".jar")) {
            //重名名输出文件,因为可能同名,会覆盖
            String jarName = jarInput.getName();
            String jarAbsolutePath = jarInput.getFile().getAbsolutePath();
            String md5Name = DigestUtils.md5Hex(jarInput.getFile().getAbsolutePath());
            MLog.debug("解析jar=" + jarName + " path=" + jarAbsolutePath);

            if (jarName.endsWith(".jar")) {
                jarName = jarName.substring(0, jarName.length() - 4);
            }

            JarFile jarFile = new JarFile(jarInput.getFile());
            Enumeration<JarEntry> enumeration = jarFile.entries();
            File tmpFile = new File(jarInput.getFile().getParent() + File.separator + "classes_temp.jar");

            //避免上次的缓存被重复插入
            if (tmpFile.exists()) {
                tmpFile.delete();
            }

            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tmpFile));
            //用于保存
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = enumeration.nextElement();
                String entryName = jarEntry.getName();
                ZipEntry zipEntry = new ZipEntry(entryName);
                InputStream inputStream = jarFile.getInputStream(jarEntry);
                MLog.debug("遍历jar class=" + entryName);
                if (isValidClass(entryName)) {
                    printHandClassLog(TYPE_JAR, entryName);
                    ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream));
                    ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
                    MethodTraceClassVisitor methodTraceClassVisitor = new MethodTraceClassVisitor(classWriter);
                    classReader.accept(methodTraceClassVisitor, ClassReader.EXPAND_FRAMES);

                    jarOutputStream.putNextEntry(zipEntry);
                    byte[] code = classWriter.toByteArray();
                    jarOutputStream.write(code);
                } else {
                    jarOutputStream.putNextEntry(zipEntry);
                    jarOutputStream.write(IOUtils.toByteArray(inputStream));
                }
                jarOutputStream.closeEntry();
            }
            //结束
            jarOutputStream.close();
            jarFile.close();
            //获取output目录
            File dest = outputProvider.getContentLocation(jarName + md5Name,
                jarInput.getContentTypes(), jarInput.getScopes(), Format.JAR);
            FileUtils.copyFile(tmpFile, dest);
            tmpFile.delete();
        }
    }

    private void handleJarIncremental(JarInput jarInput, TransformOutputProvider outputProvider) throws IOException {
        //获取输入文件的状态
        Status status = jarInput.getStatus();
        //根据文件的Status做出不同的操作
        switch (status) {
            case ADDED:
            case CHANGED:
                handJarInput(jarInput, outputProvider);
                break;
            case REMOVED:
                //删除所有输出
                outputProvider.deleteAll();
                break;
            case NOTCHANGED:
                //do nothing
                break;
            default:
        }
    }

    private static boolean isValidClass(String name) {
        return name.endsWith(".class") && !name.equals("R.class")
            && !name.startsWith("R\\$")
            && !name.startsWith("<init>")
            && !name.startsWith("<clinit>")
            && !name.equals("BuildConfig.class");
    }

    private static void printHandClassLog(String type, String className) {
        String msg = "-------handler type =" + type + " " + className + " class  <" + className + "> -----------";
        MLog.info(msg);
    }

}


