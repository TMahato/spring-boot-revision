-- =============================================================================
-- forward-auth — a custom Kong plugin.
--
-- Authenticates every request at the edge by asking authService who the caller
-- is, then handing the verified identity to the upstream service as a header.
--
--   client ──Authorization: Bearer <jwt>──► kong
--                                            │  GET /auth/v1/ping
--                                            ▼
--                                       authService ──► 200 + userId | 401
--                                            │
--                                            ▼
--                                   sets X-User-Id, proxies upstream
--
-- The downstream services validate nothing — they read X-User-Id and trust it.
-- That trust rests on two things, and BOTH are required:
--
--   1. this plugin CLEARS any client-supplied X-User-Id before setting its own
--      (see the spoof guard below), and
--   2. the services are not published to the host, so Kong is the only route in
--      (see the note at the bottom of docker-compose.yml).
--
-- See notes/chapter-7 §10.5.
-- =============================================================================

local http = require "resty.http"

local ForwardAuth = {
  -- Deliberately BELOW rate-limiting (901) so the rate limiter runs first.
  -- Above it, a flood of unauthenticated requests would each trigger a call to
  -- authService before being throttled — turning the gateway into an
  -- amplifier pointed at the service we most need to stay up.
  PRIORITY = 800,
  VERSION = "1.0.0",
}

-- Does `path` sit under any of the configured public prefixes?
local function is_public(path, public_paths)
  for _, prefix in ipairs(public_paths) do
    if path == prefix or path:sub(1, #prefix + 1) == prefix .. "/" then
      return true
    end
  end
  return false
end

-- The actual call to authService. Returns userId, or nil + an error kind
-- ("unauthorized" | "unavailable") so the caller can pick the right status.
local function resolve_user_id(conf, auth_header)
  local httpc = http.new()
  httpc:set_timeout(conf.timeout)

  local res, err = httpc:request_uri(conf.auth_endpoint, {
    method = "GET",
    headers = { ["Authorization"] = auth_header },
    keepalive_timeout = 60000,
    keepalive_pool = 10,
  })

  if not res then
    kong.log.err("forward-auth: ", conf.auth_endpoint, " unreachable: ", err)
    return nil, "unavailable"
  end

  if res.status ~= 200 then
    return nil, "unauthorized"
  end

  -- /auth/v1/ping returns the bare userId as text/plain.
  local user_id = string.match(res.body or "", "^%s*(.-)%s*$")
  if not user_id or user_id == "" then
    kong.log.err("forward-auth: ping returned 200 with an empty body")
    return nil, "unauthorized"
  end

  return user_id
end

function ForwardAuth:access(conf)
  local path = kong.request.get_path()

  -- Public routes bypass the check entirely. This list MUST cover:
  --   /auth/v1/login, /signup, /refreshToken — how you OBTAIN a token, so
  --      requiring one here is a deadlock; and
  --   /auth/v1/ping — this plugin's own upstream. Without it every request
  --      recurses into itself until Kong runs out of sockets.
  -- Those endpoints are not unprotected: Spring Security guards ping and
  -- authenticates anything not on its own permitAll list.
  if is_public(path, conf.public_paths) then
    return
  end

  -- SPOOF GUARD. Drop whatever the client sent under this header name before
  -- we set our own. Without this line a caller simply supplies X-User-Id and
  -- becomes any user they like — this is the most important line in the file.
  kong.service.request.clear_header(conf.upstream_header)

  local auth_header = kong.request.get_header("Authorization")
  if not auth_header then
    return kong.response.exit(401, { message = "Missing Authorization header" })
  end

  local user_id, failure

  if conf.cache_ttl and conf.cache_ttl > 0 then
    -- Cache the token -> userId mapping so a burst of requests from one client
    -- costs one ping rather than N. Keyed on the token itself.
    --
    -- TRADE-OFF: a revoked token keeps working until the entry expires. Keep
    -- the TTL short, and leave it at 0 if immediate revocation matters more
    -- than the round trip.
    local cache_key = "forward-auth:" .. auth_header
    local err
    user_id, err = kong.cache:get(cache_key, { ttl = conf.cache_ttl },
      function()
        local id, kind = resolve_user_id(conf, auth_header)
        if not id then
          -- Returning nil here would be cached as a negative hit; raise instead
          -- so failures are never cached.
          return nil, kind
        end
        return id
      end)
    if not user_id then
      failure = err
    end
  else
    user_id, failure = resolve_user_id(conf, auth_header)
  end

  if not user_id then
    -- "Auth service is down" is NOT the same as "your token is bad". Answering
    -- 401 during an outage sends every client off to re-login, stampeding the
    -- service that is already struggling.
    if failure == "unavailable" then
      return kong.response.exit(503, { message = "Auth service unavailable" })
    end
    return kong.response.exit(401, { message = "Unauthorized" })
  end

  -- Hand the verified identity to the upstream service.
  kong.service.request.set_header(conf.upstream_header, user_id)
end

return ForwardAuth
