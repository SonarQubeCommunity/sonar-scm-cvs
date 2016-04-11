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

import com.google.common.base.Joiner;
import org.apache.commons.io.FileUtils;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.batch.fs.FileSystem;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.utils.TempFolder;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class CvsBlameCommand extends BlameCommand {

  private static final String ANNOTATE = "annotate";

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
    GlobalOptions globalOptions = buildGlobalOptions(fs.baseDir());

    for (InputFile inputFile : input.filesToBlame()) {

      List<String> args = buildAnnotateArguments(inputFile);

      CvsBlameConsumer consumer = new CvsBlameConsumer(inputFile.relativePath());
      try {
        boolean isSuccess = commandExecutor.processCommand(ANNOTATE, globalOptions, args.toArray(new String[args.size()]), fs.baseDir(), consumer);
        if (!isSuccess) {
          throw new IllegalStateException("The CVS annotate command [" + commandToString(globalOptions, args) + "] failed.\n\nStdout:\n"
            + consumer.getStdout() + "\n\nStderr:\n"
            + consumer.getStderr());
        }
      } catch (CommandException e) {
        throw new IllegalStateException("The CVS annotate command [" + commandToString(globalOptions, args) + "] failed", e.getUnderlyingException());
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
  }

  private String commandToString(GlobalOptions globalOptions, List<String> args) {
    StringBuilder sb = new StringBuilder();
    sb.append("cvs ");
    if (globalOptions.getCVSRoot() != null) {
      sb.append("-d ");
      sb.append(globalOptions.getCVSRoot());
      sb.append(" ");
    }
    sb.append(globalOptions.getCVSCommand().trim());
    sb.append(" ");
    sb.append(ANNOTATE);
    sb.append(" ");
    Joiner.on(' ').appendTo(sb, args);
    return sb.toString();
  }

  /**
   * Obtain the CVS root from the CVS directory
   *
   * @return the CVSRoot string
   */
  private static String getCVSRoot(File baseDir) {
    File rootFile = new File(baseDir, "CVS/Root");
    try {
      return FileUtils.readFileToString(rootFile).trim();
    } catch (IOException e) {
      throw new IllegalStateException("Can't read " + rootFile.getAbsolutePath(), e);
    }
  }

  GlobalOptions buildGlobalOptions(File baseDir) {

    GlobalOptions opts = new GlobalOptions();
    if (!config.compressionDisabled()) {
      opts.setCompressionLevel(config.compressionLevel());
    }
    opts.setIgnoreCvsrc(!config.useCvsrc());

    File tempDir = tempFolder.newDir("cvs");
    opts.setTempDir(tempDir);

    if (config.cvsRoot() != null) {
      opts.setCVSRoot(config.cvsRoot());
    } else {
      opts.setCVSRoot(getCVSRoot(baseDir));
    }
    opts.setTraceExecution(LOG.isDebugEnabled());
    opts.setVeryQuiet(!LOG.isDebugEnabled());

    return opts;
  }

  List<String> buildAnnotateArguments(InputFile inputFile) {

    List<String> args = new ArrayList<String>();

    if (config.revision() != null) {
      args.add("-r");
      args.add(config.revision());
    }
    args.add(inputFile.relativePath());

    return args;
  }

}
