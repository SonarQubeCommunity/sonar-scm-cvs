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

import com.google.common.io.Closeables;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.rules.TemporaryFolder;
import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.fs.internal.DefaultFileSystem;
import org.sonar.api.batch.fs.internal.DefaultInputFile;
import org.sonar.api.batch.scm.BlameCommand.BlameInput;
import org.sonar.api.batch.scm.BlameCommand.BlameOutput;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.config.PropertyDefinitions;
import org.sonar.api.config.Settings;
import org.sonar.api.utils.DateUtils;
import org.sonar.api.utils.System2;
import org.sonar.api.utils.internal.DefaultTempFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Date;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CvsBlameCommandTest {

  private static final String DUMMY_JAVA = "src/main/java/org/dummy/Dummy.java";
  private static final String DUMMY_JAVA_NESTED = "java/org/dummy/Dummy.java";

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Rule
  public TemporaryFolder temp = new TemporaryFolder();

  private DefaultFileSystem fs;
  private BlameInput input;

  @Before
  public void prepare() throws IOException {
    fs = new DefaultFileSystem();
    input = mock(BlameInput.class);
    when(input.fileSystem()).thenReturn(fs);
  }

  @Test
  public void testBlame() throws IOException {
    File projectDir = temp.newFolder();
    File repoDir = temp.newFolder();
    javaUnzip(new File("test-repos/cvsroot.zip"), repoDir);
    javaUnzip(new File("test-repos/module1.zip"), projectDir);

    Settings settings = new Settings(new PropertyDefinitions(CvsConfiguration.getProperties()));
    settings.setProperty(CvsConfiguration.CVS_ROOT_PROP_KEY, ":local:" + new File(repoDir, "cvsroot").getAbsolutePath());
    CvsConfiguration config = new CvsConfiguration(settings);
    CvsBlameCommand cvsBlameCommand = new CvsBlameCommand(config,
      new DefaultTempFolder(temp.newFolder()), System2.INSTANCE);

    File baseDir = new File(projectDir, "module1");
    config.setBaseDir(baseDir);
    fs.setBaseDir(baseDir);
    DefaultInputFile inputFile = new DefaultInputFile("foo", DUMMY_JAVA)
      .setFile(new File(baseDir, DUMMY_JAVA));
    fs.add(inputFile);

    BlameOutput blameResult = mock(BlameOutput.class);
    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    cvsBlameCommand.blame(input, blameResult);

    Date revisionDate = DateUtils.parseDate("2014-10-21");
    String revision = "1.1";
    String author = "julien";
    verify(blameResult).blameResult(inputFile,
      Arrays.asList(
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author)));
  }

  @Test
  public void testBlameInNestedFolder() throws IOException {
    File projectDir = temp.newFolder();
    File repoDir = temp.newFolder();
    javaUnzip(new File("test-repos/cvsroot.zip"), repoDir);
    javaUnzip(new File("test-repos/module1.zip"), projectDir);

    Settings settings = new Settings(new PropertyDefinitions(CvsConfiguration.getProperties()));
    settings.setProperty(CvsConfiguration.CVS_ROOT_PROP_KEY, ":local:" + new File(repoDir, "cvsroot").getAbsolutePath());
    CvsConfiguration config = new CvsConfiguration(settings);
    CvsBlameCommand cvsBlameCommand = new CvsBlameCommand(config,
      new DefaultTempFolder(temp.newFolder()), System2.INSTANCE);

    File baseDir = new File(projectDir, "module1/src/main");
    config.setBaseDir(baseDir);
    fs.setBaseDir(baseDir);
    DefaultInputFile inputFile = new DefaultInputFile("foo", DUMMY_JAVA_NESTED)
      .setFile(new File(baseDir, DUMMY_JAVA_NESTED));
    fs.add(inputFile);

    BlameOutput blameResult = mock(BlameOutput.class);
    when(input.filesToBlame()).thenReturn(Arrays.<InputFile>asList(inputFile));
    cvsBlameCommand.blame(input, blameResult);

    Date revisionDate = DateUtils.parseDate("2014-10-21");
    String revision = "1.1";
    String author = "julien";
    verify(blameResult).blameResult(inputFile,
      Arrays.asList(
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author),
        new BlameLine().revision(revision).date(revisionDate).author(author)));
  }

  private static void javaUnzip(File zip, File toDir) {
    try {
      ZipFile zipFile = new ZipFile(zip);
      try {
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
          ZipEntry entry = entries.nextElement();
          File to = new File(toDir, entry.getName());
          if (entry.isDirectory()) {
            FileUtils.forceMkdir(to);
          } else {
            File parent = to.getParentFile();
            if (parent != null) {
              FileUtils.forceMkdir(parent);
            }

            OutputStream fos = new FileOutputStream(to);
            try {
              IOUtils.copy(zipFile.getInputStream(entry), fos);
            } finally {
              Closeables.closeQuietly(fos);
            }
          }
        }
      } finally {
        zipFile.close();
      }
    } catch (Exception e) {
      throw new IllegalStateException("Fail to unzip " + zip + " to " + toDir, e);
    }
  }

}
