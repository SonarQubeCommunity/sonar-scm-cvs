/*
 * SonarQube :: Plugins :: SCM :: CVS
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

import ch.ethz.ssh2.Connection;
import ch.ethz.ssh2.Session;
import ch.ethz.ssh2.StreamGobbler;
import org.netbeans.lib.cvsclient.CVSRoot;
import org.netbeans.lib.cvsclient.command.CommandAbortedException;
import org.netbeans.lib.cvsclient.connection.AbstractConnection;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.ConnectionModifier;
import org.netbeans.lib.cvsclient.util.LoggedDataInputStream;
import org.netbeans.lib.cvsclient.util.LoggedDataOutputStream;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * @author <a href="mailto:evenisse@apache.org">Emmanuel Venisse</a>
 * Provides support for the :ext: connection method.
 */
public class ExtConnection extends AbstractConnection {
  private String host;

  private int port;

  private String userName;

  private String password;

  private Connection connection;

  private Session session;

  private BufferedReader stderrReader;

  public ExtConnection(CVSRoot cvsRoot) {
    this(cvsRoot.getHostName(), cvsRoot.getPort(), cvsRoot.getUserName(), cvsRoot.getPassword(), cvsRoot.getRepository());
  }

  public ExtConnection(String host, int port, String username, String password, String repository) {
    this.userName = username;
    if (this.userName == null) {
      this.userName = System.getProperty("user.name");
    }
    this.password = password;
    this.host = host;
    setRepository(repository);
    this.port = port;
    if (this.port == 0) {
      this.port = 22;
    }
  }

  public void open() throws AuthenticationException, CommandAbortedException {
    connection = new Connection(host, port);

    /*
     * TODO: add proxy support
     * ProxyData proxy = new HTTPProxyData( proxyHost, proxyPort, proxyUserName, proxyPassword );
     * 
     * connection.setProxyData( proxy );
     */

    try {
      // TODO: connection timeout?
      connection.connect();
    } catch (IOException e) {
      String message = "Cannot connect. Reason: " + e.getMessage();
      throw new AuthenticationException(message, e, message);
    }

    File privateKey = getPrivateKey();

    try {
      boolean authenticated;
      if (privateKey != null && privateKey.exists()) {
        authenticated = connection.authenticateWithPublicKey(userName, privateKey, getPassphrase());
      } else {
        authenticated = connection.authenticateWithPassword(userName, password);
      }

      if (!authenticated) {
        String message = "Authentication failed.";
        throw new AuthenticationException(message, message);
      }
    } catch (IOException e) {
      closeConnection();
      String message = "Cannot authenticate. Reason: " + e.getMessage();
      throw new AuthenticationException(message, e, message);
    }

    try {
      session = connection.openSession();
    } catch (IOException e) {
      String message = "Cannot open session. Reason: " + e.getMessage();
      throw new CommandAbortedException(message, message);
    }

    String command = "cvs server";
    try {
      session.execCommand(command);
    } catch (IOException e) {
      String message = "Cannot execute remote command: " + command;
      throw new CommandAbortedException(message, message);
    }

    InputStream stdout = new StreamGobbler(session.getStdout());
    InputStream stderr = new StreamGobbler(session.getStderr());
    stderrReader = new BufferedReader(new InputStreamReader(stderr));
    setInputStream(new LoggedDataInputStream(stdout));
    setOutputStream(new LoggedDataOutputStream(session.getStdin()));
  }

  public void verify() throws AuthenticationException {
    try {
      open();
      verifyProtocol();
      close();
    } catch (Exception e) {
      String message = "Failed to verify the connection: " + e.getMessage();
      throw new AuthenticationException(message, e, message);
    }
  }

  private void closeConnection() {
    try {
      if (stderrReader != null) {
        while (true) {
          String line = stderrReader.readLine();
          if (line == null) {
            break;
          }

          System.err.println(line);
        }
      }
    } catch (IOException e) {
      // nothing to do
    }

    if (session != null) {
      System.out.println("Exit code:" + session.getExitStatus().intValue());
      session.close();
    }

    if (connection != null) {
      connection.close();
    }

    reset();
  }

  private void reset() {
    connection = null;
    session = null;
    stderrReader = null;
    setInputStream(null);
    setOutputStream(null);
  }

  public void close() throws IOException {
    closeConnection();
  }

  public boolean isOpen() {
    return connection != null;
  }

  public int getPort() {
    return port;
  }

  public void modifyInputStream(ConnectionModifier modifier) throws IOException {
    modifier.modifyInputStream(getInputStream());
  }

  public void modifyOutputStream(ConnectionModifier modifier) throws IOException {
    modifier.modifyOutputStream(getOutputStream());
  }

  private File getPrivateKey() {
    // If user don't define a password, he want to use a private key
    File privateKey = null;
    if (password == null) {
      String pk = System.getProperty("maven.scm.cvs.java.ssh.privateKey");
      if (pk != null) {
        privateKey = new File(pk);
      } else {
        privateKey = findPrivateKey();
      }
    }
    return privateKey;
  }

  private String getPassphrase() {
    String passphrase = System.getProperty("maven.scm.cvs.java.ssh.passphrase");

    if (passphrase == null) {
      passphrase = "";
    }

    return passphrase;
  }

  private File findPrivateKey() {
    String privateKeyDirectory = System.getProperty("maven.scm.ssh.privateKeyDirectory");

    if (privateKeyDirectory == null) {
      privateKeyDirectory = System.getProperty("user.home");
    }

    File privateKey = new File(privateKeyDirectory, ".ssh/id_dsa");

    if (!privateKey.exists()) {
      privateKey = new File(privateKeyDirectory, ".ssh/id_rsa");
    }

    return privateKey;
  }
}
