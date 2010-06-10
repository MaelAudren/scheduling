/*
 * ################################################################
 *
 * ProActive Parallel Suite(TM): The Java(TM) library for
 *    Parallel, Distributed, Multi-Core Computing for
 *    Enterprise Grids & Clouds
 *
 * Copyright (C) 1997-2010 INRIA/University of 
 * 				Nice-Sophia Antipolis/ActiveEon
 * Contact: proactive@ow2.org or contact@activeeon.com
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; version 3 of
 * the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307
 * USA
 *
 * If needed, contact us to obtain a release under GPL Version 2 
 * or a different license than the GPL.
 *
 *  Initial developer(s):               The ProActive Team
 *                        http://proactive.inria.fr/team_members.htm
 *  Contributor(s): ActiveEon Team - http://www.activeeon.com
 *
 * ################################################################
 * $$ACTIVEEON_CONTRIBUTOR$$
 */
package org.ow2.proactive.scheduler.ext.matlab.util;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.JarURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;

import org.objectweb.proactive.core.util.OperatingSystem;
import org.objectweb.proactive.core.node.NodeException;
import org.objectweb.proactive.api.PAActiveObject;
import org.ow2.proactive.scheduler.ext.common.util.IOTools;
import org.ow2.proactive.scheduler.ext.common.util.LinuxShellExecuter;
import org.ow2.proactive.scheduler.ext.common.util.ProcessResult;
import org.ow2.proactive.scheduler.ext.common.util.Shell;
import org.ow2.proactive.scheduler.ext.matlab.exception.MatlabInitException;


public class MatlabFinder {

    /** the OS where this JVM is running **/
    private static OperatingSystem os = OperatingSystem.getOperatingSystem();

    private static String MATLAB_SCRIPT_LINUX = "find_matlab_command.sh";
    private static String MATLAB_SCRIPT_WINDOWS = "find_matlab_command.bat";

    /**
     * Utility function to find Matlab
     * @throws IOException
     * @throws InterruptedException
     * @throws MatlabInitException
     */
    public static MatlabConfiguration findMatlab(boolean debug) throws IOException, InterruptedException,
            MatlabInitException, NodeException {

        Process p1 = null;
        MatlabConfiguration answer = null;

        if (os.equals(OperatingSystem.unix)) {
            // Under linux we launch an instance of the Shell
            // and then pipe to it the script's content
            if (debug) {
                System.out.println("Using script at " + MATLAB_SCRIPT_LINUX);
            }
            InputStream is = MatlabFinder.class.getResourceAsStream(MATLAB_SCRIPT_LINUX);
            p1 = LinuxShellExecuter.executeShellScript(is, Shell.Bash);
        } else if (os.equals(OperatingSystem.windows)) {
            // We can't execute the script on Windows the same way,
            // we need to write the content of the batch file locally and then launch the file
            InputStream is = MatlabFinder.class.getResourceAsStream(MATLAB_SCRIPT_WINDOWS);

            // Code for writing the content of the stream inside a local file
            List<String> inputLines = IOTools.getContentAsList(is);
            String tmpDir = System.getProperty("java.io.tmpdir");
            String nodeName = PAActiveObject.getNode().getVMInformation().getName().replace('-', '_') + "_" +
                PAActiveObject.getNode().getNodeInformation().getName().replace('-', '_');
            File nodeDir = new File(tmpDir, nodeName);
            if (!nodeDir.exists()) {
                nodeDir.mkdir();
                nodeDir.deleteOnExit();
            }
            File batchFile = new File(nodeDir, MATLAB_SCRIPT_WINDOWS);

            if (batchFile.exists()) {
                batchFile.delete();
            }

            batchFile.createNewFile();
            batchFile.deleteOnExit();

            if (batchFile.canWrite()) {
                PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(batchFile)));

                for (String line : inputLines) {
                    pw.println(line);
                    pw.flush();
                }
                pw.close();
            } else {
                throw new MatlabInitException("can't write in : " + batchFile);
            }

            // End of this code
            if (debug) {
                System.out.println("Using script at " + batchFile.getAbsolutePath());
            }

