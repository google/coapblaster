# Serving a Resource #

Server program:

    import com.google.iot.m2m.LocalEndpointManager;
    import com.google.iot.m2m.Server;
    import com.google.iot.m2m.LocalEndpoint;
    import com.google.iot.m2m.Coap;
    import com.google.iot.m2m.Resource;
    import com.google.iot.m2m.InboundRequestHandler;
    import com.google.iot.m2m.Code;

    LocalEndpointManager manager = new LocalEndpointManager();

    Server server = new Server(manager);

    LocalEndpoint udpEndpoint = manager.getLocalEndpointForScheme(Coap.SCHEME_UDP);

    server.addLocalEndpoint(udpEndpoint);

    Resource<InboundRequestHandler> rootResource = new Resource<>();
    Resource<InboundRequestHandler> testFolder = new Resource<>();

    InboundRequestHandler helloHandler = new InboundRequestHandler() {
        public void onInboundRequest(InboundRequest inboundRequest) {
            inboundRequest.sendSimpleResponse(Code.RESPONSE_CONTENT, "Hello, World!");
        }
    }

    testFolder.addChild("hello", helloHandler);
    rootResource.addChild("test", testFolder);

    server.setRequestHandler(rootResource);

    server.start();

Client program:

    import com.google.iot.m2m.LocalEndpointManager;
    import com.google.iot.m2m.Client;
    import com.google.iot.m2m.Transaction;
    import com.google.iot.m2m.Message;

    LocalEndpointManager manager = new LocalEndpointManager();

    Client client = new Client(manager, "coap://127.0.0.1/test/hello");

    Transaction transaction = client.newRequestBuilder().send();

    Message response = transaction.getResponse();

    System.out.println("Got response: " + response.getPayloadAsString());

Client output:

    Got response: Hello, World!
