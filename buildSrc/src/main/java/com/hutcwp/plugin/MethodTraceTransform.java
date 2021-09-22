package com.hutcwp.plugin;


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
import org.apache.commons.codec.digest.DigestUtils;
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
import java.util.zip.ZipEntry;

import static org.objectweb.asm.ClassReader.EXPAND_FRAMES;


/**
 * author : kevin date : 2021/9/19 12:48 AM description :
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
//                    handleJar(jarInput, outputProvider);
                    handJarInput(jarInput, outputProvider);
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

    private void handleDirectory(DirectoryInput directoryInput, TransformOutputProvider outputProvider)
        throws IOException {
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


    //遍历jarInputs 得到对应的class 交给ASM处理
    private static void handJarInput(JarInput jarInput, TransformOutputProvider outputProvider) throws IOException {
        if (jarInput.getFile().getAbsolutePath().endsWith(".jar")) {
            //重名名输出文件,因为可能同名,会覆盖
            String jarName = jarInput.getName();
            System.out.println("jarName=" + jarName + " path=" + jarInput.getFile().getAbsolutePath());
            String md5Name = DigestUtils.md5Hex(jarInput.getFile().getAbsolutePath());
            if (jarName.endsWith(".jar")) {
                jarName = jarName.substring(0, jarName.length() - 4);
            }
            JarFile jarFile = new JarFile(jarInput.getFile());
            Enumeration enumeration = jarFile.entries();
            File tmpFile = new File(jarInput.getFile().getParent() + File.separator + "classes_temp.jar");
            //避免上次的缓存被重复插入
            if (tmpFile.exists()) {
                tmpFile.delete();
            }
            JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(tmpFile));
            //用于保存
            while (enumeration.hasMoreElements()) {
                JarEntry jarEntry = (JarEntry) enumeration.nextElement();
                String entryName = jarEntry.getName();
                ZipEntry zipEntry = new ZipEntry(entryName);
                InputStream inputStream = jarFile.getInputStream(jarEntry);
                System.out.println("class=" + entryName);
                //需要插桩class 根据自己的需求来-------------
                if (isValidClass(entryName)) {
                    //class文件处理
                    System.out.println("----------- jar class  <" + entryName + "> -----------");
                    jarOutputStream.putNextEntry(zipEntry);
                    ClassReader classReader = new ClassReader(IOUtils.toByteArray(inputStream));
                    ClassWriter classWriter = new ClassWriter(classReader, ClassWriter.COMPUTE_MAXS);
                    //创建类访问器   并交给它去处理
                    MethodTraceClassVisitor methodTraceClassVisitor = new MethodTraceClassVisitor(classWriter);
                    classReader.accept(methodTraceClassVisitor, ClassReader.EXPAND_FRAMES);
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

    private void handleJar(JarInput jarInput, TransformOutputProvider outputProvider) throws IOException {
        System.out.println("handleJar");

        File srcJarFile = jarInput.getFile();
        File destJarFile = outputProvider.getContentLocation(
            jarInput.getName(),
            jarInput.getContentTypes(),
            jarInput.getScopes(),
            Format.JAR
        );

//        FileUtils.copyFile(srcJarFile, destJarFile);

        try {
            JarFile jarFile = new JarFile(srcJarFile);
            JarOutputStream destJarFileOs = new JarOutputStream(new FileOutputStream(destJarFile));
            Enumeration<JarEntry> enumeration = jarFile.entries();
            while (enumeration.hasMoreElements()) {
                JarEntry entry = enumeration.nextElement();
                InputStream entryIs = jarFile.getInputStream(entry);
                destJarFileOs.putNextEntry(new JarEntry(entry.getName()));
//                if (isValidClass(entry.getName())) {
//                    //通过asm修改源class文件
//                    ClassReader classReader = new ClassReader(entryIs);
//                    ClassWriter classWriter = new ClassWriter(0);
//                    MethodTraceClassVisitor methodTraceClassVisitor = new MethodTraceClassVisitor(classWriter);
//                    classReader.accept(methodTraceClassVisitor, EXPAND_FRAMES);
//                    //然后把修改后的class文件复制到destJar中
//                    destJarFileOs.write(classWriter.toByteArray());
//                } else {
                //原封不动地复制到destJar中
                destJarFileOs.write(IOUtils.toByteArray(entryIs));
//                }
                destJarFileOs.closeEntry();
            }
        } catch (IOException e) {
            System.out.println("遇到了问题");
            e.printStackTrace();
        }
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

    private static boolean isValidClass(String name) {
        return name.endsWith(".class") && !name.equals("R.class")
            && name.contains("MainActivity")
            && name.contains("AubMainActivity")
            && !name.startsWith("R\\$")
            && !name.startsWith("<init>")
            && !name.startsWith("<clinit>")
            && !name.equals("BuildConfig.class");
    }

}


