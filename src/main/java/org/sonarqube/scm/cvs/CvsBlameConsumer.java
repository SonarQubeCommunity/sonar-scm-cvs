/*
 * SonarQube :: Plugins :: SCM :: CVS
 * Copyright (C) 2014-2018 SonarSource SA
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

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import org.netbeans.lib.cvsclient.event.CVSAdapter;
import org.netbeans.lib.cvsclient.event.MessageEvent;
import org.sonar.api.batch.scm.BlameLine;

public class CvsBlameConsumer extends CVSAdapter {

  private static final String CVS_TIMESTAMP_PATTERN = "dd-MMM-yy";

  /* 1.1 (tor 24-Mar-03): */
  private static final Pattern LINE_PATTERN = Pattern.compile("(.*)\\((.*)\\s+(.*)\\)");

  private final StringBuffer taggedLine = new StringBuffer();
  private List<BlameLine> lines = new ArrayList<>();

  private DateFormat format;
  private String filename;

  private StringBuilder stdout = new StringBuilder();
  private StringBuilder stderr = new StringBuilder();

  public CvsBlameConsumer(String filename) {
    this.filename = filename;
    this.format = new SimpleDateFormat(CVS_TIMESTAMP_PATTERN, Locale.US);
  }

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

    if (e.isTagged()) {
      String message = MessageEvent.parseTaggedMessage(taggedLine, e.getMessage());
      if (message != null) {
        consume(e.isError(), message);
      }
    } else {
      consume(e.isError(), line);
    }
  }

  private void consume(boolean isError, String message) {
    if (isError) {
      stderr.append(message).append("\n");
    } else {
      stdout.append(message).append("\n");
      consumeLine(message);
    }
  }

  private void consumeLine(@Nullable String line) {
    if (line != null && line.indexOf(':') > 0) {
      String annotation = line.substring(0, line.indexOf(':'));
      Matcher matcher = LINE_PATTERN.matcher(annotation);
      if (matcher.matches()) {
        String revision = matcher.group(1).trim();
        String author = matcher.group(2).trim();
        String dateTimeStr = matcher.group(3).trim();

        Date dateTime = parseDate(dateTimeStr);
        lines.add(new BlameLine().date(dateTime).revision(revision).author(author));
      }
    }
  }

  public List<BlameLine> getLines() {
    return lines;
  }

  /**
   * Converts the date timestamp from the output into a date object.
   *
   * @return A date representing the timestamp of the log entry.
   */
  protected Date parseDate(String date) {
    try {
      return format.parse(date);
    } catch (ParseException e) {
      throw new IllegalStateException("Unable to parse date " + date + " in blame of file " + filename, e);
    }
  }

  public StringBuilder getStdout() {
    return stdout;
  }

  public StringBuilder getStderr() {
    return stderr;
  }
}
