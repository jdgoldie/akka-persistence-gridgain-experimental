**Overview**

This project is an experiment in managing the akka-persistence journal as a stream.  GridGain was selected as the basis for this experiment because it offers streaming and an in-memory data fabric with indexing and query support.  The goal of the project is to demonstrate that streaming the journal opens up the possibility of generating *real-time projections of application state* and additionally, allows for the application of *complex event processing* at an application wide level.

The current state of this project is a minimum-viable-journal implementation that passes [Martin Krasser's akka-persistence-testkit](https://github.com/krasserm/akka-persistence-testkit).  (I did not use the the akka experimental tck since it tests deprecated methods which I did not implement.  This really needs to be fixed.)

**Plans**

There is still a lot of work to do with the journal to see if the ideas it is testing will work.  In a rough semblance of priority order, the roadmap is:

- Determine *durability* strategy (file system?, etc?)
- Move *index storage* to GridGain cache
- Accomodate *deployment* scenarios

**Adding to your project**

Feel free to experiment with the code if you like.  It is still a very rough work-in-progress.  You could even use it in production if you are extremely daring, but I would not recommend it.

[![Build Status](https://travis-ci.org/jdgoldie/akka-persistence-gridgain-experimental.svg)](https://travis-ci.org/jdgoldie/akka-persistence-gridgain-experimental)

[![Coverage Status](https://img.shields.io/coveralls/jdgoldie/akka-persistence-gridgain-experimental.svg)](https://coveralls.io/r/jdgoldie/akka-persistence-gridgain-experimental)

Add a resolver:

	defaultResolvers += ("jdgoldie at bintray" at "http://dl.bintray.com/jdgoldie/maven")

and the dependency:

	  "com.github.jdgoldie" %% "akka-persistence-gridgain-experimental" % "0.0.1"

