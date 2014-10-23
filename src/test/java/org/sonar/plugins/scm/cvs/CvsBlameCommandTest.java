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

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.netbeans.lib.cvsclient.command.CommandException;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.CVSListener;
import org.netbeans.lib.cvsclient.event.MessageEvent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.scm.BlameCommand.BlameInput;
import org.sonar.api.batch.scm.BlameCommand.BlameOutput;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.TempFolder;
import org.sonar.api.utils.internal.DefaultTempFolder;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CvsBlameCommandTest {

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  private DefaultFileSystem fs;
  private File baseDir;
  private BlameInput input;

  @Before
  public void prepare() throws IOException {
    baseDir = temp.newFolder();
    fs = new DefaultFileSystem();
    fs.setBaseDir(baseDir);
    input = mock(BlameInput.class);
    when(input.fileSystem()).thenReturn(fs);
  }

  @Test
  public void testParsingOfOutput() throws IOException, AuthenticationException, CommandException {
    File source = new File(baseDir, "src/foo.xoo");
    FileUtils.write(source, "sample content");
    DefaultInputFile inputFile1 = new DefaultInputFile("foo", "src/foo.xoo")
      .setAbsolutePath(new File(baseDir, "src/foo.xoo").getAbsolutePath())
      .setLines(7);
    fs.add(inputFile1);
    DefaultInputFile inputFile2 = new DefaultInputFile("foo", "src/foo.xoo")
      .setAbsolutePath(new File(baseDir, "src/foo.xoo").getAbsolutePath())
      .setLines(8);
    fs.add(inputFile2);

    BlameOutput result = mock(BlameOutput.class);
    CvsCommandExecutor commandExecutor = mock(CvsCommandExecutor.class);

    when(commandExecutor.processCommand(any(String[].class), any(File.class), any(File.class), any(CVSListener.class))).thenAnswer(new Answer<Boolean>() {

      @Override
      public Boolean answer(InvocationOnMock invocation) throws Throwable {
        CVSListener listener = (CVSListener) invocation.getArguments()[3];
        List<String> lines = IOUtils.readLines(getClass().getResourceAsStream("/annotate.xml"), "UTF-8");
        for (String line : lines) {
          listener.messageSent(new MessageEvent("", line, false));
        }
        return true;
      }
    });

    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile1, inputFile2));

    new CvsBlameCommand(mock(CvsConfiguration.class), new DefaultTempFolder(temp.newFolder()), commandExecutor).blame(input, result);
    verify(result).blameResult(inputFile1,
      Arrays.asList(
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.2").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.1").author("julien")));

    verify(result).blameResult(inputFile2,
      Arrays.asList(
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.2").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0200")).revision("1.1").author("julien")));
  }

  @Test
  public void testAllParams() throws IOException {
    DefaultInputFile inputFile = new DefaultInputFile("foo", "src/foo.xoo")
      .setAbsolutePath(new File(baseDir, "src/foo.xoo").getAbsolutePath())
      .setLines(7);

    CvsCommandExecutor commandExecutor = mock(CvsCommandExecutor.class);
    Settings settings = new Settings(new PropertyDefinitions(CvsConfiguration.getProperties()));
    TempFolder tempFolder = mock(TempFolder.class);
    File tmp = new File("tmpcvs");
    when(tempFolder.newDir("cvs")).thenReturn(tmp);
    CvsBlameCommand cvsBlameCommand = new CvsBlameCommand(new CvsConfiguration(settings), tempFolder, commandExecutor);

    assertThat(cvsBlameCommand.buildAnnotateArguments(inputFile)).containsExactly("-z3", "-f", "-T", tmp.getAbsolutePath(), "-q", "annotate",
      "src/foo.xoo");

    settings.setProperty(CvsConfiguration.TRACE_PROP_KEY, "true");
    settings.setProperty(CvsConfiguration.USE_CVSRC_PROP_KEY, "true");
    settings.setProperty(CvsConfiguration.CVS_ROOT_PROP_KEY, ":pserver:foo");
    settings.setProperty(CvsConfiguration.REV_PROP_KEY, "my-branch");
    settings.setProperty(CvsConfiguration.DISABLE_COMPRESSION_PROP_KEY, "true");
    assertThat(cvsBlameCommand.buildAnnotateArguments(inputFile)).containsExactly("-t", "-T", tmp.getAbsolutePath(), "-d", ":pserver:foo", "-r", "my-branch", "-q",
      "annotate",
      "src/foo.xoo");
    settings.setProperty(CvsConfiguration.DISABLE_COMPRESSION_PROP_KEY, "false");
    settings.setProperty(CvsConfiguration.COMPRESSION_LEVEL_PROP_KEY, "4");
    assertThat(cvsBlameCommand.buildAnnotateArguments(inputFile)).containsExactly("-z4", "-t", "-T", tmp.getAbsolutePath(), "-d", ":pserver:foo", "-r", "my-branch", "-q",
      "annotate",
      "src/foo.xoo");
  }
}
