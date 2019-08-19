/*
 * SonarQube :: Plugins :: SCM :: CVS
 * Copyright (C) 2014-2019 SonarSource SA
 * mailto:info AT sonarsource DOT com
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
package org.sonarqube.scm.cvs;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import java.io.File;
import java.io.IOException;
import javax.annotation.Nullable;
import org.netbeans.lib.cvsclient.connection.AbstractConnection;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.connection.ConnectionModifier;
import org.netbeans.lib.cvsclient.util.LoggedDataInputStream;
import org.netbeans.lib.cvsclient.util.LoggedDataOutputStream;

/**
 * Provides support for the :ext: connection method.
 */
public class SshConnection extends AbstractConnection {

  private String host;

  private int port;

  private String username;

  private String password;

  private Session sesConnection;

  private ChannelExec channel;

  private String passphrase;

  public SshConnection(String host, int port, @Nullable String username, @Nullable String password, @Nullable String passphrase, String repository) {
    this.username = username;
    this.password = password;
    this.host = host;
    this.passphrase = passphrase;
    setRepository(repository);
    this.port = port;
    if (this.port == 0) {
      this.port = 22;
    }
  }

  @Override
  public void open() throws AuthenticationException {
    JSch jschSSHChannel = new JSch();
    try {
      if (password == null) {
        // If user don't define a password, he wants to use a private key
        File privateKey = findPrivateKey();
        if (privateKey.exists()) {
          jschSSHChannel.addIdentity(privateKey.getAbsolutePath(), trimToEmpty(passphrase));
        }
      }
      sesConnection = jschSSHChannel.getSession(username, host, port);
      sesConnection.setPassword(password);

      // TODO to be perfectly secured we should allow users to use jschSSHChannel.setKnownHosts(knownHostsFileName);
      // but since we are only using SSH to do blame I assume it's safe to do:
      sesConnection.setConfig("StrictHostKeyChecking", "no");

      sesConnection.connect(60 * 1000);
    } catch (JSchException e) {
      throw new AuthenticationException(e, e.getMessage());
    }

    try {
      channel = (ChannelExec) sesConnection.openChannel("exec");
      channel.setCommand("cvs server");
      channel.connect(60 * 1000);

      setInputStream(new LoggedDataInputStream(channel.getInputStream()));
      setOutputStream(new LoggedDataOutputStream(channel.getOutputStream()));
    } catch (JSchException | IOException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  @Override
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
    if (channel != null) {
      channel.disconnect();
    }

    if (sesConnection != null) {
      sesConnection.disconnect();
    }

    reset();
  }

  private void reset() {
    sesConnection = null;
    channel = null;
    setInputStream(null);
    setOutputStream(null);
  }

  @Override
  public void close() {
    closeConnection();
  }

  @Override
  public boolean isOpen() {
    return sesConnection != null;
  }

  @Override
  public int getPort() {
    return port;
  }

  @Override
  public void modifyInputStream(ConnectionModifier modifier) throws IOException {
    modifier.modifyInputStream(getInputStream());
  }

  @Override
  public void modifyOutputStream(ConnectionModifier modifier) throws IOException {
    modifier.modifyOutputStream(getOutputStream());
  }

  private static File findPrivateKey() {
    File privateKey = new File(System.getProperty("user.home"), ".ssh/id_dsa");
    if (!privateKey.exists()) {
      privateKey = new File(System.getProperty("user.home"), ".ssh/id_rsa");
    }
    return privateKey;
  }

  private static String trimToEmpty(@Nullable String str) {
    return str == null ? "" : str.trim();
  }
}
