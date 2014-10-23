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

import com.google.common.base.Joiner;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.utils.TempFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class CvsBlameCommand extends BlameCommand {

  private static final Logger LOG = LoggerFactory.getLogger(CvsBlameCommand.class);

  private final CvsConfiguration config;
  private final TempFolder tempFolder;
  private final CvsCommandExecutor commandExecutor;

  public CvsBlameCommand(CvsConfiguration config, TempFolder tempFolder, CvsCommandExecutor commandExecutor) {
    this.config = config;
    this.tempFolder = tempFolder;
    this.commandExecutor = commandExecutor;
  }

  @Override
  public void blame(BlameInput input, BlameOutput output) {
    FileSystem fs = input.fileSystem();
    LOG.debug("Working directory: " + fs.baseDir().getAbsolutePath());
    for (InputFile inputFile : input.filesToBlame()) {
      List<String> args = buildAnnotateArguments(inputFile);

      CvsBlameConsumer consumer = new CvsBlameConsumer(inputFile.relativePath());
      try {
        boolean isSuccess = commandExecutor.processCommand(args.toArray(new String[args.size()]), config.baseDir(), fs.baseDir(), consumer);
        if (!isSuccess) {
          throw new IllegalStateException("The CVS annotate command [cvs " + Joiner.on(' ').join(args) + "] failed.\n\nStdout:\n" + consumer.getStdout() + "\n\nStderr:\n"
            + consumer.getStderr());
        }
      } catch (CommandException e) {
        throw new IllegalStateException("The CVS annotate command [cvs " + Joiner.on(' ').join(args) + "] failed", e.getUnderlyingException());
      } catch (AuthenticationException e) {
        throw new IllegalStateException("Unable to connect", e);
      }
      List<BlameLine> lines = consumer.getLines();
      if (lines.size() == inputFile.lines() - 1) {
        // SONARPLUGINS-3097 CVS do not report blame on last empty line
        lines.add(lines.get(lines.size() - 1));
      }
      output.blameResult(inputFile, lines);
    }
    commandExecutor.disconnect();
  }

  List<String> buildAnnotateArguments(InputFile inputFile) {

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

    if (config.revision() != null) {
      args.add("-r");
      args.add(config.revision());
    }

    args.add("-q");

    args.add("annotate");

    args.add(inputFile.relativePath());

    return args;
  }

}
