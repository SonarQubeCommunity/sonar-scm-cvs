/*
 * SonarQube :: Plugins :: CVS
 * Copyright (C) 2009 ${owner}
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.scm.cvs;

import com.google.common.base.Joiner;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.netbeans.lib.cvsclient.CVSRoot;
import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.admin.StandardAdminHandler;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.commandLine.CommandFactory;
import org.netbeans.lib.cvsclient.commandLine.GetOpt;
import org.netbeans.lib.cvsclient.connection.AbstractConnection;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.Connection;
import org.netbeans.lib.cvsclient.connection.ConnectionFactory;
import org.netbeans.lib.cvsclient.connection.PServerConnection;
import org.netbeans.lib.cvsclient.connection.StandardScrambler;
import org.netbeans.lib.cvsclient.event.CVSListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.TempFolder;

import javax.annotation.Nullable;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CvsBlameCommand extends BlameCommand {

  private static final Logger LOG = LoggerFactory.getLogger(CvsBlameCommand.class);

  /**
   * The connection to the server
   */
  private Connection connection;

  /**
   * The client that manages interactions with the server
   */
  private Client client;

  private final System2 system2;
  private final CvsConfiguration config;
  private final TempFolder tempFolder;

  public CvsBlameCommand(CvsConfiguration config, TempFolder tempFolder) {
    this(config, tempFolder, System2.INSTANCE);
  }

  CvsBlameCommand(CvsConfiguration config, TempFolder tempFolder, System2 system2) {
    this.config = config;
    this.tempFolder = tempFolder;
    this.system2 = system2;
  }

  @Override
  public void blame(BlameInput input, BlameOutput output) {
    FileSystem fs = input.fileSystem();
    LOG.debug("Working directory: " + fs.baseDir().getAbsolutePath());
    for (InputFile inputFile : input.filesToBlame()) {
      List<String> args = annotateArguments(inputFile);

      CvsLogListener logListener = new CvsLogListener();
      CvsBlameConsumer consumer = new CvsBlameConsumer(inputFile.relativePath());
      try {
        boolean isSuccess = processCommand(args.toArray(new String[args.size()]), config.baseDir(), fs.baseDir(), logListener);
        if (!isSuccess) {
          throw new IllegalStateException("The CVS annotate command [cvs " + Joiner.on(' ').join(args) + "] failed: " + logListener.getStdout().toString());
        }
        BufferedReader stream = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(logListener.getStdout().toString().getBytes())));
        String line;
        while ((line = stream.readLine()) != null) {
          consumer.consumeLine(line);
        }
      } catch (CommandException e) {
        throw new IllegalStateException("The CVS annotate command [cvs " + Joiner.on(' ').join(args) + "] failed", e.getUnderlyingException());
      } catch (Exception e) {
        throw new IllegalStateException("The CVS annotate command [cvs " + Joiner.on(' ').join(args) + "] failed: " + logListener.getStderr().toString(), e);
      }
      List<BlameLine> lines = consumer.getLines();
      output.blameResult(inputFile, lines);
    }
    disconnect();
  }

  public List<String> annotateArguments(InputFile inputFile) {

    List<String> args = new ArrayList<String>();
    if (!config.compressionDisabled()) {
      args.add("-z" + config.compressionLevel());
    }

    if (!config.useCvsRc()) {
      // don't use ~/.cvsrc
      args.add("-f");
    }

    if (config.traceCvsCommands()) {
      args.add("-t");
    }

    File tempDir = tempFolder.newDir("cvs");
    args.add("-T");
    args.add(tempDir.getAbsolutePath());

    if (config.cvsRoot() != null) {
      args.add("-d");
      args.add(config.cvsRoot());
    }

    args.add("-q");

    args.add("annotate");

    args.add(inputFile.relativePath());

    return args;
  }

  /**
   * Process the CVS command passed in args[] array with all necessary
   * options. The only difference from main() method is, that this method
   * does not exit the JVM and provides command output.
   *
   * @param args The command with options
   */
  private boolean processCommand(String[] args, @Nullable File cvsBaseDir, File projectBaseDir, CVSListener listener) throws Exception {
    // Set up the CVSRoot. Note that it might still be null after this
    // call if the user has decided to set it with the -d command line global option
    GlobalOptions globalOptions = new GlobalOptions();
    if (cvsBaseDir != null) {
      globalOptions.setCVSRoot(getCVSRoot(cvsBaseDir));
    }

    // Set up any global options specified. These occur before thename of the command to run
    int commandIndex;

    try {
      commandIndex = processGlobalOptions(args, globalOptions);
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Invalid argument: " + e);
    }

    // if we don't have a CVS root by now, the user has messed up
    if (globalOptions.getCVSRoot() == null) {
      throw new IllegalStateException("No CVS root is set. Please set " + CvsConfiguration.CVS_ROOT_PROP_KEY + ".");
    }

    // parse the CVS root into its constituent parts
    CVSRoot root;
    final String cvsRoot = globalOptions.getCVSRoot();
    try {
      root = CVSRoot.parse(cvsRoot);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Incorrect format for CVSRoot: " + cvsRoot + "\nThe correct format is: "
        + "[:method:][[user][:password]@][hostname:[port]]/path/to/repository"
        + "\nwhere \"method\" is pserver.", e);
    }

    final String command = args[commandIndex];

    // this is not login, but a 'real' cvs command, so construct it,
    // set the options, and then connect to the server and execute it

    org.netbeans.lib.cvsclient.command.Command c;
    try {
      c = CommandFactory.getDefault().createCommand(command, args, ++commandIndex, globalOptions, cvsBaseDir.getAbsolutePath());
    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Illegal argument: " + e.getMessage(), e);
    }

    String password = null;

    if (CVSRoot.METHOD_PSERVER.equals(root.getMethod())) {
      password = root.getPassword();
      if (password != null) {
        password = StandardScrambler.getInstance().scramble(password);
      } else {
        password = lookupPassword(cvsRoot);
        if (password == null) {
          password = StandardScrambler.getInstance().scramble("");
          // an empty password
        }
      }
    }
    connect(projectBaseDir, root, password);
    client.getEventManager().addCVSListener(listener);
    LOG.debug("Executing CVS command: " + c.getCVSCommand());
    return client.executeCommand(c, globalOptions);
  }

  /**
   * Lookup the password in the .cvspass file. This file is looked for in the
   * user.home directory if the option cvs.passfile is not set
   *
   * @param cvsRoot the CVS root for which the password is being searched
   * @return the password, scrambled
   */
  private static String lookupPassword(String cvsRoot) {
    File passFile = new File(System.getProperty("cygwin.user.home", System.getProperty("user.home")) + File.separatorChar + ".cvspass");

    BufferedReader reader = null;
    String password = null;

    try {
      reader = new BufferedReader(new FileReader(passFile));
      password = processCvspass(cvsRoot, reader);
    } catch (IOException e) {
      LOG.warn("Could not read password for '" + cvsRoot + "' from '" + passFile + "'", e);
      return null;
    } finally {
      if (reader != null) {
        try {
          reader.close();
        } catch (IOException e) {
          LOG.error("Warning: could not close password file.");
        }
      }
    }
    if (password == null) {
      LOG.error("Didn't find password for CVSROOT '" + cvsRoot + "'.");
    }
    return password;
  }

  /**
   * Read in a list of return delimited lines from .cvspass and retreive
   * the password.  Return null if the cvsRoot can't be found.
   *
   * @param cvsRoot the CVS root for which the password is being searched
   * @param reader  A buffered reader of lines of cvspass information
   * @return The password, or null if it can't be found.
   * @throws IOException
   */
  private static String processCvspass(String cvsRoot, BufferedReader reader) throws IOException {
    String line;
    String password = null;
    while ((line = reader.readLine()) != null) {
      if (line.startsWith("/")) {
        String[] cvspass = StringUtils.split(line, " ");
        String cvspassRoot = cvspass[1];
        if (compareCvsRoot(cvsRoot, cvspassRoot)) {
          int index = line.indexOf(cvspassRoot) + cvspassRoot.length() + 1;
          password = line.substring(index);
          break;
        }
      } else if (line.startsWith(cvsRoot)) {
        password = line.substring(cvsRoot.length() + 1);
        break;
      }
    }
    return password;
  }

  static boolean compareCvsRoot(String cvsRoot, String target) {
    String s1 = completeCvsRootPort(cvsRoot);
    String s2 = completeCvsRootPort(target);
    return s1 != null && s1.equals(s2);
  }

  private static String completeCvsRootPort(String cvsRoot) {
    String result = cvsRoot;
    int idx = cvsRoot.indexOf(':');
    for (int i = 0; i < 2 && idx != -1; i++) {
      idx = cvsRoot.indexOf(':', idx + 1);
    }
    if (idx != -1 && cvsRoot.charAt(idx + 1) == '/') {
      StringBuilder sb = new StringBuilder();
      sb.append(cvsRoot.substring(0, idx + 1));
      sb.append("2401");
      sb.append(cvsRoot.substring(idx + 1));
      result = sb.toString();
    }
    return result;

  }

  /**
   * Creates the connection and the client and connects.
   */
  private void connect(File baseDir, CVSRoot root, String password) throws AuthenticationException, CommandAbortedException {
    if (client != null) {
      return;
    }
    if (CVSRoot.METHOD_EXT.equals(root.getMethod())) {
      String cvsRsh = system2.envVariable("CVS_RSH");

      if (cvsRsh != null) {
        if (cvsRsh.indexOf(' ') < 0) {
          // cvs_rsh should be 'rsh' or 'ssh'
          // Complete the command to use
          String username = root.getUserName();
          if (username == null) {
            username = System.getProperty("user.name");
          }

          cvsRsh += " " + username + "@" + root.getHostName() + " cvs server";
        }

        AbstractConnection conn = new org.netbeans.lib.cvsclient.connection.ExtConnection(cvsRsh);
        conn.setRepository(root.getRepository());
        connection = conn;
      } else {
        connection = new ExtConnection(root);
      }
    } else {
      connection = ConnectionFactory.getConnection(root);
      if (CVSRoot.METHOD_PSERVER.equals(root.getMethod())) {
        ((PServerConnection) connection).setEncodedPassword(password);
      }
    }
    connection.open();

    client = new Client(connection, new StandardAdminHandler());
    client.setLocalPath(baseDir.getAbsolutePath());
  }

  private void disconnect() {
    if (connection != null && connection.isOpen()) {
      try {
        connection.close();
      } catch (IOException e) {
        throw new IllegalStateException("Unable to disconnect", e);
      }
    }
  }

  /**
   * Obtain the CVS root from the CVS directory
   *
   * @return the CVSRoot string
   */
  private static String getCVSRoot(File cvsBaseDir) {
    File f = cvsBaseDir;
    File rootFile = new File(f, "CVS/Root");
    try {
      return FileUtils.readFileToString(rootFile).trim().substring(1);
    } catch (IOException e) {
      throw new IllegalStateException("Can't read " + rootFile.getAbsolutePath());
    }
  }

  /**
   * Process global options passed into the application
   *
   * @param args          the argument list, complete
   * @param globalOptions the global options structure that will be passed to
   *                      the command
   */
  private static int processGlobalOptions(String[] args, GlobalOptions globalOptions)
  {
    final String getOptString = globalOptions.getOptString();
    GetOpt go = new GetOpt(args, getOptString);
    int ch;
    while ((ch = go.getopt()) != GetOpt.optEOF)
    {
      String arg = go.optArgGet();
      boolean success = globalOptions.setCVSCommand((char) ch, arg);
      if (!success)
      {
        throw new IllegalArgumentException("Failed to set CVS Command: -" + ch + " = " + arg);
      }
    }

    return go.optIndexGet();
  }

}
