package su.sres.signalservice.api.subscriptions;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class SubscriptionClientSecret {

  private final String id;
  private final String clientSecret;

  @JsonCreator
  public SubscriptionClientSecret(@JsonProperty("clientSecret") String clientSecret) {
    this.id           = clientSecret.replaceFirst("_secret.*", "");
    this.clientSecret = clientSecret;
  }

  public String getId() {
    return id;
  }

  public String getClientSecret() {
    return clientSecret;
  }
}
