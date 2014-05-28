package com.intellij.compiler.artifacts;

import com.intellij.compiler.CompilerTestUtil;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.packaging.artifacts.Artifact;
import com.intellij.packaging.artifacts.ModifiableArtifactModel;
import com.intellij.packaging.elements.PackagingElementFactory;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;

import java.io.IOException;

import static com.intellij.compiler.artifacts.ArtifactsTestCase.commitModel;
import static com.intellij.compiler.artifacts.ArtifactsTestCase.renameFile;

/**
 * @author nik
 */
public class IncrementalArtifactsCompilerTest extends ArtifactCompilerTestCase {
  public void testAddFile() {
    VirtualFile file = createFile("dir/file.txt");
    Artifact a = addArtifact(root().dirCopy(file.getParent()));
    make(a);
    assertOutput(a, fs().file("file.txt"));

    createFile("dir/file2.txt");
    make(a);
    assertOutput(a, fs().file("file.txt").file("file2.txt"));
  }

  public void testChangeFile() {
    VirtualFile file = createFile("file.txt", "a");
    Artifact a = addArtifact(root().dir("dir").file(file));
    compileProject();
    assertOutput(a, fs().dir("dir").file("file.txt", "a"));

    changeFile(file, "b");
    compileProject();
    assertOutput(a, fs().dir("dir").file("file.txt", "b"));
  }

  public void testAddRemoveJavaClass() {
    final VirtualFile file = createFile("src/A.java", "public class A {}");
    final Module module = addModule("a", file.getParent());
    Artifact a = addArtifact(root().module(module));
    make(module);
    make(a);
    assertOutput(a, fs().file("A.class"));
    VirtualFile file2 = createFile("src/B.java", "public class B{}");
    make(module);
    make(a);
    assertOutput(a, fs().file("A.class").file("B.class"));

    deleteFile(file2);
    make(module);
    make(a);
    assertOutput(a, fs().file("A.class"));
  }

  public void testCopyResourcesFromModuleOutput() {
    VirtualFile file = createFile("src/a.xml", "1");
    Module module = addModule("a", file.getParent());
    Artifact artifact = addArtifact(root().module(module));
    make(artifact);
    assertOutput(artifact, fs().file("a.xml", "1"));

    changeFile(file, "2");
    make(artifact);
    assertOutput(artifact, fs().file("a.xml", "2"));
  }

  public void testRebuildArtifactAfterClean() {
    Artifact a = addArtifact(root().file(createFile("file.txt", "123")));
    make(a);
    assertOutput(a, fs().file("file.txt"));

    delete(getOutputDir(a));
    make(a);
    assertOutput(a, fs().file("file.txt", "123"));
  }

  public void testChangeFileInIncludedArtifact() {
    VirtualFile file = createFile("file.txt", "a");
    Artifact included = addArtifact("i", root().file(file));
    Artifact a = addArtifact(root().artifact(included));
    make(a);
    assertOutput(a, fs().file("file.txt", "a"));

    changeFile(file, "b");
    make(a);
    assertOutput(a, fs().file("file.txt", "b"));
  }

  public void testDeleteFileInIncludedArtifact() {
    VirtualFile file1 = createFile("d/1.txt");
    createFile("d/2.txt");
    Artifact included = addArtifact("i", root().dirCopy(file1.getParent()));
    Artifact a = addArtifact(root().artifact(included));
    make(a);
    assertOutput(a, fs().file("1.txt").file("2.txt"));

    delete(file1);
    make(a);
    assertOutput(a, fs().file("2.txt"));
  }

  public void testChangeFileInArtifactIncludedInArchive() {
    VirtualFile file = createFile("file.txt", "a");
    Artifact included = addArtifact("i", root().file(file));
    Artifact a = addArtifact(archive("a.jar").artifact(included));
    make(a);
    assertOutput(a, fs().archive("a.jar").file("file.txt", "a"));

    changeFile(file, "b");
    make(a);
    assertOutput(a, fs().archive("a.jar").file("file.txt", "b"));
  }

