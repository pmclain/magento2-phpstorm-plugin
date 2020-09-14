/*
 * Copyright © Magento, Inc. All rights reserved.
 * See COPYING.txt for license details.
 */

package com.magento.idea.magento2plugin.actions.generation.generator;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.maddyhome.idea.copyright.actions.UpdateCopyrightProcessor;
import com.magento.idea.magento2plugin.bundles.ValidatorBundle;
import com.magento.idea.magento2plugin.indexes.ModuleIndex;
import com.magento.idea.magento2plugin.magento.packages.Areas;
import com.magento.idea.magento2plugin.magento.packages.ComponentType;
import com.magento.idea.magento2plugin.util.RegExUtil;
import com.magento.idea.magento2plugin.util.magento.GetComponentNameByDirectory;
import com.magento.idea.magento2plugin.util.magento.GetComponentTypeByNameUtil;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class OverrideInThemeGenerator {
    private final ValidatorBundle validatorBundle;

    private final Project project;

    public OverrideInThemeGenerator(final Project project) {
        this.project = project;
        this.validatorBundle = new ValidatorBundle();
    }

    /**
     * Action entry point.
     *
     * @param baseFile PsiFile
     * @param themeName String
     */
    public void execute(final PsiFile baseFile, final String themeName) {
        final GetComponentNameByDirectory getComponentNameByDirectory = GetComponentNameByDirectory
                .getInstance(project);
        final String componentType = GetComponentTypeByNameUtil.execute(getComponentNameByDirectory
                .execute(baseFile.getContainingDirectory()));

        List<String> pathComponents;
        if (componentType.equals(ComponentType.module.toString())) {
            pathComponents = getModulePathComponents(
                    baseFile,
                    getComponentNameByDirectory.execute(baseFile.getContainingDirectory())
            );
        } else if (componentType.equals(ComponentType.theme.toString())) {
            pathComponents = getThemePathComponents(baseFile);
        } else {
            return;
        }

        final ModuleIndex moduleIndex = ModuleIndex.getInstance(project);
        PsiDirectory directory = moduleIndex.getModuleDirectoryByModuleName(themeName);
        directory = getTargetDirectory(directory, pathComponents);

        if (directory.findFile(baseFile.getName()) != null) {
            JBPopupFactory.getInstance()
                    .createMessage(
                        validatorBundle.message("validator.file.alreadyExists", baseFile.getName())
                    )
                    .showCenteredInCurrentWindow(project);
            directory.findFile(baseFile.getName()).navigate(true);
            return;
        }

        final PsiDirectory finalDirectory = directory;
        ApplicationManager.getApplication().runWriteAction(() -> {
            finalDirectory.copyFileFrom(baseFile.getName(), baseFile);
        });

        final PsiFile newFile = directory.findFile(baseFile.getName());
        assert newFile != null;
        final Module module = ModuleUtilCore.findModuleForPsiElement(newFile);
        final UpdateCopyrightProcessor processor = new UpdateCopyrightProcessor(
                project,
                module,
                newFile
        );
        processor.run();

        newFile.navigate(true);
    }

    private List<String> getModulePathComponents(final PsiFile file, final String componentName) {
        final List<String> pathComponents = new ArrayList<>();
        PsiDirectory parent = file.getParent();
        while (!parent.getName().equals(Areas.frontend.toString())
                && !parent.getName().equals(Areas.adminhtml.toString())) {
            pathComponents.add(parent.getName());
            parent = parent.getParent();
        }
        pathComponents.add(componentName);
        Collections.reverse(pathComponents);

        return pathComponents;
    }

    private List<String> getThemePathComponents(final PsiFile file) {
        final List<String> pathComponents = new ArrayList<>();
        final Pattern pattern = Pattern.compile(RegExUtil.Magento.MODULE_NAME);

        PsiDirectory parent = file.getParent();
        do {
            pathComponents.add(parent.getName());
            parent = parent.getParent();
        } while (!pattern.matcher(parent.getName()).find());
        pathComponents.add(parent.getName());
        Collections.reverse(pathComponents);

        return pathComponents;
    }

    private PsiDirectory getTargetDirectory(
            PsiDirectory directory, //NOPMD
            final List<String> pathComponents
    ) {
        for (final String directoryName : pathComponents) {
            if (directory.findSubdirectory(directoryName) != null) { //NOPMD
                directory = directory.findSubdirectory(directoryName);
            } else {
                final PsiDirectory finalDirectory = directory;
                ApplicationManager.getApplication().runWriteAction(() -> {
                    finalDirectory.createSubdirectory(directoryName);
                });
                return finalDirectory;
            }
        }

        return directory;
    }
}