/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.sun.enterprise.security;

import com.sun.enterprise.util.LocalStringManagerImpl;
import com.sun.enterprise.v3.server.ServerEnvironment;
import com.sun.enterprise.v3.server.Globals;
import com.sun.logging.LogDomains;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jvnet.hk2.component.Habitat;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.annotations.Inject;

@Service
public class SecurityServicesUtil {

    private static Habitat habitat = Globals.getDefaultHabitat();
    
    private static final LocalStringManagerImpl _localStrings =
            new LocalStringManagerImpl(SecurityServicesUtil.class);
    private static final Logger _logger = LogDomains.getLogger(LogDomains.SECURITY_LOGGER);
    // SecureRandom number used for HTTPS and IIOP/SSL.
    // This number is accessed by iiop/IIOPSSLSocketFactory
    // & web/security/SSLSocketFactory classes.
    public static final SecureRandom secureRandom = new SecureRandom();

    @Inject
    ServerEnvironment env;

    static {
        secureRandom.setSeed(System.currentTimeMillis());
    }

    /**
     * code moved from J2EEServer.run()
     */
    public void initSecureSeed() {
        File secureSeedFile = new File(env.getConfigDirPath(),"secure.seed");

        // read the secure random from the file
        long seed = readSecureSeed(secureSeedFile);
        secureRandom.setSeed(seed);
        // generate a new one for the next startup
        seed = secureRandom.nextLong();
        writeSecureSeed(secureSeedFile, seed);
        secureSeedFile = null;
    }

    /** read the secure random number from the file.
     *  If the seed is not present, the default expensive SecureRandom seed
     *  generation algorithm is invoked to return a new seed number
     *  @param fname the file to be read - here secure.seed file.
     */
    private long readSecureSeed(File fname) {
        byte[] seed;
        try {
            BufferedReader fis = new BufferedReader(new FileReader(fname));
            try {
                String line = fis.readLine();
                fis.close();
                // returning a long value.
                Long lseed = new Long(line);
                return lseed.longValue();
            } catch (IOException e) {
                if (fis != null) {
                    fis.close();
                }
            }
        } catch (Throwable e) {  // IASRI 4666401 if all fails just create new
        }
        // BEGIN IASRI 4703002
        // In order to work around JVM bug 4709460 avoid internal seeding.
        // (Call setSeed again (see static block) to attempt to add some
        // minimal randomness; setSeed calls are cumulative)

        secureRandom.setSeed(System.currentTimeMillis());
        long newSeed = secureRandom.nextLong();
        return newSeed;
    }

    /** write the new secure seed to the secure.seed file to speed up
     * startup the next time the server is started.
     * @param fname secure.seed file
     * @param seed seed the value of the 8 byte seed.
     */
    private void writeSecureSeed(File fname, long seed) {
        try {
            FileOutputStream fos = new FileOutputStream(fname);
            String sseed = Long.toString(seed);
            fos.write(sseed.getBytes());
            fos.close();
        } catch (IOException e) {
            String errmsg =
                    _localStrings.getLocalString("j2ee.startupslow",
                    "Cannot write the seed file for fast startup. The next startup will be slow.");

            _logger.log(Level.WARNING, errmsg);
        }

    }

    public Habitat getHabitat() {
        return habitat;
    }

    public static SecurityServicesUtil getInstance() {
        // return my singleton service
        return habitat.getComponent(SecurityServicesUtil.class);
    }
}