  public void testChangeFileInIncludedArchive() {
    VirtualFile file = createFile("file.txt", "a");
    Artifact included = addArtifact("i", archive("f.jar").file(file));
    Artifact a = addArtifact(root().artifact(included));
    make(a);
    assertOutput(a, fs().archive("f.jar").file("file.txt", "a"));

    changeFile(file, "b");
    make(a);
    assertOutput(a, fs().archive("f.jar").file("file.txt", "b"));
  }

  public void testDoNotTreatClassFileAsDirtyAfterMake() {
    VirtualFile file = createFile("src/A.java", "class A{}");
    Module module = addModule("a", file.getParent());
    VirtualFile out = createChildDirectory(file.getParent().getParent(), "module-out");
    PsiTestUtil.addContentRoot(module, out);
    PsiTestUtil.setCompilerOutputPath(module, out.getUrl(), false);

    Artifact a = addArtifact(root().module(module));
    make(a);
    assertOutput(a, fs().file("A.class"));
    make(a).assertUpToDate();

    changeFile(file, "class A{int a;}");
    make(a);
    assertOutput(a, fs().file("A.class"));
    make(a).assertUpToDate();
  }

  public void testDoNotCopyFilesFromIgnoredFolder() {
    VirtualFile file1 = createFile("d/1.txt");
    Artifact a = addArtifact(root().dirCopy(file1.getParent()));
    make(a);
    assertOutput(a, fs().file("1.txt"));

    VirtualFile file2 = createFile("d/CVS/2.txt");
    make(a);
    assertOutput(a, fs().file("1.txt"));

    changeFile(file2);
    make(a);
    assertOutput(a, fs().file("1.txt"));
  }

  public void testChangeFileOutsideContentRoot() throws IOException {
    final Artifact a = addArtifact(root().file(createFile("1.txt")));
    make(a);
    assertOutput(a, fs().file("1.txt"));

    VirtualFile virtualDir = getVirtualFile(createTempDir("externalDir"));
    final VirtualFile file = VfsTestUtil.createFile(virtualDir, "2.txt", "a");
    new WriteAction() {
      protected void run(final Result result) {
        ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
        model.getOrCreateModifiableArtifact(a).getRootElement()
          .addFirstChild(PackagingElementFactory.getInstance().createFileCopy(file.getPath(), null));
        model.commit();
      }
    }.execute();


    make(a);
    assertOutput(a, fs().file("1.txt").file("2.txt", "a"));

    changeFile(file, "b");
    make(a);
    assertOutput(a, fs().file("1.txt").file("2.txt", "b"));
  }

  //IDEA-95420
  public void testDeleteClassAndRebuildArtifact() {
    createFile("src/A.java", "class A{}");
    VirtualFile file = createFile("src/B.java", "class B{}");
    Module m = addModule("m", file.getParent());
    Artifact dir = addArtifact(root().module(m));
    Artifact jar = addArtifact("jar", archive("a.jar").module(m));
    make(dir, jar);
    assertOutput(m, fs().file("A.class").file("B.class"));
    assertOutput(dir, fs().file("A.class").file("B.class"));
    assertOutput(jar, fs().archive("a.jar").file("A.class").file("B.class"));

    deleteFile(file);
    recompile(dir, jar);
    assertOutput(m, fs().file("A.class"));
    assertOutput(dir, fs().file("A.class"));
    assertOutput(jar, fs().archive("a.jar").file("A.class"));
  }

  @Override
  protected void setUpProject() throws Exception {
    super.setUpProject();
    CompilerTestUtil.setupJavacForTests(getProject());
  }

  public void testOneFileInTwoArtifacts() throws Exception {
    final VirtualFile file = createFile("file.txt");
    final Artifact a1 = addArtifact("a1",
                                    root().dir("dir").file(file));

    final Artifact a2 = addArtifact("a2",
                                    root().dir("dir2").file(file));

    compileProject();
    make(a1).assertUpToDate();
    make(a2).assertUpToDate();
    compileProject().assertUpToDate();

    changeFile(file);
    make(a1).assertRecompiled("file.txt");
    make(a1).assertUpToDate();
    make(a2).assertRecompiled("file.txt");
    make(a2).assertUpToDate();
    make(a1).assertUpToDate();
    compileProject().assertUpToDate();
  }

