/*
 *  The MIT License
 *
 *  Copyright 2013 rinrinne All rights reserved.
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package com.sonyericsson.hudson.plugins.gerrit.trigger.extensions;

import com.sonymobile.tools.gerrit.gerritevents.dto.events.GerritTriggeredEvent;
import com.sonyericsson.hudson.plugins.gerrit.trigger.GerritServer;
import com.sonyericsson.hudson.plugins.gerrit.trigger.PluginImpl;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.DuplicatesUtil;
import com.sonyericsson.hudson.plugins.gerrit.trigger.mock.Setup;
import com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock;

import hudson.ExtensionList;
import hudson.model.Result;
import org.apache.sshd.SshServer;
import org.jvnet.hudson.test.HudsonTestCase;
import org.jvnet.hudson.test.TestExtension;
import org.jvnet.hudson.test.recipes.LocalData;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import jenkins.model.Jenkins;

import static com.sonymobile.tools.gerrit.gerritevents.mock.SshdServerMock.GERRIT_STREAM_EVENTS;

//CS IGNORE MagicNumber FOR NEXT 200 LINES. REASON: Testdata.

/**
 * Test for {@link GerritTriggeredBuildListener}.
 *
 * @author rinrinne &lt;rinrin.ne@gmail.com&gt;
 */
public class GerritTriggeredBuildListenerTest extends HudsonTestCase {

    private SshServer sshd;
    @SuppressWarnings("unused")
    private SshdServerMock.KeyPairFiles sshKey;
    private SshdServerMock server;

    private static CountDownLatch buildListenerLatch;

    @Override
    protected void setUp() throws Exception {
        sshKey = SshdServerMock.generateKeyPair();
        server = new SshdServerMock();
        sshd = SshdServerMock.startServer(server);
        server.returnCommandFor("gerrit ls-projects", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor(GERRIT_STREAM_EVENTS, SshdServerMock.CommandMock.class);
        server.returnCommandFor("gerrit review.*", SshdServerMock.EofCommandMock.class);
        server.returnCommandFor("gerrit version", SshdServerMock.EofCommandMock.class);
        System.setProperty(PluginImpl.TEST_SSH_KEYFILE_LOCATION_PROPERTY, sshKey.getPrivateKey().getAbsolutePath());
        super.setUp();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        sshd.stop(true);
        sshd = null;
    }

    /**
     * Tests that {@link GrritTriggeredBuildListener} can listen triggered build.
     *
     * @throws Exception if so.
     */
    @LocalData
    public void testListenTriggeredBuild() throws Exception {
        ExtensionList<GerritTriggeredBuildListener> list =
                Jenkins.getInstance().getExtensionList(GerritTriggeredBuildListener.class);
        assertTrue("Listener has not been registered", list.size() > 0);

        buildListenerLatch = new CountDownLatch(2);
        GerritServer gerritServer = PluginImpl.getInstance().getServer(PluginImpl.DEFAULT_SERVER_NAME);
        DuplicatesUtil.createGerritTriggeredJob(this, "projectX");
        server.waitForCommand(GERRIT_STREAM_EVENTS, 2000);
        gerritServer.triggerEvent(Setup.createPatchsetCreated());

        assertTrue("Time out", buildListenerLatch.await(15, TimeUnit.SECONDS));
    }

    /**
     * A {@link GrritTriggeredBuildListener} implementation class.
     * This would be automatically registered to system by @TestExtension annotation.
     */
    @TestExtension("testListenTriggeredBuild")
    public static class GerritTriggeredBuildListenerImpl extends GerritTriggeredBuildListener {

        @Override
        public void onStarted(GerritTriggeredEvent event, String command) {
            System.out.println("onStarted: [event] " + event.getEventType() + " [command] " + command);
            buildListenerLatch.countDown();
        }

        @Override
        public void onCompleted(Result result, GerritTriggeredEvent event, String command) {
            System.out.println("onStarted: [event] " + event.getEventType()
                    + " [result] " + result.toString()
                    + " [command] " + command);
            buildListenerLatch.countDown();
        }
    }
}
