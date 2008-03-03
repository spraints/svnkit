/*
 * ====================================================================
 * Copyright (c) 2004-2007 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.tmatesoft.svn.cli2;

import java.text.MessageFormat;

import org.tmatesoft.svn.core.SVNAuthenticationException;
import org.tmatesoft.svn.core.SVNCancelException;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;


/**
 * @version 1.1.2
 * @author  TMate Software Ltd.
 */
public abstract class AbstractSVNLauncher {

    private static volatile boolean ourIsCompleted;

    protected void run(String[] args) {
        ourIsCompleted = false;
        
        if (needArgs() && (args == null || args.length < 1)) {
            printBasicUsage();
            failure();
            return;
        }
        initRA();
        registerOptions();
        registerCommands();

        SVNCommandLine commandLine = new SVNCommandLine(needCommand());
        try {
            commandLine.init(args);
        } catch (SVNException e) {
            handleError(e);
            printBasicUsage();
            failure();
            return;
        }
        AbstractSVNCommandEnvironment env = createCommandEnvironment();
        Runtime.getRuntime().addShutdownHook(new Thread(new Cancellator(env)));
        
        try {
            try {
                env.init(commandLine);
            } catch (SVNException e) {
                handleError(e);
                if (e instanceof SVNCancelException || e instanceof SVNAuthenticationException) {
                    env.dispose();
                    failure();
                    return;
                }
                printBasicUsage();
                env.dispose();
                failure();
                return;
            }
    
            env.initClientManager();
            if (!env.run()) {
                env.dispose();
                failure();
                return;
            }
            env.dispose();
            success();
        } catch (Throwable th) {
            th.printStackTrace();
            if (env != null) {
                env.dispose();
            }
            failure();
        } finally {
            setCompleted();
        }
    }

    protected abstract boolean needArgs();

    protected abstract boolean needCommand();

    protected abstract String getProgramName();

    protected abstract AbstractSVNCommandEnvironment createCommandEnvironment();
    
    protected void printBasicUsage() {
        System.err.println(MessageFormat.format("Type ''{0} help'' for usage.", new Object[] {getProgramName()}));
    }

    protected abstract void registerCommands();

    protected abstract void registerOptions();
    
    private void initRA() {
        DAVRepositoryFactory.setup();
        SVNRepositoryFactoryImpl.setup();
        FSRepositoryFactory.setup();
    }

    public void handleError(SVNException e) {
        System.err.println(e.getMessage());
    }

    public void failure() {
        setCompleted();
        try {
            System.exit(1);
        } catch (SecurityException se) {
            
        }
    }

    public void success() {
        setCompleted();
        try {
            System.exit(0);
        } catch (SecurityException se) {
            
        }
    }
    
    private void setCompleted() {
        synchronized (AbstractSVNLauncher.class) {
            ourIsCompleted = true;
            AbstractSVNLauncher.class.notifyAll();
        }
    }
    
    private class Cancellator implements Runnable {
        
        private AbstractSVNCommandEnvironment myEnvironment;

        public Cancellator(AbstractSVNCommandEnvironment env) {
            myEnvironment = env;
        }
        
        public void run() {
            myEnvironment.setCancelled();
            synchronized (AbstractSVNLauncher.class) {
                while(!ourIsCompleted) {
                    try {
                        AbstractSVNLauncher.class.wait();
                    } catch (InterruptedException e) {
                    }
                }
            }
        }
    }


}