  public void testDeleteFile() throws Exception {
    final VirtualFile file = createFile("index.html");
    addArtifact(root().file(file));

    compileProject();
    deleteFile(file);
    compileProject().assertDeleted("out/artifacts/a/index.html");
  }

  //IDEADEV-40714
  public void testOverwriteFileInArchive() throws Exception {
    final VirtualFile file1 = createFile("a/a.txt", "a");
    final VirtualFile file2 = createFile("b/a.txt", "b");
    addArtifact(root()
                 .archive("x.jar")
                  .file(file1)
                  .file(file2));
    compileProject();
    changeFile(file1);
    compileProject().assertRecompiled("a/a.txt");
  }


  public void testRenameFile() throws Exception {
    final VirtualFile file = createFile("a/a.txt");
    final Artifact a = addArtifact(root().dirCopy(file.getParent()));
    compileProject();

    assertOutput(a, fs().file("a.txt"));
    renameFile(file, "b.txt");
    compileProject();
    assertOutput(a, fs().file("b.txt"));
  }

  //IDEADEV-41556
  public void testDeleteFilesFromSelectedArtifactsOnly() throws Exception {
    final VirtualFile file = createFile("a/a.txt");
    final Artifact a1 = addArtifact("a1", root().dirCopy(file.getParent()));
    final Artifact a2 = addArtifact("a2", root().dirCopy(file.getParent()));

    compileProject();
    assertOutput(a1, fs().file("a.txt"));
    assertOutput(a2, fs().file("a.txt"));

    deleteFile(file);
    make(a1).assertDeleted("out/artifacts/a1/a.txt");
    assertEmptyOutput(a1);
    assertOutput(a2, fs().file("a.txt"));

    make(a2).assertDeleted("out/artifacts/a2/a.txt");
    assertEmptyOutput(a1);
    assertEmptyOutput(a2);
  }

  //todo[nik] uncomment when deleting obsolete directories will be supported
  public void _testRenameDirectoryInLayout() throws Exception {
    final VirtualFile file = createFile("a.txt");
    final Artifact a = addArtifact("a", root().dir("d1").file(file));
    compileProject();
    assertOutput(a, fs().dir("d1").file("a.txt"));

    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.getOrCreateModifiableArtifact(a).setRootElement(root().dir("d2").file(file).build());
    commitModel(model);

    compileProject();
    assertOutput(a, fs().dir("d2").file("a.txt"));
  }

  public void testDeleteOutputWhenOutputPathIsChanged() throws Exception {
    final VirtualFile file = createFile("a.txt");
    final Artifact a = addArtifact("a", root().file(file));
    compileProject();
    assertOutput(a, fs().file("a.txt"));

    final ModifiableArtifactModel model = getArtifactManager().createModifiableModel();
    model.getOrCreateModifiableArtifact(a).setOutputPath(getProjectBasePath() + "/xxx");
    commitModel(model);

    compileProject().assertRecompiledAndDeleted(new String[]{"a.txt"}, "out/artifacts/a/a.txt");
    assertOutput(a, fs().file("a.txt"));
  }

  //todo[nik] this test sometimes fails on server
  public void _testChangeFileInArchive() throws Exception {
    final VirtualFile file = createFile("a.txt", "a");
    final Artifact a = addArtifact("a", root().archive("a.jar").file(file));
    make(a);

    final String jarPath = a.getOutputPath() + "/a.jar";
    JarFileSystem.getInstance().setNoCopyJarForPath(jarPath + JarFileSystem.JAR_SEPARATOR);
    final Artifact b = addArtifact("b", root().extractedDir(jarPath, "/"));
    make(b);
    assertOutput(b, fs().file("a.txt", "a"));

    make(a).assertUpToDate();
    make(b).assertUpToDate();

    changeFile(file, "b");
    make(b).assertUpToDate();
    make(a).assertRecompiled("a.txt");
    assertOutput(a, fs().archive("a.jar").file("a.txt", "b"));
    changeFileInJar(jarPath, "a.txt");

    make(b).assertRecompiled("out/artifacts/a/a.jar!/a.txt");
    assertOutput(b, fs().file("a.txt", "b"));
    make(b).assertUpToDate();
  }
}