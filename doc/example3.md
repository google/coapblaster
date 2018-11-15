# Serving an Observable Resource #

Server program:

    import com.google.iot.m2m.LocalEndpointManager;
    import com.google.iot.m2m.LocalEndpoint;
    import com.google.iot.m2m.Server;
    import com.google.iot.m2m.Coap;
    import com.google.iot.m2m.Resource;
    import com.google.iot.m2m.Observable;
    import com.google.iot.m2m.InboundRequestHandler;
    import com.google.iot.m2m.Code;
    import java.util.concurrent.ScheduledExecutorService;
    import static java.util.concurrent.TimeUnit.*;

    LocalEndpointManager manager = new LocalEndpointManager();

    Server server = new Server(manager);

    LocalEndpoint udpEndpoint = manager.getLocalEndpointForScheme(Coap.SCHEME_UDP);

    server.addLocalEndpoint(udpEndpoint);

    Resource<InboundRequestHandler> rootResource = new Resource<>();
    Observable observable = new Observable();

    InboundRequestHandler timeHandler = new InboundRequestHandler() {
        public void onInboundRequest(InboundRequest inboundRequest) {
            if (!observable.handleInboundRequest(inboundRequest) {
                inboundRequest.sendSimpleResponse(
                    Code.RESPONSE_CONTENT,
                    "ms: " + System.currentTimeMillis());
            }
        }
    }

    ScheduledExecutorService scheduler = new Executors.newSingleThreadScheduledExecutor();

    scheduler.scheduleAtFixedRate(observable::trigger(), 1, 1, SECONDS);

    rootResource.addChild("time", timeHandler);

    server.setRequestHandler(rootResource);

    server.start();

Client program:

    import com.google.iot.m2m.LocalEndpointManager;
    import com.google.iot.m2m.Client;
    import com.google.iot.m2m.Option;
    import com.google.iot.m2m.Transaction;
    import com.google.iot.m2m.Message;
    import java.util.concurrent.ScheduledExecutorService;

    LocalEndpointManager manager = new LocalEndpointManager();

    Client client = new Client(manager, "coap://127.0.0.1/");

    Transaction transaction = client.newRequestBuilder()
            .changePath("/time")
            .addOption(Option.OBSERVE)
            .send();

    ScheduledExecutorService executor
            = new Executors.newSingleThreadScheduledExecutor();

    transaction.registerCallback(executor, new Transaction.Callback() {
        public void onTransactionResponse(LocalEndpoint le, Message response) {
            System.out.println(response.getPayloadAsString());
        }
    });

Client output:

    ms: 847903
    ms: 848905
    ms: 849906
    ms: 850906
    ...
