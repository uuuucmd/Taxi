# API Gateway

`api-gateway` is the external HTTP entry point for the passenger and driver APIs. It uses Nacos discovery and only defines explicit routes for those two API services; discovery-based automatic routes are disabled.

| Gateway path | Nacos service | Downstream path |
| --- | --- | --- |
| `/passenger/**` | `api-passenger` | Path without the `/passenger` prefix |
| `/driver/**` | `api-driver` | Path without the `/driver` prefix |

For example, `POST /passenger/order/add` is forwarded to `api-passenger` as `POST /order/add`. The request body and headers, including `Authorization`, are forwarded unchanged so the API service can apply its existing authentication and business checks.

The gateway does not route to `service-order`. The `api-boss` route is intentionally omitted until that API has authentication and authorization. Keep API and business-service ports private in deployment network rules; adding the gateway does not by itself block direct connections to those service ports.

The existing `service-sse-push` client connection and `service-pay` provider callback are separate ingress paths and are not routed by this gateway yet. Account for those flows explicitly before restricting their ports; do not expose unrelated business-service ports such as `service-order`.

Run Nacos and the API services first, then start the gateway from the repository root:

```powershell
mvn -pl api-gateway spring-boot:run
```

The gateway listens on port `8080` by default. Set `GATEWAY_PORT` to override it, and use the existing `NACOS_SERVER_ADDR`, `NACOS_USERNAME`, and `NACOS_PASSWORD` environment variables to configure Nacos.
