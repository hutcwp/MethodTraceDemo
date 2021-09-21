package com.hutcwp.plugin;

import com.android.build.gradle.AppExtension;

import org.gradle.api.Plugin;
import org.gradle.api.Project;

class MethodTracePlugin implements Plugin<Project> {

    @Override
    public void apply(Project project) {
        System.out.println("MethodTracePlugin apply invoke!");
        AppExtension android = project.getExtensions().findByType(AppExtension.class);
        android.registerTransform(new MethodTraceTransform(project));
    }
}