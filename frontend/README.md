# FlyTaxi Frontend

Vue 3 + Vite frontend for the local FlyTaxi backend services.

## Run

```powershell
cd <path-to-repository>\frontend
npm install
npm run dev -- --host 0.0.0.0
```

Open `http://localhost:5173/`.

## Backend Proxy

The dev server proxies requests to the local backend ports:

- `/api/passenger/*` -> `http://localhost:8081/*`
- `/api/driver/*` -> `http://localhost:8088/*`
- `/api/boss/*` -> `http://localhost:8087/*`

Start the backend services first from the repository root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\start-flytaxi.ps1
```

## Trip Addresses

Passenger estimates and orders resolve the entered addresses through the authenticated
`/api/passenger/locations/geocode` endpoint and `service-map` before sending coordinates.
Configure `AMAP_KEY` in the repository `.env` with an Amap Web Service key. Addresses
should include a city and a specific location. Unresolved or ambiguous addresses show
an error rather than using preset coordinates. Editing either address or the vehicle
type clears the previous estimate; click the estimate button to recalculate.

## Driver Orders

The driver order screen loads the signed-in driver's latest 100 assigned orders
through `/api/driver/order/list`. Drivers select a route from the pending or finished
list; order and driver identifiers are kept internal. Available trip actions follow
the order status, and successful actions refresh the list. The driver API derives
the phone from the login token and checks order ownership before changing a trip.

## Admin Driver Directory

The admin driver list uses `GET /api/boss/driver-users` with `page`, `pageSize`,
optional `keyword` (name or phone), and optional `state` (1 enabled, 0 disabled).
The backend returns `records`, `total`, `page`, and `pageSize`; page sizes are
limited to 100. Both enabled and disabled drivers are included by default.
Only basic directory fields are returned, not license or contract details.
Select the edit action to update a driver via the existing `PUT /driver-user`
endpoint. Adding a driver returns to the list. The current admin API has no
admin authentication; keep it on the trusted local network until access controls
are implemented.

## Dispatch Readiness

A driver directory entry alone does not make a driver available. Sign in as the
driver and register a vehicle, or have an administrator add and bind an enabled
vehicle, then start work. Vehicle city names are resolved through `service-map` and stored as
six-digit city codes; numeric inputs must be city codes, not district or province
codes. Driver enabled state is 1; vehicle enabled state is 0.

Order preflight uses the pricing city from `fareType`, not the departure district.
Availability queries the driver, work status, binding, and vehicle tables directly,
not the legacy `v_city_driver_work_status` view. Local simulation is enabled by
default (`LOCAL_SIMULATION=true`); dispatch searches vehicle positions in Redis,
then checks vehicle city, enabled state, type, driver work status, and ongoing
orders. No driver or vehicle is activated automatically by an order request.

The driver's vehicle panel reloads the signed-in account's vehicle before every
location upload. An unbound account shows a registration form and does not upload a
placeholder vehicle ID. The upload API derives the vehicle from the driver's token
and active binding. Location timestamps use fresh epoch milliseconds when the
optional time field is empty; storage failures are not reported as success.

Drivers can register a new vehicle in the vehicle panel. The server uses the
signed-in driver's phone to create the vehicle and binding in one database
transaction; client-supplied IDs, state, and map identifiers are ignored. A plate
already registered in the fleet cannot be claimed by another driver. Drivers
with an existing binding must contact an administrator to change vehicles.

## Local Simulation

Keep `AMAP_KEY` configured for address resolution and ordinary driving routes.
`AMAP_SID`, terminal IDs, and trace IDs are not required in the default local
mode. Both driver and admin vehicle registration skip Amap track provisioning.

In the driver vehicle panel, enter a detailed location and select the location
action, then save the simulated position. Manual coordinates remain available.
Start work before placing a nearby passenger order. Local dispatch searches
within 1, 3, and 5 km, nearest first. Coordinates always use longitude,latitude.
Positions expire from dispatch after `LOCAL_LOCATION_TTL_SECONDS` (default 1800
seconds); upload a fresh position when testing after this interval. Older uploads
cannot replace a newer position, and invalid batches are rejected before writing.
Redis stores the latest position, not a complete driving track.

Simulated trip settlement uses the order's planned driving route distance in
meters and duration in seconds, followed by the existing price calculation and
finalization retry flow. This is a simulation, not billing from actual movement.
No local location is uploaded to the Amap track service. Binding IDs returned by
the driver API are strings so JavaScript does not round database identifiers.

The legacy track integration is retained behind `LOCAL_SIMULATION=false`. That
mode still requires valid AMAP_KEY and AMAP_SID and vehicle map initialization.
Use the same mode for all services and restart them after changing it. Internal
service endpoints are intended for the trusted local development network only.
