# Example 1: Fetching Large Resources #

Fetching large resources (even those that require block2 transfers)
are no different from small resources as far as the `Client` API is
concerned:

Program:

    import com.google.iot.m2m.LocalEndpointManager;
    import com.google.iot.m2m.Client;
    import com.google.iot.m2m.Transaction;
    import com.google.iot.m2m.Message;

    LocalEndpointManager manager = new LocalEndpointManager();

    Client client = new Client(manager, "coap://coap.me/");

    Transaction transaction = client.newRequestBuilder()
            .changePath("/large")
            .send();

    Message response = transaction.getResponse();

    System.out.println(response.getPayloadAsString());

Which will print out the following:

         0                   1                   2                   3
        0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |Ver| T |  TKL  |      Code     |          Message ID           |
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |   Token (if any, TKL bytes) ...
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |   Options (if any) ...
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
       |1 1 1 1 1 1 1 1|    Payload (if any) ...
       +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+

    [...]

       Token Length (TKL):  4-bit unsigned integer.  Indicates the length of
          the variable-length Token field (0-8 bytes).  Lengths 9-15 are
          reserved, MUST NOT be sent, and MUST be processed as a message
          format error.

       Code:  8-bit unsigned integer, split into a 3-bit class (most
          significant bits) and a 5-bit detail (least significant bits),
          documented as c.dd where c is a digit from 0 to 7 for the 3-bit
          subfield and dd are two digits from 00 to 31 for the 5-bit
          subfield.  The class can indicate a request (0), a success
          response (2), a client error response (4), or a server error
          response (5).  (All other class values are reserved.)  As a
          special case, Code 0.00 indicates an Empty message.  In case of a
          request, the Code field indicates the Request Method; in case of a
          response a Response Code.  Possible values are maintained in the
          CoAP Code Registries (Section 12.1).  The semantics of requests
          and responses are defined in Section 5.
