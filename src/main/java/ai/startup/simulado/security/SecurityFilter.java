package ai.startup.simulado.security;

import ai.startup.simulado.auth.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Set;

public class SecurityFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/actuator/health"
    );

    public SecurityFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {

        addCors(res);
        if ("OPTIONS".equalsIgnoreCase(req.getMethod())) {
            res.setStatus(HttpServletResponse.SC_OK);
            return;
        }

        if (isPublic(req)) {
            chain.doFilter(req, res);
            return;
        }

        String token = extractToken(req);
        if (token == null || token.isBlank()) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing or invalid authentication token");
            return;
        }

        Claims claims;
        try { claims = jwtService.validar(token); }
        catch (Exception e) {
            res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
            return;
        }

        String email = claims.get("email", String.class);
        String permissao = claims.get("permissao", String.class);
        if (permissao == null) permissao = "USER";

        req.setAttribute("authEmail", email);
        req.setAttribute("authPermissao", permissao);

        chain.doFilter(req, res);
    }

    /**
     * Extrai JWT token: primeiro tenta cookie "jwt", depois Authorization header
     */
    private String extractToken(HttpServletRequest request) {
        // 1. Tentar obter de cookie (prioridade)
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if ("jwt".equals(cookie.getName())) {
                    String value = cookie.getValue();
                    if (value != null && !value.isBlank()) {
                        return value;
                    }
                }
            }
        }

        // 2. Fallback: Authorization header (retrocompatibilidade)
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }

        return null;
    }

    private boolean isPublic(HttpServletRequest req) {
        String path = req.getRequestURI();
        for (String p : PUBLIC_PATHS) if (pathMatcher.match(p, path)) return true;
        return false;
    }

    private void addCors(HttpServletResponse res) {
        res.setHeader("Access-Control-Allow-Origin", "http://localhost:5173");
        res.setHeader("Access-Control-Allow-Credentials", "true");
        res.setHeader("Access-Control-Allow-Methods", "GET,POST,PUT,DELETE,OPTIONS");
        res.setHeader("Access-Control-Allow-Headers", "Authorization,Content-Type");
        res.setHeader("Access-Control-Expose-Headers", "Authorization");
        res.setHeader("Access-Control-Max-Age", "3600");
    }
}
