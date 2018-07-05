package org.apereo.cas.support.oauth.web.response.accesstoken;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.tuple.Pair;
import org.apereo.cas.authentication.DefaultAuthenticationBuilder;
import org.apereo.cas.support.oauth.OAuth20Constants;
import org.apereo.cas.support.oauth.web.response.accesstoken.ext.AccessTokenRequestDataHolder;
import org.apereo.cas.ticket.OAuthToken;
import org.apereo.cas.ticket.TicketGrantingTicket;
import org.apereo.cas.ticket.TicketState;
import org.apereo.cas.ticket.accesstoken.AccessToken;
import org.apereo.cas.ticket.accesstoken.AccessTokenFactory;
import org.apereo.cas.ticket.code.OAuthCode;
import org.apereo.cas.ticket.refreshtoken.RefreshToken;
import org.apereo.cas.ticket.refreshtoken.RefreshTokenFactory;
import org.apereo.cas.ticket.registry.TicketRegistry;
import org.apereo.cas.util.function.FunctionUtils;

/**
 * This is {@link OAuth20DefaultTokenGenerator}.
 *
 * @author Misagh Moayyed
 * @since 5.2.0
 */
@Slf4j
@AllArgsConstructor
public class OAuth20DefaultTokenGenerator implements OAuth20TokenGenerator {


    /**
     * The Access token factory.
     */
    protected final AccessTokenFactory accessTokenFactory;

    /**
     * The refresh token factory.
     */
    protected final RefreshTokenFactory refreshTokenFactory;

    /**
     * The Ticket registry.
     */
    protected final TicketRegistry ticketRegistry;

    @Override
    public Pair<AccessToken, RefreshToken> generate(final AccessTokenRequestDataHolder holder) {
        LOGGER.debug("Creating refresh token for [{}]", holder.getService());
        val authn = DefaultAuthenticationBuilder
            .newInstance(holder.getAuthentication())
            .addAttribute(OAuth20Constants.GRANT_TYPE, holder.getGrantType().toString())
            .build();

        LOGGER.debug("Creating access token for [{}]", holder);
        val accessToken = this.accessTokenFactory.create(holder.getService(),
            authn, holder.getTicketGrantingTicket(), holder.getScopes());

        LOGGER.debug("Created access token [{}]", accessToken);
        addTicketToRegistry(accessToken, holder.getTicketGrantingTicket());
        LOGGER.debug("Added access token [{}] to registry", accessToken);

        if (holder.getToken() instanceof OAuthCode) {
            val codeState = TicketState.class.cast(holder.getToken());
            codeState.update();

            if (holder.getToken().isExpired()) {
                this.ticketRegistry.deleteTicket(holder.getToken().getId());
            } else {
                this.ticketRegistry.updateTicket(holder.getToken());
            }
            this.ticketRegistry.updateTicket(holder.getTicketGrantingTicket());
        }

        val refreshToken = FunctionUtils.doIf(holder.isGenerateRefreshToken(),
            () -> generateRefreshToken(holder),
            () -> {
                LOGGER.debug("Service [{}] is not able/allowed to receive refresh tokens", holder.getService());
                return null;
            }).get();

        return Pair.of(accessToken, refreshToken);
    }


    /**
     * Add ticket to registry.
     *
     * @param ticket               the ticket
     * @param ticketGrantingTicket the ticket granting ticket
     */
    protected void addTicketToRegistry(final OAuthToken ticket, final TicketGrantingTicket ticketGrantingTicket) {
        LOGGER.debug("Adding OAuth ticket [{}] to registry", ticket);
        this.ticketRegistry.addTicket(ticket);
        if (ticketGrantingTicket != null) {
            LOGGER.debug("Updating ticket-granting ticket [{}]", ticketGrantingTicket);
            this.ticketRegistry.updateTicket(ticketGrantingTicket);
        }
    }

    private RefreshToken generateRefreshToken(final AccessTokenRequestDataHolder responseHolder) {
        LOGGER.debug("Creating refresh token for [{}]", responseHolder.getService());
        val refreshToken = this.refreshTokenFactory.create(responseHolder.getService(),
            responseHolder.getAuthentication(), responseHolder.getTicketGrantingTicket(), responseHolder.getScopes());
        LOGGER.debug("Adding refresh token [{}] to the registry", refreshToken);
        addTicketToRegistry(refreshToken, responseHolder.getTicketGrantingTicket());
        return refreshToken;
    }
}
