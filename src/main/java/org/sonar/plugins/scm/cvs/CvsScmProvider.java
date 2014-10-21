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

import org.sonar.api.batch.scm.BlameCommand;
import org.sonar.api.batch.scm.ScmProvider;

import java.io.File;

public class CvsScmProvider extends ScmProvider {

  private final CvsBlameCommand blameCommand;
  private final CvsConfiguration config;

  public CvsScmProvider(CvsBlameCommand blameCommand, CvsConfiguration config) {
    this.blameCommand = blameCommand;
    this.config = config;
  }

  @Override
  public String key() {
    return "cvs";
  }

  @Override
  public boolean supports(File baseDir) {
    File cvsDir = new File(baseDir, "CVS");
    if (cvsDir.exists() && cvsDir.isDirectory()) {
      config.setBaseDir(baseDir);
      return true;
    }
    if (baseDir.getParentFile() != null) {
      return supports(baseDir.getParentFile());
    } else {
      return false;
    }
  }

  @Override
  public BlameCommand blameCommand() {
    return this.blameCommand;
  }
}
