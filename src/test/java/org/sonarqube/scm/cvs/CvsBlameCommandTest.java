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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
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
import org.netbeans.lib.cvsclient.command.GlobalOptions;
import org.netbeans.lib.cvsclient.connection.AuthenticationException;
import org.netbeans.lib.cvsclient.event.CVSListener;
import org.netbeans.lib.cvsclient.event.MessageEvent;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.TestInputFileBuilder;
import org.sonar.api.batch.scm.BlameCommand.BlameInput;
import org.sonar.api.batch.scm.BlameCommand.BlameOutput;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.internal.MapSettings;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.TempFolder;
import org.sonar.api.utils.internal.DefaultTempFolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CvsBlameCommandTest {

  @Rule
  public UTCRule utcRule = new UTCRule();

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
    fs = new DefaultFileSystem(baseDir);
    input = mock(BlameInput.class);
    when(input.fileSystem()).thenReturn(fs);

    File cvsRoot = new File(baseDir, "CVS/Root");
    FileUtils.write(cvsRoot, ":pserver:bar");
  }

  @Test
  public void testParsingOfOutput() throws IOException, AuthenticationException, CommandException {
    File source = new File(baseDir, "src/foo.xoo");
    FileUtils.write(source, "sample content");
    InputFile inputFile1 = new TestInputFileBuilder("foo", "src/foo.xoo")
      .setModuleBaseDir(baseDir.toPath())
      .setLines(7)
      .build();
    fs.add(inputFile1);
    InputFile inputFile2 = new TestInputFileBuilder("foo", "src/foo.xoo")
      .setModuleBaseDir(baseDir.toPath())
      .setLines(8)
      .build();
    fs.add(inputFile2);

    BlameOutput result = mock(BlameOutput.class);
    CvsCommandExecutor commandExecutor = mock(CvsCommandExecutor.class);

    when(commandExecutor.processCommand(eq("annotate"), any(), any(), any(), any())).thenAnswer(new Answer<Boolean>() {

      @Override
      public Boolean answer(InvocationOnMock invocation) throws Throwable {
        CVSListener listener = (CVSListener) invocation.getArguments()[4];
        List<String> lines = IOUtils.readLines(getClass().getResourceAsStream("/annotate.xml"), "UTF-8");
        for (String line : lines) {
          listener.messageSent(new MessageEvent("", line, false));
        }
        return true;
      }
    });

    when(input.filesToBlame()).thenReturn(Arrays.asList(inputFile1, inputFile2));

    new CvsBlameCommand(mock(CvsConfiguration.class), new DefaultTempFolder(temp.newFolder()), commandExecutor).blame(input, result);
    verify(result).blameResult(inputFile1,
      Arrays.asList(
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.2").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.1").author("julien")));

    verify(result).blameResult(inputFile2,
      Arrays.asList(
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.2").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.1").author("julien"),
        new BlameLine().date(DateUtils.parseDateTime("2014-10-21T00:00:00+0000")).revision("1.1").author("julien")));
  }

  @Test
  public void testUnknowError() throws IOException, AuthenticationException, CommandException {
    File source = new File(baseDir, "src/foo.xoo");
    FileUtils.write(source, "sample content");
    InputFile inputFile = new TestInputFileBuilder("foo", "src/foo.xoo")
      .setModuleBaseDir(baseDir.toPath())
      .setLines(7)
      .build();
    fs.add(inputFile);

    BlameOutput result = mock(BlameOutput.class);
    CvsCommandExecutor commandExecutor = mock(CvsCommandExecutor.class);

    when(commandExecutor.processCommand(eq("annotate"), any(GlobalOptions.class), any(String[].class), any(File.class), any(CVSListener.class))).thenAnswer(new Answer<Boolean>() {

      @Override
      public Boolean answer(InvocationOnMock invocation) {
        CVSListener listener = (CVSListener) invocation.getArguments()[4];
        listener.messageSent(new MessageEvent("", "Unknow error", true));
        return false;
      }
    });

    when(input.filesToBlame()).thenReturn(Arrays.asList(inputFile));
    File tempFile = temp.newFile("cvs");
    TempFolder tempFolder = mock(TempFolder.class);
    when(tempFolder.newDir("cvs")).thenReturn(tempFile);

    thrown.expect(IllegalStateException.class);
    thrown
      .expectMessage("The CVS annotate command [cvs -d :pserver:bar -t -f -T " + tempFile.getAbsolutePath()
        + " annotate src/foo.xoo] failed.");
    thrown.expectMessage("Unknow error");

    new CvsBlameCommand(mock(CvsConfiguration.class), tempFolder, commandExecutor).blame(input, result);
  }

  @Test
  public void testAnnotateParams() {
    InputFile inputFile = new TestInputFileBuilder("foo", "src/foo.xoo")
      .setModuleBaseDir(baseDir.toPath())
      .setLines(7)
      .build();

    CvsCommandExecutor commandExecutor = mock(CvsCommandExecutor.class);
    MapSettings settings = new MapSettings(new PropertyDefinitions(CvsConfiguration.getProperties()));
    TempFolder tempFolder = mock(TempFolder.class);
    File tmp = new File("tmpcvs");
    when(tempFolder.newDir("cvs")).thenReturn(tmp);
    CvsBlameCommand cvsBlameCommand = new CvsBlameCommand(new CvsConfiguration(settings.asConfig()), tempFolder, commandExecutor);

    assertThat(cvsBlameCommand.buildAnnotateArguments(inputFile)).containsExactly("src/foo.xoo");

    settings.setProperty(CvsConfiguration.REV_PROP_KEY, "my-branch");
    assertThat(cvsBlameCommand.buildAnnotateArguments(inputFile)).containsExactly("-r", "my-branch", "src/foo.xoo");
  }

  @Test
  public void testGlobalOptions() {
    CvsCommandExecutor commandExecutor = mock(CvsCommandExecutor.class);
    MapSettings settings = new MapSettings(new PropertyDefinitions(CvsConfiguration.getProperties()));
    TempFolder tempFolder = mock(TempFolder.class);
    File tmp = new File("tmpcvs");
    when(tempFolder.newDir("cvs")).thenReturn(tmp);
    CvsConfiguration config = new CvsConfiguration(settings.asConfig());
    CvsBlameCommand cvsBlameCommand = new CvsBlameCommand(config, tempFolder, commandExecutor);

    GlobalOptions globalOptions = cvsBlameCommand.buildGlobalOptions(baseDir);
    assertThat(globalOptions.getCompressionLevel()).isEqualTo(3);
    assertThat(globalOptions.isIgnoreCvsrc()).isTrue();
    assertThat(globalOptions.getTempDir()).isEqualTo(tmp);
    assertThat(globalOptions.getCVSRoot()).isEqualTo(":pserver:bar");

    settings.setProperty(CvsConfiguration.USE_CVSRC_PROP_KEY, "true");
    settings.setProperty(CvsConfiguration.CVS_ROOT_PROP_KEY, ":pserver:foo");
    settings.setProperty(CvsConfiguration.DISABLE_COMPRESSION_PROP_KEY, "true");
    globalOptions = cvsBlameCommand.buildGlobalOptions(baseDir);
    assertThat(globalOptions.getCompressionLevel()).isEqualTo(0);
    assertThat(globalOptions.getCVSRoot()).isEqualTo(":pserver:foo");
    assertThat(globalOptions.isIgnoreCvsrc()).isFalse();
    settings.setProperty(CvsConfiguration.DISABLE_COMPRESSION_PROP_KEY, "false");
    settings.setProperty(CvsConfiguration.COMPRESSION_LEVEL_PROP_KEY, "4");
    globalOptions = cvsBlameCommand.buildGlobalOptions(baseDir);
    assertThat(globalOptions.getCompressionLevel()).isEqualTo(4);
  }
}
