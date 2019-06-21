<p align="center">
  <a href="https://tigase.net/">
    <img
      alt="Tigase Socks5 Proxy Component"
      src="https://github.com/tigaseinc/website-assets/raw/master/tigase/images/tigase-logo.png?raw=true"
      width="300"
    />
  </a>
</p>

<p align="center">
  Tigase Socks5 Proxy Component
</p>

<p align="center">
  <img alt="Tigase Logo" src="https://github.com/tigaseinc/website-assets/raw/master/tigase/images/tigase-logo.png?raw=true" width="25"/>
  <img src="https://tc.tigase.net/app/rest/builds/buildType:(id:TigaseSocks5_Build)/statusIcon" width="100"/>
</p>

# What it is

Tigase Socks5 Proxy Component provides support for Socks5 proxy as described in [XEP-0065: SOCKS5 Bytestreams](https://xmpp.org/extensions/xep-0065.html) for Tigase XMPP Server and its additional components.

# Features

Tigase Socks5 Proxy Component provides:
* support for mediated Socks5 connections 
* accounting of used transfer
* limits of transfer usage per instance/domain/user

# Support

When looking for support, please first search for answers to your question in the available online channels:

* Our online documentation: [Tigase Docs](https://docs.tigase.net)
* Our online forums: [Tigase Forums](https://help.tigase.net/portal/community)
* Our online Knowledge Base [Tigase KB](https://help.tigase.net/portal/kb)

If you didn't find an answer in the resources above, feel free to submit your question to either our 
[community portal](https://help.tigase.net/portal/community) or open a [support ticket](https://help.tigase.net/portal/newticket).

# Downloads

You can download distribution version of Tigase XMPP Server which contains Tigase Socks5 Proxy Component directly from [here](https://github.com/tigaseinc/tigase-server/releases).

If you wish to downloand SNAPSHOT build of the development version of Tigase XMPP Server which contains Tigase Socks5 Proxy Component you can grab it from [here](https://build.tigase.net/nightlies/dists/latest/tigase-server-dist-max.zip).

# Installation and usage

Documentation of the project is part of the Tigase XMPP Server distribution package and it is also available as part of [Tigase XMPP Server documnetation page](https://docs.tigase.net/).

# Compilation 

Compilation of the project is very easy as it is typical Maven project. All you need to do is to execute
````bash
mvn package test
````
to compile the project and run unit tests.

# License

<img alt="Tigase Tigase Logo" src="https://github.com/tigaseinc/website-assets/raw/master/tigase/images/tigase-logo.png?raw=true" width="25"/> This is official <a href="https://tigase.net/">Tigase</a> Repository.
Copyright (c) 2013-2019 Tigase, Inc.

Licensed under AGPL License Version 3. Other licensing options available upon request.
