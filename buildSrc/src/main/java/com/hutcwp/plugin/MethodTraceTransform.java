package com.hutcwp.plugin;


import com.android.build.api.transform.DirectoryInput;
import com.android.build.api.transform.Format;
import com.android.build.api.transform.JarInput;
import com.android.build.api.transform.QualifiedContent;
import com.android.build.api.transform.Status;
import com.android.build.api.transform.Transform;
import com.android.build.api.transform.TransformInput;
import com.android.build.api.transform.TransformInvocation;
import com.android.build.api.transform.TransformOutputProvider;
import com.android.build.gradle.internal.pipeline.TransformManager;
import com.android.utils.FileUtils;

import org.apache.commons.io.IOUtils;
import org.gradle.api.Project;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

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

import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;


/**
 * author : kevin
 * date : 2021/9/19 12:48 AM
 * description :
 */
public class MethodTraceTransform extends Transform {

    private Project mProject;

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
        return TransformManager.SCOPE_FULL_PROJECT;
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
                    handleJar(jarInput, outputProvider);
                }
            }

            try {
                for (DirectoryInput directoryInput : directoryInputs) {
                    if (isIncremental) {
                        //增量处理目录文件
                        handleDirectoryIncremental(directoryInput, outputProvider);
                    } else {
                        //非增量处理目录文件
                        handleDirectory(directoryInput, outputProvider);
                    }
                }
            } catch (Exception e) {
                System.out.println("发生异常了，e=" + e.getMessage());
                e.printStackTrace();
            }

        }
    }

    private void handleDirectory(DirectoryInput directoryInput, TransformOutputProvider outputProvider) throws IOException {
        System.out.println("handleDirectory");
        File srcDirFile = directoryInput.getFile();
        File destDir = outputProvider.getContentLocation(
                directoryInput.getName(),
                directoryInput.getContentTypes(),
                directoryInput.getScopes(),
                Format.DIRECTORY);

//        //是否是目录
        if (srcDirFile.isDirectory()) {
            //列出目录所有文件（包含子文件夹，子文件夹内文件
            for (File file : FileUtils.getAllFiles(srcDirFile)) {
                String name = file.getName();
                System.out.println("handle dir-> " + name);
                if (isValidClass(name)) {
                    System.out.println("real handle dir-> " + name);

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

    private void handleDirectoryIncremental(DirectoryInput directoryInput, TransformOutputProvider outputProvider) throws IOException {
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

    private void handleJar(JarInput jarInput, TransformOutputProvider outputProvider) throws IOException {
        System.out.println("handleJar");

        File srcJarFile = jarInput.getFile();
        File destJarFile = outputProvider.getContentLocation(
                jarInput.getName(),
                jarInput.getContentTypes(),
                jarInput.getScopes(),
                Format.JAR
        );

        FileUtils.copyFile(srcJarFile, destJarFile);

//        try {
//            JarFile jarFile = new JarFile(srcJarFile);
//            JarOutputStream destJarFileOs = new JarOutputStream(new FileOutputStream(destJarFile));
//            Enumeration<JarEntry> enumeration = jarFile.entries();
//            while (enumeration.hasMoreElements()) {
//                JarEntry entry = enumeration.nextElement();
//                InputStream entryIs = jarFile.getInputStream(entry);
//                destJarFileOs.putNextEntry(new JarEntry(entry.getName()));
//                if (isValidClass(entry.getName())) {
//                    //通过asm修改源class文件
//                    ClassReader classReader = new ClassReader(entryIs);
//                    ClassWriter classWriter = new ClassWriter(0);
//                    MethodTraceClassVisitor methodTraceClassVisitor = new MethodTraceClassVisitor(classWriter);
//                    classReader.accept(methodTraceClassVisitor, EXPAND_FRAMES);
//                    //然后把修改后的class文件复制到destJar中
//                    destJarFileOs.write(classWriter.toByteArray());
//                } else {
//                    //原封不动地复制到destJar中
//                    destJarFileOs.write(IOUtils.toByteArray(entryIs));
//                }
//                destJarFileOs.closeEntry();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }


    private void handleJarIncremental(JarInput jarInput, TransformOutputProvider outputProvider) throws IOException {
        //获取输入文件的状态
        Status status = jarInput.getStatus();
        //根据文件的Status做出不同的操作
        switch (status) {
            case ADDED:
            case CHANGED:
                handleJar(jarInput, outputProvider);
                break;
            case REMOVED:
                //删除所有输出
                try {
                    outputProvider.deleteAll();
                } catch (IOException e) {
                    e.printStackTrace();
                }
                break;
            case NOTCHANGED:
                //do nothing
                break;
            default:
        }
    }

    private boolean isValidClass(String name) {
        return name.endsWith(".class") && !name.equals("R.class")
                && !name.startsWith("R\\$") && !name.equals("BuildConfig.class");
    }

}


