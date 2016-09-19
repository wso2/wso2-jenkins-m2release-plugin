WSO2 Jenkins M2 Release Plugin
=================
Allows you to perfrom maven release builds from within Jenkins.

This document explain the effort went into automatically doing a release after each push by incrementing the micro part of the version number. This is one more step towards CI/CD. So what needs to happen is, Jenkins should automatically take over and do the release & push to WSO2 Nexus.

The rationale behind this is, developers feel guilty to increment version numbers and this leads to a long release train being created when a product has to be released. So we will follow the feature branch approach and when the feature in completed, we will merge to the master, and the release will happen. It could be even bug fixes which are pushed to GitHub which will trigger the automatic release.

The next step would be if some product or another component is using a WSO2 dependency which is old, a notification will be sent to the repo owners saying that newer versions are available.

Following is the sequence diagram of the process for this flow. It shows the interactions of Git users, Git repos, Jenkins and Nexus. The process was implemented via a Jenkins plugin.

![alt text][design]

Refer the [design and configuration guide] for more info. 

This plugin was written extending the [m2release-plugin].


Wiki and Info
-------------
User documentation of the upstream plugin can be found on the [wiki]

[wiki]: http://wiki.jenkins-ci.org/display/JENKINS/M2+Release+Plugin
[MIT Licence]: https://github.com/jenkinsci/m2release-plugin/raw/master/LICENCE.txt
[design]: continous-delivery-release-sequence-1.1.png
[m2release-plugin]: https://github.com/jenkinsci/m2release-plugin
[design and configuration guide]: https://docs.google.com/document/d/1BGG7ewYH6_qFaxVroe_-0p4daqiSWN6S2ooLxKpCh8I/edit#heading=h.sjlr0jbn1vbf
