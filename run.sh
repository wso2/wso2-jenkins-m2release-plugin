mvn clean install -Dmaven.test.skip
sudo service jenkins stop
sudo rm -rf /var/lib/jenkins/plugins/m2release*
sudo cp target/m2release.hpi /var/lib/jenkins/plugins/
sudo service jenkins start

