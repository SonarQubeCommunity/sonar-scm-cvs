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

import java.util.Arrays;
import java.util.List;
import javax.annotation.CheckForNull;
import org.sonar.api.CoreProperties;
import org.sonar.api.PropertyType;
import org.sonar.api.batch.InstantiationStrategy;
import org.sonar.api.batch.ScannerSide;
import org.sonar.api.config.Configuration;
import org.sonar.api.config.PropertyDefinition;
import org.sonar.api.resources.Qualifiers;

@InstantiationStrategy(InstantiationStrategy.PER_BATCH)
@ScannerSide
public class CvsConfiguration {

  private static final String FALSE = "false";
  private static final String CATEGORY_CVS = "CVS";
  public static final String USER_PROP_KEY = "sonar.cvs.username";
  public static final String PASSWORD_PROP_KEY = "sonar.cvs.password.secured";
  public static final String PASSPHRASE_PROP_KEY = "sonar.cvs.passphrase.secured";
  public static final String DISABLE_COMPRESSION_PROP_KEY = "sonar.cvs.compression.disabled";
  public static final String COMPRESSION_LEVEL_PROP_KEY = "sonar.cvs.compressionLevel";
  public static final String USE_CVSRC_PROP_KEY = "sonar.cvs.useCvsrc";
  public static final String REV_PROP_KEY = "sonar.cvs.revision";

  public static final String CVS_ROOT_PROP_KEY = "sonar.cvs.cvsRoot";

  private final Configuration settings;

  public CvsConfiguration(Configuration settings) {
    this.settings = settings;
  }

  public static List<PropertyDefinition> getProperties() {
    return Arrays.asList(
      PropertyDefinition.builder(USER_PROP_KEY)
        .name("Username")
        .description("Username to be used for CVS authentication")
        .type(PropertyType.STRING)
        .onQualifiers(Qualifiers.PROJECT)
        .category(CoreProperties.CATEGORY_SCM)
        .subCategory(CATEGORY_CVS)
        .index(0)
        .build(),
      PropertyDefinition.builder(PASSWORD_PROP_KEY)
        .name("Password")
        .description("Password to be used for CVS authentication")
        .type(PropertyType.PASSWORD)
        .onQualifiers(Qualifiers.PROJECT)
        .category(CoreProperties.CATEGORY_SCM)
        .subCategory(CATEGORY_CVS)
        .index(1)
        .build(),
      PropertyDefinition.builder(PASSPHRASE_PROP_KEY)
        .name("Passphrase")
        .description("Passphrase to be used for SSH authentication using public key")
        .type(PropertyType.PASSWORD)
        .onQualifiers(Qualifiers.PROJECT)
        .category(CoreProperties.CATEGORY_SCM)
        .subCategory(CATEGORY_CVS)
        .index(2)
        .build(),
      PropertyDefinition.builder(DISABLE_COMPRESSION_PROP_KEY)
        .name("Disable compression")
        .description("Disable compression")
        .type(PropertyType.BOOLEAN)
        .defaultValue(FALSE)
        .category(CoreProperties.CATEGORY_SCM)
        .subCategory(CATEGORY_CVS)
        .index(3)
        .build(),
      PropertyDefinition.builder(COMPRESSION_LEVEL_PROP_KEY)
        .name("Compression level")
        .description("Compression level")
        .type(PropertyType.INTEGER)
        .defaultValue("3")
        .category(CoreProperties.CATEGORY_SCM)
        .subCategory(CATEGORY_CVS)
        .index(4)
        .build(),
      PropertyDefinition.builder(USE_CVSRC_PROP_KEY)
        .name("Use .cvsrc file")
        .description("Consider content of .cvsrc file")
        .type(PropertyType.BOOLEAN)
        .defaultValue(FALSE)
        .category(CoreProperties.CATEGORY_SCM)
        .subCategory(CATEGORY_CVS)
        .index(5)
        .build(),
      PropertyDefinition.builder(CVS_ROOT_PROP_KEY)
        .name("CVSRoot")
        .description("CVSRoot string. For example :pserver:host:/folder. Will be automatically detected by default")
        .type(PropertyType.STRING)
        .onlyOnQualifiers(Qualifiers.PROJECT)
        .category(CoreProperties.CATEGORY_SCM)
        .subCategory(CATEGORY_CVS)
        .index(6)
        .build(),
      PropertyDefinition.builder(REV_PROP_KEY)
        .name("Revision/tag")
        .description("Revision/tag used to execute annotate (-r)")
        .type(PropertyType.STRING)
        .onlyOnQualifiers(Qualifiers.PROJECT)
        .category(CoreProperties.CATEGORY_SCM)
        .subCategory(CATEGORY_CVS)
        .index(7)
        .build());
  }

  @CheckForNull
  public String username() {
    return settings.get(USER_PROP_KEY).orElse(null);
  }

  @CheckForNull
  public String password() {
    return settings.get(PASSWORD_PROP_KEY).orElse(null);
  }

  public boolean compressionDisabled() {
    return settings.getBoolean(DISABLE_COMPRESSION_PROP_KEY).orElseThrow(() -> new IllegalStateException("Missing default value of " + DISABLE_COMPRESSION_PROP_KEY));
  }

  public int compressionLevel() {
    return settings.getInt(COMPRESSION_LEVEL_PROP_KEY).orElseThrow(() -> new IllegalStateException("Missing default value of " + COMPRESSION_LEVEL_PROP_KEY));
  }

  public boolean useCvsrc() {
    return settings.getBoolean(USE_CVSRC_PROP_KEY).orElseThrow(() -> new IllegalStateException("Missing default value of " + USE_CVSRC_PROP_KEY));
  }

  @CheckForNull
  public String cvsRoot() {
    return settings.get(CVS_ROOT_PROP_KEY).orElse(null);
  }

  @CheckForNull
  public String revision() {
    return settings.get(REV_PROP_KEY).orElse(null);
  }

  @CheckForNull
  public String passphrase() {
    return settings.get(PASSPHRASE_PROP_KEY).orElse(null);
  }

}
