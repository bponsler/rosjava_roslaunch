# rosjava_roslaunch
An implementation of roslaunch in Java

NOTE: this project is very much in its early stages and there are many pieces of functionality that are not yet implemented

The following items are known to be not implemented:

    - launching of remote nodes
    - launching of test nodes
    - the --child command line argument
    - the - command line option

However -- this list is not exhaustive.

This project currently requires the following libraries:

  - Apache commons lang:
    download the jar from http://commons.apache.org/proper/commons-lang/download_lang.cgi
  - Apache commons codec:
    download the jar from https://commons.apache.org/proper/commons-codec/download_codec.cgi
  - SnakeYaml:
    download the jar from http://repo1.maven.org/maven2/org/yaml/snakeyaml/1.16/
  - JSch
    download the jar from http://www.jcraft.com/jsch/