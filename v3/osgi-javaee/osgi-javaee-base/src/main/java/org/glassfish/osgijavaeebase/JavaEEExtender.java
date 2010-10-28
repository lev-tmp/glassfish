/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */


package org.glassfish.osgijavaeebase;

import org.osgi.framework.*;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This extender is responsible for detecting and deploying any Java EE OSGi bundle.
 *
 * @author Sanjeeb.Sahoo@Sun.COM
 */
public class JavaEEExtender implements Extender, SynchronousBundleListener {
    /*
     * Implementation Note: All methods are synchronized, because we don't allow the extender to stop while it
     * is deploying or undeploying something. Similarly, while it is being stopped, we don't want it to deploy
     * or undeploy something.
     * This is a synchronous bundle listener because it listens to LAZY_ACTIVATION event.
     */

    // TODO(Sahoo): We should consider using a BundleTracker instead of event listener.

    private OSGiContainer c;
    private static final Logger logger =
            Logger.getLogger(JavaEEExtender.class.getPackage().getName());
    private BundleContext context;
    private ServiceRegistration reg;

    public JavaEEExtender(BundleContext context) {
        this.context = context;
    }

    public synchronized void start() {
        c = new OSGiContainer(context);
        c.init();
        reg = context.registerService(OSGiContainer.class.getName(), c, null);
        context.addBundleListener(this);
    }

    public synchronized void stop() {
        if (c == null) return;
        context.removeBundleListener(this);
        if (c != null) c.shutdown();
        c = null;
        reg.unregister();
        reg = null;
    }

    public void bundleChanged(BundleEvent event) {
        Bundle bundle = event.getBundle();
        switch (event.getType()) {
            case BundleEvent.STARTED:
                // A bundle with LAZY_ACTIVATION policy can be started with eager activation policy unless
                // START_ACTIVATION_POLICY is set in the options while calling bundle.start(int options).
                // So, we can't rely on LAZY_ACTIVATION event alone to deploy a bundle with lazy activation policy.
                // At the same time, if a bundle with lazy activation policy is indeed started lazily, then
                // we would have deployed the bundle upon receiving the LAZY_ACTIVATION event in which case we must
                // avoid duplicate deployment upon receiving STARTED event. Hopefully this explains why we check
                // c.isDeployed(bundle).
                if (!c.isDeployed(bundle)) {
                    deploy(bundle);
                }
                break;
            case BundleEvent.LAZY_ACTIVATION:
                deploy(bundle);
                break;
            case BundleEvent.STOPPED:
                undeploy(bundle);
                break;
        }
    }

    private synchronized void deploy(Bundle b) {
        if (!isStarted()) return;
        try {
            c.deploy(b);
        }
        catch (Exception e) {
            logger.logp(Level.SEVERE, "JavaEEExtender", "deploy",
                    "Exception deploying bundle {0}",
                    new Object[]{b.getLocation()});
            logger.logp(Level.SEVERE, "JavaEEExtender", "deploy",
                    "Exception Stack Trace", e);
        }
    }

    private synchronized void undeploy(Bundle b) {
        if (!isStarted()) return;
        try {
            if (c.isDeployed(b)) {
                c.undeploy(b);
            }
        }
        catch (Exception e) {
            logger.logp(Level.SEVERE, "JavaEEExtender", "undeploy",
                    "Exception undeploying bundle {0}",
                    new Object[]{b.getLocation()});
            logger.logp(Level.SEVERE, "JavaEEExtender", "undeploy",
                    "Exception Stack Trace", e);
        }
    }

    private synchronized boolean isStarted() {
        return c!= null;
    }
}
