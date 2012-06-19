package org.tmatesoft.svn.test;

import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.wc.admin.SVNAdminAreaFactory;
import org.tmatesoft.svn.core.internal.wc17.SVNWCContext;
import org.tmatesoft.svn.core.internal.wc17.db.ISVNWCDb;
import org.tmatesoft.svn.core.internal.wc2.compat.SvnCodec;
import org.tmatesoft.svn.core.wc.*;
import org.tmatesoft.svn.core.wc2.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

public class StatusTest {

    @Test
    public void testRemoteStatusShowsLockDavAccess() throws Exception {
        final TestOptions options = TestOptions.getInstance();
        Assume.assumeTrue(TestUtil.areAllApacheOptionsSpecified(options));

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteStatusShowsLockDavAccess", options);
        try {
            final ISVNAuthenticationManager authenticationManager = new BasicAuthenticationManager("user1", "user1");

            final Map<String, String> loginToPassword = new HashMap<String, String>();
            loginToPassword.put("user1", "user1");

            final SVNURL url = sandbox.createSvnRepositoryWithDavAccess(loginToPassword);

            final CommitBuilder commitBuilder1 = new CommitBuilder(url);
            commitBuilder1.setAuthenticationManager(authenticationManager);
            commitBuilder1.addDirectory("directory");
            commitBuilder1.commit();

            final CommitBuilder commitBuilder2 = new CommitBuilder(url);
            commitBuilder2.setAuthenticationManager(authenticationManager);
            commitBuilder2.addFile("directory/file");
            commitBuilder2.commit();

            final SVNURL directoryUrl = url.appendPath("directory", false);
            final SVNURL fileUrl = directoryUrl.appendPath("file", false);

            final File workingCopyDirectory = sandbox.createDirectory("wc");

            svnOperationFactory.setAuthenticationManager(authenticationManager);

            final SvnCheckout checkout = svnOperationFactory.createCheckout();
            checkout.setSource(SvnTarget.fromURL(directoryUrl));
            checkout.setRevision(SVNRevision.create(1));
            checkout.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
            checkout.run();

            final SvnSetLock setLock = svnOperationFactory.createSetLock();
            setLock.setSingleTarget(SvnTarget.fromURL(fileUrl));
            setLock.run();


            final SVNWCContext context = new SVNWCContext(svnOperationFactory.getOptions(), svnOperationFactory.getEventHandler());
            try {
                final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
                getStatus.setRemote(true);
                getStatus.setReportAll(true);
                getStatus.setSingleTarget(SvnTarget.fromFile(workingCopyDirectory));
                getStatus.setReceiver(new ISvnObjectReceiver<SvnStatus>() {
                    public void receive(SvnTarget target, SvnStatus status) throws SVNException {
                        final SVNStatus oldStatus = SvnCodec.status(context, status);

                        if ("file".equals(status.getPath().getName())) {
                            Assert.assertNull(status.getLock());
                            Assert.assertNotNull(status.getRepositoryLock());
                            Assert.assertNull(oldStatus.getLocalLock());
                            Assert.assertNotNull(oldStatus.getRemoteLock());
                        } else {
                            Assert.assertNull(status.getLock());
                            Assert.assertNull(status.getRepositoryLock());
                            Assert.assertNull(oldStatus.getLocalLock());
                            Assert.assertNull(oldStatus.getRemoteLock());
                        }
                    }
                });
                getStatus.run();
            } finally {
                context.close();
            }
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testRemoteStatusOnModifiedFileDepthEmpty() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testRemoteStatusOnModifiedFileDepthEmpty", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("file");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url);

            final File file = workingCopy.getFile("file");
            TestUtil.writeFileContentsString(file, "modified");

            final SvnGetStatus getStatus = svnOperationFactory.createGetStatus();
            getStatus.setDepth(SVNDepth.EMPTY);
            getStatus.setRemote(true);
            getStatus.setSingleTarget(SvnTarget.fromFile(file));
            final SvnStatus status = getStatus.run();

            Assert.assertNotNull(status);
            Assert.assertEquals(SVNStatusType.STATUS_MODIFIED, status.getTextStatus());
        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    @Test
    public void testWorkingCopyFormatReported() throws Exception {
        final TestOptions options = TestOptions.getInstance();

        final SvnOperationFactory svnOperationFactory = new SvnOperationFactory();
        final Sandbox sandbox = Sandbox.createWithCleanup(getTestName() + ".testWorkingCopyFormatReported", options);
        try {
            final SVNURL url = sandbox.createSvnRepository();

            final CommitBuilder commitBuilder = new CommitBuilder(url);
            commitBuilder.addFile("remotelyAddedFile");
            commitBuilder.commit();

            final WorkingCopy workingCopy = sandbox.checkoutNewWorkingCopy(url, 0);

            final File unversionedFile = workingCopy.getFile("unversionedFile");
            TestUtil.writeFileContentsString(unversionedFile, "contents");

            final File addedFile = workingCopy.getFile("addedFile");
            TestUtil.writeFileContentsString(addedFile, "contents");
            workingCopy.add(addedFile);

            final int expectedWcFormat = TestUtil.isNewWorkingCopyTest() ? ISVNWCDb.WC_FORMAT_17 : SVNAdminAreaFactory.WC_FORMAT_16;

            final SVNClientManager clientManager = SVNClientManager.newInstance();
            try {
                final SVNStatusClient statusClient = clientManager.getStatusClient();
                statusClient.doStatus(workingCopy.getWorkingCopyDirectory(), SVNRevision.WORKING, SVNDepth.INFINITY, true, true, true, false, new ISVNStatusHandler() {
                    public void handleStatus(SVNStatus status) throws SVNException {
                        Assert.assertEquals(expectedWcFormat, status.getWorkingCopyFormat());
                    }
                }, null);
            } finally {
                clientManager.dispose();
            }

        } finally {
            svnOperationFactory.dispose();
            sandbox.dispose();
        }
    }

    private String getTestName() {
        return "StatusTest";
    }
}

