name := "XmlToRdf"

version := "0.1"

scalaVersion := "2.12.3"

libraryDependencies += "net.sf.saxon" % "Saxon-HE" % "9.8.0-4"

libraryDependencies += "it.unibz.inf.ontop" % "ontop-quest-owlapi" % "1.18.1"

libraryDependencies += "it.unibz.inf.ontop" % "ontop-quest-sesame" % "1.18.1"

resolvers +=  "XQJ Repository" at "http://files.basex.org/maven"

libraryDependencies += "org.basex" % "basex" % "7.6"

resolvers += "XQL Maven Repo" at "http://xqj.net/maven"

<dependency>
  <groupId>net.xqj</groupId>
  <artifactId>basex-xqj</artifactId>
  <version>1.2.0</version>
</dependency>
<dependency>
  <groupId>com.xqj2</groupId>
  <artifactId>xqj2</artifactId>
  <version>0.1.0</version>
</dependency>
<dependency>
  <groupId>javax.xml.xquery</groupId>
  <artifactId>xqj-api</artifactId>
  <version>1.0</version>
</dependency>


        
