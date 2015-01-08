//
//  Copyright 2015 Joshua Goldie
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.
//

import sbt.Keys._
import bintray.AttrMap
import bintray._
import scoverage.ScoverageSbtPlugin._

lazy val root = (project in file("."))
  .settings(name := "akka-persistence-gridgain-experimental")
  .settings(version := "0.0.1")
  .settings(scalaVersion := "2.11.2")
  .settings(organization := "com.github.jdgoldie")
  .settings(crossScalaVersions := Seq("2.11.2","2.10.4"))
  .settings(licenses += ("Apache-2.0", url("http://www.apache.org/licenses/LICENSE-2.0")))
  .settings(bintrayPublishSettings:_*)
  .settings(bintray.Keys.repository in bintray.Keys.bintray := "maven")
  .settings(bintray.Keys.bintrayOrganization in bintray.Keys.bintray := None)
  .settings(resolvers ++= Seq("krasserm at bintray" at "http://dl.bintray.com/krasserm/maven"))
  .settings(libraryDependencies ++= Seq(
    "com.typesafe.akka"   %% "akka-persistence-experimental"      % "2.3.8" % "compile" withSources() withJavadoc(),
    "com.typesafe.akka"   %% "akka-actor"                         % "2.3.8" % "compile" withSources() withJavadoc(),
    "com.typesafe.akka"   %% "akka-remote"                        % "2.3.8" % "test"    withSources() withJavadoc(),
    "com.typesafe.akka"   %% "akka-testkit"                       % "2.3.8" % "test"    withSources() withJavadoc(),
    "com.github.krasserm" %% "akka-persistence-testkit"           % "0.3.4" % "test",
    "org.scalatest"       %% "scalatest"                          % "2.1.4" % "test"    withSources() withJavadoc(),
    "org.gridgain"        %  "gridgain-fabric"                    % "6.5.6" % "compile",
    "org.scoverage"       %% "scalac-scoverage-plugin"            % "1.0.1" % "compile"))

