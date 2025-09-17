package ai.startup.simulado.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;

@Service
public class JwtService {
    private final Key key;

    public JwtService(@Value("${jwt.secret}") String secretBase64) {
        if (secretBase64 == null || secretBase64.isBlank())
            throw new IllegalStateException("JWT secret not configured (JWT_SECRET).");
        byte[] bytes = Decoders.BASE64.decode(secretBase64);
        if (bytes.length < 32) throw new IllegalStateException("JWT secret must be >= 32 bytes.");
        this.key = Keys.hmacShaKeyFor(bytes);
    }

    public Claims validar(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
    }
}