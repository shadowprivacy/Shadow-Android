package su.sres.signalservice.internal.websocket;

import org.whispersystems.libsignal.util.guava.Function;

import su.sres.signalservice.api.push.exceptions.AuthorizationFailedException;
import su.sres.signalservice.api.push.exceptions.CaptchaRequiredException;
import su.sres.signalservice.api.push.exceptions.DeprecatedVersionException;
import su.sres.signalservice.api.push.exceptions.ExpectationFailedException;
import su.sres.signalservice.api.push.exceptions.MalformedResponseException;
import su.sres.signalservice.api.push.exceptions.NonSuccessfulResponseCodeException;
import su.sres.signalservice.api.push.exceptions.NotFoundException;
import su.sres.signalservice.api.push.exceptions.ProofRequiredException;
import su.sres.signalservice.api.push.exceptions.RateLimitException;
import su.sres.signalservice.api.push.exceptions.ServerRejectedException;
import su.sres.signalservice.internal.push.AuthCredentials;
import su.sres.signalservice.internal.push.DeviceLimit;
import su.sres.signalservice.internal.push.DeviceLimitExceededException;
import su.sres.signalservice.internal.push.LockedException;
import su.sres.signalservice.internal.push.MismatchedDevices;
import su.sres.signalservice.internal.push.ProofRequiredResponse;
import su.sres.signalservice.internal.push.PushServiceSocket;
import su.sres.signalservice.internal.push.StaleDevices;
import su.sres.signalservice.internal.push.exceptions.MismatchedDevicesException;
import su.sres.signalservice.internal.push.exceptions.StaleDevicesException;
import su.sres.signalservice.internal.util.JsonUtil;
import su.sres.signalservice.internal.util.Util;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * A default implementation of a {@link ErrorMapper} that can parse most known application
 * errors.
 * <p>
 * Can be extended to add custom error mapping via {@link #extend()}.
 * <p>
 * While this call can be used directly, it is primarily intended to be used as part of
 * {@link DefaultResponseMapper}.
 */
public final class DefaultErrorMapper implements ErrorMapper {

  private static final DefaultErrorMapper INSTANCE = new DefaultErrorMapper();

  private final Map<Integer, ErrorMapper> customErrorMappers;

  public static DefaultErrorMapper getDefault() {
    return INSTANCE;
  }

  public static DefaultErrorMapper.Builder extend() {
    return new DefaultErrorMapper.Builder();
  }

  private DefaultErrorMapper() {
    this(Collections.emptyMap());
  }

  private DefaultErrorMapper(Map<Integer, ErrorMapper> customErrorMappers) {
    this.customErrorMappers = customErrorMappers;
  }

  public Throwable parseError(WebsocketResponse websocketResponse) {
    return parseError(websocketResponse.getStatus(), websocketResponse.getBody(), websocketResponse::getHeader);
  }

  @Override
  public Throwable parseError(int status, String body, Function<String, String> getHeader) {
    if (customErrorMappers.containsKey(status)) {
      return customErrorMappers.get(status).parseError(status, body, getHeader);
    }

    switch (status) {
      case 401:
      case 403:
        return new AuthorizationFailedException(status, "Authorization failed!");
      case 402:
        return new CaptchaRequiredException();
      case 404:
        return new NotFoundException("Not found");
      case 409:
        try {
          return new MismatchedDevicesException(JsonUtil.fromJsonResponse(body, MismatchedDevices.class));
        } catch (MalformedResponseException e) {
          return e;
        }
      case 410:
        try {
          return new StaleDevicesException(JsonUtil.fromJsonResponse(body, StaleDevices.class));
        } catch (MalformedResponseException e) {
          return e;
        }
      case 411:
        try {
          return new DeviceLimitExceededException(JsonUtil.fromJsonResponse(body, DeviceLimit.class));
        } catch (MalformedResponseException e) {
          return e;
        }
      case 429:
        return new RateLimitException("Rate limit exceeded: " + status);
      case 417:
        return new ExpectationFailedException();
      case 423:
        PushServiceSocket.RegistrationLockFailure accountLockFailure;
        try {
          accountLockFailure = JsonUtil.fromJsonResponse(body, PushServiceSocket.RegistrationLockFailure.class);
        } catch (MalformedResponseException e) {
          return e;
        }

        return new LockedException(accountLockFailure.length,
                                   accountLockFailure.timeRemaining);
      case 428:
        ProofRequiredResponse proofRequiredResponse;
        try {
          proofRequiredResponse = JsonUtil.fromJsonResponse(body, ProofRequiredResponse.class);
        } catch (MalformedResponseException e) {
          return e;
        }
        String retryAfterRaw = getHeader.apply("Retry-After");
        long retryAfter = Util.parseInt(retryAfterRaw, -1);

        return new ProofRequiredException(proofRequiredResponse, retryAfter);
      case 499:
        return new DeprecatedVersionException();
      case 508:
        return new ServerRejectedException();
    }

    if (status != 200 && status != 202 && status != 204) {
      return new NonSuccessfulResponseCodeException(status, "Bad response: " + status);
    }

    return null;
  }

  public static class Builder {
    private final Map<Integer, ErrorMapper> customErrorMappers = new HashMap<>();

    public Builder withCustom(int status, ErrorMapper errorMapper) {
      customErrorMappers.put(status, errorMapper);
      return this;
    }

    public ErrorMapper build() {
      return new DefaultErrorMapper(customErrorMappers);
    }
  }
}
