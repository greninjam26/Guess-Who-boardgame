package com.guesswho.web;

import com.guesswho.api.ApiVersion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Turns away clients too old for this server, and says why.
 *
 * <p>A filter rather than a check in each controller, because the one thing
 * worse than no version check is one that a new endpoint can forget to make.
 * Every path under {@code /api} goes through this, uniformly — a rule people
 * have to remember per endpoint is a rule that decays.</p>
 *
 * <p>It answers every request with the server's own version too, so a client can
 * tell how far behind it is without having to fail first.</p>
 */
@Component
class ApiVersionFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        response.setHeader(ApiVersion.HEADER, String.valueOf(ApiVersion.CURRENT));
        int claimed = ApiVersion.claimedBy(request.getHeader(ApiVersion.HEADER));
        if (ApiVersion.isSupported(claimed)) {
            chain.doFilter(request, response);
            return;
        }
        //426 rather than 400: the request was not malformed, the client is. The
        //body says what to do about it in words a player can act on, because the
        //status code alone reaches nobody who is not reading a log.
        response.setStatus(HttpServletResponse.SC_UPGRADE_REQUIRED);
        response.setContentType("application/json");
        response.getWriter().write("""
                {"detail":"This version of Guess Who is too old to play online. \
                Download the latest one and try again."}""");
    }

    /** Only the API is versioned; the rest of the server has no contract to keep. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api");
    }
}
