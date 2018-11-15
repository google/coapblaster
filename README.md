CoapBlaster
===========

CoapBlaster is an experimental open-source [CoAP](https://tools.ietf.org/html/rfc7252) client/server library
written in Java.

## Design Goals ##

The initial design goals for CoapBlaster were best approximated as the following:

 * Full RFC7252 support
 * Flexible, easy-to-use API that supports both synchronous and asynchronous design patterns
 * Robust, easy-to-use [resource observation](https://tools.ietf.org/html/rfc7641)
 * Easily swappable back-end to support various transports
 * Full support for DTLS, COSE, and possibly OSCORE

Most of these goals have already been met, while a few are still in progress.

Note that because CoapBlaster is still at the experimental/preview stage, it should not
be considered production ready.

## Current Features ##

Currently, CoapBlaster supports the following:

 * Full RFC7252 support for the Constrained Application Protocol
     * Retransmission/deduplication support
     * Tunable transmission parameters
 * API allows for both asynchronous and synchronous usage
 * Supports sending and receiving asynchronous CoAP responses
 * [Effortless](doc/example3.md) [RFC7641](https://tools.ietf.org/html/rfc7641) observation support
 * Support for block2 transfers on both client and server interfaces
 * Support for parsing and composing [RFC6690](https://tools.ietf.org/html/rfc6690)-style link formats
 * Support for diverting client requests through a CoAP proxy

## Planned Features ##

 * [DTLS](https://tools.ietf.org/html/rfc6347)
 * [COSE](https://tools.ietf.org/html/rfc8152)
 * [OSCORE](https://tools.ietf.org/html/draft-ietf-core-object-security-15)

## Documentation ##

 * [API JavaDoc](https://google.github.io/coapblaster/releases/latest/apidocs/)
 * [Github Project](https://github.com/google/coapblaster/)

## Usage Examples ##

### Fetching a simple resource ###

Program:

    import com.google.iot.m2m.LocalEndpointManager;
    import com.google.iot.m2m.Client;
    import com.google.iot.m2m.Transaction;
    import com.google.iot.m2m.Message;

    LocalEndpointManager manager = new LocalEndpointManager();

    Client client = new Client(manager, "coap://coap.me/test");

    Transaction transaction = client.newRequestBuilder().send();

    Message response = transaction.getResponse();

    System.out.println("Got response: " + response);

Which will print out a long line which looks something like this:

    <CONTENT IN ACK MID:44712 TOK:"f43c" 2.05 RADDR:"/134.102.218.18:5683" OPTS:[ETag:thZhiYJXp/I=, Content-Format:0] TEXT:"welcome to the ETSI plugtest! last change: 2018-1...">

### Additional Examples ###

 * [Example 1: Fetching resources](doc/example1.md)
 * [Example 2: Serving a simple resource](doc/example2.md)
 * [Example 3: Serving an observable resource](doc/example3.md)

## Building and Installing ##

This project uses Maven for building. Once Maven is installed, you
should be able to build and install the project by doing the
following:

    mvn verify
    mvn install

Note that the master branch of this project depends on
[CborTree](https://github.com/google/cbortree/), so you may need
to download, build, and install that project first.

### Adding to Projects ###

Gradle:

    dependencies {
	  compile 'com.google.iot.coap:coap:0.01.00'
	}

Maven:

    <dependency>
	  <groupId>com.google.iot.coap</groupId>
	  <artifactId>coap</artifactId>
	  <version>0.01.00</version>
    </dependency>

## Building and Installing ##

This project uses Maven for building. Once Maven is installed, you should be able to build
and install the project by doing the following:

    mvn verify
    mvn install

Note that the master branch of this project depends on [CborTree](https://github.com/google/cbortree/),
so you may need to download, build, and install those projects first.

## See Also ##

 * [CborTree](https://github.com/google/cbortree/)

## License ##

CoapBlaster is released under the [Apache 2.0 license](LICENSE).

	Copyright 2018 Google Inc.

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.

## Disclaimer ##

This is not an officially supported Google product.
