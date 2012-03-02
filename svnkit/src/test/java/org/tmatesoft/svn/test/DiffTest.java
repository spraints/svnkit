package org.tmatesoft.svn.test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.internal.wc.DefaultSVNOptions;
import org.tmatesoft.svn.core.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.internal.wc2.ng.SvnDiffGenerator;
import org.tmatesoft.svn.core.wc.ISVNDiffGenerator;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNDiffClient;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnCopy;
import org.tmatesoft.svn.core.wc2.SvnCopySource;
import org.tmatesoft.svn.core.wc2.SvnDiff;
import org.tmatesoft.svn.core.wc2.SvnOperationFactory;
import org.tmatesoft.svn.core.wc2.SvnTarget;

public class DiffTest {

    @Test
    public void testRemoteDiffTwoFiles() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteDiffTwoFiles", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file1", "contents1".getBytes());
            commitBuilder.addFile("directory/file2", "contents2".getBytes());
            final SVNCommitInfo commitInfo = commitBuilder.commit();

            final SVNRevision svnRevision = SVNRevision.create(commitInfo.getNewRevision());

            final SVNURL url1 = url.appendPath("directory/file1", false);
            final SVNURL url2 = url.appendPath("directory/file2", false);

            final String actualDiffOutput = runDiff(svnOperationFactory, url1, svnRevision, url2, svnRevision);
            final String expectedDiffOutput = "Index: file1" + "\n" +
                    "===================================================================" + "\n" +
                    "--- file1\t(.../file1)\t(revision 1)" + "\n" +
                    "+++ file1\t(.../file2)\t(revision 1)" + "\n" +
                    "@@ -1 +0,0 @@" + "\n" +
                    "-contents1\n" +
                    "\\ No newline at end of file" + "\n" +
                    "Index: file1" + "\n" +
                    "===================================================================" + "\n" +
                    "--- file1\t(.../file1)\t(revision 0)" + "\n" +
                    "+++ file1\t(.../file2)\t(revision 1)" + "\n" +
                    "@@ -0,0 +1 @@" + "\n" +
                    "+contents2" + "\n" +
                    "\\ No newline at end of file" + "\n";

            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteDiffOneFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteDiffOneFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file", "contents1".getBytes());
            final SVNCommitInfo commitInfo1 = commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("directory/file", "contents2".getBytes());
            final SVNCommitInfo commitInfo2 = commitBuilder2.commit();

            final SVNRevision startRevision = SVNRevision.create(commitInfo1.getNewRevision());
            final SVNRevision endRevision = SVNRevision.create(commitInfo2.getNewRevision());

            final SVNURL fileUrl = url.appendPath("directory/file", false);

            final String actualDiffOutput = runDiff(svnOperationFactory, fileUrl, startRevision, endRevision);

            final String expectedDiffOutput = "Index: file" + "\n" +
                    "===================================================================" + "\n" +
                    "--- file\t(revision 1)" + "\n" +
                    "+++ file\t(revision 2)" + "\n" +
                    "@@ -1 +1 @@" + "\n" +
                    "-contents1" + "\n" +
                    "\\ No newline at end of file" + "\n" +
                    "+contents2" + "\n" +
                    "\\ No newline at end of file" + "\n";

            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testLocalDiffOneFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLocalDiffOneFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file", "contents1".getBytes());
            final SVNCommitInfo commitInfo1 = commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("directory/file", "contents2".getBytes());
            final SVNCommitInfo commitInfo2 = commitBuilder2.commit();

            final SVNRevision svnRevision1 = SVNRevision.create(commitInfo1.getNewRevision());
            final SVNRevision svnRevision2 = SVNRevision.create(commitInfo2.getNewRevision());

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "directory/file");

            final String actualDiffOutput = runDiff(svnOperationFactory, file, svnRevision1, svnRevision2);

            final String expectedDiffOutput = "Index: " + file.getPath() + "\n" +
                    "===================================================================\n" +
                    "--- " + file.getPath() + "\t(revision " + svnRevision1.getNumber() + ")\n" +
                    "+++ " + file.getPath() + "\t(revision " + svnRevision2.getNumber() + ")\n" +
                    "@@ -1 +1 @@\n" +
                    "-contents1\n" +
                    "\\ No newline at end of file\n" +
                    "+contents2\n" +
                    "\\ No newline at end of file\n";

            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testLocalToRemoteDiffOneFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testLocalDiffOneFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file", "contents1".getBytes());
            commitBuilder1.addFile("directory/anotherFile", "anotherContents".getBytes());
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("directory/file", "contents2".getBytes());
            commitBuilder2.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "directory/file");

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setTargets(SvnTarget.fromFile(file, SVNRevision.WORKING), SvnTarget.fromURL(url.appendPath("directory/anotherFile", false), SVNRevision.create(1)));
            diff.setOutput(byteArrayOutputStream);
            diff.run();

            //TODO finish the test
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDiffAddedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDiffAddedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File fileToAdd = new File(workingCopyDirectory, "fileToAdd");
            //noinspection ResultOfMethodCallIgnored
            fileToAdd.createNewFile();

            workingCopy.add(fileToAdd);

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setTargets(SvnTarget.fromFile(workingCopyDirectory, SVNRevision.BASE), SvnTarget.fromFile(workingCopyDirectory, SVNRevision.WORKING));
            diff.setOutput(byteArrayOutputStream);
            diff.run();

            String actualDiffOutput = new String(byteArrayOutputStream.toByteArray());
            final String expectedDiffOutput = "Index: " + fileToAdd.getPath() + "\n" +
                             "===================================================================\n";
            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDiffReplacedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDiffReplacedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("fileToReplace");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File fileToReplace = new File(workingCopyDirectory, "fileToReplace");
            workingCopy.delete(fileToReplace);
            //noinspection ResultOfMethodCallIgnored
            fileToReplace.createNewFile();
            workingCopy.add(fileToReplace);

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setTargets(SvnTarget.fromFile(workingCopyDirectory, SVNRevision.BASE), SvnTarget.fromFile(workingCopyDirectory, SVNRevision.WORKING));
            diff.setOutput(byteArrayOutputStream);
            diff.setIgnoreAncestry(true);
            diff.run();

            String actualDiffOutput = new String(byteArrayOutputStream.toByteArray());
            final String expectedDiffOutput = "";
            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Ignore("Temporarily ignored")
    @Test
    public void testPropertiesChangedOnlyHeaderIsPrinted() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testPropertiesChangedOnlyHeaderIsPrinted", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.addDirectory("directory");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File file = new File(workingCopyDirectory, "file");
            final File directory = new File(workingCopyDirectory, "directory");

            workingCopy.setProperty(file, "fileProperty", SVNPropertyValue.create("filePropertyValue"));
            workingCopy.setProperty(directory, "directoryProperty", SVNPropertyValue.create("directoryPropertyValue"));

            final String fileDiffHeader = "Index: file\n" +
                    "===================================================================\n" +
                    "--- file\t(revision 1)\n" +
                    "+++ file\t(working copy)\n";
            final String directoryDiffHeader = "Index: directory\n" +
                    "===================================================================\n" +
                    "--- directory\t(revision 1)\n" +
                    "+++ directory\t(working copy)\n";

            final String actualFileDiffOutput = runLocalDiff(svnOperationFactory, file, workingCopyDirectory);
            final String actualDirectoryDiffOutput = runLocalDiff(svnOperationFactory, directory, workingCopyDirectory);

            Assert.assertTrue(actualFileDiffOutput.startsWith(fileDiffHeader));
            Assert.assertTrue(actualDirectoryDiffOutput.startsWith(directoryDiffHeader));
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDiffLocalReplacedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDiffLocalReplacedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("fileToReplace");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File fileToReplace = new File(workingCopyDirectory, "fileToReplace");
            workingCopy.delete(fileToReplace);
            //noinspection ResultOfMethodCallIgnored
            fileToReplace.createNewFile();
            TestUtil.writeFileContentsString(fileToReplace, "newContents");
            workingCopy.add(fileToReplace);

            final String actualDiffOutput = runLocalDiff(svnOperationFactory, fileToReplace, workingCopyDirectory);
            final String expectedDiffOutput = "Index: " + fileToReplace.getPath() + "\n" +
                    "===================================================================\n" +
                    "--- " + fileToReplace.getPath() + "\t" + (TestUtil.isNewWorkingCopyTest() ? "(working copy)" : "(revision 1)") + "\n" +
                    "+++ " + fileToReplace.getPath() + "\t(working copy)\n" +
                    "@@ -0,0 +1 @@\n" +
                    "+newContents\n" +
                    "\\ No newline at end of file\n";
            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Ignore
    @Test
    public void testDiffLocalCopiedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDiffLocalCopiedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("sourceFile");
            commitBuilder1.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 1);
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File targetFile = new File(workingCopyDirectory, "targetFile");
            final File sourceFile = new File(workingCopyDirectory, "sourceFile");

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(sourceFile), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(targetFile));
            copy.setFailWhenDstExists(true);
            copy.setMove(true);
            copy.run();

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setTarget(SvnTarget.fromFile(targetFile, SVNRevision.WORKING), SVNRevision.HEAD, SVNRevision.WORKING);
            diff.setOutput(byteArrayOutputStream);
            diff.setShowCopiesAsAdds(false);
            diff.run();

            final String actualDiffOutput =  new String(byteArrayOutputStream.toByteArray());
            final String expectedDiffOutput = "";

            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testDiffLocalRelativeTarget() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testDiffLocalRelativeTarget", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("directory/file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File absoluteFile = new File(workingCopyDirectory, "directory/file");
            final File relativeFile = new File(SVNPathUtil.getRelativePath(
                    new File("").getAbsolutePath().replace(File.separatorChar, '/'),
                    absoluteFile.getAbsolutePath().replace(File.separatorChar, '/')
            ));

            TestUtil.writeFileContentsString(absoluteFile, "new contents");

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

            SvnDiff diff = svnOperationFactory.createDiff();
            diff.setTargets(SvnTarget.fromFile(relativeFile, SVNRevision.BASE), SvnTarget.fromFile(relativeFile, SVNRevision.WORKING));
            diff.setIgnoreAncestry(true);
            diff.setOutput(byteArrayOutputStream);
            diff.run();

            final String actualDiffOutput =  new String(byteArrayOutputStream.toByteArray());
            final String expectedDiffOutput = "";

            System.out.println("actualDiffOutput = " + actualDiffOutput);

            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);


        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testOldDiffGeneratorIsCalledOnCorrectPaths() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testOldDiffGeneratorIsCalledOnCorrectPaths", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.addFile("directory/file");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.changeFile("directory/file", "newContents".getBytes());
            commitBuilder2.commit();

            final OldGenerator generator = new OldGenerator();

            final SVNClientManager svnClientManager = SVNClientManager.newInstance();
            SVNDiffClient client = new SVNDiffClient(svnClientManager, new DefaultSVNOptions());
            client.setDiffGenerator(generator);
            client.doDiff(url, SVNRevision.create(1), SVNRevision.create(1), SVNRevision.create(2), SVNDepth.INFINITY, true, SVNFileUtil.DUMMY_OUT);

            final List<GeneratorCall> expectedCalls = new ArrayList<GeneratorCall>();
            expectedCalls.add(new GeneratorCall(GeneratorCallKind.DISPLAY_FILE_DIFF, "directory/file"));

            Assert.assertEquals(expectedCalls, generator.calls);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testGitDiffFormatForCopiedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testGitDiffFormatForCopiedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("copySource");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File copySourceFile = new File(workingCopyDirectory, "copySource");
            final File copyTargetFile = new File(workingCopyDirectory, "copyTarget");

            final SvnCopy copy = svnOperationFactory.createCopy();
            copy.addCopySource(SvnCopySource.create(SvnTarget.fromFile(copySourceFile), SVNRevision.WORKING));
            copy.setSingleTarget(SvnTarget.fromFile(copyTargetFile, SVNRevision.WORKING));
            copy.run();

            TestUtil.writeFileContentsString(copyTargetFile, "New contents (copy)");

            final File basePath = new File("");

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(basePath);

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setTargets(SvnTarget.fromFile(workingCopyDirectory, SVNRevision.BASE), SvnTarget.fromFile(workingCopyDirectory, SVNRevision.WORKING));
            diff.setUseGitDiffFormat(true);
            diff.setOutput(byteArrayOutputStream);
            diff.setDiffGenerator(diffGenerator);
            diff.run();

            final String actualDiffOutput = byteArrayOutputStream.toString();
            final String expectedDiffOutput = "Index: " +
                    getRelativePath(copyTargetFile, basePath) +
                    "\n" +
                    "===================================================================\n" +
                    "diff --git a/copySource b/copyTarget\n" +
                    "copy from copySource\n" +
                    "copy to copyTarget\n" +
                    "--- a/copySource\t(revision 0)\n" +
                    "+++ b/copyTarget\t(working copy)\n" +
                    "@@ -0,0 +1 @@\n" +
                    "+New contents (copy)\n" +
                    "\\ No newline at end of file\n";
            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testGitDiffFormatForMovedFile() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testGitDiffFormatForMovedFile", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("moveSource");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, SVNRevision.HEAD.getNumber());
            final File workingCopyDirectory = workingCopy.getWorkingCopyDirectory();

            final File moveSourceFile = new File(workingCopyDirectory, "moveSource");
            final File moveTargetFile = new File(workingCopyDirectory, "moveTarget");

            final SvnCopy move = svnOperationFactory.createCopy();
            move.setMove(true);
            move.addCopySource(SvnCopySource.create(SvnTarget.fromFile(moveSourceFile), SVNRevision.WORKING));
            move.setSingleTarget(SvnTarget.fromFile(moveTargetFile, SVNRevision.WORKING));
            move.run();

            TestUtil.writeFileContentsString(moveTargetFile, "New contents (move)");

            final File basePath = new File("");

            final SvnDiffGenerator diffGenerator = new SvnDiffGenerator();
            diffGenerator.setBasePath(basePath);

            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            final SvnDiff diff = svnOperationFactory.createDiff();
            diff.setTargets(SvnTarget.fromFile(workingCopyDirectory, SVNRevision.BASE), SvnTarget.fromFile(workingCopyDirectory, SVNRevision.WORKING));
            diff.setUseGitDiffFormat(true);
            diff.setOutput(byteArrayOutputStream);
            diff.setDiffGenerator(diffGenerator);
            diff.run();

            final String actualDiffOutput = byteArrayOutputStream.toString();
            final String expectedDiffOutput = "Index: " +
                    getRelativePath(moveSourceFile, basePath) +
                    "\n" +
                    "===================================================================\n" +
                    "diff --git a/moveSource b/moveSource\n" +
                    "deleted file mode 10644\n" +
                    "Index: " +
                    getRelativePath(moveTargetFile, basePath) +
                    "\n" +
                    "===================================================================\n" +
                    "diff --git a/moveSource b/moveTarget\n" +
                    "copy from moveSource\n" +
                    "copy to moveTarget\n" +
                    "--- a/moveSource\t(revision 0)\n" +
                    "+++ b/moveTarget\t(working copy)\n" +
                    "@@ -0,0 +1 @@\n" +
                    "+New contents (move)\n" +
                    "\\ No newline at end of file\n";
            Assert.assertEquals(expectedDiffOutput, actualDiffOutput);
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getRelativePath(File path, File basePath) {
        return SVNPathUtil.getRelativePath(basePath.getAbsolutePath().replace(File.separatorChar, '/'),
                path.getAbsolutePath().replace(File.separatorChar, '/'));
    }

    private String runLocalDiff(SvnOperationFactory svnOperationFactory, File target, File relativeToDirectory) throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        final SvnDiff diff = svnOperationFactory.createDiff();
        diff.setTargets(SvnTarget.fromFile(target, SVNRevision.BASE), SvnTarget.fromFile(target, SVNRevision.WORKING));
        diff.setOutput(byteArrayOutputStream);
        diff.setRelativeToDirectory(relativeToDirectory);
        diff.setIgnoreAncestry(true);
        diff.run();
        return new String(byteArrayOutputStream.toByteArray());
    }

    private String runDiff(SvnOperationFactory svnOperationFactory, SVNURL fileUrl, SVNRevision startRevision, SVNRevision endRevision) throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        final SvnDiff diff = svnOperationFactory.createDiff();
        diff.setTarget(SvnTarget.fromURL(fileUrl, startRevision), startRevision, endRevision);
        diff.setOutput(byteArrayOutputStream);
        diff.run();

        return new String(byteArrayOutputStream.toByteArray());
    }

    private String runDiff(SvnOperationFactory svnOperationFactory, SVNURL url1, SVNRevision svnRevision1, SVNURL url2, SVNRevision svnRevision2) throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        final SvnDiff diff = svnOperationFactory.createDiff();
        diff.setTargets(SvnTarget.fromURL(url1, svnRevision1), SvnTarget.fromURL(url2, svnRevision2));
        diff.setOutput(byteArrayOutputStream);
        diff.run();

        return new String(byteArrayOutputStream.toByteArray());
    }

    private String runDiff(SvnOperationFactory svnOperationFactory, File file, SVNRevision startRevision, SVNRevision endRevision) throws SVNException {
        final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        final SvnDiff diff = svnOperationFactory.createDiff();
        diff.setTarget(SvnTarget.fromFile(file, startRevision), startRevision, endRevision);
        diff.setOutput(byteArrayOutputStream);
        diff.run();

        return new String(byteArrayOutputStream.toByteArray());
    }

    public String getTestName() {
        return "DiffTest";
    }

    private static enum GeneratorCallKind {
        DISPLAY_PROP_DIFF, DISPLAY_FILE_DIFF, DISPLAY_DELETED_DIRECTORY, DISPLAY_ADDED_DIRECTORY
    }

    private static class GeneratorCall {
        private final GeneratorCallKind callKind;
        private final String path;

        public GeneratorCall(GeneratorCallKind callKind, String path) {
            this.callKind = callKind;
            this.path = path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            GeneratorCall that = (GeneratorCall) o;

            if (callKind != that.callKind) {
                return false;
            }
            if (!path.equals(that.path)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = callKind.hashCode();
            result = 31 * result + path.hashCode();
            return result;
        }

        @Override
        public String toString() {
            return "GeneratorCall{" +
                    "callKind=" + callKind +
                    ", path='" + path + '\'' +
                    '}';
        }
    }

    private static class OldGenerator implements ISVNDiffGenerator {

        private final List<GeneratorCall> calls;

        private OldGenerator() {
            calls = new ArrayList<GeneratorCall>();
        }

        public void init(String anchorPath1, String anchorPath2) {
        }

        public void setBasePath(File basePath) {
        }

        public void setForcedBinaryDiff(boolean forced) {
        }

        public void setEncoding(String encoding) {
        }

        public String getEncoding() {
            return null;
        }

        public void setEOL(byte[] eol) {
        }

        public byte[] getEOL() {
            return SVNProperty.EOL_LF_BYTES;
        }

        public void setDiffDeleted(boolean isDiffDeleted) {
        }

        public boolean isDiffDeleted() {
            return false;
        }

        public void setDiffAdded(boolean isDiffAdded) {
        }

        public boolean isDiffAdded() {
            return true;
        }

        public void setDiffCopied(boolean isDiffCopied) {
        }

        public boolean isDiffCopied() {
            return false;
        }

        public void setDiffUnversioned(boolean diffUnversioned) {
        }

        public boolean isDiffUnversioned() {
            return false;
        }

        public File createTempDirectory() throws SVNException {
            return SVNFileUtil.createTempDirectory("svnkitdiff");
        }

        public void displayPropDiff(String path, SVNProperties baseProps, SVNProperties diff, OutputStream result) throws SVNException {
            calls.add(new GeneratorCall(GeneratorCallKind.DISPLAY_PROP_DIFF, path));
        }

        public void displayFileDiff(String path, File file1, File file2, String rev1, String rev2, String mimeType1, String mimeType2, OutputStream result) throws SVNException {
            calls.add(new GeneratorCall(GeneratorCallKind.DISPLAY_FILE_DIFF, path));
        }

        public void displayDeletedDirectory(String path, String rev1, String rev2) throws SVNException {
            calls.add(new GeneratorCall(GeneratorCallKind.DISPLAY_DELETED_DIRECTORY, path));
        }

        public void displayAddedDirectory(String path, String rev1, String rev2) throws SVNException {
            calls.add(new GeneratorCall(GeneratorCallKind.DISPLAY_ADDED_DIRECTORY, path));
        }

        public boolean isForcedBinaryDiff() {
            return false;
        }
    }
}
