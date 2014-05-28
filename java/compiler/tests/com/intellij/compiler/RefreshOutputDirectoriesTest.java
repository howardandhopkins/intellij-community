package com.intellij.compiler;

import com.intellij.ProjectTopics;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootAdapter;
import com.intellij.openapi.roots.ModuleRootEvent;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.PsiTestUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @author nik
 */
public class RefreshOutputDirectoriesTest extends BaseCompilerTestCase {
  private int myRootsChangedCount;

  public void testSingleModule() {
    final VirtualFile file = createFile("src/A.java", "public class A {}");
    final Module module = addModule("m", file.getParent());
    subscribeToRootChanges();
    make(module);
    assertOutputDirectoriesRefreshed(module);
  }

  public void testManyModulesWithInheritedOutputPath() {
    doTestManyModules(false);
  }

  public void testManyModulesWithCustomOutputPaths() {
    doTestManyModules(true);
  }

  private void doTestManyModules(boolean inheritOutputPath) {
    List<Module> modules = new ArrayList<Module>();
    for (int i = 0; i < 15; i++) {
      VirtualFile file = createFile("m" + i + "/src/A" + i + ".java", "public class A" + i + "{}");
      final String moduleName = "m" + i;
      VirtualFile sourceRoot = file.getParent();
      Module module;
      if (inheritOutputPath) {
        module = addModule(moduleName, sourceRoot);
      }
      else {
        module = createModule(moduleName);
        VirtualFile contentRoot = sourceRoot.getParent();
        PsiTestUtil.addContentRoot(module, contentRoot);
        PsiTestUtil.addSourceRoot(module, sourceRoot);
        ModuleRootModificationUtil.setModuleSdk(module, getTestProjectJdk());
        PsiTestUtil.setCompilerOutputPath(module, contentRoot.getUrl() + "/classes", false);
      }
      modules.add(module);

    }
    Module[] modulesArray = modules.toArray(new Module[modules.size()]);
    subscribeToRootChanges();
    make(modulesArray);
    assertOutputDirectoriesRefreshed(modulesArray);
  }

  private void subscribeToRootChanges() {
    myProject.getMessageBus().connect(myTestRootDisposable).subscribe(ProjectTopics.PROJECT_ROOTS, new ModuleRootAdapter() {
      @Override
      public void rootsChanged(ModuleRootEvent event) {
        myRootsChangedCount++;
      }
    });
  }

  private void assertOutputDirectoriesRefreshed(Module... modules) {
    assertEquals(1, myRootsChangedCount);
    for (Module module : modules) {
      File dir = getOutputDir(module, false);
      VirtualFile outputDir = LocalFileSystem.getInstance().findFileByIoFile(dir);
      assertNotNull("output directory wasn't refreshed: " + dir.getAbsolutePath(), outputDir);
    }
    System.out.println("myRootsChangedCount = " + myRootsChangedCount);
  }
}
