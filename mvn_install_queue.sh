JAR=/media/ahbbollen/DATADRIVE1/Queue.jar
ID=gatk-queue-package-distribution
VERSION=3.2
GROUP=org.broadinstitute.gatk

mvn install:install-file -Dfile=$JAR -DgroupId=$GROUP -DartifactId=$ID -Dversion=$VERSION -Dpackaging=jar

