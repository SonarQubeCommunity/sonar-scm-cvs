/*
 * SonarQube :: Plugins :: SCM :: CVS
 * Copyright (C) 2014-2016 SonarSource SA
 * mailto:contact AT sonarsource DOT com
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.plugins.scm.cvs;

import org.netbeans.lib.cvsclient.CVSRoot;
import org.netbeans.lib.cvsclient.Client;
import org.netbeans.lib.cvsclient.admin.StandardAdminHandler;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.commandLine.CommandFactory;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.Connection;
import org.netbeans.lib.cvsclient.connection.ConnectionFactory;
import org.netbeans.lib.cvsclient.connection.PServerConnection;
import org.netbeans.lib.cvsclient.connection.StandardScrambler;
import org.netbeans.lib.cvsclient.event.CVSListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.BatchComponent;
import org.sonar.api.batch.InstantiationStrategy;

import javax.annotation.CheckForNull;

import java.io.File;
import java.io.IOException;

/**
 * Highly inspired from Maven SCM CVS provider
 */
@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
public class CvsCommandExecutor implements BatchComponent {

  private static final Logger LOG = LoggerFactory.getLogger(CvsCommandExecutor.class);

  /**
   * The connection to the server
   */
  private Connection connection;

  /**
   * The client that manages interactions with the server
   */
  private Client client;

  private final CvsConfiguration config;

  public CvsCommandExecutor(CvsConfiguration config) {
    this.config = config;
  }

  /**
   * Process the CVS command passed in args[] array with all necessary
   * options. The only difference from main() method is, that this method
   * does not exit the JVM and provides command output.
   *
   * @param args The command with options
   * @throws AuthenticationException 
   * @throws CommandException 
   */
  public boolean processCommand(String command, GlobalOptions globalOptions, String[] args, File workingDir, CVSListener listener)
    throws AuthenticationException, CommandException {

    // if we don't have a CVS root by now, the user has messed up
    if (globalOptions.getCVSRoot() == null) {
      throw new IllegalStateException("No CVS root is set. Please set " + CvsConfiguration.CVS_ROOT_PROP_KEY + ".");
    }

    final String cvsRoot = globalOptions.getCVSRoot();
    CVSRoot root = parseCvsRoot(cvsRoot);

    org.netbeans.lib.cvsclient.command.Command c = CommandFactory.getDefault().createCommand(command, args, 0, globalOptions, workingDir.getAbsolutePath());

    String username = getUsername(root);
    String password = getPassword(cvsRoot, root);
    try {
      connect(workingDir, root, username, password);
      client.getEventManager().addCVSListener(listener);
      LOG.debug("Executing CVS command: " + c.getCVSCommand());
      return client.executeCommand(c, globalOptions);
    } finally {
      disconnect();
    }
  }

  /**
   * Password will be scrambled for pserver method
   */
  @CheckForNull
  private String getPassword(final String cvsRoot, CVSRoot root) {
    String password;
    if (config.password() != null) {
      password = config.password();
    } else {
      password = root.getPassword();
    }

    if (CVSRoot.METHOD_PSERVER.equals(root.getMethod())) {
      if (password != null) {
        return StandardScrambler.getInstance().scramble(password);
      } else {
        // an empty password
        return StandardScrambler.getInstance().scramble("");
      }
    } else {
      return password;
    }
  }

  @CheckForNull
  private String getUsername(CVSRoot root) {
    if (config.username() != null) {
      return config.username();
    }
    return root.getUserName();
  }

  /**
   * parse the CVS root into its constituent parts
   */
  private CVSRoot parseCvsRoot(final String cvsRoot) {
    CVSRoot root;
    try {
      root = CVSRoot.parse(cvsRoot);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Incorrect format for CVSRoot: " + cvsRoot + "\nThe correct format is: "
        + "[:method:][[user][:password]@][hostname:[port]]/path/to/repository"
        + "\nwhere \"method\" is pserver.", e);
    }
    return root;
  }

  /**
   * Creates the connection and the client and connects.
   */
  private void connect(File baseDir, CVSRoot root, String username, String password) throws AuthenticationException, CommandAbortedException {
    if (CVSRoot.METHOD_EXT.equals(root.getMethod())) {
      connection = new SshConnection(root.getHostName(), root.getPort(), username, password, config.passphrase(), root.getRepository());
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

  public void disconnect() {
    if (connection != null && connection.isOpen()) {
      try {
        connection.close();
      } catch (IOException e) {
        throw new IllegalStateException("Unable to disconnect", e);
      }
    }
  }

}
