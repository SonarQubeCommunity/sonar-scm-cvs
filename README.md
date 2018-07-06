# CVS Plugin

[![Build Status](https://travis-ci.org/SonarQubeCommunity/sonar-scm-cvs.svg?branch=master)](https://travis-ci.org/SonarQubeCommunity/sonar-scm-cvs)

## Description / Features
Implements SCM dependent features of SonarQube for CVS projects. Pure Java implementation so no need to have CVS command line tool installed on the computer doing the SQ analysis.

## Usage
Install the plugin in SonarQube. Auto-detection will work if there is a CVS folder in the project root directory. Otherwise you can force the provider using -Dsonar.scm.provider=cvs.
You can also configure some optional properties:

<table>
<tr><th>Key</th><th>Description</th><th>Default value</th></tr>
<tr><td>sonar.cvs.username</td>
	<td>Username to be used for CVS authentication. Optional if already present in CVS/Root.</td></tr>
<tr><td>sonar.cvs.password.secured</td>
	<td>Password to be used for CVS authentication.</td></tr>
<tr><td>sonar.cvs.passphrase.secured</td>
	<td>Passphrase to be used for SSH authentication using public key.</td></tr>
<tr><td>sonar.cvs.compression.disabled</td>
	<td>Disable compression.</td>
	<td>false</td></tr>
<tr><td>sonar.cvs.compressionLevel</td>
	<td>Compression level.</td>
	<td>3</td></tr>
<tr><td>sonar.cvs.useCvsrc</td>
	<td>Consider content of .cvsrc file.</td>
	<td>false</td></tr>
<tr><td>sonar.cvs.cvsRoot</td>
	<td>CVSRoot string. For example :pserver:host:/folder. Will be automatically detected by default (reading CVS/Root).</td></tr>
<tr><td>sonar.cvs.revision</td>
	<td>Revision/tag used to execute annotate (equivalent to -r command line option). Required if you are working on a branch since CVS returns annotations from HEAD by default.</td></tr>
</table>

## Known Limitations
* Blame is not executed in parallel since we are not confident in the thread safety of cvsclient library.
* cvs annotate <afile> returns information from server for the given file in HEAD revision. If you are working on a branch you have to manually pass the branch using sonar.cvs.revision property.
* consequence of previous point is that we are not able to properly detect that there are local uncommited changes. So annotate result can be inconsistent with source code analyzed by SonarQube.
* cvs annotate only returns date of the change (and not datetime like most other providers). This lack of precision can be an issue to distinguish changes commited in the same day.

## Developer informations
The plugin use the Netbeans pure Java implementation of CVS client: https://versioncontrol.netbeans.org/javacvs/library/

### License

Copyright 2014-2018 SonarSource.

Licensed under the [GNU Lesser General Public License, Version 3.0](http://www.gnu.org/licenses/lgpl.txt)
