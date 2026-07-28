-- =============================================================================
-- Config schema for the forward-auth plugin.
--
-- Everything the handler reads off `conf` must be declared here — Kong validates
-- the declarative config against this at boot, so a typo in kong.yml is a
-- startup failure with a clear message rather than a nil dereference on the
-- first request.
-- =============================================================================

return {
  name = "forward-auth",
  fields = {
    { config = {
        type = "record",
        fields = {

          -- The endpoint that trades a token for a userId. A service NAME, not
          -- localhost: inside the Kong container localhost is Kong itself.
          { auth_endpoint = {
              type = "string",
              required = true,
              default = "http://authservice:8080/auth/v1/ping",
            } },

          -- Prefixes that skip the check. Matched as exact-or-path-prefix, so
          -- "/auth/v1/login" covers "/auth/v1/login" and "/auth/v1/login/x" but
          -- NOT "/auth/v1/loginfoo".
          --
          -- The auth service's own endpoints MUST stay here: login/signup are
          -- how you get a token, and /ping is this plugin's own upstream.
          { public_paths = {
              type = "array",
              elements = { type = "string" },
              default = {
                "/auth/v1/login",
                "/auth/v1/signup",
                "/auth/v1/refreshToken",
                "/auth/v1/ping",
              },
            } },

          -- The header the verified identity is injected as. Must match what the
          -- services read: @RequestHeader("X-User-Id") in the Java controllers,
          -- request.headers.get('x-user-id') in dsService.
          { upstream_header = {
              type = "string",
              default = "X-User-Id",
            } },

          -- Milliseconds to wait on the ping call before giving up (-> 503).
          { timeout = {
              type = "number",
              default = 5000,
            } },

          -- Seconds to cache a token -> userId mapping. 0 disables caching.
          --
          -- Off by default on purpose: caching means a revoked token keeps
          -- working until the entry expires. Turn it on once the extra round
          -- trip per request actually hurts, and keep the value small.
          { cache_ttl = {
              type = "number",
              default = 0,
            } },
        },
      },
    },
  },
}
