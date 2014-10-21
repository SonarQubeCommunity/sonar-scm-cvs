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

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.netbeans.lib.cvsclient.event.CVSAdapter;
import org.netbeans.lib.cvsclient.event.MessageEvent;

/**
 * A basic implementation of a CVS listener. It merely saves up
 * into StringBuilders the stdout and stderr printstreams.
 *
 * @author <a href="mailto:epugh@upstate.com">Eric Pugh</a>
 *
 */
public class CvsLogListener extends CVSAdapter {
  private final StringBuffer taggedLine = new StringBuffer();

  private StringBuffer stdout = new StringBuffer();

  private StringBuffer stderr = new StringBuffer();

  /**
   * Called when the server wants to send a message to be displayed to the
   * user. The message is only for information purposes and clients can
   * choose to ignore these messages if they wish.
   *
   * {@inheritDoc}
   */
  @Override
  public void messageSent(MessageEvent e) {
    String line = e.getMessage();
    StringBuffer stream = e.isError() ? stderr : stdout;

    if (e.isTagged()) {
      String message = MessageEvent.parseTaggedMessage(taggedLine, e.getMessage());
      if (message != null) {
        // stream.println(message);
        stream.append(message).append("\n");
      }
    } else {
      // stream.println(line);
      stream.append(line).append("\n");
    }
  }

  /**
   * @return Returns the standard output from cvs as a StringBuilder..
   */
  public StringBuffer getStdout() {
    return stdout;
  }

  /**
   * @return Returns the standard error from cvs as a StringBuilder..
   */
  public StringBuffer getStderr() {
    return stderr;
  }
}