            p1 = Runtime.getRuntime().exec(new String[] { "cmd", "/c", batchFile.getName() }, null,
                    batchFile.getParentFile());
        } else {
            throw new UnsupportedOperationException("Finding Matlab on " + os + " is not supported yet");
        }

        ProcessResult pres = IOTools.blockingGetProcessResult(p1);

        if (debug) {
            System.out.println("Output of script :");
            for (String ln : pres.getOutput()) {
                System.out.println(ln);
            }
            System.out.flush();
            System.err.println("Error output of script :");
            for (String ln : pres.getError()) {
                System.err.println(ln);
            }
            System.err.flush();
        }

        // The batch file is supposed to write, if it's successful, two lines :
        // 1st line : the full path to the matlab command
        // 2nd line : the name of the os-dependant arch dir
        if (p1.waitFor() == 0) {
            String line;
            String home = null;
            int i = 0;
            String[] output = pres.getOutput();
            while (!(line = output[i++]).startsWith("----")) {
                home = line;
            }

            File file = new File(home);
            String matlabHome = file.getAbsolutePath();

            String matlabBinPath = output[i++];
            File binpath = new File(matlabBinPath);
            String matlabCommandName = binpath.getName();
            String matlabBinDir = binpath.getParent();
            String matlabLibDirName = output[i++];
            String matlabVersion = null;
            while (!(line = output[i++]).startsWith("----")) {
                matlabVersion = line;
            }
            String ptolemyPath;
            try {
                ptolemyPath = findPtolemyLibDir(matlabVersion, matlabLibDirName);
            } catch (URISyntaxException e) {
                throw new MatlabInitException(e);
            }

            answer = new MatlabConfiguration(matlabHome, matlabVersion, matlabLibDirName, matlabBinDir,
                matlabCommandName, ptolemyPath);

        } else {
            StringWriter error_message = new StringWriter();
            PrintWriter pw = new PrintWriter(error_message);
            pw.println("Error during find_matlab script execution !");
            pw.println("Output log:");
            String[] output = pres.getOutput();
            String[] error = pres.getOutput();
            for (String l : output) {
                pw.println(l);
            }
            pw.println("Error log:");
            for (String l : error) {
                pw.println(l);
            }

            throw new MatlabInitException(error_message.toString());
        }
        return answer;
    }

    /**
     * Finds where the ptolemy library is installed for this specific architecture
     * @return a path to ptolemy libraries
     * @throws IOException
     * @throws URISyntaxException
     * @throws MatlabInitException
     * @throws IOException 
     * @throws URISyntaxException 
     */
    private static String findPtolemyLibDir(String matlabVersion, String matlabLibDirName)
            throws MatlabInitException, IOException, URISyntaxException {
        JarURLConnection conn = (JarURLConnection) MatlabFinder.class.getResource("/ptolemy/matlab")
                .openConnection();
        URL jarFileURL = conn.getJarFileURL();

        File jarFile = new File(jarFileURL.toURI());
        File libDirFile = jarFile.getParentFile();
        URI ptolemyLibDirURI = libDirFile.toURI().resolve(
                matlabVersion + "/" + matlabLibDirName.replace("\\", "/") + "/");
        File answer = new File(ptolemyLibDirURI);

        if (!answer.exists() || !answer.canRead()) {
            throw new MatlabInitException("Can't find ptolemy native library at " + answer +
                ". The native library is generated from scripts in SCHEDULER/extensions/matlab. Refer to README file.");
        } else {
            File libraryFile = new File(ptolemyLibDirURI.resolve(System.mapLibraryName("ptmatlab")));
            if (!libraryFile.exists() || !libraryFile.canRead()) {
                throw new MatlabInitException("Can't find ptolemy native library at " + libraryFile +
                    ". The native library is generated from scripts in SCHEDULER/extensions/matlab. Refer to README file.");
            }
        }
        return answer.getAbsolutePath();
    }

    public static void main(String[] args) throws MatlabInitException, IOException, InterruptedException,
            NodeException {
        MatlabFinder.findMatlab(true);
    }

}
